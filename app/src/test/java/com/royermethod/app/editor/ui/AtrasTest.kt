package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clip
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

    /**
     * Lo de dentro de Animar como en VistaAnimacion.alAtras (que necesita
     * Compose para crearse): apaga lo que diga [AtrasAnimacion.de].
     */
    private class Animar(var menu: Boolean = false, var agregando: Boolean = false, var ajustes: Boolean = false) {
        fun alAtras(hayElegida: Boolean): Boolean {
            when (AtrasAnimacion.de(menu, agregando, ajustes, hayElegida) ?: return false) {
                AtrasAnimacion.MENU -> menu = false
                AtrasAnimacion.AGREGANDO -> agregando = false
                AtrasAnimacion.AJUSTES -> ajustes = false
            }
            return true
        }
    }

    @Test fun enAnimarCierraElDesplegableDespuesElModoYDespuesLaHoja() {
        val e = estado()
        e.seleccion = Seleccion.Bloque(1)
        e.panel = Panel.ANIMACION
        val v = Animar(menu = true, ajustes = true)
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
        // "+" encendido tambien se apaga antes de cerrar, y antes que los ajustes
        v.agregando = true
        v.ajustes = true
        atrasEditor(e) {}
        assertFalse(v.agregando)
        assertTrue(v.ajustes)
        assertEquals(Panel.ANIMACION, e.panel)
        // Sin elegida los ajustes no se ven: atras no se gasta en ellos
        hayElegida = false
        atrasEditor(e) {}
        assertNull(e.panel)
        assertEquals(Seleccion.Bloque(1), e.seleccion)
        quitar()
    }

    @Test fun elOrdenDeAnimarEsDesplegableMasYAjustes() {
        assertEquals(AtrasAnimacion.MENU, AtrasAnimacion.de(menu = true, agregando = true, ajustes = true, hayElegida = true))
        assertEquals(AtrasAnimacion.AGREGANDO, AtrasAnimacion.de(menu = false, agregando = true, ajustes = true, hayElegida = true))
        assertEquals(AtrasAnimacion.AJUSTES, AtrasAnimacion.de(menu = false, agregando = false, ajustes = true, hayElegida = true))
        assertNull(AtrasAnimacion.de(menu = false, agregando = false, ajustes = true, hayElegida = false))
        assertNull(AtrasAnimacion.de(menu = false, agregando = false, ajustes = false, hayElegida = true))
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
        val v = Animar(menu = true)
        e.alAtras { v.alAtras(false) }
        e.seleccion = Seleccion.Bloque(1)
        atrasEditor(e) {}
        assertTrue(v.menu)
        assertNull(e.seleccion)
    }
}
