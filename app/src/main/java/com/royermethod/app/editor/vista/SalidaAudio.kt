package com.royermethod.app.editor.vista

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
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
    // Cada clip se abre un segundo antes de sonar, en otro hilo: abrirlo aqui
    // dejaria al altavoz sin muestras justo en la junta y se oiria un corte.
    private val mezclador = Mezclador(segmentos, total, desde, antelacion = Pcm.HZ.toLong()) { s, dentro ->
        Mezclador.lector(s, dentro) { uri, d ->
            DecodificadorAudio.abrir(app, Uri.parse(uri), d)?.let { it to it.hz }
        }
    }
    private val pista: AudioTrack
    @Volatile
    private var sonando = true
    @Volatile
    private var framesEscritos = 0L
    @Volatile
    private var escrituraCompleta = false
    @Volatile
    private var completaNs = 0L
    @Volatile
    var fallo = false
        private set
    private val hilo: Thread
    private val framesObjetivo = (total - desde).coerceAtLeast(0L)
    private val reloj = RelojAudio(Pcm.HZ)
    private val timestamp = AudioTimestamp()
    private var siguienteTimestampNs = 0L
    private var ultimoTimestamp: RelojAudio.Marca? = null
    private var timestampEstable = false
    private var intentosSinTimestamp = 0
    private var ultimoUnderrun = -1

    /** Diagnostico opcional: lo que se leyo del dispositivo en cada consulta. */
    data class Lectura(
        val nanoTime: Long, val cabezal: Long, val escritos: Long,
        val marcaFrames: Long?, val marcaNanoTime: Long?, val posicion: Long, val congelado: Boolean
    )
    var alLeer: ((Lectura) -> Unit)? = null

    /**
     * Ya se escribio todo y el altavoz no empezo en 300 ms: no va a sonar (el
     * relleno de silencio deberia evitarlo). La vista previa sigue con el
     * reloj del sistema. Solo desde el hilo GL.
     */
    val sinArrancar: Boolean get() = escrituraCompleta && !reloj.arranco &&
        System.nanoTime() - completaNs > 300_000_000L

    /** El ultimo frame ya se ha drenado, incluyendo el redondeo a muestras. */
    val agotada: Boolean get() = escrituraCompleta &&
        maxOf(reloj.ultimaPosicion, reloj.cabezalObservado) >= framesObjetivo

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
            // 150 ms de margen: absorbe un tiron del telefono sin que se oiga. El
            // reloj no se retrasa, porque sale de lo que ya sono, no de lo escrito.
            .setBufferSizeInBytes(maxOf(minimo * 2, Pcm.HZ * 15 / 100 * Pcm.CANALES * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        hilo = Thread({ bucle() }, "sonido")
        hilo.start()
    }

    private fun bucle() {
        val f = FloatArray(1024 * Pcm.CANALES)
        val s = ShortArray(1024 * Pcm.CANALES)
        var arrancada = false
        var shortsEscritos = 0L
        try {
            while (sonando) {
                val n = mezclador.leer(f, 1024)
                if (n <= 0) {
                    completaNs = System.nanoTime()
                    escrituraCompleta = framesEscritos >= framesObjetivo
                    if (sonando && !escrituraCompleta) fallo = true
                    if (escrituraCompleta) rellenarSilencio(s, arrancada)
                    break
                }
                for (i in 0 until n * Pcm.CANALES) s[i] = (f[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                var escritos = 0
                while (escritos < n * Pcm.CANALES && sonando) {
                    val r = pista.write(s, escritos, n * Pcm.CANALES - escritos)
                    if (r < 0) {
                        if (sonando) fallo = true
                        return
                    }
                    if (r == 0) { Thread.yield(); continue }
                    escritos += r
                    shortsEscritos += r
                    framesEscritos = shortsEscritos / Pcm.CANALES
                    // Arranca con algo ya en el buffer: si no, el primer trozo sale a tirones
                    if (!arrancada) { pista.play(); arrancada = true }
                }
            }
        } catch (_: Exception) {
            // El hilo GL toma una base nueva antes de pasar al reloj del sistema.
            if (sonando) fallo = true
        } finally {
            mezclador.cerrar()
        }
    }

    /**
     * En MODE_STREAM la pista no empieza a sonar hasta llenar su buffer: si el
     * tramo que queda es mas corto (play justo antes del final), no sonaria
     * nunca y la imagen se quedaria quieta. Se completa con silencio, que no
     * cuenta en [framesEscritos]: el reloj no pasa del final real.
     */
    private fun rellenarSilencio(s: ShortArray, arrancada: Boolean) {
        s.fill(0)
        var faltan = pista.bufferSizeInFrames.toLong() * Pcm.CANALES
        var empezo = arrancada
        while (faltan > 0 && sonando) {
            val r = pista.write(s, 0, minOf(s.size.toLong(), faltan).toInt())
            if (r < 0) return
            if (r == 0) { Thread.yield(); continue }
            faltan -= r
            if (!empezo) { pista.play(); empezo = true }
        }
    }

    /** Posicion continua del altavoz; se consulta solamente desde el hilo GL. */
    fun sonados(): Long {
        val ahora = System.nanoTime()
        val cabezal = pista.playbackHeadPosition.toLong() and 0xffffffffL
        val underruns = pista.underrunCount
        val reproduciendo = sonando && pista.playState == AudioTrack.PLAYSTATE_PLAYING
        if (ultimoUnderrun >= 0 && underruns > ultimoUnderrun) resincronizarTimestamp(ahora)
        ultimoUnderrun = underruns
        var marca: RelojAudio.Marca? = null
        // Hasta que el reloj acepte el primer timestamp se pregunta en cada
        // fotograma: es el que dice cuando empieza a sonar de verdad.
        if (reproduciendo && (ahora >= siguienteTimestampNs || !reloj.tieneMarca)) {
            if (pista.getTimestamp(timestamp)) {
                marca = RelojAudio.Marca(timestamp.framePosition, timestamp.nanoTime)
                val anterior = ultimoTimestamp
                if (anterior != null && marca.frames > anterior.frames && marca.nanoTime > anterior.nanoTime) timestampEstable = true
                ultimoTimestamp = marca
                intentosSinTimestamp = 0
            } else {
                timestampEstable = false
                ultimoTimestamp = null
                intentosSinTimestamp++
            }
            // Tras dos informes que avanzan basta un ancla ocasional. En rutas
            // sin timestamps se usa el cabezal interpolado y se reintenta luego.
            siguienteTimestampNs = ahora + if (timestampEstable || intentosSinTimestamp >= 20) 10_000_000_000L else 100_000_000L
        }
        val posicion = reloj.posicion(ahora, cabezal, framesEscritos, reproduciendo, underruns, marca)
        // Congelado, el reloj descarta las marcas: no cuentan para darlo por
        // estable, y al recuperarse hace falta una nueva enseguida.
        if (reloj.congelado) resincronizarTimestamp(ahora)
        alLeer?.invoke(Lectura(ahora, cabezal, framesEscritos, marca?.frames, marca?.nanoTime, posicion, reloj.congelado))
        return posicion
    }

    private fun resincronizarTimestamp(ahora: Long) {
        timestampEstable = false
        ultimoTimestamp = null
        intentosSinTimestamp = 0
        siguienteTimestampNs = ahora
    }

    fun parar() {
        sonando = false
        runCatching { pista.pause() }
        runCatching { pista.flush() }
        hilo.join(500)
        runCatching { pista.stop() }
        pista.release()
    }
}
