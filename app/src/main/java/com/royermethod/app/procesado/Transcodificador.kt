package com.royermethod.app.procesado

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Lo que hay que hacerle al video.
 *
 * @param ladoCorto a cuanto bajar el lado menor. 0 lo deja como esta.
 * @param fpsSalida a cuantos fps dejarlo. 0 lo deja como esta. Nunca sube por
 *   encima de los del original: sin generar frames, subirlos solo repite los
 *   que ya hay y eso no da fluidez, da tirones y peso de mas.
 * @param bpp bits por pixel del objetivo. Es la medida que de verdad manda:
 *   el mismo bitrate se ve muy distinto segun cuantos pixeles tenga que
 *   repartir, y por eso el ajuste no puede ser un bitrate fijo.
 */
data class Ajustes(
    val ladoCorto: Int,
    val fpsSalida: Int,
    val bpp: Float,
    /** 0 lo deja plano; 0.4 es lo que usaba el pipeline de escritorio. */
    val realce: Float = 0f,
    /**
     * Pasar cada fotograma de salida por el modelo de reconstruccion.
     *
     * Esto no es afilar. Afilar solo subraya lo que ya hay; el modelo devuelve
     * detalle que la compresion del original se llevo. Cuesta lo suyo: es otro
     * paso por la GPU por cada fotograma que sale.
     */
    val reconstruir: Boolean = false,
    /**
     * A cuantos fotogramas por segundo generar antes de mezclar. 0 lo apaga.
     *
     * Se generan intermedios con el modelo y luego se promedian de vuelta a
     * los fps de salida. Ese promedio es el desenfoque de movimiento: la
     * fluidez no viene de tener mas fotogramas en el archivo final, viene de
     * que cada uno lleve dentro el rastro del movimiento.
     */
    val fpsGenerados: Int = 0,
    /** Mezclar los generados. Sin esto solo se tira uno de cada tantos. */
    val estabilizar: Boolean = true,
    /** Tramos que van a camara lenta. Vacio = velocidad normal. */
    val tramos: List<Tramo> = emptyList()
)

/**
 * Un trozo del video que va mas despacio.
 *
 * Ralentizar bien no es repetir fotogramas: es soltar los que se generaron en
 * vez de promediarlos. Si se interpola a 240 y en lugar de mezclar ocho en uno
 * se sueltan de uno en uno, ese tramo dura ocho veces mas y se ve fluido,
 * porque cada fotograma es distinto de verdad.
 *
 * Por eso el factor no puede ser cualquiera: como mucho, tantas veces como
 * fotogramas se hayan generado por cada uno de salida. Para ir mas lento hay
 * que generar mas.
 */
data class Tramo(val desdeMs: Int, val hastaMs: Int, val factor: Int)

/** Lo que hizo de verdad, que no tiene por que ser lo que se le pidio. */
data class Resultado(
    val archivo: File,
    val ancho: Int,
    val alto: Int,
    val fps: Int,
    val bytes: Long
)

private const val TIEMPO_ESPERA = 10_000L

/**
 * Decodifica, reduce y vuelve a codificar.
 *
 * El fotograma nunca sale de la GPU: el decodificador lo deja como textura,
 * se dibuja reducido sobre la superficie del codificador y este lo comprime.
 * La alternativa, bajar cada fotograma a memoria para tocarlo, son varios
 * megas de ida y vuelta por fotograma y en un movil no sale a cuenta.
 *
 * El audio no se toca: se copian sus paquetes tal cual al archivo nuevo.
 * Recodificarlo solo anadiria una perdida que nadie ha pedido.
 */
fun transcodificar(
    context: Context,
    entrada: Uri,
    salida: File,
    ajustes: Ajustes,
    /** fotogramas hechos, fotogramas totales, avance de 0 a 1. */
    onProgreso: (Int, Int, Float) -> Unit = { _, _, _ -> }
): Resultado {
    val extractor = MediaExtractor()
    extractor.setDataSource(context, entrada, null)

    var pistaVideo = -1
    var pistaAudio = -1
    for (i in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
        if (pistaVideo < 0 && mime.startsWith("video/")) pistaVideo = i
        if (pistaAudio < 0 && mime.startsWith("audio/")) pistaAudio = i
    }
    require(pistaVideo >= 0) { "el archivo no tiene video" }

    val formatoOrigen = extractor.getTrackFormat(pistaVideo)
    val anchoOrigen = formatoOrigen.getInteger(MediaFormat.KEY_WIDTH)
    val altoOrigen = formatoOrigen.getInteger(MediaFormat.KEY_HEIGHT)
    val duracionUs = if (formatoOrigen.containsKey(MediaFormat.KEY_DURATION)) {
        formatoOrigen.getLong(MediaFormat.KEY_DURATION)
    } else 0L
    val giro = if (formatoOrigen.containsKey(MediaFormat.KEY_ROTATION)) {
        formatoOrigen.getInteger(MediaFormat.KEY_ROTATION)
    } else 0
    val fpsOrigen = entero(formatoOrigen, MediaFormat.KEY_FRAME_RATE, 30).coerceIn(1, 240)

    val (anchoDestino, altoDestino) = medidas(anchoOrigen, altoOrigen, ajustes.ladoCorto)

    // Solo con el modelo cargado tiene sentido pedir mas fotogramas de los que
    // trae el archivo. Sin el, subir los fps repetiria los que ya hay.
    val interpolando = ajustes.fpsGenerados > fpsOrigen && Rife.listo()

    val fpsDestino = when {
        ajustes.fpsSalida <= 0 -> fpsOrigen
        interpolando -> ajustes.fpsSalida
        else -> minOf(ajustes.fpsSalida, fpsOrigen)
    }
    val bitrate = (ajustes.bpp * anchoDestino * altoDestino * fpsDestino).toInt()
        .coerceAtLeast(200_000)

    val formatoSalida = MediaFormat.createVideoFormat(
        MediaFormat.MIMETYPE_VIDEO_AVC, anchoDestino, altoDestino
    ).apply {
        setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
        setInteger(MediaFormat.KEY_FRAME_RATE, fpsDestino)
        // Dos segundos entre fotogramas clave. Mas separados se gana calidad
        // por bit, pero las apps que rebobinan solo pueden saltar de clave en
        // clave y el video se vuelve incomodo de mover.
        setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        setInteger(
            MediaFormat.KEY_BITRATE_MODE,
            MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // El perfil alto es el que permite fotogramas B, que son los que
            // hacen que un edit con mucho movimiento no se coma el bitrate.
            // El codificador puede ignorarlo; si lo hace, sale igual pero peor.
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
            setInteger(MediaFormat.KEY_MAX_B_FRAMES, 2)
        }
    }

    val codificador = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
    codificador.configure(formatoSalida, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    val superficieEntrada = codificador.createInputSurface()

    val gl = EntornoGl(superficieEntrada)
    gl.activar()
    val reductor = Reductor(
        anchoOrigen, altoOrigen, anchoDestino, altoDestino, ajustes.realce
    )
    reductor.preparar()

    // Cuantos intermedios por cada hueco entre fotogramas del original, y
    // cuantos de esos se promedian para cada fotograma de salida.
    //
    // Los dos tienen que salir exactos. Si al promediar sobrara o faltara
    // medio fotograma, el reparto se descuadraria y el video iria a saltos:
    // ese fue el error del primer intento en el ordenador.
    val multiplicador = if (interpolando) {
        pasoLimpio(fpsOrigen, fpsDestino, ajustes.fpsGenerados)
    } else 0
    val porSalida = if (multiplicador > 0) (fpsOrigen * multiplicador) / fpsDestino else 0
    val interpola = multiplicador > 1 && porSalida > 0

    // Reconstruir tambien obliga a bajar el fotograma a memoria, igual que
    // interpolar: el modelo trabaja sobre pixeles, no sobre texturas.
    val mejorando = ajustes.reconstruir && Waifu2x.listo()
    if (interpola || mejorando) reductor.prepararIdaYVuelta()

    // El fotograma decodificado no aparece cuando se suelta el buffer sino
    // cuando avisa la textura, desde otro hilo. Sin esperar ese aviso,
    // updateTexImage cogeria el anterior y saldrian fotogramas repetidos.
    val hayFotograma = Semaphore(0)
    reductor.surfaceTexture.setOnFrameAvailableListener { hayFotograma.release() }

    val decodificador = MediaCodec.createDecoderByType(
        formatoOrigen.getString(MediaFormat.KEY_MIME)!!
    )
    // El giro va una sola vez, como aviso en el contenedor. Si el decodificador
    // lo recibe, lo aplica al pintar en la textura y el video saldria girado
    // dos veces (y aplastado: las medidas de salida son las del original).
    if (giro != 0) formatoOrigen.setInteger(MediaFormat.KEY_ROTATION, 0)
    decodificador.configure(formatoOrigen, Surface(reductor.surfaceTexture), null, 0)

    val muxer = MediaMuxer(salida.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    if (giro != 0) muxer.setOrientationHint(giro)

    var pistaVideoSalida = -1
    var pistaAudioSalida = -1
    var muxerEnMarcha = false

    codificador.start()
    decodificador.start()
    extractor.selectTrack(pistaVideo)

    val info = MediaCodec.BufferInfo()
    val infoSalida = MediaCodec.BufferInfo()
    var entradaAgotada = false
    var decodificadorAgotado = false
    var codificadorAgotado = false

    // Salto de fotogramas para bajar los fps. Se marca el siguiente instante
    // que toca y se descarta todo lo que caiga antes; asi el reparto queda
    // regular aunque los del original no lo esten.
    val pasoUs = 1_000_000L / fpsDestino
    var siguienteUs = 0L
    var ultimoPtsUs = 0L
    var salidos = 0

    // Estado de la generacion de fotogramas. Ventana deslizante: en memoria
    // solo estan el fotograma anterior, el actual, el que se acaba de generar
    // y la suma en curso. Un clip de ocho segundos a 960 son casi ocho mil
    // imagenes; guardarlas seria llenar el telefono.
    val muestras = anchoDestino * altoDestino * 4
    val enMemoria = interpola || mejorando
    var bufPrevio = if (enMemoria) Rife.buffer(anchoDestino, altoDestino) else null
    var bufActual = if (enMemoria) Rife.buffer(anchoDestino, altoDestino) else null
    val bufGenerado = if (interpola) Rife.buffer(anchoDestino, altoDestino) else null
    val bufMezcla = if (interpola) Rife.buffer(anchoDestino, altoDestino) else null
    val acumulador = if (interpola) Rife.acumuladorPara(anchoDestino, altoDestino) else null
    // El doble de tamano: es donde escribe el modelo antes de promediar.
    val bufDoble = if (mejorando) Waifu2x.buffer(anchoDestino * 2, altoDestino * 2) else null
    val bufMejor = if (mejorando) Waifu2x.buffer(anchoDestino, altoDestino) else null
    var hayPrevio = false
    var enAcumulador = 0
    var indiceSalida = 0
    var dentroDelGrupo = 0

    /**
     * Recoge lo que el codificador tenga listo y lo escribe al archivo.
     *
     * Hay que llamarla tambien desde dentro del bucle de generacion, no solo
     * una vez por vuelta del bucle grande. Interpolando, un fotograma del
     * original produce hasta cuarenta generados y varios de salida seguidos;
     * si se empujan todos sin recoger, se acaban los buffers de la superficie
     * del codificador y publicar se queda esperando uno libre. Y no lo va a
     * haber nunca, porque el unico que los libera es esto, que esta detras del
     * bloqueo. Eso era el video que avanzaba un rato y se plantaba.
     */
    fun vaciarCodificador() {
        while (true) {
            val idx = codificador.dequeueOutputBuffer(infoSalida, 0)
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Unico momento en que se conoce el formato real de salida, y
                // el muxer exige todas las pistas antes de arrancar.
                pistaVideoSalida = muxer.addTrack(codificador.outputFormat)
                if (pistaAudio >= 0) {
                    pistaAudioSalida = muxer.addTrack(extractor.getTrackFormat(pistaAudio))
                }
                muxer.start()
                muxerEnMarcha = true
                continue
            }
            if (idx < 0) return

            val buffer = codificador.getOutputBuffer(idx)
            val esConfig = infoSalida.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            if (!esConfig && infoSalida.size > 0 && muxerEnMarcha && buffer != null) {
                muxer.writeSampleData(pistaVideoSalida, buffer, infoSalida)
            }
            codificador.releaseOutputBuffer(idx, false)
            if (infoSalida.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                codificadorAgotado = true
                return
            }
        }
    }

    /**
     * Saca un fotograma de salida hacia el codificador.
     *
     * Aqui es donde pasa por el modelo de reconstruccion, si se pidio: sobre
     * el fotograma final, ya mezclado y del tamano de salida. Antes de mezclar
     * no serviria, porque la mezcla volveria a ablandar lo que el modelo
     * acabase de recuperar.
     */
    fun emitir(fuente: java.nio.ByteBuffer, nanos: Long = -1L) {
        val listo = if (mejorando) {
            if (Waifu2x.mejorarIgual(fuente, bufDoble!!, bufMejor!!, anchoDestino, altoDestino)) {
                bufMejor!!
            } else fuente
        } else fuente

        vaciarCodificador()
        reductor.dibujarDesde(listo)
        // Interpolando, los instantes los marca la rejilla de salida; sin
        // interpolar hay que respetar los del archivo original o el audio se
        // separaria de la imagen.
        gl.marcarTiempo(if (nanos >= 0) nanos else indiceSalida * 1_000_000_000L / fpsDestino)
        gl.publicar()
        indiceSalida++
        salidos++
    }

    /**
     * Cuantos generados entran en cada fotograma de salida, aqui y ahora.
     *
     * Fuera de los tramos lentos son todos: se mezclan y el video va a su
     * velocidad. Dentro de un tramo entran menos, asi que hacen falta mas
     * fotogramas de salida para cubrir el mismo trozo de original y ese trozo
     * dura mas. Ahi esta toda la camara lenta: no se repite nada, se reparte.
     */
    fun grupoEn(fuenteUs: Long): Int {
        if (ajustes.tramos.isEmpty()) return porSalida
        val ms = (fuenteUs / 1000).toInt()
        val t = ajustes.tramos.firstOrNull { ms >= it.desdeMs && ms < it.hastaMs }
            ?: return porSalida
        return (porSalida / t.factor.coerceAtLeast(1)).coerceAtLeast(1)
    }

    /** Coloca un generado en su sitio: sumandolo a la mezcla o soltandolo. */
    fun encajar(generado: java.nio.ByteBuffer, fuenteUs: Long) {
        val grupo = grupoEn(fuenteUs)
        if (ajustes.estabilizar) {
            Rife.acumular(generado, acumulador!!, muestras)
            enAcumulador++
            if (enAcumulador >= grupo) {
                Rife.promediar(acumulador, bufMezcla!!, muestras, enAcumulador)
                emitir(bufMezcla)
                enAcumulador = 0
            }
        } else {
            // Sin mezclar: se queda uno de cada tantos. Es lo que hace falta
            // cuando lo que se quiere es un archivo de mas fotogramas por
            // segundo de verdad, no arrastre.
            if (dentroDelGrupo == 0) emitir(generado)
            dentroDelGrupo = (dentroDelGrupo + 1) % grupo
        }
    }
    // Para el contador. Sale de la duracion y los fps, asi que es una
    // prevision, no una cuenta: si el archivo trae fotogramas irregulares el
    // total real puede quedarse a uno o dos.
    val totalPrevisto = if (duracionUs > 0) {
        ((duracionUs / 1_000_000.0) * fpsDestino).toInt().coerceAtLeast(1)
    } else 0

    try {
        while (!codificadorAgotado) {

            // 1. del archivo al decodificador
            if (!entradaAgotada) {
                val idx = decodificador.dequeueInputBuffer(TIEMPO_ESPERA)
                if (idx >= 0) {
                    val buffer = decodificador.getInputBuffer(idx)!!
                    val leidos = extractor.readSampleData(buffer, 0)
                    if (leidos < 0) {
                        decodificador.queueInputBuffer(
                            idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        entradaAgotada = true
                    } else {
                        decodificador.queueInputBuffer(idx, 0, leidos, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // 2. del decodificador a la GPU, y de ahi al codificador
            if (!decodificadorAgotado) {
                val idx = decodificador.dequeueOutputBuffer(info, TIEMPO_ESPERA)
                if (idx >= 0) {
                    // Interpolando hacen falta todos los fotogramas del
                    // original: son la materia prima. Sin interpolar se
                    // descartan los que caen entre dos instantes de salida.
                    val tocaEste =
                        info.size > 0 && (interpola || info.presentationTimeUs >= siguienteUs)
                    decodificador.releaseOutputBuffer(idx, tocaEste)

                    if (tocaEste) {
                        while (siguienteUs <= info.presentationTimeUs) siguienteUs += pasoUs
                        hayFotograma.tryAcquire(2500, TimeUnit.MILLISECONDS)
                        reductor.surfaceTexture.updateTexImage()

                        if (enMemoria) {
                            reductor.reducirAparte()
                            reductor.leer(bufActual!!)

                            if (!interpola) {
                                // Solo reconstruccion: cada fotograma sale tal
                                // cual, con su instante original.
                                emitir(bufActual, info.presentationTimeUs * 1000)
                            } else if (hayPrevio) {
                                for (k in 0 until multiplicador) {
                                    val fuente = if (k == 0) {
                                        // El instante cero es el fotograma
                                        // anterior tal cual: pedirselo al
                                        // modelo costaria lo mismo que uno
                                        // inventado para devolver lo que ya
                                        // tenemos.
                                        bufPrevio!!
                                    } else {
                                        val bien = Rife.interpolar(
                                            bufPrevio!!, bufActual, bufGenerado!!,
                                            anchoDestino, altoDestino,
                                            k / multiplicador.toFloat()
                                        )
                                        // Si el modelo falla, su buffer se
                                        // queda con lo que hubiera dentro. Sin
                                        // comprobarlo, esa basura acababa en el
                                        // video como un fotograma negro o roto.
                                        // Mejor repetir el anterior: se pierde
                                        // suavidad en ese instante, pero no se
                                        // ve un destrozo.
                                        if (bien) bufGenerado else bufPrevio!!
                                    }
                                    encajar(fuente, info.presentationTimeUs)
                                }
                            }
                            val t = bufPrevio
                            bufPrevio = bufActual
                            bufActual = t
                            hayPrevio = true
                        } else {
                            reductor.dibujar()
                            gl.marcarTiempo(info.presentationTimeUs * 1000)
                            gl.publicar()
                            salidos++
                        }

                        ultimoPtsUs = info.presentationTimeUs
                        if (duracionUs > 0) {
                            onProgreso(
                                salidos,
                                totalPrevisto,
                                (ultimoPtsUs.toFloat() / duracionUs).coerceIn(0f, 1f)
                            )
                        }
                    }

                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        decodificadorAgotado = true
                        codificador.signalEndOfInputStream()
                    }
                }
            }

            // 3. del codificador al archivo
            vaciarCodificador()

            // Al final ya no entra nada nuevo, asi que sin esperar un poco el
            // bucle giraria en vacio quemando el procesador hasta que salga el
            // ultimo fotograma.
            if (decodificadorAgotado && !codificadorAgotado) {
                Thread.sleep(2)
            }
        }

        // 4. el audio, tal cual venia
        //
        // Con tramos lentos no se copia: el video pasa a durar mas que el
        // audio y a partir del primer tramo dejaria de cuadrar. En un edit la
        // musica se pone despues de todos modos.
        if (pistaAudioSalida >= 0 && muxerEnMarcha && ajustes.tramos.isEmpty()) {
            copiarAudio(extractor, pistaVideo, pistaAudio, muxer, pistaAudioSalida)
        }
        // Sin audio no hay que dejar la pista vacia en el archivo
    } finally {
        runCatching { decodificador.stop() }
        runCatching { decodificador.release() }
        runCatching { codificador.stop() }
        runCatching { codificador.release() }
        runCatching { reductor.soltar() }
        runCatching { gl.soltar() }
        runCatching { superficieEntrada.release() }
        if (muxerEnMarcha) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        runCatching { extractor.release() }
    }

    onProgreso(salidos, maxOf(totalPrevisto, salidos), 1f)
    return Resultado(salida, anchoDestino, altoDestino, fpsDestino, salida.length())
}

/** Copia los paquetes de audio sin recodificar. */
private fun copiarAudio(
    extractor: MediaExtractor,
    pistaVideo: Int,
    pistaAudio: Int,
    muxer: MediaMuxer,
    destino: Int
) {
    extractor.unselectTrack(pistaVideo)
    extractor.selectTrack(pistaAudio)
    extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)

    val buffer = ByteBuffer.allocate(256 * 1024)
    val info = MediaCodec.BufferInfo()
    while (true) {
        val leidos = extractor.readSampleData(buffer, 0)
        if (leidos < 0) break
        info.offset = 0
        info.size = leidos
        info.presentationTimeUs = extractor.sampleTime
        info.flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
            MediaCodec.BUFFER_FLAG_KEY_FRAME
        } else 0
        muxer.writeSampleData(destino, buffer, info)
        extractor.advance()
    }
}

/**
 * Medidas de salida.
 *
 * Manda el lado corto porque es la referencia con la que todo el mundo habla
 * de resolucion.
 *
 * Se redondea a par y no a multiplo de 16, que era lo primero que puse. Con
 * 16, un video de 1080 de ancho al que no habia que tocarle nada salia a
 * 1088: se ampliaba sin motivo y ademas cambiaba la proporcion un 0.7%, que
 * es poco pero suficiente para que el original y el resultado no cuadren al
 * superponerlos en el comparador. Par es lo unico que H.264 exige de verdad;
 * el relleno hasta el bloque lo hace el codificador por dentro y no cuesta
 * calidad.
 */
private fun medidas(ancho: Int, alto: Int, ladoCorto: Int): Pair<Int, Int> {
    if (ladoCorto <= 0 || minOf(ancho, alto) <= ladoCorto) {
        return par(ancho) to par(alto)
    }
    val factor = ladoCorto.toFloat() / minOf(ancho, alto)
    return par((ancho * factor).toInt()) to par((alto * factor).toInt())
}

private fun par(v: Int): Int = (v / 2) * 2

/**
 * Cuantos intermedios generar por cada hueco del original.
 *
 * No vale cualquier numero. La cuenta tiene que cerrar por los dos lados: los
 * generados por segundo han de ser multiplo exacto de los fotogramas del
 * original, y a la vez divisibles entre los de salida. Si sobrara o faltara
 * medio fotograma al promediar, el reparto se descuadra y el video sale a
 * saltos aunque tenga mil fotogramas por segundo dentro.
 *
 * Con 24 de origen y 30 de salida, por ejemplo, solo sirven los multiplos de
 * cinco: 120, 240, 480, 960. Por eso se busca el que mas se acerque a lo
 * pedido en vez de usarlo tal cual.
 */
private fun pasoLimpio(fpsOrigen: Int, fpsSalida: Int, objetivo: Int): Int {
    if (objetivo <= fpsOrigen || fpsOrigen <= 0 || fpsSalida <= 0) return 0
    val ideal = (objetivo.toFloat() / fpsOrigen).toInt().coerceAtLeast(2)
    for (desvio in 0..ideal) {
        for (m in listOf(ideal - desvio, ideal + desvio)) {
            if (m < 2) continue
            if ((fpsOrigen * m) % fpsSalida == 0) return m
        }
    }
    return 0
}

private fun entero(formato: MediaFormat, clave: String, porDefecto: Int): Int {
    if (!formato.containsKey(clave)) return porDefecto
    return runCatching { formato.getInteger(clave) }
        .getOrElse { runCatching { formato.getFloat(clave).toInt() }.getOrDefault(porDefecto) }
}
