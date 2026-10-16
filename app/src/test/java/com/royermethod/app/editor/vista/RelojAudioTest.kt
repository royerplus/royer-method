package com.royermethod.app.editor.vista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelojAudioTest {
    private val hz = 48_000
    private val base = 1_000_000_000L

    @Test
    fun unCabezalDe2048FramesNoEliminaCuadrosDe30Fps() {
        val reloj = RelojAudio(hz)
        val cuadros = linkedSetOf<Long>()
        for (i in 0..240) {
            val ns = i * 1_000_000_000L / 120
            val frames = ns * hz / 1_000_000_000L
            val cabezal = frames / 2048L * 2048L
            val marca = if (i == 6) RelojAudio.Marca(0L, base) else null
            val posicion = reloj.posicion(base + ns, cabezal, frames + 7200L, true, 0, marca)
            if (i >= 24) cuadros.add(posicion * 30L / hz)
        }
        assertEquals((6L..60L).toList(), cuadros.toList())
    }

    @Test
    fun unTimestampFuturoPermiteCalcularLaPosicionAnterior() {
        val reloj = RelojAudio(hz)
        val posicion = reloj.posicion(base + 50_000_000L, 2048L, 12_000L, true, 0,
            RelojAudio.Marca(4800L, base + 100_000_000L))
        assertEquals(2400L, posicion)
    }

    @Test
    fun noCuentaLaPreparacionDelDecoderComoAudioReproducido() {
        val reloj = RelojAudio(hz)
        assertEquals(0L, reloj.posicion(base, 0L, 7200L, false, 0))
        assertEquals(0L, reloj.posicion(base + 1_000_000_000L, 0L, 7200L, true, 0))
        // Primer avance sin timestamp: espera, no corre con el cabezal
        assertEquals(0L, reloj.posicion(base + 1_050_000_000L, 2048L, 10_000L, true, 0))
        // Pasado el arranque, el cabezal de respaldo; el segundo de preparacion no cuenta
        assertEquals(21_248L, reloj.posicion(base + 1_450_000_000L, 21_248L, 40_000L, true, 0))
    }

    @Test
    fun jamasAdelantaMuestrasQueNoSeHanEscrito() {
        val reloj = RelojAudio(hz)
        reloj.posicion(base, 1000L, 1200L, true, 0, RelojAudio.Marca(1000L, base))
        assertEquals(1200L, reloj.posicion(base + 10_000_000L, 1000L, 1200L, true, 0))
        assertEquals(1200L, reloj.posicion(base + 200_000_000L, 1000L, 1200L, true, 0))
    }

    @Test
    fun unUnderrunCongelaHastaUnAvanceNuevoDelDispositivo() {
        val reloj = RelojAudio(hz)
        reloj.posicion(base, 2048L, 20_000L, true, 0, RelojAudio.Marca(2048L, base))
        val anterior = reloj.posicion(base + 20_000_000L, 2048L, 20_000L, true, 0)
        assertEquals(3008L, anterior)
        assertEquals(anterior, reloj.posicion(base + 30_000_000L, 2048L, 20_000L, true, 1))
        assertTrue(reloj.congelado)
        assertEquals(anterior, reloj.posicion(base + 40_000_000L, 2048L, 20_000L, true, 1,
            RelojAudio.Marca(4800L, base + 40_000_000L)))
        assertEquals(4096L, reloj.posicion(base + 45_000_000L, 4096L, 20_000L, true, 1,
            RelojAudio.Marca(4096L, base + 45_000_000L)))
        assertFalse(reloj.congelado)
    }

    @Test
    fun elCabezalEstancadoNoDejaCorrerElRelojDeSistema() {
        val reloj = RelojAudio(hz)
        reloj.posicion(base, 2048L, 100_000L, true, 0, RelojAudio.Marca(2048L, base))
        val limite = reloj.posicion(base + 90_000_000L, 2048L, 100_000L, true, 0)
        assertEquals(4096L, limite)
        assertEquals(limite, reloj.posicion(base + 120_000_000L, 2048L, 100_000L, true, 0))
        assertTrue(reloj.congelado)
        assertEquals(limite, reloj.posicion(base + 5_000_000_000L, 2048L, 100_000L, true, 0))
    }

    @Test
    fun laRafagaSeAdaptaYLosRetrasosLargosTienenUnTope() {
        val reloj = RelojAudio(hz)
        reloj.posicion(base, 4096L, 100_000L, true, 0)
        assertEquals(4096L, reloj.rafagaFrames)
        reloj.posicion(base + 50_000_000L, 50_000L, 100_000L, true, 0)
        assertEquals(4800L, reloj.rafagaFrames)
        assertTrue(reloj.posicion(base + 150_000_000L, 50_000L, 100_000L, true, 0) <= 54_800L)
    }

    @Test
    fun extenderElContadorDe32BitsNoReiniciaLaPosicion() {
        val reloj = RelojAudio(hz)
        val escritos = 0x1_0000_2000L
        reloj.posicion(base, 0x7fff_f000L, escritos, true, 0)
        reloj.posicion(base + 500_000_000L, 0xffff_f000L, escritos, true, 0)
        val posicion = reloj.posicion(base + 510_000_000L, 0x0000_0800L, escritos, true, 0)
        assertEquals(0x1_0000_0800L, posicion)
        assertEquals(0x1_0000_0800L, reloj.cabezalObservado)
    }

    @Test
    fun unaCorreccionDeTimestampNuncaHaceRetrocederLaImagen() {
        val reloj = RelojAudio(hz)
        reloj.posicion(base, 2048L, 20_000L, true, 0, RelojAudio.Marca(2048L, base))
        val anterior = reloj.posicion(base + 20_000_000L, 2048L, 20_000L, true, 0)
        assertEquals(anterior, reloj.posicion(base + 40_000_000L, 4096L, 20_000L, true, 0,
            RelojAudio.Marca(3000L, base + 40_000_000L)))
        assertTrue(reloj.posicion(base + 60_000_000L, 4096L, 20_000L, true, 0) > anterior)
    }

    @Test
    fun unTimestampCorruptoUsaElCabezalEnLugarDeDarUnSalto() {
        val reloj = RelojAudio(hz)
        // Ni salta a la marca imposible ni corre todavia con el cabezal
        assertEquals(0L, reloj.posicion(base, 2048L, 7200L, true, 0,
            RelojAudio.Marca(1_000_000L, base)))
        assertEquals(0L, reloj.posicion(base + 10_000_000L, 2528L, 7200L, true, 0,
            RelojAudio.Marca(2528L, base + 10_000_000_000L)))
        // Sin ninguna marca valida, pasado el arranque sigue al cabezal
        assertEquals(23_648L, reloj.posicion(base + 450_000_000L, 23_648L, 40_000L, true, 0,
            RelojAudio.Marca(1_000_000L, base + 450_000_000L)))
    }

    /**
     * El cabezal (lo consumido) va 100 ms por delante de lo que suena. Antes,
     * hasta el primer timestamp la imagen corria con el cabezal y al llegar
     * este quedaba por detras: la imagen se paraba ~100-200 ms al empezar.
     */
    @Test
    fun conLatenciaLaImagenNoSeCongelaAlLlegarElPrimerTimestamp() {
        val reloj = RelojAudio(hz)
        val latencia = 100_000_000L
        val posiciones = ArrayList<Long>()
        for (i in 0..50) {
            val ns = i * 16_000_000L
            val cabezal = 4800L + ns * hz / 1_000_000_000L
            // El primer timestamp llega en la tercera consulta: el frame 0 suena en base + latencia
            val marca = if (i >= 2) RelojAudio.Marca(0L, base + latencia) else null
            posiciones.add(reloj.posicion(base + ns, cabezal, cabezal + 7200L, true, 0, marca))
        }
        val arranque = posiciones.indexOfFirst { it > 0L }
        assertTrue("Empieza a moverse cuando suena", arranque in 6..8)
        for (i in arranque until posiciones.size - 1) {
            assertTrue("Se paro en la consulta $i: $posiciones", posiciones[i + 1] > posiciones[i])
        }
        assertEquals((800_000_000L - latencia) * hz / 1_000_000_000L, posiciones.last())
    }

    @Test
    fun sinTimestampEsperaAlArranqueYLuegoSigueAlCabezal() {
        val reloj = RelojAudio(hz)
        for (i in 0..24) {
            val ns = i * 16_000_000L
            val cabezal = 2048L + ns * hz / 1_000_000_000L
            assertEquals(0L, reloj.posicion(base + ns, cabezal, cabezal + 7200L, true, 0))
        }
        val ns = 410_000_000L
        val cabezal = 2048L + ns * hz / 1_000_000_000L
        assertEquals(cabezal, reloj.posicion(base + ns, cabezal, cabezal + 7200L, true, 0))
    }
}
