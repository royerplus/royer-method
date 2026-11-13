package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControlesEfectoTest {
    @Test fun pulsarPuntosAUnaRapidezFraccionariaPermiteSeleccionarlosSinDuplicados() {
        val punto = ProgresoEfecto(30_012_345, 0.7f)
        for (rapidez in listOf(0.1f, 0.3f, 0.7f, 1.5f, 3f, 9.999f)) {
            val e = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR,
                inicioUs = 1_000_000, finUs = 400_000_000, desfaseUs = -33_333,
                duracionBaseUs = 90_000_000, rapidez = rapidez,
                progresos = listOf(ProgresoEfecto(0, 0f), punto, ProgresoEfecto(90_000_000, 1f)))
            val seleccionado = e.puntoEnFase(e.faseControl(e.localDelPunto(punto)))!!
            assertEquals("rapidez $rapidez", punto, seleccionado)
            // La edicion sustituye exactamente el punto seleccionado; quitarlo deja los demas.
            val editados = e.progresos.filter { it.tiempoUs != seleccionado.tiempoUs } +
                seleccionado.copy(valor = 0.8f)
            assertEquals(3, editados.size)
            assertEquals(1, editados.count { it.tiempoUs == punto.tiempoUs })
            assertEquals(2, e.progresos.filter { it.tiempoUs != seleccionado.tiempoUs }.size)
        }
    }

    @Test fun elUltimoMicrosegundoYLosVecinosUsanLaToleranciaDeLaRapidez() {
        val ultimo = ProgresoEfecto(3_000_000, 1f)
        val e = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, finUs = 1_000_000,
            rapidez = 3f, duracionBaseUs = 3_000_000,
            progresos = listOf(ProgresoEfecto(2_999_990, 0.5f), ultimo))
        assertEquals(ultimo, e.puntoEnFase(e.faseControl(999_999)))
        assertEquals(e.progresos.first(), e.puntoEnFase(2_999_992))
        assertNull(e.puntoEnFase(2_999_995))
    }

    @Test fun finDelUltimoFotogramaAceleradoAdmiteTodosLosControles() {
        val c = Clip(1, origen = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, 3_000_000, 30f),
            hastaUs = 3_000_000, efectos = listOf(EfectoVideo(TipoEfectoVideo.FLASH,
                inicioUs = 2_966_667, finUs = 3_000_000))).conVelocidad(2f)
        val e = c.efectos.single()
        val min = minimoFinEfecto(e.inicioUs, Linea.tiempoDe(1), c.duracionUs)
        assertEquals(1_500_000L, min)
        // Deslizador, -1f, +1f y al cabezal pueden recibir tiempos distintos.
        for (t in listOf(0L, 1_466_667L, 1_499_999L, 1_500_000L, 1_533_333L))
            assertEquals(c.duracionUs, acotarBordeEfecto(t, min, c.duracionUs))
    }

    @Test fun limiteInvertidoDefensivoNuncaLanzaNiSaleDeSuMinimo() {
        for (t in listOf(-1L, 0L, 90L, 100L, 110L, 150L))
            assertEquals(110L, acotarBordeEfecto(t, 110, 100))
        assertEquals(90L, acotarBordeEfecto(80, 90, 120))
        assertEquals(120L, acotarBordeEfecto(140, 90, 120))
    }
}
