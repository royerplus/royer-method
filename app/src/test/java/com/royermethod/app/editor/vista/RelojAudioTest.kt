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
        assertEquals(2048L, reloj.posicion(base + 1_050_000_000L, 2048L, 10_000L, true, 0))
        assertEquals(2816L, reloj.posicion(base + 1_066_000_000L, 2048L, 10_000L, true, 0))
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
        reloj.posicion(base + 10_000_000L, 0xffff_f000L, escritos, true, 0)
        val posicion = reloj.posicion(base + 20_000_000L, 0x0000_0800L, escritos, true, 0)
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
        assertEquals(2048L, reloj.posicion(base, 2048L, 7200L, true, 0,
            RelojAudio.Marca(1_000_000L, base)))
        assertEquals(2528L, reloj.posicion(base + 10_000_000L, 2048L, 7200L, true, 0,
            RelojAudio.Marca(2528L, base + 10_000_000_000L)))
    }
}
