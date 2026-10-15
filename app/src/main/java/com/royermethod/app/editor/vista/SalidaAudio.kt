package com.royermethod.app.editor.vista

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Mezclador
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.Segmento

/**
 * El sonido de la vista previa, y su reloj.
 *
 * La imagen sigue al sonido y no al reves: el oido nota un desfase de 40 ms,
 * el ojo no nota que un fotograma llegue 10 ms tarde. Por eso el instante de
 * la vista previa sale de cuantas muestras ha reproducido ya el altavoz.
 */
internal class SalidaAudio(
    context: Context,
    segmentos: List<Segmento>,
    total: Long,
    /** Frame por el que empieza. */
    private val desde: Long
) {
    private val app = context.applicationContext
    private val mezclador = Mezclador(segmentos, total, desde) { s, dentro ->
        Mezclador.lector(s, dentro) { uri, d ->
            DecodificadorAudio.abrir(app, Uri.parse(uri), d)?.let { it to it.hz }
        }
    }
    private val pista: AudioTrack
    @Volatile
    private var sonando = true
    private val hilo: Thread

    init {
        val minimo = AudioTrack.getMinBufferSize(Pcm.HZ, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        pista = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Pcm.HZ)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minimo * 2, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        hilo = Thread({ bucle() }, "sonido")
        hilo.start()
    }

    private fun bucle() {
        val f = FloatArray(1024 * Pcm.CANALES)
        val s = ShortArray(1024 * Pcm.CANALES)
        var arrancada = false
        try {
            while (sonando) {
                val n = mezclador.leer(f, 1024)
                if (n <= 0) break
                for (i in 0 until n * 2) s[i] = (f[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                var escritos = 0
                while (escritos < n * 2 && sonando) {
                    val r = pista.write(s, escritos, n * 2 - escritos)
                    if (r < 0) return
                    escritos += r
                    // Arranca con algo ya en el buffer: si no, el primer trozo sale a tirones
                    if (!arrancada) { pista.play(); arrancada = true }
                }
            }
        } catch (_: Exception) {
            // Sin sonido la vista previa sigue: el reloj cae al del sistema
        } finally {
            mezclador.cerrar()
        }
    }

    /** Frames que ya han sonado por el altavoz, desde [desde]. */
    fun sonados(): Long = pista.playbackHeadPosition.toLong() and 0xffffffffL

    fun parar() {
        sonando = false
        runCatching { pista.pause() }
        runCatching { pista.flush() }
        hilo.join(500)
        runCatching { pista.stop() }
        pista.release()
    }
}
