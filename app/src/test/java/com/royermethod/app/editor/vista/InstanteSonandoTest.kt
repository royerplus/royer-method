package com.royermethod.app.editor.vista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstanteSonandoTest {
    private val base = 5_000_000_000L
    private val vsync120 = 1_000_000_000L / 120

    /** Lecturas del reloj a 30 por segundo (el ritmo de la vista previa) con el sonido en [t0] + lo que pasa. */
    private fun lecturasA30(s: InstanteSonando, t0: Long, desdeNs: Long, hastaNs: Long, cada: Long = 1_000_000_000L / 30) {
        var ns = desdeNs
        var anterior = Long.MIN_VALUE
        while (ns <= hastaNs) {
            val t = t0 + (ns - base) / 1_000
            s.leer(t, ns, t > anterior, Long.MAX_VALUE)
            anterior = t
            ns += cada
        }
    }

    @Test
    fun aCientoVeinteHzAvanzaEnCadaRefrescoYNoSaltaDe33Ms() {
        val s = InstanteSonando()
        s.reiniciar(1_000_000L)
        var lectura = base
        var anterior = Long.MIN_VALUE
        val pasos = ArrayList<Long>()
        // Un segundo de refrescos a 120 Hz; el reloj se lee a 30 por segundo, 3 ms despues del vsync
        for (i in 1..120) {
            val ns = base + i * vsync120
            while (lectura + 3_000_000L <= ns) {
                val t = 1_000_000L + (lectura + 3_000_000L - base) / 1_000
                s.leer(t, lectura + 3_000_000L, lectura > base, Long.MAX_VALUE)
                lectura += 1_000_000_000L / 30
            }
            val r = s.en(ns)
            if (anterior != Long.MIN_VALUE) pasos.add(r - anterior)
            anterior = r
        }
        // Despues del arranque, cada refresco avanza unos 8,3 ms, nunca 0 ni 33
        val estables = pasos.drop(8)
        assertTrue("pasos $estables", estables.all { it in 7_000L..10_000L })
    }

    @Test
    fun nuncaVaHaciaAtrasAunqueUnaLecturaLlegueDetras() {
        val s = InstanteSonando()
        s.reiniciar(0L)
        s.leer(100_000L, base, true, Long.MAX_VALUE)
        val adelantado = s.en(base + 30_000_000L)
        assertEquals(130_000L, adelantado)
        // El reloj del sonido dice menos de lo ya ensenado: se queda quieto
        s.leer(120_000L, base + 31_000_000L, true, Long.MAX_VALUE)
        assertEquals(130_000L, s.en(base + 32_000_000L))
        assertEquals(130_000L, s.en(base + 35_000_000L))
        assertEquals(134_000L, s.en(base + 45_000_000L))
    }

    @Test
    fun siElSonidoNoAvanzaNoSeAdelanta() {
        val s = InstanteSonando()
        s.reiniciar(2_000_000L)
        // Al arrancar, el reloj todavia no se mueve
        s.leer(2_000_000L, base, false, Long.MAX_VALUE)
        assertEquals(2_000_000L, s.en(base + 100_000_000L))
    }

    @Test
    fun conUnParonDelDibujoSigueComoMuchoDoscientosMs() {
        val s = InstanteSonando()
        s.reiniciar(0L)
        val cada = 33_333_000L
        val ultima = base + 15 * cada
        lecturasA30(s, 0L, base, ultima, cada)
        val tUltima = 15 * cada / 1_000
        // El hilo de dibujo se para 120 ms: la linea sigue avanzando con el reloj del sistema
        assertEquals(tUltima + 120_000L, s.en(ultima + 120_000_000L))
        // Si el paron se alarga, se para en el tope y espera
        assertEquals(tUltima + InstanteSonando.ADELANTO_MAX_US, s.en(ultima + 900_000_000L))
    }

    @Test
    fun alReiniciarPuedeIrAtrasYNoPasaDelFinal() {
        val s = InstanteSonando()
        s.reiniciar(0L)
        s.leer(900_000L, base, true, 1_000_000L)
        assertEquals(1_000_000L, s.en(base + 150_000_000L))
        s.reiniciar(300_000L)
        assertEquals(300_000L, s.en(base + 160_000_000L))
    }
}
