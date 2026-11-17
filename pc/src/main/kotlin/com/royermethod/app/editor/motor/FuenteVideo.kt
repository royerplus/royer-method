package com.royermethod.app.editor.motor

import android.content.Context
import android.net.Uri
import android.opengl.GLES20
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Un video abierto para sacar fotogramas en el instante que se pida (PC).
 *
 * Misma idea y mismo contrato que la del telefono: para cada instante se
 * ensena el ultimo fotograma del archivo que ya ha empezado, y el siguiente se
 * guarda para la proxima peticion, asi no se pierde ni se repite ninguno.
 * Aqui lee FFmpeg (JavaCV) en vez de MediaCodec y el fotograma se sube a una
 * textura normal en RGBA con la fila de arriba primero.
 *
 * Todo se llama desde el hilo que tiene el contexto de OpenGL, salvo
 * [preparar] y [soltarDecodificador], que no lo tocan.
 */
class FuenteVideo(
    context: Context,
    val uri: Uri,
    /** Grados que trae marcados el archivo; el giro lo hace la copia. */
    private val giro: Int,
    private val vista: Boolean = false,
    abrir: Uri = uri,
    private val corrimientoUs: Long = 0L,
    diferida: Boolean = false
) {
    private val archivo = abrir
    private var lector: FFmpegFrameGrabber? = null
    private val texturaVideo: Int

    var anchoCrudo = 0
        private set
    var altoCrudo = 0
        private set
    /** Duracion de un fotograma del archivo. */
    private var pasoUs = 33_333L

    var ptsActual = -1L
        private set
    var ultimaPeticion = -1L
        private set

    /** Un fotograma ya convertido a RGBA, fuera de FFmpeg (que reutiliza el suyo). */
    private class Cuadro(var pixeles: ByteBuffer, var pts: Long)
    private val libres = ArrayDeque<Cuadro>()
    private var pendiente: Cuadro? = null
    private var salidaAgotada = false
    /** Ya se subio a la textura algo con estas medidas. */
    private var texturaLista = false

    var copia: Lamina? = null
        private set
    private var intermedia: Lamina? = null
    private var ptsCopia = -1L

    var usadaEn = -1L

    init {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texturaVideo = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaVideo)
        Lamina.filtros(GLES20.GL_TEXTURE_2D)
        if (!diferida) {
            try {
                abrirDecodificador()
            } catch (e: Exception) {
                GLES20.glDeleteTextures(1, intArrayOf(texturaVideo), 0)
                throw e
            }
        }
    }

    private fun abrirDecodificador() {
        if (lector != null) return
        val g = FFmpegFrameGrabber(archivo.archivo())
        g.pixelFormat = avutil.AV_PIX_FMT_RGBA
        // Todos los nucleos para decodificar; sin sonido (lo lee el mezclador)
        g.setVideoOption("threads", "0")
        g.audioStream = -1
        try {
            g.start()
        } catch (e: Exception) {
            runCatching { g.release() }
            throw e
        }
        require(g.hasVideo()) { "el archivo no tiene video" }
        anchoCrudo = g.imageWidth
        altoCrudo = g.imageHeight
        val fps = g.videoFrameRate.takeIf { it > 1.0 && it < 1000.0 } ?: 30.0
        pasoUs = (1_000_000.0 / fps).toLong()
        lector = g
    }

    val anchoVisible: Int get() = if (giro == 90 || giro == 270) altoCrudo else anchoCrudo
    val altoVisible: Int get() = if (giro == 90 || giro == 270) anchoCrudo else altoCrudo

    fun irA(objetivo: Long, esperaMaxMs: Long = 6000, seguir: (() -> Boolean)? = null): Boolean {
        if (seguir != null && !seguir()) throw LecturaAbandonada()
        abrirDecodificador()
        ultimaPeticion = objetivo
        if (vista && ptsActual >= 0 && objetivo < ptsActual && objetivo + TOLERANCIA_VISTA >= ptsActual) return true
        val retroceso = ptsActual >= 0 && objetivo + TOLERANCIA < ptsActual
        val delante = maxOf(ptsActual, pendiente?.pts ?: -1L)
        val lejos = objetivo - delante > SALTO_LARGO
        if (retroceso || lejos || ptsActual < 0 && pendiente == null && salidaAgotada) saltar(objetivo)

        if (ptsActual >= 0 && objetivo + TOLERANCIA >= ptsActual &&
            ((pendiente?.pts ?: Long.MIN_VALUE) > objetivo + TOLERANCIA || salidaAgotada && pendiente == null)
        ) return true

        var candidato: Cuadro? = null
        var abandonada = false
        val limite = System.nanoTime() + esperaMaxMs * 1_000_000
        while (true) {
            val c = pendiente?.also { pendiente = null } ?: leer() ?: break
            if (c.pts <= objetivo + TOLERANCIA) {
                candidato?.let { libres.addLast(it) }
                candidato = c
                if (seguir != null && !seguir()) { abandonada = true; break }
                if (System.nanoTime() > limite) break
            } else {
                pendiente = c
                break
            }
        }
        if (abandonada) {
            candidato?.let { pintar(it); ultimaPeticion = it.pts }
            throw LecturaAbandonada()
        }
        if (candidato == null && ptsActual < 0) {
            candidato = pendiente?.also { pendiente = null }
        }
        if (candidato == null) return ptsActual >= 0
        pintar(candidato)
        return true
    }

    /** Solo la vista previa, en otro hilo: deja el lector justo antes de [objetivo] sin pintar. */
    fun preparar(objetivo: Long, seguir: () -> Boolean): Boolean {
        abrirDecodificador()
        saltar(objetivo - MARGEN_PREPARAR)
        if (!seguir()) return false
        val c = leer() ?: return false
        pendiente = c
        ultimaPeticion = objetivo - MARGEN_PREPARAR
        return true
    }

    val agotada: Boolean get() = salidaAgotada && pendiente == null

    fun siguiente(esperaMaxMs: Long = 6000): Boolean {
        abrirDecodificador()
        val c = pendiente?.also { pendiente = null } ?: leer() ?: return false
        pintar(c)
        return true
    }

    /** El siguiente fotograma del archivo, copiado; null al final. */
    private fun leer(): Cuadro? {
        if (salidaAgotada) return null
        val g = lector ?: return null
        val f: Frame = try {
            g.grabImage()
        } catch (e: Exception) {
            null
        } ?: run { salidaAgotada = true; return null }
        val img = f.image?.firstOrNull() as? ByteBuffer ?: run { salidaAgotada = true; return null }
        val w = f.imageWidth
        val h = f.imageHeight
        if (w != anchoCrudo || h != altoCrudo) {
            // Cambio de medidas a mitad (raro): se rehace la textura
            anchoCrudo = w; altoCrudo = h
            texturaLista = false
            libres.clear()
        }
        val fila = w * 4
        val bytes = fila * h
        val c = libres.removeFirstOrNull()?.takeIf { it.pixeles.capacity() >= bytes }
            ?: Cuadro(ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder()), 0L)
        val paso = f.imageStride.let { if (it >= fila) it else fila }
        val destino = c.pixeles
        destino.clear()
        if (paso == fila) {
            val o = img.duplicate()
            o.position(0)
            o.limit(bytes)
            destino.put(o)
        } else {
            val o = img.duplicate()
            for (y in 0 until h) {
                o.limit(y * paso + fila)
                o.position(y * paso)
                destino.put(o)
            }
        }
        destino.flip()
        c.pts = f.timestamp + corrimientoUs
        return c
    }

    private fun pintar(c: Cuadro) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaVideo)
        c.pixeles.position(0)
        if (!texturaLista) {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, anchoCrudo, altoCrudo, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, c.pixeles)
            texturaLista = true
        } else {
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, anchoCrudo, altoCrudo,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, c.pixeles)
        }
        ptsActual = c.pts
        libres.addLast(c)
        while (libres.size > 3) libres.removeFirst()
    }

    private fun saltar(objetivo: Long) {
        pendiente?.let { libres.addLast(it) }
        pendiente = null
        val g = lector ?: return
        // Un fotograma y medio antes: FFmpeg se para en el primero que empieza
        // en o despues de lo pedido, y aqui hace falta el que ya se ve en
        // [objetivo], que puede haber empezado antes.
        val desde = (objetivo - corrimientoUs - pasoUs * 3 / 2).coerceAtLeast(0L)
        try {
            g.setVideoTimestamp(desde)
            salidaAgotada = false
        } catch (e: Exception) {
            salidaAgotada = true
        }
        ptsActual = -1
        ptsCopia = -1
    }

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
        reduccion.reducir(texturaVideo, mapa(), anchoVisible, altoVisible, m, c)
        ptsCopia = ptsActual
        return c
    }

    /** De la coordenada de la copia (origen arriba, ya girada) a la de la textura (fila 0 arriba). */
    private fun mapa(): FloatArray {
        // copia (u,v) -> textura (x,y): igual que en el telefono, sin matriz de SurfaceTexture
        val g = when (giro) {
            90 -> floatArrayOf(0f, 1f, 0f, -1f, 0f, 1f)
            180 -> floatArrayOf(-1f, 0f, 1f, 0f, -1f, 1f)
            270 -> floatArrayOf(0f, -1f, 1f, 1f, 0f, 0f)
            else -> floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f)
        }
        // Por columnas para glUniformMatrix3fv: x = g0 u + g1 v + g2, y = g3 u + g4 v + g5
        return floatArrayOf(g[0], g[3], 0f, g[1], g[4], 0f, g[2], g[5], 1f)
    }

    fun soltar() {
        soltarDecodificador()
        soltarGl()
    }

    @Volatile
    var decodificadorSuelto = false
        private set

    fun soltarDecodificador() {
        lector?.let { runCatching { it.stop() }; runCatching { it.release() } }
        lector = null
        pendiente = null
        libres.clear()
        decodificadorSuelto = true
    }

    fun soltarGl() {
        GLES20.glDeleteTextures(1, intArrayOf(texturaVideo), 0)
        copia?.soltar()
        copia = null
        intermedia?.soltar()
        intermedia = null
    }

    companion object {
        const val TOLERANCIA = 1_000L
        const val TOLERANCIA_VISTA = 30_000L
        const val MARGEN_PREPARAR = 100_000L
        @Volatile
        @JvmStatic
        var registrar = false
        private const val SALTO_LARGO = 3_000_000L
    }
}

class LecturaAbandonada : RuntimeException(null, null, false, false)
