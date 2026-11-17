package com.royermethod.pc.vista

import android.content.Context
import android.net.Uri
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Mezclador
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.Segmento
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine

/**
 * El sonido de la vista previa en el PC, y su reloj.
 *
 * Como en el telefono, la imagen sigue al sonido: el instante sale de cuantas
 * muestras ha sacado ya la tarjeta de sonido. Mezcla con el mismo [Mezclador]
 * que la exportacion y escribe en una linea de javax.sound a 48 kHz.
 */
class SalidaAudio(
    context: Context,
    segmentos: List<Segmento>,
    total: Long,
    /** Frame por el que empieza. */
    private val desde: Long
) {
    private val app = context.applicationContext
    private val mezclador = Mezclador(segmentos, total, desde, antelacion = Pcm.HZ.toLong()) { s, dentro ->
        Mezclador.lector(s, dentro) { uri, d ->
            DecodificadorAudio.abrir(app, Uri.parse(uri), d)?.let { it to it.hz }
        }
    }
    private val linea: SourceDataLine
    @Volatile
    private var sonando = true
    @Volatile
    var acabada = false
        private set
    private val hilo: Thread

    init {
        val formato = AudioFormat(Pcm.HZ.toFloat(), 16, Pcm.CANALES, true, false)
        linea = AudioSystem.getSourceDataLine(formato)
        // Unos 85 ms de bufer: poco retraso entre imagen y sonido al empezar
        linea.open(formato, 4096 * 4)
        hilo = Thread({ escribir() }, "sonido-vista").apply {
            isDaemon = true
            priority = Thread.MAX_PRIORITY
        }
        hilo.start()
    }

    private fun escribir() {
        val muestras = FloatArray(1024 * Pcm.CANALES)
        val bytes = ByteArray(1024 * Pcm.CANALES * 2)
        try {
            while (sonando) {
                val n = mezclador.leer(muestras, 1024)
                if (n <= 0) break
                var j = 0
                for (k in 0 until n * Pcm.CANALES) {
                    val v = Math.round((muestras[k] * 32767f).coerceIn(-32768f, 32767f))
                    bytes[j++] = (v and 0xFF).toByte()
                    bytes[j++] = ((v shr 8) and 0xFF).toByte()
                }
                var escrito = 0
                while (escrito < j && sonando) escrito += linea.write(bytes, escrito, j - escrito)
            }
            if (sonando) linea.drain()
        } catch (_: Exception) {
        } finally {
            acabada = true
            runCatching { mezclador.cerrar() }
        }
    }

    /** Empieza (o sigue) a sonar. */
    fun arrancar() = linea.start()

    fun pausar() = linea.stop()

    /** Frame del proyecto que suena ahora. */
    val frame: Long get() = desde + linea.longFramePosition

    fun parar() {
        sonando = false
        runCatching { linea.stop() }
        runCatching { linea.flush() }
        runCatching { linea.close() }
    }
}
