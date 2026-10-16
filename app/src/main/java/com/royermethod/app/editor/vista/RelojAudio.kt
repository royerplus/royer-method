package com.royermethod.app.editor.vista

/**
 * Posicion continua a partir de informes discretos del dispositivo. No depende
 * de Android: los tiempos de entrada usan la misma base que System.nanoTime.
 *
 * El reloj nunca adelanta datos que no se hayan escrito ni mas de una rafaga
 * respecto al cabezal observado. Si se agota el audio se queda quieto; al
 * recuperarse espera a que el dispositivo alcance la ultima posicion mostrada.
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
    private var huboAvance = false
    private var ultimoUnderrun = -1
    private var cabezalAlCongelar = 0L
    private var marca: Marca? = null

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
            huboAvance = true
        }
        val seAgoto = ultimoUnderrun >= 0 && underruns > ultimoUnderrun
        ultimoUnderrun = underruns
        val estancado = huboAvance && ahoraNs - ultimoAvanceNs > nanosDe(rafagaFrames) * 2L + 20_000_000L
        if (!reproduciendo || seAgoto || estancado) {
            congelado = true
            cabezalAlCongelar = cabezal
            marca = null
            return ultimaPosicion
        }
        if (congelado) {
            if (cabezal <= cabezalAlCongelar) return ultimaPosicion
            congelado = false
        }
        // Antes de que arranque el altavoz no usamos el tiempo de preparacion
        // del decoder como si hubiera sido tiempo de reproduccion.
        if (!huboAvance) return ultimaPosicion

        if (nuevaMarca != null) {
            val frames = extender(nuevaMarca.frames, cabezal)
            // Un timestamp puede estar en el futuro: el audio ya tiene una
            // presentacion comprometida. Se conserva el delta negativo.
            val distanciaNs = ahoraNs - nuevaMarca.nanoTime
            if (distanciaNs in -5_000_000_000L..5_000_000_000L && frames in 0L..escritos) {
                val candidata = frames + framesDe(distanciaNs)
                if (kotlin.math.abs(candidata - cabezal) <= hz * 2L) marca = Marca(frames, nuevaMarca.nanoTime)
            }
        }
        val m = marca
        val candidata = if (m != null) m.frames + framesDe(ahoraNs - m.nanoTime)
        else cabezal + framesDe((ahoraNs - ultimoAvanceNs).coerceAtLeast(0L))
        val limite = minOf(escritos.coerceAtLeast(0L), cabezal + rafagaFrames)
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
}
