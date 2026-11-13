package com.royermethod.app.editor.vista

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.net.Uri
import android.os.Process
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
 *
 * Se puede pausar y seguir sin rehacer nada ([pausar], [reanudar]): rehacerlo
 * todo en cada pausa (pista nueva, abrir otra vez cada archivo, llenar el
 * bufer) tardaba en el telefono. Y puede quedarse callada con el bufer ya
 * lleno hasta [arrancar], para que el sonido no entre antes que la imagen.
 */
internal class SalidaAudio(
    context: Context,
    segmentos: List<Segmento>,
    total: Long,
    /** Frame por el que empieza. */
    private val desde: Long,
    /** false: llena el bufer y espera a [arrancar] para sonar. */
    arrancarYa: Boolean = true
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
    /** Lo que cabe en la pista, en shorts: antes de sonar no se escribe mas. */
    private val capacidad: Long
    /** Guarda [sonando], [permitido], [pausada] y [enMarcha]; el escritor espera en el. */
    private val control = Object()
    @Volatile
    private var sonando = true
    /** Puede sonar: desde el principio, o desde [arrancar]. */
    @Volatile
    private var permitido = arrancarYa
    @Volatile
    private var permitidoNs = 0L
    /** La pista ya recibio play(). Con [control]. */
    private var enMarcha = false
    /** Shorts dados a la pista, silencio incluido. Solo el escritor lo cambia. */
    @Volatile
    private var enPista = 0L
    /** En pausa: la pista quieta con lo que tenia y el escritor esperando. */
    @Volatile
    var pausada = false
        private set
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
        val marcaFrames: Long?, val marcaNanoTime: Long?, val posicion: Long, val congelado: Boolean,
        val underruns: Int = 0
    )
    var alLeer: ((Lectura) -> Unit)? = null

    /**
     * Ya se escribio todo, podia sonar y el altavoz no empezo en 300 ms: no va
     * a sonar (el relleno de silencio deberia evitarlo). La vista previa sigue
     * con el reloj del sistema. Solo desde el hilo GL.
     */
    val sinArrancar: Boolean get() = escrituraCompleta && permitido && !pausada && !reloj.arranco &&
        System.nanoTime() - maxOf(completaNs, permitidoNs) > 300_000_000L

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
            // 250 ms de margen: absorbe un tiron del telefono sin que se oiga. El
            // reloj no se retrasa, porque sale de lo que ya sono, no de lo escrito;
            // y el arranque tampoco, porque el bufer se llena antes de arrancar.
            .setBufferSizeInBytes(maxOf(minimo * 2, Pcm.HZ / 4 * Pcm.CANALES * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        capacidad = pista.bufferSizeInFrames.toLong() * Pcm.CANALES
        if (permitido) permitidoNs = System.nanoTime()
        hilo = Thread({ bucle() }, "sonido").apply { isDaemon = true }
        hilo.start()
    }

    private fun bucle() {
        // Que el telefono no lo deje sin turno: sin muestras a tiempo se oye un corte
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) }
        val f = FloatArray(1024 * Pcm.CANALES)
        val s = ShortArray(1024 * Pcm.CANALES)
        try {
            while (sonando) {
                val n = mezclador.leer(f, 1024)
                if (n <= 0) {
                    completaNs = System.nanoTime()
                    escrituraCompleta = framesEscritos >= framesObjetivo
                    if (sonando && !escrituraCompleta) fallo = true
                    if (escrituraCompleta) rellenarSilencio(s)
                    break
                }
                for (i in 0 until n * Pcm.CANALES) s[i] = (f[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
                if (!escribir(s, n * Pcm.CANALES, cuenta = true)) break
            }
        } catch (_: Exception) {
            // El hilo GL toma una base nueva antes de pasar al reloj del sistema.
            if (sonando) fallo = true
        } finally {
            // Los archivos se sueltan ya; la pista no, que aun suena lo escrito.
            // Se suelta aqui y no en parar(): quien para no espera a este hilo.
            mezclador.cerrar()
            synchronized(control) {
                while (sonando) control.wait()
                runCatching { pista.stop() }
                runCatching { pista.release() }
            }
        }
    }

    /**
     * Da [cuantos] shorts de [s] a la pista. Antes de sonar no pasa de lo que
     * cabe (luego esperaria sin fin); en pausa espera a [reanudar]. false si
     * hay que dejarlo: parada o fallo.
     */
    private fun escribir(s: ShortArray, cuantos: Int, cuenta: Boolean): Boolean {
        var escritos = 0
        while (escritos < cuantos) {
            val cabe = esperarTurno()
            if (!sonando) return false
            val r = pista.write(s, escritos, minOf((cuantos - escritos).toLong(), cabe).toInt())
            if (r < 0) {
                if (sonando) fallo = true
                return false
            }
            // 0: la pausa corto la escritura, o no habia sitio todavia
            if (r == 0) { Thread.yield(); continue }
            escritos += r
            enPista += r
            if (cuenta) {
                shortsEscritos += r
                framesEscritos = shortsEscritos / Pcm.CANALES
            }
            arrancarSiToca()
        }
        return true
    }
    private var shortsEscritos = 0L

    /**
     * Espera mientras esta en pausa, o mientras no puede sonar y la pista ya
     * esta llena. Dice cuantos shorts se pueden escribir ahora.
     */
    private fun esperarTurno(): Long = synchronized(control) {
        while (sonando && (pausada || !enMarcha && enPista >= capacidad)) control.wait()
        if (enMarcha) Long.MAX_VALUE else (capacidad - enPista).coerceAtLeast(1L)
    }

    /** La primera vez que hay algo escrito y se puede, play(). */
    private fun arrancarSiToca() {
        synchronized(control) {
            if (enMarcha || !permitido || pausada || !sonando || enPista <= 0L) return
            runCatching { pista.play() }.onFailure { fallo = true }
            enMarcha = true
            control.notifyAll()
        }
    }

    /**
     * En MODE_STREAM la pista no empieza a sonar hasta llenar su buffer: si el
     * tramo que queda es mas corto (play justo antes del final), no sonaria
     * nunca y la imagen se quedaria quieta. Se completa con silencio, que no
     * cuenta en [framesEscritos]: el reloj no pasa del final real.
     */
    private fun rellenarSilencio(s: ShortArray) {
        s.fill(0)
        var faltan = pista.bufferSizeInFrames.toLong() * Pcm.CANALES
        while (faltan > 0 && sonando) {
            val n = minOf(s.size.toLong(), faltan).toInt()
            if (!escribir(s, n, cuenta = false)) return
            faltan -= n
        }
    }

    /**
     * Ya puede sonar: lo pide la vista previa cuando la imagen ya esta en
     * pantalla. Desde cualquier hilo; repetirlo no hace nada.
     */
    fun arrancar() {
        synchronized(control) {
            if (permitido) return
            permitido = true
            permitidoNs = System.nanoTime()
        }
        arrancarSiToca()
    }

    /**
     * Calla en el acto sin tirar lo que hay en la pista: [reanudar] sigue
     * justo ahi. Desde cualquier hilo (la interfaz la llama al tocar pausa,
     * antes de que el hilo de dibujo llegue a la orden).
     */
    fun pausar() {
        synchronized(control) {
            if (pausada || !sonando) return
            pausada = true
            if (enMarcha) runCatching { pista.pause() }
        }
    }

    /** Sigue sonando desde donde se pauso. Desde el hilo GL. */
    fun reanudar() {
        synchronized(control) {
            if (!pausada || !sonando) return
            pausada = false
            if (enMarcha) runCatching { pista.play() }.onFailure { fallo = true }
            control.notifyAll()
        }
        arrancarSiToca()
    }

    /** Posicion continua del altavoz; se consulta solamente desde el hilo GL. */
    fun sonados(): Long {
        val ahora = System.nanoTime()
        val cabezal = pista.playbackHeadPosition.toLong() and 0xffffffffL
        val underruns = pista.underrunCount
        val reproduciendo = sonando && !pausada && pista.playState == AudioTrack.PLAYSTATE_PLAYING
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
        alLeer?.invoke(Lectura(ahora, cabezal, framesEscritos, marca?.frames, marca?.nanoTime, posicion, reloj.congelado, underruns))
        return posicion
    }

    private fun resincronizarTimestamp(ahora: Long) {
        timestampEstable = false
        ultimoTimestamp = null
        intentosSinTimestamp = 0
        siguienteTimestampNs = ahora
    }

    /**
     * Calla y la deja para siempre. No espera a nada: el hilo del sonido sale
     * enseguida (pausar y vaciar cortan su escritura) y el suelta la pista y
     * cierra los archivos. Antes se esperaba aqui hasta medio segundo en cada
     * pausa y en cada busqueda.
     */
    fun parar() {
        synchronized(control) {
            if (!sonando) return
            sonando = false
            // Corta una escritura en curso; con el cerrojo, el hilo del sonido
            // no la suelta a la vez
            runCatching { pista.pause() }
            runCatching { pista.flush() }
            control.notifyAll()
        }
    }
}
