package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class ClavesPropiedadesTest {
    private fun estado(c: Clip = Clip(1, texto = Texto("Hola"), hastaUs = 2_000_000)) =
        EstadoEditor(Proyecto(principal = listOf(c), siguienteId = 2)).also { it.elegir(Seleccion.Bloque(1)); it.tiempo = 1_000_000 }

    @Test fun porDefectoSeCreanMovimientoYEstiloEnUnSoloDeshacer() {
        val e = estado(); val p = e.proyecto
        cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, false)
        val c = e.clipElegido!!
        assertTrue(c.claves.any { it.tiempoUs == 1_000_000L })
        assertTrue(c.propiedades.keys.contains(Propiedad.OPACIDAD_SOMBRA_TEXTO))
        e.deshacer(); assertEquals(p, e.proyecto)
    }
    @Test fun unaClaveDeMovimientoExistenteAdmiteEstiloSinBorrarse() {
        val e = estado(Clip(1, texto = Texto("Hola"), claves = listOf(Clave(1_000_000, Encuadre(x = .4f))), hastaUs = 2_000_000))
        cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, false)
        assertEquals(.4f, e.clipElegido!!.claves.single { it.tiempoUs == 1_000_000L }.encuadre.x, 0f)
        assertTrue(e.clipElegido!!.propiedades.isNotEmpty())
    }
    @Test fun soloMovimientoNoCreaCanalesDeTexto() {
        val e = estado(); e.modoClaveTexto = 1
        cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, false)
        assertTrue(e.clipElegido!!.propiedades.isEmpty())
        assertTrue(e.clipElegido!!.claves.isNotEmpty())
    }
    @Test fun canalIndependienteNoTocaElMovimiento() {
        val e = estado(); e.canalPropiedad = Propiedad.OPACIDAD_SOMBRA_TEXTO
        cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, false)
        assertEquals(setOf(Propiedad.OPACIDAD_SOMBRA_TEXTO), e.clipElegido!!.propiedades.keys)
        assertTrue(e.clipElegido!!.claves.isEmpty())
    }
    @Test fun quitarCanalIndependienteRespetaLosOtros() {
        val e = estado(); cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, false)
        val movimiento = e.clipElegido!!.claves
        e.canalPropiedad = Propiedad.OPACIDAD_SOMBRA_TEXTO
        cambiarClavePropiedades(e, GrupoPropiedad.TEXTO, true, true)
        assertEquals(movimiento, e.clipElegido!!.claves)
        assertFalse(e.clipElegido!!.propiedades.getValue(Propiedad.OPACIDAD_SOMBRA_TEXTO).any { it.tiempoUs == 1_000_000L })
        assertTrue(e.clipElegido!!.propiedades.getValue(Propiedad.TAMANO).any { it.tiempoUs == 1_000_000L })
    }
    @Test fun capaDeAjusteNoAlargaElMontajeNiElProyectoCorto() {
        val e = estado(Clip(1, texto = Texto("Hola"), hastaUs = 1_000_000))
        crearCapaAjuste(e)
        assertEquals(1_000_000L, e.clipElegido!!.duracionUs)
        assertEquals(1_000_000L, Linea.duracion(e.proyecto))
        e.cambiarClip { it.copy(hastaUs = 10_000_000) }
        assertEquals(1_000_000L, Linea.duracion(e.proyecto))
    }
    @Test fun perspectivaSeGuardaEnLaClaveEnFotosYVideos() {
        val c = Clip(1, origen = Origen("v", "v", TipoOrigen.VIDEO, 720, 1280, 2_000_000), claves = listOf(Clave(0, Encuadre())))
        val p = Proyecto(principal = listOf(c))
        val q = Edicion.ajustarEncuadre(p, 1, 1_000_000) { it.copy(inclinacionX = 30f, inclinacionY = -20f) }
        assertEquals(30f, q.principal.single().encuadreEn(1_000_000).inclinacionX, 0f)
        assertEquals(-20f, q.principal.single().encuadreEn(1_000_000).inclinacionY, 0f)
    }
}
