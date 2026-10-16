package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EfectosPersistenciaTest {
    @Test
    fun guardaTodosLosEfectosYLaContinuidadDeUnRecorte() {
        val efectos = TipoEfectoVideo.values().mapIndexed { i, tipo ->
            EfectoVideo(tipo, 100_000, 2_000_000, 1.5f, 2f,
                desfaseUs = 400_000, fundidoEntradaUs = 500_000, fundidoSalidaUs = 700_000,
                duracionBaseUs = 2_300_000, activo = i % 2 == 0,
                progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(700_000, 0.8f), ProgresoEfecto(2_300_000, 1f)))
        }
        val p = Proyecto(principal = listOf(Clip(1, texto = Texto("hola"), efectos = efectos)), siguienteId = 2)
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
    }

    @Test
    fun losProyectosAnterioresYLosTiposDesconocidosSiguenAbriendo() {
        val viejo = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"}}]}""")!!
        assertTrue(viejo.principal.single().efectos.isEmpty())
        val nuevo = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"efectos":[
            {"tipo":"DEL_FUTURO","inicio":0,"fin":2000000},
            {"tipo":"RGB","inicio":100,"fin":100},
            {"tipo":"RGB","inicio":0,"fin":2000000,"intensidad":-2,"rapidez":99}
        ]}]}""")!!
        val e = nuevo.principal.single().efectos.single()
        assertEquals(0f, e.intensidad, 0f)
        assertEquals(10f, e.rapidez, 0f)
        assertTrue(e.activo)
    }
}
