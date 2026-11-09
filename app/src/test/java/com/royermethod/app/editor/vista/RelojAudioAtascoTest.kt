package com.royermethod.app.editor.vista

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Emula lo que SalidaAudio.sonados() le pasa a RelojAudio: cabezal a rafagas
 * de 960 frames cada 20 ms, latencia de salida de 100 ms, timestamps con la
 * misma politica de consulta (cada consulta hasta tener marca, luego 100 ms,
 * estable -> 10 s, reinicio al congelar o con underrun).
 */
class RelojAudioAtascoTest {
    private val hz = 48_000
    private val base = 10_000_000_000L
    private val lat = 100 // ms

    private class Dispositivo(val atasco: IntRange, val marcaTrasAtasco: String) {
        // frames consumidos (cabezal) y oidos en el ms t
        val H = 2000 / 20 * 960L
        fun cabezal(t: Int): Long = when {
            t < atasco.first -> t / 20 * 960L
            t <= atasco.last -> H
            else -> H + (t - atasco.last) / 20 * 960L
        }
        fun oido(t: Int, lat: Int): Long {
            val sinAtasco = maxOf(0L, (t - lat) * 48L)
            return when {
                t < atasco.last + lat -> minOf(sinAtasco, H)
                else -> H + (t - atasco.last - lat) * 48L
            }
        }
    }

    private fun correr(d: Dispositivo, log: Boolean): List<Triple<Int, Long, Long>> {
        val r = RelojAudio(hz)
        var siguiente = 0L
        var anterior: RelojAudio.Marca? = null
        var estable = false
        var underruns = 0
        var ultimoUnderrun = -1
        val traza = ArrayList<Triple<Int, Long, Long>>()
        var t = 0
        while (t <= 3000) {
            val ahora = base + t * 1_000_000L
            if (t >= d.atasco.first) underruns = 1
            if (ultimoUnderrun >= 0 && underruns > ultimoUnderrun) { estable = false; anterior = null; siguiente = ahora }
            ultimoUnderrun = underruns
            var marca: RelojAudio.Marca? = null
            if (ahora >= siguiente || !r.tieneMarca) {
                val tm = t / 20 * 20
                val valida = when {
                    tm <= lat -> false
                    tm < d.atasco.first -> true
                    d.marcaTrasAtasco == "ninguna" -> tm >= d.atasco.last + lat + 50
                    else -> true
                }
                val m = when {
                    !valida -> null
                    d.marcaTrasAtasco == "vieja" && tm >= d.atasco.first && tm < d.atasco.last + lat + 50 ->
                        RelojAudio.Marca(d.oido(d.atasco.first - 20, lat), base + (d.atasco.first - 20) * 1_000_000L)
                    else -> RelojAudio.Marca(d.oido(tm, lat), base + tm * 1_000_000L)
                }
                if (m != null) {
                    val a = anterior
                    if (a != null && m.frames > a.frames && m.nanoTime > a.nanoTime) estable = true
                    anterior = m
                } else { estable = false; anterior = null }
                marca = m
                siguiente = ahora + if (estable) 10_000_000_000L else 100_000_000L
            }
            val p = r.posicion(ahora, d.cabezal(t), d.cabezal(t) + 7200, true, underruns, marca)
            if (r.congelado) { estable = false; anterior = null; siguiente = ahora }
            traza.add(Triple(t, p, d.oido(t, lat)))
            if (log && t in 1980..2560 && t % 16 == 0) println("t=$t ms reloj=${p / 48} ms oido=${d.oido(t, lat) / 48} ms  dif=${(p - d.oido(t, lat)) / 48} ms congelado=${r.congelado}")
            t += 8
        }
        return traza
    }

    @Test
    fun trasUnUnderrunSinMarcaNuevaLaImagenSeAdelantaAlSonido() {
        println("--- sin marca durante 150 ms tras el atasco ---")
        val tr = correr(Dispositivo(2000..2300, "ninguna"), true)
        val antes = tr.filter { it.first in 2000..2100 }.maxOf { it.third - it.second } / 48
        val despues = tr.filter { it.first in 2300..2600 }.maxOf { it.second - it.third } / 48
        println("max imagen por detras del sonido al atascarse: $antes ms; max por delante al recuperarse: $despues ms")
        // Antes de este arreglo: 104 y 100 ms.
        assertTrue("por detras $antes ms", antes <= 10)
        assertTrue("por delante $despues ms", despues <= 10)
    }

    @Test
    fun trasUnUnderrunConMarcaViejaTambienSeAdelanta() {
        println("--- marca de antes del atasco ---")
        val tr = correr(Dispositivo(2000..2300, "vieja"), true)
        val despues = tr.filter { it.first in 2300..2600 }.maxOf { it.second - it.third } / 48
        println("max por delante al recuperarse: $despues ms")
        // Antes de este arreglo: 142 ms.
        assertTrue("por delante $despues ms", despues <= 10)
    }
}

/**
 * Pausa sin desmontar la pista (SalidaAudio.pausar / reanudar): el cabezal se
 * para, lo que habia en camino al altavoz termina de sonar, y al seguir la
 * pista aun devuelve un rato el timestamp de antes de la pausa.
 */
class RelojAudioPausaTest {
    private val base = 10_000_000_000L
    private val lat = 100
    private val pausa = 2000
    private val sigue = 5000
    private val h = pausa / 20 * 960L

    private fun cabezal(t: Int): Long = when {
        t < pausa -> t / 20 * 960L
        t < sigue -> h
        else -> h + (t - sigue) / 20 * 960L
    }

    private fun oido(t: Int): Long = when {
        t < sigue + lat -> minOf(maxOf(0L, (t - lat) * 48L), h)
        else -> h + (t - sigue - lat) * 48L
    }

    @Test
    fun alSeguirNoVuelveAtrasNiSeAdelantaAlSonido() {
        val r = RelojAudio(48_000)
        var siguiente = 0L
        var anterior: RelojAudio.Marca? = null
        var estable = false
        var ultima = 0L
        var t = 0
        var maxDelante = 0L
        var maxDetras = 0L
        while (t <= 7000) {
            val ahora = base + t * 1_000_000L
            val sonando = t < pausa || t >= sigue
            var marca: RelojAudio.Marca? = null
            if (sonando && (ahora >= siguiente || !r.tieneMarca)) {
                val tm = t / 20 * 20
                val m = when {
                    tm <= lat -> null
                    tm < pausa -> RelojAudio.Marca(oido(tm), base + tm * 1_000_000L)
                    // La pista recien reanudada repite la marca de la pausa
                    tm < sigue + lat + 50 -> RelojAudio.Marca(oido(pausa - 10), base + (pausa - 10) * 1_000_000L)
                    else -> RelojAudio.Marca(oido(tm), base + tm * 1_000_000L)
                }
                if (m != null) {
                    val a = anterior
                    if (a != null && m.frames > a.frames && m.nanoTime > a.nanoTime) estable = true
                    anterior = m
                } else { estable = false; anterior = null }
                marca = m
                siguiente = ahora + if (estable) 10_000_000_000L else 100_000_000L
            }
            val p = r.posicion(ahora, cabezal(t), cabezal(t) + 7200, sonando, 0, marca)
            if (r.congelado) { estable = false; anterior = null; siguiente = ahora }
            assertTrue("vuelve atras en t=$t", p >= ultima)
            ultima = p
            if (t >= sigue) maxDelante = maxOf(maxDelante, p - oido(t))
            if (t >= sigue + lat + 150) maxDetras = maxOf(maxDetras, oido(t) - p)
            t += 8
        }
        assertTrue("la imagen se adelanta ${maxDelante / 48} ms al sonido", maxDelante <= 10 * 48)
        assertTrue("la imagen va ${maxDetras / 48} ms por detras", maxDetras <= 25 * 48)
        assertTrue("no siguio", ultima > h + 1500 * 48)
    }
}
