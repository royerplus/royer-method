package com.royermethod.app.editor.motor

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
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
    private val giro: Int
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

    /** Instante del fotograma que hay ahora en la textura. -1 si ninguno. */
    var ptsActual = -1L
        private set

    /** Instante de la ultima peticion: sirve para elegir que fuente reutilizar. */
    var ultimaPeticion = -1L
        private set

    // El siguiente fotograma ya decodificado, guardado sin pintar.
    private var pendiente = -1
    private var ptsPendiente = 0L
    private var entradaAgotada = false
    private var salidaAgotada = false

    /** La imagen derecha y al tamano de uso, lista para el compositor. */
    var copia: Lamina? = null
        private set
    private var ptsCopia = -1L

    /** Se marca al usarla en un fotograma; las que no se usan se cierran. */
    var usadaEn = -1L

    init {
        extractor.setDataSource(context, uri, null)
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

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texturaOes = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texturaOes)
        Lamina.filtros(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        superficieTextura = SurfaceTexture(texturaOes)
        superficieTextura.setOnFrameAvailableListener({ hayFotograma.release() }, avisos)
        superficie = Surface(superficieTextura)

        decodificador = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
        decodificador.configure(formato, superficie, null, 0)
        decodificador.start()
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
    fun irA(objetivo: Long, esperaMaxMs: Long = 6000): Boolean {
        ultimaPeticion = objetivo
        // Hacia atras, o muy por delante, hay que saltar. Hacia delante y
        // cerca sale mas a cuenta seguir decodificando que buscar el
        // fotograma clave anterior y rehacer desde ahi.
        val retroceso = ptsActual >= 0 && objetivo + TOLERANCIA < ptsActual
        val lejos = objetivo - maxOf(ptsActual, if (pendiente >= 0) ptsPendiente else -1) > SALTO_LARGO
        if (retroceso || lejos || ptsActual < 0 && pendiente < 0 && entradaAgotada) saltar(objetivo)

        // Si el guardado ya es el que toca o se pasa, no hay nada que leer
        if (ptsActual >= 0 && objetivo + TOLERANCIA >= ptsActual &&
            (pendiente >= 0 && ptsPendiente > objetivo + TOLERANCIA || salidaAgotada)
        ) return true

        var candidato = -1
        var ptsCandidato = 0L
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
            }
            if (pts <= objetivo + TOLERANCIA) {
                // Este ya ha empezado. Puede que haya otro mas tarde que
                // tambien: este solo es candidato hasta ver el siguiente.
                if (candidato >= 0) decodificador.releaseOutputBuffer(candidato, false)
                candidato = idx; ptsCandidato = pts
                if (salidaAgotada) break
            } else {
                // Es del futuro: se guarda para la proxima vez.
                pendiente = idx; ptsPendiente = pts
                break
            }
        }

        // Pedido antes del primer fotograma (pasa al empezar algunos clips):
        // mejor ensenar el primero que dejar la textura vacia.
        if (candidato < 0 && ptsActual < 0 && pendiente >= 0) {
            candidato = pendiente; ptsCandidato = ptsPendiente; pendiente = -1
        }
        if (candidato < 0) return ptsActual >= 0
        return pintar(candidato, ptsCandidato)
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
            val leidos = extractor.readSampleData(buffer, 0)
            if (leidos < 0) {
                decodificador.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                entradaAgotada = true
            } else {
                decodificador.queueInputBuffer(idx, 0, leidos, extractor.sampleTime, 0)
                extractor.advance()
            }
        }
    }

    private fun saltar(objetivo: Long) {
        if (pendiente >= 0) {
            runCatching { decodificador.releaseOutputBuffer(pendiente, false) }
            pendiente = -1
        }
        extractor.seekTo(objetivo.coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        decodificador.flush()
        entradaAgotada = false
        salidaAgotada = false
        ptsActual = -1
        ptsCopia = -1
    }

    /**
     * Pasa el fotograma actual a [copia], derecho y a [ancho] x [alto].
     *
     * Solo si cambio: un video a 24 fps exportado a 30 repite fotograma una
     * de cada cinco veces, y no hay que volver a copiar lo mismo.
     */
    fun copiar(ancho: Int, alto: Int, programa: Programa): Lamina {
        var c = copia
        if (c == null || c.ancho != ancho || c.alto != alto) {
            c?.soltar()
            c = Lamina(ancho, alto)
            copia = c
            ptsCopia = -1
        }
        if (ptsCopia == ptsActual && ptsCopia >= 0) return c

        superficieTextura.getTransformMatrix(matrizOes)
        c.activar()
        GLES20.glDisable(GLES20.GL_BLEND)
        programa.usar()
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texturaOes)
        GLES20.glUniform1i(programa.u("sTex"), 0)
        GLES20.glUniformMatrix3fv(programa.u("uMapa"), 1, false, mapa(), 0)
        // Un cuarto de pixel de la copia a cada lado: las cuatro muestras
        // reparten el pixel en cuatro cuadrantes.
        GLES20.glUniform2f(programa.u("uPaso"), 0.25f / ancho, 0.25f / alto)
        programa.dibujar(voltear = true)
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
        if (pendiente >= 0) runCatching { decodificador.releaseOutputBuffer(pendiente, false) }
        runCatching { decodificador.stop() }
        runCatching { decodificador.release() }
        runCatching { extractor.release() }
        runCatching { superficie.release() }
        runCatching { superficieTextura.release() }
        GLES20.glDeleteTextures(1, intArrayOf(texturaOes), 0)
        copia?.soltar()
        copia = null
    }

    companion object {
        /**
         * Margen para dar por empezado un fotograma. Los instantes del
         * archivo vienen redondeados a la escala de tiempo del contenedor, y
         * un fotograma que "empieza" en 33366 cuando se pide 33333 es el
         * mismo para cualquiera que lo mire.
         */
        const val TOLERANCIA = 1_000L

        /** Mas alla de esto por delante, saltar sale mas barato que decodificar. */
        private const val SALTO_LARGO = 3_000_000L

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
