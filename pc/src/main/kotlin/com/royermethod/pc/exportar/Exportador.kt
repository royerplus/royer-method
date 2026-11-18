package com.royermethod.pc.exportar

import android.content.Context
import android.net.Uri
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Mezclador
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.PlanAudio
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PlanExportacion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.motor.Compositor
import com.royermethod.app.editor.motor.Egl
import com.royermethod.app.editor.motor.Fluidez
import com.royermethod.app.editor.motor.Lamina
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameRecorder
import org.bytedeco.javacv.Frame
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Cancelado : Exception("cancelado")

data class Informe(
    val archivo: File,
    val bytes: Long,
    val ancho: Int,
    val alto: Int,
    /** "H.265" o "H.264". */
    val codec: String,
    val verificacion: Verificacion,
    val avisos: Int
)

/**
 * Exporta un proyecto a MP4 en el PC.
 *
 * Cada fotograma se dibuja con el mismo motor que la vista previa (y que el
 * telefono), se lee de la GPU y se le da a FFmpeg con su numero: el video
 * sale exactamente a los fps del proyecto (30 o 60), sin perder ni repetir ninguno. El codificador es
 * x265 (H.265) o x264 (H.264) por programa, que a igual tamano dan mejor
 * imagen que los del telefono. El bitrate es el del plan del proyecto: la
 * media de sus tramos, con el tramo mas exigente como techo.
 */
class Exportador(
    private val context: Context,
    private val proyecto: Proyecto,
    private val destino: File,
    private val cancelado: () -> Boolean = { false },
    private val alAvanzar: (hechos: Long, total: Long) -> Unit = { _, _ -> }
) {
    fun exportar(): Informe {
        val segmentos = PlanAudio.de(proyecto)
        val conAudio = segmentos.isNotEmpty()
        val plan = PlanExportacion.de(proyecto, conAudio = conAudio)
        require(plan.fotogramas > 0) { "El proyecto esta vacio" }
        if (destino.exists()) destino.delete()
        destino.parentFile?.mkdirs()

        val hevc = plan.hevc
        var bitrateMedio = 0.0
        var bitrateMax = 0
        for (n in 0 until plan.fotogramas) {
            val b = plan.bitrateEn(n)
            bitrateMedio += b.toDouble() / plan.fotogramas
            bitrateMax = maxOf(bitrateMax, b)
        }

        val grabador = FFmpegFrameRecorder(destino, plan.ancho, plan.alto, if (conAudio) Pcm.CANALES else 0)
        var egl: Egl? = null
        var compositor: Compositor? = null
        var lamina: Lamina? = null
        var mezclador: Mezclador? = null
        var bien = false
        try {
            grabador.format = "mp4"
            grabador.frameRate = plan.fps.toDouble()
            grabador.pixelFormat = avutil.AV_PIX_FMT_YUV420P
            grabador.videoCodecName = if (hevc) "libx265" else "libx264"
            grabador.videoBitrate = bitrateMedio.toInt()
            grabador.gopSize = plan.fps * 4
            grabador.setVideoOption("preset", "medium")
            grabador.setVideoOption("maxrate", (bitrateMax * 1.5).toInt().toString())
            grabador.setVideoOption("bufsize", (bitrateMax * 2).toString())
            if (hevc) grabador.setVideoOption("x265-params", "log-level=error")
            grabador.setOption("movflags", "+faststart")
            if (conAudio) {
                grabador.audioCodec = avcodec.AV_CODEC_ID_AAC
                grabador.sampleRate = Pcm.HZ
                grabador.audioBitrate = plan.audioKbps * 1000
            }
            grabador.start()

            val e = Egl().also { egl = it }
            e.activar()
            val fluido = (proyecto.principal + proyecto.capas.map { it.clip }).any { it.fluido }
            val c = Compositor(context, plan.ancho, plan.alto, if (fluido) Fluidez(context) else null).also { compositor = it }
            val l = Lamina(plan.ancho, plan.alto).also { lamina = it }
            val escena = Escena(proyecto, plan.ancho, plan.alto, c.medidor)
            val pixeles = ByteBuffer.allocateDirect(plan.ancho * plan.alto * 4).order(ByteOrder.nativeOrder())
            val cuadro = Frame(plan.ancho, plan.alto, Frame.DEPTH_UBYTE, 4)
            val imagen = cuadro.image[0] as ByteBuffer

            val totalAudio = PlanAudio.total(proyecto)
            if (conAudio) {
                mezclador = Mezclador(segmentos, totalAudio) { s, dentro ->
                    Mezclador.lector(s, dentro) { uri, desde ->
                        DecodificadorAudio.abrir(context, Uri.parse(uri), desde)?.let { it to it.hz }
                    }
                }
            }
            val muestras = FloatArray(1024 * Pcm.CANALES)
            var audioHecho = 0L

            for (n in 0 until plan.fotogramas) {
                if (cancelado()) throw Cancelado()
                c.dibujar(escena.cuadro(n), l)
                l.leer(pixeles)
                imagen.position(0)
                pixeles.position(0)
                imagen.put(pixeles)
                imagen.position(0)
                grabador.record(cuadro, avutil.AV_PIX_FMT_RGBA)

                // El sonido hasta donde va el video
                val m = mezclador
                if (m != null) {
                    val hasta = Pcm.frames(Linea.tiempoDe(n + 1, plan.fps)).coerceAtMost(totalAudio)
                    audioHecho = sonido(grabador, m, muestras, audioHecho, hasta)
                }
                alAvanzar(n + 1, plan.fotogramas)
            }
            mezclador?.let { audioHecho = sonido(grabador, it, muestras, audioHecho, totalAudio) }
            grabador.stop()

            val verificacion = Verificacion.de(destino, plan.fotogramas, plan.fps)
            bien = true
            return Informe(destino, destino.length(), plan.ancho, plan.alto, if (hevc) "H.265" else "H.264", verificacion, c.avisos)
        } finally {
            mezclador?.cerrar()
            runCatching { compositor?.soltar() }
            runCatching { lamina?.soltar() }
            runCatching { egl?.soltar() }
            runCatching { grabador.release() }
            if (!bien) destino.delete()
        }
    }

    private fun sonido(g: FFmpegFrameRecorder, m: Mezclador, buf: FloatArray, desde: Long, hasta: Long): Long {
        var hecho = desde
        while (hecho < hasta) {
            val n = m.leer(buf, minOf(1024L, hasta - hecho).toInt())
            if (n <= 0) break
            g.recordSamples(Pcm.HZ, Pcm.CANALES, FloatBuffer.wrap(buf, 0, n * Pcm.CANALES))
            hecho += n
        }
        return hecho
    }
}
