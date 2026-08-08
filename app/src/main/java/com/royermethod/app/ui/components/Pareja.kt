package com.royermethod.app.ui.components

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Dos videos que van clavados.
 *
 * El intento anterior era dos MediaPlayer sincronizados a base de mirar sus
 * posiciones y corregir. No puede funcionar, y conviene entender por que
 * antes de tocar nada de aqui:
 *
 *  - Cada MediaPlayer lleva su propio reloj interno. Arrancan con
 *    milisegundos de diferencia, decodifican a ritmos distintos y dan la
 *    vuelta al bucle en momentos distintos. Android no ofrece ninguna forma
 *    de atarlos entre si.
 *  - currentPosition no dice que fotograma hay en pantalla, dice por donde va
 *    el reloj de reproduccion. Medir el desfase con eso es medir con una
 *    regla que se mueve.
 *  - Y corregir saltando lo empeoraba: seekTo retrocede al fotograma clave
 *    anterior, que con nuestro codificado esta hasta dos segundos atras.
 *
 * Aqui no hay dos relojes. Hay uno, que es este, y los dos videos se
 * decodifican a mano: en cada vuelta se le dice a cada uno "ensena el
 * fotograma que corresponde a este instante". Van clavados por construccion,
 * no por correccion, asi que no hay nada que pueda desincronizarse.
 */
class Pareja(
    val izquierdo: LadoVideo,
    val derecho: LadoVideo
) {
    var reproduciendo by mutableStateOf(true)
    var duracionUs by mutableLongStateOf(0L)

    /** El instante que se esta viendo. Lo leen los dos lados. */
    val relojUs = mutableLongStateOf(0L)

    fun alternar() {
        reproduciendo = !reproduciendo
    }

    fun buscar(fraccion: Float) {
        if (duracionUs <= 0) return
        val destino = (fraccion.coerceIn(0f, 1f) * duracionUs).toLong()
        relojUs.longValue = destino
        izquierdo.saltarA(destino)
        derecho.saltarA(destino)
    }
}

/**
 * Un video decodificado a mano.
 *
 * No reproduce por su cuenta: se le va pidiendo el fotograma que toca. Toda
 * la gracia esta en avanzar(), que suelta a pantalla los fotogramas cuyo
 * instante ya ha pasado y se guarda el siguiente sin soltarlo.
 */
class LadoVideo(private val context: Context, private val uri: Uri) {

    private val extractor = MediaExtractor()
    private var codec: MediaCodec? = null
    private val info = MediaCodec.BufferInfo()
    private var pista = -1

    var duracionUs = 0L
        private set
    var proporcion by mutableFloatStateOf(9f / 16f)
        private set
    var giro = 0
        private set
    var pausado by mutableStateOf(false)

    /** La entrega la vista cuando su textura esta viva. */
    @Volatile
    var superficie: Surface? = null

    private var indiceEspera = -1
    private var ptsEspera = 0L
    private var entradaAgotada = false

    /**
     * Instante del primer fotograma del archivo.
     *
     * No siempre es cero. Muchos contenedores arrancan la pista de video en
     * un instante cualquiera, y el nuestro hereda el del original. Si no se
     * descuenta, cada lado esta midiendo desde un origen distinto y comparar
     * sus tiempos no significa nada.
     */
    private var origenUs = Long.MIN_VALUE

    /** Ultimo instante sacado a pantalla, ya descontado el origen. */
    @Volatile
    var visibleUs = 0L
        private set

    /** Ya hay imagen. Hasta entonces el reloj no debe correr. */
    @Volatile
    var haPintado = false
        private set

    fun abrir(): Boolean = runCatching {
        extractor.setDataSource(context, uri, null)
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                pista = i
                break
            }
        }
        if (pista < 0) return false

        val formato = extractor.getTrackFormat(pista)
        duracionUs = if (formato.containsKey(MediaFormat.KEY_DURATION)) {
            formato.getLong(MediaFormat.KEY_DURATION)
        } else 0L
        giro = if (formato.containsKey(MediaFormat.KEY_ROTATION)) {
            formato.getInteger(MediaFormat.KEY_ROTATION)
        } else 0

        // Las medidas de verdad son las del recorte, no las de la pista.
        //
        // Un codificador trabaja en bloques, asi que si la imagen no encaja
        // justo guarda unos pixeles de mas y anota hasta donde vale. Al
        // reducir a resoluciones que no son multiplo del bloque, esa
        // diferencia hace que la proporcion salga mal y el video se vea
        // estirado. Con el recorte aplicado cuadra siempre.
        val ancho: Float
        val alto: Float
        if (formato.containsKey("crop-left") && formato.containsKey("crop-right")) {
            ancho = (formato.getInteger("crop-right") - formato.getInteger("crop-left") + 1)
                .toFloat()
            alto = (formato.getInteger("crop-bottom") - formato.getInteger("crop-top") + 1)
                .toFloat()
        } else {
            ancho = formato.getInteger(MediaFormat.KEY_WIDTH).toFloat()
            alto = formato.getInteger(MediaFormat.KEY_HEIGHT).toFloat()
        }
        // Un video grabado en vertical viene tumbado con una marca de giro.
        // El decodificador no la aplica, asi que la proporcion hay que darle
        // la vuelta a mano o el marco sale con la forma equivocada.
        proporcion = if (giro == 90 || giro == 270) alto / ancho else ancho / alto

        extractor.selectTrack(pista)
        true
    }.getOrDefault(false)

    /** Arranca el decodificador. Necesita que la vista ya tenga superficie. */
    fun encender(): Boolean {
        if (codec != null) return true
        val destino = superficie ?: return false
        return runCatching {
            val formato = extractor.getTrackFormat(pista)
            val c = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
            c.configure(formato, destino, null, 0)
            c.start()
            codec = c
            true
        }.getOrDefault(false)
    }

    fun soltar() {
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        codec = null
        runCatching { extractor.release() }
        runCatching { superficie?.release() }
        superficie = null
    }

    /**
     * Vuelve al instante pedido.
     *
     * El extractor solo puede colocarse en un fotograma clave, asi que se
     * retrocede al anterior y se decodifica hacia delante sin ensenar nada
     * hasta llegar. Es lo que hace cualquier reproductor por dentro; la
     * diferencia es que aqui controlamos cuando se ve el resultado.
     */
    fun saltarA(us: Long) {
        val c = codec ?: return
        val base = if (origenUs == Long.MIN_VALUE) 0L else origenUs
        runCatching {
            extractor.seekTo(us + base, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            c.flush()
        }
        indiceEspera = -1
        entradaAgotada = false
    }

    /**
     * Deja en pantalla el fotograma que corresponde a relojUs.
     *
     * Los fotogramas cuyo instante ya paso se sueltan a pantalla en orden; el
     * primero que todavia no toca se queda guardado sin soltar hasta su
     * momento. Como los dos lados reciben el mismo relojUs, los dos ensenan
     * el mismo instante. No hay margen de error que corregir.
     */
    fun avanzar(relojUs: Long) {
        val c = codec ?: return
        if (pausado) return

        alimentar(c)

        var vueltas = 0
        while (vueltas++ < 8) {
            if (indiceEspera < 0) {
                val i = runCatching { c.dequeueOutputBuffer(info, 0) }.getOrElse { -1 }
                if (i < 0) return
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    runCatching { c.releaseOutputBuffer(i, false) }
                    return
                }
                indiceEspera = i
                if (origenUs == Long.MIN_VALUE) origenUs = info.presentationTimeUs
                ptsEspera = info.presentationTimeUs - origenUs
            }
            if (ptsEspera > relojUs) return          // todavia no le toca
            runCatching { c.releaseOutputBuffer(indiceEspera, true) }
            visibleUs = ptsEspera
            haPintado = true
            indiceEspera = -1
        }
    }

    private fun alimentar(c: MediaCodec) {
        var vueltas = 0
        while (!entradaAgotada && vueltas++ < 8) {
            val i = runCatching { c.dequeueInputBuffer(0) }.getOrElse { -1 }
            if (i < 0) return
            val buffer = runCatching { c.getInputBuffer(i) }.getOrNull() ?: return
            val leidos = runCatching { extractor.readSampleData(buffer, 0) }.getOrDefault(-1)
            if (leidos < 0) {
                runCatching {
                    c.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                entradaAgotada = true
            } else {
                runCatching { c.queueInputBuffer(i, 0, leidos, extractor.sampleTime, 0) }
                extractor.advance()
            }
        }
    }
}

/**
 * Monta la pareja y le da cuerda.
 *
 * El bucle va en un hilo aparte porque decodificar bloquea, y se despierta
 * cada pocos milisegundos: lo justo para no llegar tarde a ningun fotograma
 * de un video de sesenta por segundo.
 */
@Composable
fun recordarPareja(izquierda: Uri, derecha: Uri, sonido: Boolean): Pareja {
    val context = LocalContext.current
    val pareja = remember(izquierda, derecha) {
        Pareja(LadoVideo(context, izquierda), LadoVideo(context, derecha))
    }

    DisposableEffect(pareja) {
        onDispose {
            pareja.izquierdo.soltar()
            pareja.derecho.soltar()
        }
    }

    LaunchedEffect(pareja) {
        withContext(Dispatchers.Default) {
            if (!pareja.izquierdo.abrir() || !pareja.derecho.abrir()) return@withContext

            // La vuelta la marca el mas corto: pasado ese punto uno de los dos
            // ya no tendria nada que ensenar.
            val vuelta = listOf(pareja.izquierdo.duracionUs, pareja.derecho.duracionUs)
                .filter { it > 0 }.minOrNull() ?: 0L
            pareja.duracionUs = vuelta

            // Esperar a que las dos vistas tengan superficie
            while (isActive && !(pareja.izquierdo.encender() && pareja.derecho.encender())) {
                delay(16)
            }

            // El reloj no arranca hasta que los dos tienen imagen. Llenar un
            // decodificador lleva unas decimas y no tarda lo mismo un archivo
            // que otro; si el reloj corriera mientras tanto, el que tardase
            // mas empezaria ya con retraso y ese retraso no se iria nunca.
            while (isActive && !(pareja.izquierdo.haPintado && pareja.derecho.haPintado)) {
                pareja.izquierdo.avanzar(0)
                pareja.derecho.avanzar(0)
                delay(5)
            }

            var ultimoNano = System.nanoTime()
            while (isActive) {
                val ahora = System.nanoTime()
                val transcurridoUs = (ahora - ultimoNano) / 1000
                ultimoNano = ahora

                if (pareja.reproduciendo) {
                    var t = pareja.relojUs.longValue + transcurridoUs
                    if (vuelta > 0 && t >= vuelta) {
                        // Se da la vuelta a la vez para los dos, porque el
                        // reloj es uno solo. Aqui no hay forma de que uno
                        // reinicie antes que el otro.
                        t = 0L
                        pareja.izquierdo.saltarA(0)
                        pareja.derecho.saltarA(0)
                    }
                    pareja.relojUs.longValue = t
                }

                val t = pareja.relojUs.longValue
                pareja.izquierdo.avanzar(t)
                pareja.derecho.avanzar(t)

                delay(5)
            }
        }
    }

    // El sonido sale de un reproductor normal, aparte. Es el mismo audio en
    // los dos videos, asi que solo hace falta uno, y solo existe mientras
    // este encendido: apagado no se crea, y asi no gasta un decodificador de
    // mas en un movil que ya lleva dos ocupados.
    if (sonido) {
        Audio(derecha, pareja)
    }

    return pareja
}

@Composable
private fun Audio(uri: Uri, pareja: Pareja) {
    val context = LocalContext.current
    val mp = remember(uri) { MediaPlayer() }

    DisposableEffect(uri) {
        runCatching {
            mp.setDataSource(context, uri)
            mp.isLooping = true
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
        }
        onDispose {
            runCatching { mp.setOnPreparedListener(null) }
            runCatching { mp.release() }
        }
    }

    // El audio manda sobre si mismo y se le trae al instante del reloj cuando
    // se separa. Al reves no: si el reloj persiguiera al audio, los dos videos
    // heredarian el temblor de currentPosition.
    LaunchedEffect(pareja) {
        while (true) {
            delay(200)
            val objetivo = pareja.relojUs.longValue / 1000
            val actual = runCatching { mp.currentPosition }.getOrNull() ?: continue
            if (!pareja.reproduciendo) {
                runCatching { if (mp.isPlaying) mp.pause() }
                continue
            }
            runCatching { if (!mp.isPlaying) mp.start() }
            if (kotlin.math.abs(actual - objetivo.toInt()) > 130) {
                runCatching { mp.seekTo(objetivo, MediaPlayer.SEEK_CLOSEST) }
            }
        }
    }
}

/**
 * La imagen de un lado.
 *
 * Igual que el reproductor sencillo: TextureView y no SurfaceView, porque
 * SurfaceView pinta en una capa aparte y no respeta ni las esquinas
 * redondeadas ni el recorte de la linea del comparador.
 */
@Composable
fun LienzoLado(
    lado: LadoVideo,
    modifier: Modifier = Modifier,
    recortar: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val caja = if (maxHeight.value > 0f) maxWidth / maxHeight else 1f
        val porAncho = if (recortar) lado.proporcion < caja else lado.proporcion > caja
        val ancho = if (porAncho) maxWidth else maxHeight * lado.proporcion
        val altura = if (porAncho) maxWidth / lado.proporcion else maxHeight

        // El giro se aplica aqui: el decodificador entrega la imagen tal como
        // esta guardada, sin hacer caso de la marca de orientacion. Con giro
        // de un cuarto, la vista se pide con los lados cambiados y luego se
        // rota, que es lo que la deja del derecho ocupando el hueco correcto.
        val cuarto = lado.giro == 90 || lado.giro == 270
        Box(
            modifier = Modifier
                .requiredSize(
                    width = if (cuarto) altura else ancho,
                    height = if (cuarto) ancho else altura
                )
                .graphicsLayer { rotationZ = lado.giro.toFloat() }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                lado.superficie = Surface(st)
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture) = true
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                        }
                    }
                }
            )
        }
    }
}
