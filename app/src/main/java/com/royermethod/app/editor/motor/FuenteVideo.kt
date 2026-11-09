package com.royermethod.app.editor.motor

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.royermethod.app.editor.exportar.FiltroColgados
import com.royermethod.app.editor.exportar.SincronizacionVideo
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Un video abierto para sacar fotogramas en el instante que se pida.
 *
 * Es el corazon de que 30 fps salgan como 30 fps. Para cada fotograma de
 * salida se pide un instante exacto del archivo, y aqui se decide cual de los
 * fotogramas del archivo es el que se ve en ese instante: el ultimo que ya ha
 * empezado. Los de antes se descartan sin pintarlos, y el siguiente se queda
 * guardado en el decodificador, sin soltar, para la proxima peticion. Asi no
 * se pierde ni se repite ninguno por ir con prisa o con retraso.
 *
 * Todo se llama desde el hilo que tiene el contexto de OpenGL.
 */
class FuenteVideo(
    context: Context,
    val uri: Uri,
    /** Grados que trae marcados el archivo; el giro lo hace la copia. */
    private val giro: Int,
    /**
     * Vista previa: decodificar lo mas rapido que pueda el movil y, hacia
     * atras, dar por bueno el fotograma de casi el mismo instante (ver
     * [TOLERANCIA_VISTA]). La exportacion va sin esto, exacta.
     */
    private val vista: Boolean = false,
    /**
     * El archivo que de verdad se lee, si no es [uri]: la copia ligera de la
     * vista previa (ver Ligeras). Todo lo de fuera sigue hablando de [uri].
     */
    abrir: Uri = uri,
    /**
     * Lo que hay que sumar a los instantes de [abrir] para que sean los de
     * [uri] (la copia puede empezar en 0 aunque el original no).
     */
    private val corrimientoUs: Long = 0L
) {
    private val extractor = MediaExtractor()
    private val decodificador: MediaCodec
    private val texturaOes: Int
    private val superficieTextura: SurfaceTexture
    private val superficie: Surface
    private val hayFotograma = Semaphore(0)
    private val info = MediaCodec.BufferInfo()
    private val matrizOes = FloatArray(16)

    /** Medidas del fotograma tal y como viene, sin girar. */
    val anchoCrudo: Int
    val altoCrudo: Int
    private val mime: String
    /** H.264 y H.265: se mira donde se cae al saltar y se tiran los B colgados. */
    private val revisar: Boolean
    private var filtro: FiltroColgados? = null
    /** Para leer un paquete sin darselo al decodificador; crece si hace falta. */
    private var paquete: ByteBuffer? = null

    /** Instante del fotograma que hay ahora en la textura. -1 si ninguno. */
    var ptsActual = -1L
        private set

    /** Instante de la ultima peticion: sirve para elegir que fuente reutilizar. */
    var ultimaPeticion = -1L
        private set

    // El siguiente fotograma ya decodificado, guardado sin pintar.
    private var pendiente = -1
    /** Diagnostico de la ultima [irA]: saltos al fotograma clave y cuadros leidos. */
    private var saltos = 0
    private var leidos = 0
    private var ptsPendiente = 0L
    private var entradaAgotada = false
    private var salidaAgotada = false

    /** La imagen derecha y al tamano de uso, lista para el compositor. */
    var copia: Lamina? = null
        private set
    /** La copia a medias: ya con su ancho, todavia con el alto del original. */
    private var intermedia: Lamina? = null
    private var ptsCopia = -1L

    /** Se marca al usarla en un fotograma; las que no se usan se cierran. */
    var usadaEn = -1L

    init {
        // Si algo falla a mitad (archivo borrado, sin decodificadores libres),
        // se suelta lo que ya se hubiera creado: el compositor lo vuelve a
        // intentar y cada intento dejaria una textura y un codec perdidos.
        var textura = 0
        var st: SurfaceTexture? = null
        var sup: Surface? = null
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, abrir, null)
            var pista = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) { pista = i; break }
            }
            require(pista >= 0) { "el archivo no tiene video" }
            extractor.selectTrack(pista)
            val formato = extractor.getTrackFormat(pista)
            anchoCrudo = formato.getInteger(MediaFormat.KEY_WIDTH)
            altoCrudo = formato.getInteger(MediaFormat.KEY_HEIGHT)
            // El giro lo aplicamos nosotros al copiar. Si se lo dejamos al
            // decodificador, unos moviles lo meten en la matriz de la textura y
            // otros no, y el video saldria girado dos veces o ninguna.
            if (formato.containsKey(MediaFormat.KEY_ROTATION)) formato.setInteger(MediaFormat.KEY_ROTATION, 0)
            mime = formato.getString(MediaFormat.KEY_MIME)!!
            revisar = SincronizacionVideo.admite(mime)
            filtro = if (revisar) FiltroColgados(mime) else null

            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            textura = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textura)
            Lamina.filtros(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
            st = SurfaceTexture(textura)
            st.setOnFrameAvailableListener({ hayFotograma.release() }, avisos)
            sup = Surface(st)

            if (vista) {
                // Lo mas rapido que pueda: saltar a un instante obliga a leer
                // desde el fotograma clave, y a su ritmo normal el movil va
                // poco mas deprisa que el video. Si no lo acepta, como siempre.
                val f = extractor.getTrackFormat(pista)
                if (f.containsKey(MediaFormat.KEY_ROTATION)) f.setInteger(MediaFormat.KEY_ROTATION, 0)
                f.setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
                f.setInteger(MediaFormat.KEY_PRIORITY, 0)
                var c: MediaCodec? = null
                codec = try {
                    c = MediaCodec.createDecoderByType(mime)
                    c.configure(f, sup, null, 0)
                    c.start()
                    c
                } catch (e: Exception) {
                    runCatching { c?.release() }
                    null
                }
            }
            if (codec == null) {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(formato, sup, null, 0)
                codec.start()
            }
        } catch (e: Exception) {
            runCatching { codec?.release() }
            runCatching { sup?.release() }
            runCatching { st?.release() }
            if (textura != 0) GLES20.glDeleteTextures(1, intArrayOf(textura), 0)
            runCatching { extractor.release() }
            throw e
        }
        texturaOes = textura
        superficieTextura = st
        superficie = sup
        decodificador = codec
    }

    /** Ancho y alto tal y como se ve, ya girado. */
    val anchoVisible: Int get() = if (giro == 90 || giro == 270) altoCrudo else anchoCrudo
    val altoVisible: Int get() = if (giro == 90 || giro == 270) anchoCrudo else altoCrudo

    /**
     * Deja en la textura el fotograma que se ve en [objetivo] (microsegundos
     * del archivo).
     *
     * @return false si no se pudo (el decodificador se atasco); la textura se
     *   queda con lo que tuviera.
     */
    fun irA(objetivo: Long, esperaMaxMs: Long = 6000, seguir: (() -> Boolean)? = null): Boolean {
        if (!registrar) return irAHasta(objetivo, esperaMaxMs, seguir)
        // Diagnostico: avisa de lo que tarda mucho, y por que
        val inicioNs = System.nanoTime()
        val antes = ptsActual
        saltos = 0; leidos = 0
        val r = try {
            irAHasta(objetivo, esperaMaxMs, seguir)
        } catch (a: LecturaAbandonada) {
            val ms = (System.nanoTime() - inicioNs) / 1_000_000
            if (ms >= 40) runCatching {
                android.util.Log.i("RoyerVista", "video abandonado a los ${ms} ms: ${uri.lastPathSegment} pide ${objetivo / 1000} ms, " +
                    "${if (saltos > 0) "salto al clave, " else ""}$leidos cuadros")
            }
            throw a
        }
        val ms = (System.nanoTime() - inicioNs) / 1_000_000
        if (ms >= 40) runCatching {
            android.util.Log.i("RoyerVista", "video lento ${ms} ms: ${uri.lastPathSegment} pide ${objetivo / 1000} ms, " +
                "tenia ${if (antes < 0) "nada" else "${antes / 1000} ms"}, ${if (saltos > 0) "salto al clave, " else ""}$leidos cuadros")
        }
        return r
    }

    private fun irAHasta(objetivo: Long, esperaMaxMs: Long, seguir: (() -> Boolean)?): Boolean {
        if (seguir != null && !seguir()) throw LecturaAbandonada()
        ultimaPeticion = objetivo
        // En la vista previa, un poco hacia atras se queda con el que tiene:
        // volver de verdad obliga a saltar al fotograma clave y leer desde ahi
        // (hasta 2 s con claves cada 4 s), y la diferencia no se ve
        if (vista && ptsActual >= 0 && objetivo < ptsActual && objetivo + TOLERANCIA_VISTA >= ptsActual) return true
        // Hacia atras, o muy por delante, hay que saltar. Hacia delante y
        // cerca sale mas a cuenta seguir decodificando que buscar el
        // fotograma clave anterior y rehacer desde ahi.
        val retroceso = ptsActual >= 0 && objetivo + TOLERANCIA < ptsActual
        val lejos = objetivo - maxOf(ptsActual, if (pendiente >= 0) ptsPendiente else -1) > SALTO_LARGO
        if (retroceso || lejos || ptsActual < 0 && pendiente < 0 && entradaAgotada) { saltar(objetivo); saltos++ }

        // Si el guardado ya es el que toca o se pasa, no hay nada que leer
        if (ptsActual >= 0 && objetivo + TOLERANCIA >= ptsActual &&
            (pendiente >= 0 && ptsPendiente > objetivo + TOLERANCIA || salidaAgotada)
        ) return true

        var candidato = -1
        var ptsCandidato = 0L
        var abandonada = false
        val limite = System.nanoTime() + esperaMaxMs * 1_000_000
        while (true) {
            alimentar()
            val idx: Int
            val pts: Long
            if (pendiente >= 0) {
                idx = pendiente; pts = ptsPendiente; pendiente = -1
            } else {
                if (salidaAgotada) break
                val r = decodificador.dequeueOutputBuffer(info, 4000)
                if (r < 0) {
                    if (System.nanoTime() > limite) break
                    if (seguir != null && !seguir()) { abandonada = true; break }
                    continue
                }
                val fin = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                if (info.size <= 0) {
                    decodificador.releaseOutputBuffer(r, false)
                    if (fin) { salidaAgotada = true; break }
                    continue
                }
                if (fin) salidaAgotada = true
                idx = r; pts = info.presentationTimeUs
                leidos++
            }
            if (pts <= objetivo + TOLERANCIA) {
                // Este ya ha empezado. Puede que haya otro mas tarde que
                // tambien: este solo es candidato hasta ver el siguiente.
                if (candidato >= 0) decodificador.releaseOutputBuffer(candidato, false)
                candidato = idx; ptsCandidato = pts
                if (salidaAgotada) break
                // Ya no hace falta (otra busqueda, pausa o play): se deja
                // pintado el ultimo leido, asi la proxima sigue desde ahi
                if (seguir != null && !seguir()) { abandonada = true; break }
            } else {
                // Es del futuro: se guarda para la proxima vez.
                pendiente = idx; ptsPendiente = pts
                break
            }
        }
        if (abandonada) {
            if (candidato >= 0 && pintar(candidato, ptsCandidato)) ultimaPeticion = ptsCandidato
            throw LecturaAbandonada()
        }

        // Pedido antes del primer fotograma (pasa al empezar algunos clips):
        // mejor ensenar el primero que dejar la textura vacia.
        if (candidato < 0 && ptsActual < 0 && pendiente >= 0) {
            candidato = pendiente; ptsCandidato = ptsPendiente; pendiente = -1
        }
        if (candidato < 0) return ptsActual >= 0
        return pintar(candidato, ptsCandidato)
    }

    /**
     * Solo la vista previa, en otro hilo y antes de que haga falta: deja el
     * decodificador justo antes de [objetivo] sin pintar nada (salta al
     * fotograma clave y tira lo de antes). Mientras tanto nadie mas la usa.
     * Despues, [irA] en [objetivo] solo lee un par de cuadros. false si se
     * abandono ([seguir]) o no se pudo: entonces no sirve y hay que cerrarla.
     */
    fun preparar(objetivo: Long, seguir: () -> Boolean): Boolean {
        saltar(objetivo)
        val limite = System.nanoTime() + 6_000_000_000L
        while (seguir()) {
            alimentar()
            val r = decodificador.dequeueOutputBuffer(info, 4000)
            if (r < 0) {
                if (System.nanoTime() > limite) return false
                continue
            }
            val fin = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (info.size <= 0) {
                decodificador.releaseOutputBuffer(r, false)
                if (fin) { salidaAgotada = true; return false }
                continue
            }
            if (fin) salidaAgotada = true
            // Se guarda el primero que puede ser el del objetivo (o el de justo
            // antes, si el video va a menos de 10 fps no importa: es la vista previa)
            if (info.presentationTimeUs >= objetivo - MARGEN_PREPARAR || salidaAgotada) {
                pendiente = r
                ptsPendiente = info.presentationTimeUs
                ultimaPeticion = objetivo - MARGEN_PREPARAR
                return true
            }
            decodificador.releaseOutputBuffer(r, false)
        }
        return false
    }

    /** Ya no quedan fotogramas por delante. */
    val agotada: Boolean get() = salidaAgotada && pendiente < 0

    /**
     * Pinta el fotograma que viene justo despues del actual, sea cual sea su
     * instante. Es para quien recorre el video en orden (la fluidez, que
     * necesita los dos fotogramas que rodean cada instante).
     */
    fun siguiente(esperaMaxMs: Long = 6000): Boolean {
        if (pendiente >= 0) {
            val i = pendiente
            pendiente = -1
            return pintar(i, ptsPendiente)
        }
        val limite = System.nanoTime() + esperaMaxMs * 1_000_000
        while (!salidaAgotada) {
            alimentar()
            val r = decodificador.dequeueOutputBuffer(info, 4000)
            if (r < 0) {
                if (System.nanoTime() > limite) return false
                continue
            }
            val fin = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (fin) salidaAgotada = true
            if (info.size <= 0) {
                decodificador.releaseOutputBuffer(r, false)
                continue
            }
            return pintar(r, info.presentationTimeUs)
        }
        return false
    }

    private fun pintar(idx: Int, pts: Long): Boolean {
        hayFotograma.drainPermits()
        decodificador.releaseOutputBuffer(idx, true)
        if (!hayFotograma.tryAcquire(1500, TimeUnit.MILLISECONDS)) return false
        superficieTextura.updateTexImage()
        ptsActual = pts
        return true
    }

    private fun alimentar() {
        while (!entradaAgotada) {
            val idx = decodificador.dequeueInputBuffer(0)
            if (idx < 0) return
            val buffer = decodificador.getInputBuffer(idx) ?: return
            var leidos = extractor.readSampleData(buffer, 0)
            // Los B colgados de un IDR no se le dan (ver FiltroColgados)
            while (leidos >= 0 && colgado(buffer, leidos)) {
                extractor.advance()
                leidos = extractor.readSampleData(buffer, 0)
            }
            if (leidos < 0) {
                decodificador.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                entradaAgotada = true
            } else {
                decodificador.queueInputBuffer(idx, 0, leidos, extractor.sampleTime + corrimientoUs, 0)
                extractor.advance()
            }
        }
    }

    private fun colgado(buffer: ByteBuffer, leidos: Int): Boolean {
        val f = filtro ?: return false
        val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
        return f.tirar(buffer, 0, leidos, extractor.sampleTime, sync)
    }

    private fun saltar(objetivo: Long) {
        if (pendiente >= 0) {
            runCatching { decodificador.releaseOutputBuffer(pendiente, false) }
            pendiente = -1
        }
        val destino = objetivo.coerceAtLeast(0)
        if (revisar) {
            EntradaVideo.posicionar(muestras, destino)
            filtro = FiltroColgados(mime)
        } else {
            extractor.seekTo(destino - corrimientoUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        }
        decodificador.flush()
        entradaAgotada = false
        salidaAgotada = false
        ptsActual = -1
        ptsCopia = -1
    }

    /** El archivo visto paquete a paquete, para [EntradaVideo]. */
    private val muestras = object : EntradaVideo.Muestras {
        override fun buscar(t: Long): Long {
            extractor.seekTo(maxOf(0L, t - corrimientoUs), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            return instante
        }

        override fun avanzar(): Boolean = extractor.advance()

        override val instante: Long get() = extractor.sampleTime.let { if (it < 0) it else it + corrimientoUs }

        override fun entrada(): Boolean? {
            val b = leerPaquete() ?: return null
            return SincronizacionVideo.puedeEmpezar(mime, b, 0, b.limit())
        }
    }

    /** El paquete actual, sin avanzar; null si no hay o no cabe en [PAQUETE_MAXIMO]. */
    private fun leerPaquete(): ByteBuffer? {
        var b = paquete ?: ByteBuffer.allocateDirect(PAQUETE_INICIAL).also { paquete = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tam = extractor.sampleSize
            if (tam > PAQUETE_MAXIMO) return null
            if (tam > b.capacity()) b = ByteBuffer.allocateDirect(tam.toInt()).also { paquete = it }
        }
        while (true) {
            b.clear()
            // Si no cabe, readSampleData lanza IllegalArgumentException
            val n = try {
                extractor.readSampleData(b, 0)
            } catch (e: IllegalArgumentException) {
                if (b.capacity() >= PAQUETE_MAXIMO || Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return null
                b = ByteBuffer.allocateDirect(b.capacity() * 2).also { paquete = it }
                continue
            }
            if (n <= 0) return null
            b.position(0)
            b.limit(n)
            return b
        }
    }

    /**
     * Pasa el fotograma actual a [copia], derecho y a [ancho] x [alto], con
     * el filtro de [reduccion].
     *
     * Solo si cambio: un video a 24 fps exportado a 30 repite fotograma una
     * de cada cinco veces, y no hay que volver a copiar lo mismo.
     */
    fun copiar(ancho: Int, alto: Int, reduccion: Reduccion): Lamina {
        var c = copia
        if (c == null || c.ancho != ancho || c.alto != alto) {
            c?.soltar()
            c = Lamina(ancho, alto)
            copia = c
            ptsCopia = -1
        }
        if (ptsCopia == ptsActual && ptsCopia >= 0) return c

        var m = intermedia
        if (m == null || m.ancho != ancho || m.alto != altoVisible) {
            m?.soltar()
            m = Lamina(ancho, altoVisible)
            intermedia = m
        }
        superficieTextura.getTransformMatrix(matrizOes)
        reduccion.reducir(texturaOes, mapa(), anchoVisible, altoVisible, m, c)
        ptsCopia = ptsActual
        return c
    }

    /**
     * De la coordenada de la copia (origen arriba a la izquierda, ya girada)
     * a la de la textura del decodificador.
     */
    private fun mapa(): FloatArray {
        // copia (u,v) -> fotograma guardado (x,y), los dos con origen arriba
        val g = when (giro) {
            90 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f)    // x = v, y = 1 - u
            180 -> floatArrayOf(-1f, 0f, 1f, 0f, -1f, 1f)  // x = 1 - u, y = 1 - v
            270 -> floatArrayOf(0f, -1f, 1f, 1f, 0f, 0f)   // x = 1 - v, y = u
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        }
        // (x,y) -> (s,t) con t hacia arriba, que es lo que espera la matriz
        val s0 = g[0]; val s1 = g[1]; val s2 = g[2]
        val t0 = -g[3]; val t1 = -g[4]; val t2 = 1f - g[5]
        // Matriz de SurfaceTexture (4x4 por columnas): tex = M * (s, t, 0, 1)
        val m = matrizOes
        val a = m[0]; val b = m[4]; val tx = m[12]
        val c = m[1]; val d = m[5]; val ty = m[13]
        // Composicion: tex.x = a*s + b*t + tx ; tex.y = c*s + d*t + ty
        val r00 = a * s0 + b * t0
        val r01 = a * s1 + b * t1
        val r02 = a * s2 + b * t2 + tx
        val r10 = c * s0 + d * t0
        val r11 = c * s1 + d * t1
        val r12 = c * s2 + d * t2 + ty
        // Por columnas para glUniformMatrix3fv
        return floatArrayOf(r00, r10, 0f, r01, r11, 0f, r02, r12, 1f)
    }

    fun soltar() {
        soltarDecodificador()
        soltarGl()
    }

    /** Ya no queda nada del decodificador; falta [soltarGl]. */
    @Volatile
    var decodificadorSuelto = false
        private set

    /**
     * La parte lenta (parar y liberar el decodificador cuesta decenas de
     * milisegundos en algunos moviles). No toca OpenGL: puede ir en otro hilo,
     * y despues [soltarGl] en el del contexto.
     */
    fun soltarDecodificador() {
        if (pendiente >= 0) runCatching { decodificador.releaseOutputBuffer(pendiente, false) }
        pendiente = -1
        runCatching { decodificador.stop() }
        runCatching { decodificador.release() }
        runCatching { extractor.release() }
        runCatching { superficie.release() }
        decodificadorSuelto = true
    }

    /** Lo que vive en el contexto de OpenGL; en su hilo y despues del decodificador. */
    fun soltarGl() {
        runCatching { superficieTextura.release() }
        GLES20.glDeleteTextures(1, intArrayOf(texturaOes), 0)
        copia?.soltar()
        copia = null
        intermedia?.soltar()
        intermedia = null
    }

    companion object {
        /**
         * Margen para dar por empezado un fotograma. Los instantes del
         * archivo vienen redondeados a la escala de tiempo del contenedor, y
         * un fotograma que "empieza" en 33366 cuando se pide 33333 es el
         * mismo para cualquiera que lo mire.
         */
        const val TOLERANCIA = 1_000L
        /**
         * Vista previa: cuanto hacia atras vale el fotograma que ya esta
         * pintado. Menos de un fotograma de la linea de tiempo (33 ms).
         */
        const val TOLERANCIA_VISTA = 30_000L
        /** Con [preparar], lo que se guarda por delante del objetivo. */
        const val MARGEN_PREPARAR = 100_000L
        /** Diagnostico de la vista previa (adb, RoyerVista): anotar lo que tarda mucho. */
        @Volatile
        @JvmStatic
        var registrar = false

        /** Mas alla de esto por delante, saltar sale mas barato que decodificar. */
        private const val SALTO_LARGO = 3_000_000L

        private const val PAQUETE_INICIAL = 512 * 1024
        private const val PAQUETE_MAXIMO = 16 * 1024 * 1024

        /**
         * Los avisos de "hay fotograma nuevo" llegan por aqui. Si fueran al
         * hilo principal, un tiron de la interfaz retrasaria el render.
         */
        private val avisos: Handler by lazy {
            val hilo = HandlerThread("fotogramas").apply { start() }
            Handler(hilo.looper)
        }
    }
}

/**
 * Lo que se estaba leyendo ya no hace falta (vista previa: llego otra
 * busqueda, la pausa o el play). La fuente queda bien para seguir.
 */
class LecturaAbandonada : RuntimeException(null, null, false, false)
