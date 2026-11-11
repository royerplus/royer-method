package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class CapasCompartidasTest {
    @Test fun nuevoAjusteDesdeUnClipIncluyeTodosYRespetaSuRango() {
        val c = Clip(1, texto = Texto("Texto"), hastaUs = 2_000_000)
        val p = Proyecto(capas = listOf(Capa(3_000_000, 0, c)), siguienteId = 2)
        val e = EstadoEditor(p); e.elegir(Seleccion.Bloque(1))
        crearCapaAjuste(e, desdeElegido = true)
        val nueva = e.proyecto.capas.single { it.id != 1L }
        assertEquals(3_000_000L, nueva.inicioUs)
        assertEquals(2_000_000L, nueva.clip.duracionUs)
        assertNull(nueva.clip.afectaA)
        assertEquals(Panel.COLOR, e.panel)
        e.deshacer(); assertEquals(p, e.proyecto)
    }
    @Test fun capaDeEfectosAbreSuCatalogoYNoUnPanelDeColor() {
        val e = EstadoEditor(Proyecto(principal = listOf(Clip(1, texto = Texto("Texto"))), siguienteId = 2))
        crearCapaAjuste(e, efectos = true)
        assertEquals(Panel.EFECTOS, e.panel)
        assertTrue(e.clipElegido!!.esAjuste)
        assertNull(e.clipElegido!!.afectaA)
    }
}
