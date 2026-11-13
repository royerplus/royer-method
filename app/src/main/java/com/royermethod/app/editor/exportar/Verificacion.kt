package com.royermethod.app.editor.exportar

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteBuffer

/**
 * Lo que de verdad quedo en el archivo, medido despues de cerrarlo.
 *
 * Que el exportador pida 30 fps no garantiza que salgan: hay codificadores
 * que se saltan fotogramas cuando van justos de bitrate, sin avisar. Aqui se
 * cuentan y se mira la separacion entre cada uno. Si algo falla, se sabe y
 * se dice, en vez de dar por bueno un video con tirones. Tambien se buscan
 * fotogramas colgados de un IDR (ver [VigiaColgados]), que no son tirones
 * pero se ven grises o con manchas.
 */
data class Verificacion(
    val fotogramas: Int,
    val esperados: Long,
    /** Media real, del primero al ultimo. */
    val fps: Double,
    /** Saltos entre fotogramas que no son de 1/30 s (repetidos o perdidos). */
    val irregulares: Int,
    val duracionVideoUs: Long,
    /** -1 si no hay pista de audio. */
    val duracionAudioUs: Long,
    /** Fotogramas que el codificador dejo mal enlazados tras un IDR. */
    val colgados: Int = 0
) {
    val perfecta: Boolean
        get() = fotogramas.toLong() == esperados && irregulares == 0 && colgados == 0 && kotlin.math.abs(fps - 30.0) < 0.01

    /** Para ensenar al usuario. */
    fun resumen(): String =
        if (perfecta) "$fotogramas fotogramas a ${"%.2f".format(fps)} fps, ninguno perdido"
        else "$fotogramas de $esperados fotogramas, ${"%.2f".format(fps)} fps, $irregulares saltos irregulares" +
            (if (colgados > 0) ", $colgados mal enlazados" else "")

    companion object {
        /** Separacion de un fotograma a 30 fps, con el redondeo a microsegundos. */
        private const val PASO_MIN = 33_332L
        private const val PASO_MAX = 33_335L

        fun de(archivo: File, esperados: Long): Verificacion {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(archivo.absolutePath)
                var pistaVideo = -1
                var pistaAudio = -1
                for (i in 0 until ex.trackCount) {
                    val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("video/") && pistaVideo < 0) pistaVideo = i
                    if (mime.startsWith("audio/") && pistaAudio < 0) pistaAudio = i
                }
                val tiempos = ArrayList<Long>()
                var colgados = 0
                if (pistaVideo >= 0) {
                    val formato = ex.getTrackFormat(pistaVideo)
                    val vigia = formato.getString(MediaFormat.KEY_MIME)
                        ?.takeIf { SincronizacionVideo.admite(it) }?.let { VigiaColgados(it) }
                    val datos = ByteBuffer.allocate(
                        if (formato.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) formato.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                        else 4 shl 20
                    )
                    ex.selectTrack(pistaVideo)
                    while (true) {
                        val t = ex.sampleTime
                        if (t < 0) break
                        tiempos.add(t)
                        // En el orden del archivo, que es el que necesita el vigia.
                        // Un paquete que no cabe se salta: no se puede mirar, pero
                        // contarlo como fallo seria mentir.
                        if (vigia != null) {
                            val n = runCatching { ex.readSampleData(datos, 0) }.getOrDefault(-1)
                            if (n > 0) vigia.ver(datos, 0, n, t)
                        }
                        if (!ex.advance()) break
                    }
                    ex.unselectTrack(pistaVideo)
                    colgados = vigia?.cuantos ?: 0
                }
                // Con fotogramas B el orden del archivo no es el de pantalla
                tiempos.sort()
                var irregulares = 0
                for (i in 1 until tiempos.size) {
                    val d = tiempos[i] - tiempos[i - 1]
                    if (d < PASO_MIN || d > PASO_MAX) irregulares++
                }
                val fps = if (tiempos.size > 1) {
                    (tiempos.size - 1) * 1_000_000.0 / (tiempos.last() - tiempos.first())
                } else 0.0
                var finAudio = -1L
                if (pistaAudio >= 0) {
                    ex.selectTrack(pistaAudio)
                    while (true) {
                        val t = ex.sampleTime
                        if (t < 0) break
                        finAudio = maxOf(finAudio, t)
                        if (!ex.advance()) break
                    }
                }
                val finVideo = if (tiempos.isEmpty()) 0L else tiempos.last() + 33_333L
                return Verificacion(tiempos.size, esperados, fps, irregulares, finVideo, finAudio, colgados)
            } finally {
                ex.release()
            }
        }
    }
}
