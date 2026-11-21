package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AjustesColorTest {

    @Test
    fun losAjustesNuevosLleganAlMotor() {
        // colorFinal suma el filtro campo por campo: uno olvidado ahi no se dibujaria
        val c = Clip(id = 1, texto = Texto("x"), color = AjustesColor(claridad = 0.4f, vivacidad = -0.3f, desvanecer = 0.5f, grano = 0.2f))
        assertEquals(c.color, c.colorFinal())
        val conFiltro = c.copy(filtro = Filtro.ANIME, intensidadFiltro = 0.5f).colorFinal()
        assertEquals(0.4f, conFiltro.claridad, 0f)
        assertEquals(-0.3f, conFiltro.vivacidad, 0f)
        assertEquals(0.5f, conFiltro.desvanecer, 0f)
        assertEquals(0.2f, conFiltro.grano, 0f)
    }

    @Test
    fun desvanecerYGranoNoPasanDeUno() {
        val a = AjustesColor(desvanecer = 0.8f, grano = 0.9f).mas(AjustesColor(desvanecer = 1f, grano = 1f), 1f)
        assertEquals(1f, a.desvanecer, 0f)
        assertEquals(1f, a.grano, 0f)
        val b = AjustesColor().mas(AjustesColor(desvanecer = -1f, grano = -1f), 1f)
        assertEquals(0f, b.desvanecer, 0f)
        assertEquals(0f, b.grano, 0f)
    }

    @Test
    fun cualquierAjusteNuevoSacaAlBloqueDelCaminoNeutro() {
        assertTrue(AjustesColor().neutro)
        for (a in listOf(AjustesColor(claridad = 0.1f), AjustesColor(vivacidad = 0.1f), AjustesColor(desvanecer = 0.1f), AjustesColor(grano = 0.1f)))
            assertFalse(a.toString(), a.neutro)
    }

    @Test
    fun losFiltrosNoUsanLosAjustesNuevos() {
        // Los filtros siguen con los mismos valores que antes. No se ven
        // identicos: contraste, saturacion, sombras, luces, exposicion y
        // nitidez cambiaron de formula con los ajustes nuevos.
        for (f in Filtro.values()) {
            val a = f.ajustes
            assertEquals(f.nombre, 0f, a.claridad, 0f)
            assertEquals(f.nombre, 0f, a.vivacidad, 0f)
            assertEquals(f.nombre, 0f, a.desvanecer, 0f)
            assertEquals(f.nombre, 0f, a.grano, 0f)
        }
    }
}
