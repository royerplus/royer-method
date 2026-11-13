package com.royermethod.app.editor.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder

/**
 * El audio de un archivo, decodificado a [Pcm] desde un instante exacto.
 *
 * Se entrega a la frecuencia del archivo ([hz]); quien lo usa lo pasa a
 * 48 kHz. Empieza justo en el instante pedido, no en el paquete que lo
 * contiene: se decodifica desde un poco antes y se tira lo que sobra. Un
 * paquete AAC son 21 ms, y un desfase asi entre imagen y sonido se nota en
 * cualquier golpe o palabra.
 */
class DecodificadorAudio private constructor(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    private val desdeUs: Long
) : FuentePcm {

    var hz = 0
        private set
    private var canales = 0
    private var flotante = false

    private val info = MediaCodec.BufferInfo()
    private var entradaAcabada = false
    private var salidaAcabada = false

    /** Decodificado y aun no entregado, ya en estereo float. */
    private var pendiente = FloatArray(0)
    private var pendienteDesde = 0
    private var pendienteHasta = 0

    /** Decodifica hasta conocer el formato de salida (la frecuencia real). */
    private fun cebar(): Boolean {
        var vueltas = 0
        while (hz == 0 && !salidaAcabada && vueltas++ < 2000) paso()
        return hz > 0
    }

    /** Una vuelta: mete un paquete si cabe y saca lo que haya. */
    private fun paso() {
        if (!entradaAcabada) {
            val i = codec.dequeueInputBuffer(ESPERA_US)
            if (i >= 0) {
                val buf = codec.getInputBuffer(i)!!
                val n = extractor.readSampleData(buf, 0)
                if (n < 0) {
                    codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    entradaAcabada = true
                } else {
                    codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                    extractor.advance()
                }
            }
        }
        val o = codec.dequeueOutputBuffer(info, ESPERA_US)
        when {
            o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> formato(codec.outputFormat)
            o >= 0 -> {
                if (hz == 0) formato(codec.outputFormat)
                if (info.size > 0) convertir(o)
                codec.releaseOutputBuffer(o, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) salidaAcabada = true
            }
        }
    }

    private fun formato(f: MediaFormat) {
        hz = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        canales = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        flotante = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
    }

    private fun convertir(indice: Int) {
        val buf = codec.getOutputBuffer(indice)!!.order(ByteOrder.nativeOrder())
        buf.position(info.offset)
        buf.limit(info.offset + info.size)
        val bytes = if (flotante) 4 else 2
        val frames = info.size / (bytes * canales)
        // Lo de antes del instante pedido sobra
        val saltar = if (info.presentationTimeUs < desdeUs) {
            ((desdeUs - info.presentationTimeUs) * hz / 1_000_000L).toInt().coerceAtMost(frames)
        } else 0
        val utiles = frames - saltar
        if (utiles <= 0) return

        // Compactar y hacer sitio
        val quedan = pendienteHasta - pendienteDesde
        if (pendienteDesde > 0) {
            System.arraycopy(pendiente, pendienteDesde * 2, pendiente, 0, quedan * 2)
            pendienteDesde = 0
            pendienteHasta = quedan
        }
        if ((quedan + utiles) * 2 > pendiente.size) pendiente = pendiente.copyOf((quedan + utiles) * 2)

        var j = pendienteHasta * 2
        if (flotante) {
            val fb = buf.asFloatBuffer()
            fb.position(saltar * canales)
            for (f in 0 until utiles) {
                val l = fb.get()
                val r = if (canales > 1) fb.get() else l
                for (c in 2 until canales) fb.get()
                pendiente[j++] = l
                pendiente[j++] = r
            }
        } else {
            val sb = buf.asShortBuffer()
            sb.position(saltar * canales)
            for (f in 0 until utiles) {
                val l = sb.get() / 32768f
                val r = if (canales > 1) sb.get() / 32768f else l
                for (c in 2 until canales) sb.get()
                pendiente[j++] = l
                pendiente[j++] = r
            }
        }
        pendienteHasta += utiles
    }

    override fun leer(destino: FloatArray, frames: Int): Int {
        while (pendienteHasta - pendienteDesde == 0 && !salidaAcabada) {
            // Un archivo roto a mitad no tumba la exportacion: se acaba ahi
            // y lo que falte sale en silencio.
            if (runCatching { paso() }.isFailure) salidaAcabada = true
        }
        val n = minOf(frames, pendienteHasta - pendienteDesde)
        if (n <= 0) return 0
        System.arraycopy(pendiente, pendienteDesde * 2, destino, 0, n * 2)
        pendienteDesde += n
        return n
    }

    override fun cerrar() {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    companion object {
        private const val ESPERA_US = 10_000L
        /** Se empieza a decodificar un poco antes: el primer paquete tras saltar sale mal. */
        private const val PREVIO_US = 100_000L

        /** null si el archivo no tiene audio o no se puede decodificar. */
        fun abrir(context: Context, uri: Uri, desdeUs: Long): DecodificadorAudio? {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(context, uri, null)
                val pista = (0 until ex.trackCount).firstOrNull {
                    ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: run { ex.release(); return null }
                ex.selectTrack(pista)
                val formato = ex.getTrackFormat(pista)
                ex.seekTo((desdeUs - PREVIO_US).coerceAtLeast(0), MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val codec = MediaCodec.createDecoderByType(formato.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(formato, null, null, 0)
                codec.start()
                val d = DecodificadorAudio(ex, codec, desdeUs)
                if (!d.cebar()) {
                    d.cerrar()
                    return null
                }
                return d
            } catch (e: Exception) {
                runCatching { ex.release() }
                return null
            }
        }
    }
}
