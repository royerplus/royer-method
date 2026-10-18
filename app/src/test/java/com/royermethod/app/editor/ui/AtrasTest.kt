package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.MomentoAnimacion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.ui.screens.atrasEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El boton atras del sistema en el editor: primero lo que un panel tiene
 * abierto dentro (el desplegable de Animar, el catalogo de Efectos), despues
 * la hoja, lo elegido y salir. El BackHandler es de Android; lo que decide
 * (atrasEditor y lo que apuntan los paneles) se prueba aqui.
 */
class AtrasTest {

    private fun estado() = EstadoEditor(Proyecto(principal = listOf(Clip(1, texto = Texto("x"), hastaUs = 2_000_000)), siguienteId = 2))

    @Test fun sinNadaApuntadoSeCierraLaHojaYDespuesSeSale() {
        val e = estado()
        var fuera = 0
        e.seleccion = Seleccion.Bloque(1)
        e.panel = Panel.EFECTOS
        assertFalse(e.atras())
        atrasEditor(e, { fuera++ })
        assertNull(e.panel)
        assertEquals(Seleccion.Bloque(1), e.seleccion)
        atrasEditor(e, { fuera++ })
        assertNull(e.seleccion)
        assertEquals(0, fuera)
        atrasEditor(e, { fuera++ })
        assertEquals(1, fuera)
    }

    @Test fun elUltimoApuntadoVaPrimeroYAlQuitarloDejaDeContar() {
        val e = estado()
        val orden = ArrayList<String>()
        val quitarA = e.alAtras { orden.add("a"); true }
        val quitarB = e.alAtras { orden.add("b"); false }
        assertTrue(e.atras())
        assertEquals(listOf("b", "a"), orden)
        quitarA()
        orden.clear()
        assertFalse(e.atras())
        assertEquals(listOf("b"), orden)
        quitarB()
        assertFalse(e.atras())
    }

    @Test fun enAnimarCierraElDesplegableDespuesElModoYDespuesLaHoja() {
        val e = estado()
        e.seleccion = Seleccion.Bloque(1)
        e.panel = Panel.ANIMACION
        val v = VistaAnimacion(MomentoAnimacion.ENTRADA, elegidaId = 7, ajustes = true)
        v.menu = true
        var hayElegida = true
        val quitar = e.alAtras { v.alAtras(hayElegida) }
        atrasEditor(e) {}
        assertFalse(v.menu)
        assertTrue(v.ajustes)
        assertEquals(Panel.ANIMACION, e.panel)
        // Despues los ajustes de la elegida: vuelve al catalogo
        atrasEditor(e) {}
        assertFalse(v.ajustes)
        assertEquals(Panel.ANIMACION, e.panel)
        // "+" encendido tambien se apaga antes de cerrar
        v.agregando = true
        atrasEditor(e) {}
        assertFalse(v.agregando)
        assertEquals(Panel.ANIMACION, e.panel)
        // Sin elegida los ajustes no se ven: atras no se gasta en ellos
        v.ajustes = true
        hayElegida = false
        atrasEditor(e) {}
        assertNull(e.panel)
        assertEquals(Seleccion.Bloque(1), e.seleccion)
        quitar()
    }

    @Test fun enEfectosVuelveDelCatalogoSiHayEfectosYSiNoCierra() {
        val e = estado()
        e.seleccion = Seleccion.Bloque(1)
        e.panel = Panel.EFECTOS
        val eleccion = EleccionEfectos(3, catalogo = true)
        var hay = true
        val quitar = e.alAtras { eleccion.alAtras(hay) }
        atrasEditor(e) {}
        assertFalse(eleccion.catalogo)
        assertEquals(3L, eleccion.id)
        assertEquals(Panel.EFECTOS, e.panel)
        atrasEditor(e) {}
        assertNull(e.panel)
        // Sin efectos el catalogo es todo lo que hay: cierra la hoja
        e.panel = Panel.EFECTOS
        eleccion.catalogo = true
        hay = false
        atrasEditor(e) {}
        assertTrue(eleccion.catalogo)
        assertNull(e.panel)
        quitar()
    }

    @Test fun conLaHojaCerradaNoSeConsultaLoDeDentro() {
        // Mientras una hoja se va (animacion de salida) sigue apuntada: atras no la toca
        val e = estado()
        val v = VistaAnimacion(MomentoAnimacion.ENTRADA)
        v.menu = true
        e.alAtras { v.alAtras(false) }
        e.seleccion = Seleccion.Bloque(1)
        atrasEditor(e) {}
        assertTrue(v.menu)
        assertNull(e.seleccion)
    }
}
