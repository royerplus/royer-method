package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RampaTest {

    private val origen = Origen(uri = "v.mp4", nombre = "v", tipo = TipoOrigen.VIDEO, duracionUs = 4_000_000, ancho = 1080, alto = 1920)
    private fun clip(r: Rampa?, v: Float = 1f) = Clip(id = 1, origen = origen, desdeUs = 0, hastaUs = 4_000_000, velocidad = v, rampa = r)
    private fun pareja(v: Float) = Rampa(CurvaVelocidad(listOf(PuntoVelocidad(0f, v), PuntoVelocidad(1f, v))))

    @Test fun unaRampaParejaEsComoLaVelocidadFija() {
        val c = clip(pareja(0.5f))
        assertEquals(8_000_000.0, c.duracionUs.toDouble(), 2_000.0)
        assertEquals(1_000_000.0, c.tiempoArchivo(2_000_000).toDouble(), 2_000.0)
        assertEquals(0.5f, c.velocidadEn(3_000_000), 1e-3f)
        // Con la velocidad del bloque se multiplica
        val d = clip(pareja(0.5f), v = 2f)
        assertEquals(4_000_000.0, d.duracionUs.toDouble(), 2_000.0)
    }

    @Test fun elGolpeLentoAlargaElBloqueYElTiempoDelArchivoSiempreAvanza() {
        val r = Rampa.PLANTILLAS.first { it.first == "Golpe lento" }.second
        val c = clip(r)
        assertTrue(c.duracionUs > 4_000_000)
        var antes = -1L
        for (t in 0 until c.duracionUs step 33_333) {
            val a = c.tiempoArchivo(t)
            assertTrue("$t", a >= antes)
            antes = a
        }
        assertEquals(3_999_999L, c.tiempoArchivo(c.duracionUs + 10))
        // En el centro va lento
        val mitad = (r.tiempoHasta(0.5f) / r.total * c.duracionUs).toLong()
        assertEquals(0.2f, c.velocidadEn(mitad), 0.02f)
        assertEquals(0.5f, r.posicionEn(r.tiempoHasta(0.5f)), 2e-3f)
    }

    @Test fun partirConservaLaDuracionYLaImagen() {
        val r = Rampa.PLANTILLAS.first { it.first == "Bala" }.second
        val c = clip(r)
        val corte = c.duracionUs / 3
        val u = c.tiempoArchivo(corte) / 4_000_000f
        val a = c.copy(hastaUs = c.tiempoArchivo(corte), rampa = r.parte(0f, u))
        val b = c.copy(desdeUs = c.tiempoArchivo(corte), rampa = r.parte(u, 1f))
        assertEquals(c.duracionUs.toDouble(), (a.duracionUs + b.duracionUs).toDouble(), c.duracionUs * 0.01)
        assertEquals(c.tiempoArchivo(corte + 500_000).toDouble(), b.tiempoArchivo(500_000).toDouble(), 20_000.0)
        assertNull(pareja(1f).parte(0f, 0.5f))
    }
}
