package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiezasPersistenciaTest {
    @Test
    fun conservaRegionesYClavesEnvolventesConCurvaLibre() {
        val pieza = PiezaVideo("Superior", RegionUv(abajo = 0.5f), Encuadre(y = -0.5f), listOf(
            Clave(-250_000, Encuadre(y = -0.5f), bezier = Bezier(0.2f, -0.1f, 0.8f, 1.2f)),
            Clave(3_250_000, Encuadre(escala = 1.2f, rotacion = 20f))))
        val p = Proyecto(principal = listOf(Clip(1, texto = Texto("texto partido"), piezas = listOf(pieza))), siguienteId = 2)
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
    }

    @Test
    fun unProyectoAnteriorSigueMostrandoElBloqueEntero() {
        val viejo = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"}}]}""")!!
        assertTrue(viejo.principal.single().piezas.isEmpty())
        val p = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"piezas":[
            {"nombre":"Mitad","region":{"izquierda":-1,"arriba":0,"derecha":2,"abajo":0.5}}
        ]}]}""")!!
        assertEquals(RegionUv(abajo = 0.5f), p.principal.single().piezas.single().region)
    }
}
