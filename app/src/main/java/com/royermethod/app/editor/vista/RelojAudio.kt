package com.royermethod.app.editor.vista

/**
 * Posicion continua a partir de informes discretos del dispositivo. No depende
 * de Android: los tiempos de entrada usan la misma base que System.nanoTime.
 *
 * El reloj nunca adelanta datos que no se hayan escrito ni mas de una rafaga
 * respecto al cabezal observado. Si se agota el audio se queda quieto; al
 * recuperarse espera a que el dispositivo alcance la ultima posicion mostrada.
 *
 * El cabezal cuenta lo que el sistema ya consumio, que va por delante de lo
 * que suena (la latencia de salida: ~80 ms en el altavoz, ~200 ms en
 * Bluetooth). Solo el timestamp dice lo que suena. Al arrancar, sin timestamp
 * todavia, el reloj espera en vez de correr con el cabezal: si corriera, al
 * llegar el primer timestamp quedaria por detras y la imagen se pararia.
 */
internal class RelojAudio(private val hz: Int, rafagaInicialFrames: Long = 2048L) {
    data class Marca(val frames: Long, val nanoTime: Long)

    private val maxRafaga = (hz / 10L).coerceAtLeast(1L)
    var rafagaFrames = rafagaInicialFrames.coerceIn(1L, maxRafaga)
        private set
    var ultimaPosicion = 0L
        private set
    var congelado = false
        private set

    private var ultimoCabezal = 0L
    val cabezalObservado: Long get() = ultimoCabezal
    private var ultimoAvanceNs = 0L
    private var primerAvanceNs = 0L
    private var huboAvance = false
    /** El dispositivo ya empezo a consumir audio. */
    val arranco: Boolean get() = huboAvance
    /** Hay un timestamp aceptado: la posicion sale de lo que suena. */
    val tieneMarca: Boolean get() = marca != null
    private var ultimoUnderrun = -1
    private var cabezalAlCongelar = 0L
    private var marca: Marca? = null
    /** Cuanto va el cabezal por delante de lo que suena (frames), aprendido de las marcas; -1 sin medir. */
    private var latencia = -1L
    /** Desde cuando esta congelado: una marca de antes es vieja (pausa, agotado). */
    private var congeladoNs = Long.MIN_VALUE
    /** El cabezal se paro (se agoto o se estanco): lo que suena no pasa del cabezal. */
    private var atascado = false

    fun posicion(
        ahoraNs: Long,
        cabezal32: Long,
        escritos: Long,
        reproduciendo: Boolean,
        underruns: Int,
        nuevaMarca: Marca? = null
    ): Long {
        val cabezal = extender(cabezal32, ultimoCabezal).coerceAtLeast(ultimoCabezal)
        val avanzo = cabezal > ultimoCabezal
        if (avanzo) {
            rafagaFrames = maxOf(rafagaFrames, (cabezal - ultimoCabezal).coerceAtMost(maxRafaga))
            ultimoCabezal = cabezal
            ultimoAvanceNs = ahoraNs
            if (!huboAvance) primerAvanceNs = ahoraNs
            huboAvance = true
        }
        val seAgoto = ultimoUnderrun >= 0 && underruns > ultimoUnderrun
        ultimoUnderrun = underruns
        val estancado = huboAvance && ahoraNs - ultimoAvanceNs > nanosDe(rafagaFrames) * 2L + 20_000_000L
        if (avanzo) atascado = false
        if (seAgoto || estancado) atascado = true
        // Atascado, lo ya consumido sigue sonando hasta el cabezal: con marca se
        // sigue hasta ahi y luego se espera. En pausa se queda donde sonaba.
        if (!reproduciendo || atascado && (marca == null || ultimaPosicion >= cabezal)) {
            if (!congelado) congeladoNs = ahoraNs
            congelado = true
            cabezalAlCongelar = cabezal
            marca = null
            return ultimaPosicion
        }
        if (congelado) {
            if (cabezal <= cabezalAlCongelar) return ultimaPosicion
            congelado = false
        }
        // Una marca positiva puede llegar antes del primer avance del cabezal.
        // En el arranque algunos dispositivos devuelven frame 0 con una fecha
        // que sigue avanzando mientras el altavoz aun espera. Usarla de ancla
        // adelanta la imagen; la primera marca real obliga luego a congelarla.
        // Hasta tener una posicion positiva esperamos en el cuadro inicial.
        if (nuevaMarca != null) {
            val frames = extender(nuevaMarca.frames, cabezal)
            // Un timestamp puede estar en el futuro: el audio ya tiene una
            // presentacion comprometida. Se conserva el delta negativo.
            val distanciaNs = ahoraNs - nuevaMarca.nanoTime
            // Una marca tomada antes de congelarse (p. ej. antes de una pausa) es
            // vieja: con ella la imagen saltaria atras o adelante al seguir.
            if (distanciaNs in -5_000_000_000L..5_000_000_000L && frames in 1L..escritos && nuevaMarca.nanoTime >= congeladoNs) {
                val candidata = frames + framesDe(distanciaNs)
                if (kotlin.math.abs(candidata - cabezal) <= hz * 2L) marca = Marca(frames, nuevaMarca.nanoTime)
            }
        }
        // Antes de que arranque el altavoz no usamos el tiempo de preparacion
        // del decoder como si hubiera sido tiempo de reproduccion.
        if (!huboAvance) return ultimaPosicion
        val m = marca
        // Sin timestamp, el cabezal solo sirve de respaldo pasado el arranque.
        if (m == null && ahoraNs - primerAvanceNs < GRACIA_SIN_MARCA_NS) return ultimaPosicion
        // Sin marca (al reanudar, tras agotarse) se sigue el cabezal menos la
        // latencia ya aprendida: sin restarla la imagen adelantaria al sonido.
        val candidata = if (m != null) m.frames + framesDe(ahoraNs - m.nanoTime)
        else cabezal + framesDe((ahoraNs - ultimoAvanceNs).coerceAtLeast(0L)) - latencia.coerceAtLeast(0L)
        if (m != null && avanzo) latencia = (cabezal - candidata).coerceIn(0L, hz.toLong())
        val limite = minOf(escritos.coerceAtLeast(0L), if (atascado) cabezal else cabezal + rafagaFrames)
        ultimaPosicion = maxOf(ultimaPosicion, candidata.coerceIn(0L, limite))
        return ultimaPosicion
    }

    private fun framesDe(ns: Long): Long = ns / 1_000_000_000L * hz + ns % 1_000_000_000L * hz / 1_000_000_000L
    private fun nanosDe(frames: Long): Long = frames * 1_000_000_000L / hz

    /** Los dos contadores de AudioTrack son de 32 bits, incluso en un Long. */
    private fun extender(valor: Long, referencia: Long): Long {
        var candidato = (referencia and -0x1_0000_0000L) + (valor and 0xffff_ffffL)
        if (candidato < referencia - 0x8000_0000L) candidato += 0x1_0000_0000L
        else if (candidato > referencia + 0x8000_0000L) candidato -= 0x1_0000_0000L
        return candidato
    }

    private companion object {
        /** Cuanto se espera al primer timestamp antes de fiarse del cabezal. */
        const val GRACIA_SIN_MARCA_NS = 400_000_000L
    }
}
