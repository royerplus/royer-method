package com.royermethod.app.editor.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanLigeraTest {

    @Test
    fun soloSiElOriginalEsGrandeOTieneLasClavesSeparadas() {
        // 1080p con claves cada 4 s (el 00001 del proyecto de Royer)
        assertTrue(PlanLigera.haceFalta(1920, 1080, 4_000_000L))
        // Pequeno pero con claves cada 2 s
        assertTrue(PlanLigera.haceFalta(640, 360, 2_000_000L))
        // Pequeno y con claves cada medio segundo: ya va ligero
        assertFalse(PlanLigera.haceFalta(640, 360, 500_000L))
        assertFalse(PlanLigera.haceFalta(720, 1280, 1_000_000L))
    }

    @Test
    fun lasMedidasBajanA720DeLadoCortoEnMultiplosDe16() {
        assertEquals(1280 to 720, PlanLigera.medidas(1920, 1080))
        assertEquals(720 to 1280, PlanLigera.medidas(1080, 1920))
        // 1080x864 -> 900x720, redondeado a 896
        assertEquals(896 to 720, PlanLigera.medidas(1080, 864))
        // Ya pequeno: no se agranda
        assertEquals(640 to 368, PlanLigera.medidas(640, 360))
        val (w, h) = PlanLigera.medidas(3840, 2160)
        assertEquals(1280 to 720, w to h)
    }

    @Test
    fun elBitrateVaConElTamanoYElRitmo() {
        val b30 = PlanLigera.bitrate(1280, 720, 30f)
        val b60 = PlanLigera.bitrate(1280, 720, 60f)
        assertTrue(b60 > b30)
        assertTrue(b30 in 1_500_000..10_000_000)
        // Sin ritmo conocido, como a 30
        assertEquals(b30, PlanLigera.bitrate(1280, 720, 0f))
        assertEquals(1_500_000, PlanLigera.bitrate(160, 96, 30f))
    }

    @Test
    fun unaCopiaConClavesSeparadasOIncompletaNoVale() {
        assertTrue(PlanLigera.copiaValida(3000, 3000, 500_000L))
        assertTrue(PlanLigera.copiaValida(3000, 2980, 600_000L))
        assertFalse(PlanLigera.copiaValida(3000, 2900, 500_000L))
        // El codificador no hizo caso de las claves
        assertFalse(PlanLigera.copiaValida(3000, 3000, 4_000_000L))
    }

    @Test
    fun sobranLasDeUsoMasViejoHastaCaberEnElLimite() {
        val g = listOf(
            PlanLigera.Guardada("a", 600, 10),
            PlanLigera.Guardada("b", 600, 30),
            PlanLigera.Guardada("c", 600, 20)
        )
        assertEquals(listOf("a", "c"), PlanLigera.sobrantes(g, 700))
        assertEquals(listOf("a"), PlanLigera.sobrantes(g, 1200))
        assertEquals(emptyList<String>(), PlanLigera.sobrantes(g, 2000))
    }
}
