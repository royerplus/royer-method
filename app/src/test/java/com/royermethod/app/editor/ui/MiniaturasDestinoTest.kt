package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class MiniaturasDestinoTest {
    private fun estado(destinos: Set<Long>? = null) = EstadoEditor(Proyecto(
        principal = listOf(Clip(1, texto = Texto("Uno")), Clip(2, texto = Texto("Dos"))),
        capas = listOf(Capa(0, 0, Clip(3, esAjuste = true, afectaA = destinos))), siguienteId = 4
    )).also { it.elegir(Seleccion.Bloque(3)) }

    @Test fun tocarUnaMiniaturaDesdeTodasSoloDesactivaEsaYSePuedeDeshacer() {
        val e = estado(); val original = e.proyecto
        alternarDestino(e, 1, setOf(1, 2))
        assertEquals(setOf(2L), e.clipElegido!!.afectaA)
        e.deshacer(); assertEquals(original, e.proyecto)
        assertNull(e.clipElegido!!.afectaA)
    }
    @Test fun tocarActivaYVolverATocarDesactivaSinCambiarElOtroDestino() {
        val e = estado(setOf(2))
        alternarDestino(e, 1, setOf(1, 2))
        assertEquals(setOf(1L, 2L), e.clipElegido!!.afectaA)
        alternarDestino(e, 1, setOf(1, 2))
        assertEquals(setOf(2L), e.clipElegido!!.afectaA)
        alternarDestino(e, 2, setOf(1, 2))
        assertEquals(emptySet<Long>(), e.clipElegido!!.afectaA)
    }
}
