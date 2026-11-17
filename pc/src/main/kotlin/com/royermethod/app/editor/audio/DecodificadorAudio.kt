package com.royermethod.app.editor.audio

import android.content.Context
import android.net.Uri
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import java.nio.FloatBuffer

/**
 * El audio de un archivo, decodificado a [Pcm] desde un instante exacto (PC).
 *
 * Lo lee FFmpeg y ya lo entrega en estereo float a 48 kHz ([hz]). Como en el
 * telefono, empieza justo en el instante pedido: se decodifica desde un poco
 * antes y se tira lo que sobra.
 */
class DecodificadorAudio private constructor(
    private val lector: FFmpegFrameGrabber,
    private val desdeUs: Long
) : FuentePcm {

    val hz: Int = lector.sampleRate
    private var acabado = false
    private var pendiente = FloatArray(0)
    private var pendienteDesde = 0
    private var pendienteHasta = 0

    /** Un trozo mas del archivo; false al final. */
    private fun paso(): Boolean {
        val f = runCatching { lector.grabSamples() }.getOrNull() ?: run { acabado = true; return false }
        val muestras = f.samples?.firstOrNull() as? FloatBuffer ?: return true
        val canales = f.audioChannels.coerceAtLeast(1)
        val b = muestras.duplicate()
        b.position(0)
        val frames = b.remaining() / canales
        val saltar = if (f.timestamp < desdeUs) ((desdeUs - f.timestamp) * hz / 1_000_000L).toInt().coerceIn(0, frames) else 0
        val utiles = frames - saltar
        if (utiles <= 0) return true
        val quedan = pendienteHasta - pendienteDesde
        if (pendienteDesde > 0) {
            System.arraycopy(pendiente, pendienteDesde * 2, pendiente, 0, quedan * 2)
            pendienteDesde = 0
            pendienteHasta = quedan
        }
        if ((quedan + utiles) * 2 > pendiente.size) pendiente = pendiente.copyOf((quedan + utiles) * 2)
        var j = pendienteHasta * 2
        b.position(saltar * canales)
        for (k in 0 until utiles) {
            val l = b.get()
            val r = if (canales > 1) b.get() else l
            for (c in 2 until canales) b.get()
            pendiente[j++] = l
            pendiente[j++] = r
        }
        pendienteHasta += utiles
        return true
    }

    override fun leer(destino: FloatArray, frames: Int): Int {
        while (pendienteHasta - pendienteDesde == 0 && !acabado) paso()
        val n = minOf(frames, pendienteHasta - pendienteDesde)
        if (n <= 0) return 0
        System.arraycopy(pendiente, pendienteDesde * 2, destino, 0, n * 2)
        pendienteDesde += n
        return n
    }

    override fun cerrar() {
        runCatching { lector.stop() }
        runCatching { lector.release() }
    }

    companion object {
        private const val PREVIO_US = 100_000L

        /** null si el archivo no tiene audio o no se puede decodificar. */
        fun abrir(context: Context, uri: Uri, desdeUs: Long): DecodificadorAudio? {
            val g = FFmpegFrameGrabber(uri.archivo())
            return try {
                g.sampleFormat = avutil.AV_SAMPLE_FMT_FLT
                g.audioChannels = 2
                g.sampleRate = Pcm.HZ
                g.videoStream = -1
                g.start()
                if (!g.hasAudio()) { runCatching { g.release() }; return null }
                if (desdeUs > PREVIO_US) g.setAudioTimestamp(desdeUs - PREVIO_US)
                DecodificadorAudio(g, desdeUs)
            } catch (e: Exception) {
                runCatching { g.release() }
                null
            }
        }
    }
}
