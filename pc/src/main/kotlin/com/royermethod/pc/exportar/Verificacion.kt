package com.royermethod.pc.exportar

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.io.File

/**
 * Lo que de verdad quedo en el MP4: cuantos fotogramas, a que ritmo y si hay
 * saltos. Igual que la del telefono, leyendo los paquetes sin decodificar.
 */
data class Verificacion(
    val fotogramas: Int,
    val esperados: Long,
    val fps: Double,
    val irregulares: Int,
    val duracionVideoUs: Long,
    val duracionAudioUs: Long
) {
    val perfecta: Boolean
        get() = fotogramas.toLong() == esperados && irregulares == 0 && kotlin.math.abs(fps - 30.0) < 0.01

    fun resumen(): String =
        if (perfecta) "$fotogramas fotogramas a ${"%.2f".format(fps)} fps, ninguno perdido"
        else "$fotogramas de $esperados fotogramas, ${"%.2f".format(fps)} fps, $irregulares saltos irregulares"

    companion object {
        private const val PASO_MIN = 33_332L
        private const val PASO_MAX = 33_335L

        fun de(archivo: File, esperados: Long): Verificacion {
            val g = FFmpegFrameGrabber(archivo)
            try {
                g.start()
                val oc = g.formatContext
                var video = -1
                var audio = -1
                for (i in 0 until oc.nb_streams()) {
                    when (oc.streams(i).codecpar().codec_type()) {
                        avutil.AVMEDIA_TYPE_VIDEO -> if (video < 0) video = i
                        avutil.AVMEDIA_TYPE_AUDIO -> if (audio < 0) audio = i
                    }
                }
                val tiempos = ArrayList<Long>()
                var finAudio = -1L
                while (true) {
                    val p = g.grabPacket() ?: break
                    val s = p.stream_index()
                    if (s == video || s == audio) {
                        val tb = oc.streams(s).time_base()
                        val us = p.pts() * 1_000_000L * tb.num() / tb.den()
                        if (s == video) tiempos.add(us) else finAudio = maxOf(finAudio, us)
                    }
                }
                tiempos.sort()
                var irregulares = 0
                for (i in 1 until tiempos.size) {
                    val d = tiempos[i] - tiempos[i - 1]
                    if (d < PASO_MIN || d > PASO_MAX) irregulares++
                }
                val fps = if (tiempos.size > 1) (tiempos.size - 1) * 1_000_000.0 / (tiempos.last() - tiempos.first()) else 0.0
                val finVideo = if (tiempos.isEmpty()) 0L else tiempos.last() + 33_333L
                return Verificacion(tiempos.size, esperados, fps, irregulares, finVideo, finAudio)
            } finally {
                runCatching { g.release() }
            }
        }
    }
}
