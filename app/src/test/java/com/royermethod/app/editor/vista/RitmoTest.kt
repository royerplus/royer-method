package com.royermethod.app.editor.vista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class RitmoTest {
    private val base = 7_000_000_000L
    private val p60 = 1_000_000_000L / 60
    private val p120 = 1_000_000_000L / 120

    private data class Visto(val n: Long, val presentarNs: Long, val relojEnPresentacionUs: Long)

    /**
     * Simula [refrescos] avisos de la pantalla cada [periodo]. El sonido va en
     * [t0] + lo que pasa desde [base] + [desfaseUs]; cada lectura con [temblorUs]
     * de ruido y el aviso con [retrasoNs] de demora.
     */
    private fun simular(
        r: Ritmo, periodo: Long, refrescos: Int, desfaseUs: Long, temblorUs: Long = 0, retrasoNs: (Int) -> Long = { 1_000_000L },
        t0: Long = 2_000_000L, semilla: Long = 1
    ): List<Visto> {
        val azar = Random(semilla)
        val vistos = ArrayList<Visto>()
        for (i in 0 until refrescos) {
            val vsync = base + i * periodo
            val ahora = vsync + retrasoNs(i)
            val ruido = if (temblorUs > 0) (azar.nextDouble() * 2 - 1) * temblorUs else 0.0
            val t = t0 + desfaseUs + (ahora - base) / 1_000 + ruido.toLong()
            val turno = r.turno(vsync, ahora, t, avanza = true) ?: continue
            val reloj = t0 + desfaseUs + (turno.presentarNs + periodo / 2 - base) / 1_000
            vistos += Visto(turno.fotograma, turno.presentarNs, reloj)
        }
        return vistos
    }

    /** Cuantos refrescos dura cada fotograma en pantalla. */
    private fun duraciones(v: List<Visto>, periodo: Long): List<Long> =
        v.zipWithNext { a, b -> Math.round((b.presentarNs - a.presentarNs).toDouble() / periodo) }

    @Test
    fun a60HzCadaFotogramaDuraDosRefrescosSeaCualSeaLaFase() {
        // Desfases que ponen los cambios de fotograma en cualquier punto entre refrescos
        for (desfase in 0L until 34_000L step 1_700L) {
            val v = simular(Ritmo(30), p60, 600, desfase)
            val d = duraciones(v, p60).drop(2)
            assertTrue("desfase $desfase: $d", d.all { it == 2L })
            // Seguidos: no se salta ninguno
            assertTrue(v.drop(3).zipWithNext().all { (a, b) -> b.n == a.n + 1 })
        }
    }

    @Test
    fun conTemblorEnElRelojNoHayTirones() {
        // El cambio de fotograma cae justo en un refresco y el reloj tiembla +-3 ms
        for (semilla in 1L..5L) {
            val v = simular(Ritmo(30), p60, 900, desfaseUs = 0, temblorUs = 3_000, semilla = semilla)
            val d = duraciones(v, p60).drop(10)
            assertTrue("semilla $semilla: ${d.groupingBy { it }.eachCount()}", d.all { it == 2L })
        }
    }

    @Test
    fun loQueSeVeEsLoQueSuenaEnEseRefresco() {
        val v = simular(Ritmo(30), p60, 600, desfaseUs = 12_345)
        for (x in v.drop(3)) {
            val inicio = x.n * 1_000_000L / 30
            // El fotograma que corresponde a lo que suena, a menos de tres cuartos de refresco
            assertTrue("$x", x.relojEnPresentacionUs >= inicio - p60 * 3 / 4_000)
            assertTrue("$x", x.relojEnPresentacionUs < inicio + 33_334 + p60 * 3 / 4_000)
        }
    }

    @Test
    fun seDibujaConMargenYSePresentaEnUnRefresco() {
        val r = Ritmo(30, periodoInicialNs = p60)
        val vsync = base
        val turno = r.turno(vsync, vsync + 1_000_000L, 5_000_000L, avanza = true)!!
        // Tres refrescos por delante a 60 Hz; se pide medio refresco antes
        assertEquals(vsync + 3 * p60 - p60 / 2, turno.presentarNs)
        assertEquals(vsync + 2 * p60, turno.limiteNs)
    }

    @Test
    fun siElAvisoLlegaMuyTardeApuntaAUnRefrescoPosterior() {
        val r = Ritmo(30, periodoInicialNs = p60)
        val vsync = base
        val ahora = vsync + 25_000_000L
        val turno = r.turno(vsync, ahora, 5_000_000L, avanza = true)!!
        assertTrue(turno.presentarNs + p60 / 2 - ahora >= Ritmo.MINIMO_NS)
        assertEquals(0L, (turno.presentarNs + p60 / 2 - vsync) % p60)
    }

    @Test
    fun conElRelojParadoNoAdelantaNada() {
        val r = Ritmo(30)
        r.reiniciar(60)
        // El sonido aun no arranca: lo que suena es el fotograma 60 todo el rato
        for (i in 0 until 20) {
            val vsync = base + i * p60
            assertNull(r.turno(vsync, vsync + 1_000_000L, 2_000_000L, avanza = false))
        }
        // Arranca: el siguiente es el 61 o el 62, nunca uno de antes
        var primero: Long? = null
        for (i in 20 until 40) {
            val vsync = base + i * p60
            val t = 2_000_000L + (i - 20) * p60 / 1_000
            val turno = r.turno(vsync, vsync + 1_000_000L, t, avanza = true) ?: continue
            if (primero == null) primero = turno.fotograma
        }
        assertTrue("$primero", primero != null && primero in 61L..63L)
    }

    @Test
    fun a120HzCadaFotogramaDuraCuatroRefrescos() {
        val r = Ritmo(30, periodoInicialNs = p120)
        val v = simular(r, p120, 1200, desfaseUs = 4_100, temblorUs = 1_500)
        val d = duraciones(v, p120).drop(10)
        assertTrue("${d.groupingBy { it }.eachCount()}", d.all { it == 4L })
    }

    @Test
    fun aprendeElPeriodoAunqueEmpieceConOtro() {
        val r = Ritmo(30, periodoInicialNs = p60)
        simular(r, p120, 300, desfaseUs = 0)
        assertTrue("${r.periodoNs}", kotlin.math.abs(r.periodoNs - p120) < 200_000L)
        val r2 = Ritmo(30, periodoInicialNs = p60)
        // Avisos que se saltan refrescos de vez en cuando: sigue en 60 Hz
        simular(r2, p60, 300, desfaseUs = 0)
        assertTrue("${r2.periodoNs}", kotlin.math.abs(r2.periodoNs - p60) < 200_000L)
    }

    @Test
    fun avisosQueLleganConDemoraVariableNoCambianElRitmo() {
        // El hilo llega entre 0 y 12 ms tarde a cada aviso
        val azar = Random(7)
        val demoras = LongArray(900) { (azar.nextDouble() * 12_000_000).toLong() }
        val v = simular(Ritmo(30), p60, 900, desfaseUs = 9_000, retrasoNs = { demoras[it] })
        val d = duraciones(v, p60).drop(5)
        assertTrue("${d.groupingBy { it }.eachCount()}", d.all { it == 2L })
    }

    @Test
    fun unaDerivaLentaSoloRepiteAlgunCuadroSuelto() {
        // El sonido va un 0,1 % mas rapido que la pantalla: la fase da la vuelta de vez en cuando
        val r = Ritmo(30)
        val vistos = ArrayList<Visto>()
        for (i in 0 until 3600) {
            val vsync = base + i * p60
            val ahora = vsync + 1_000_000L
            val t = 2_000_000L + (ahora - base) * 1001 / 1_000_000
            val turno = r.turno(vsync, ahora, t, avanza = true) ?: continue
            vistos += Visto(turno.fotograma, turno.presentarNs, 0)
        }
        val d = duraciones(vistos, p60).drop(5)
        val raros = d.count { it != 2L }
        // En 60 s, 1,8 fotogramas de deriva: unos pocos cuadros de 1 o 3 refrescos, no un temblor continuo
        assertTrue("${d.groupingBy { it }.eachCount()}", raros in 1..12)
    }
}
