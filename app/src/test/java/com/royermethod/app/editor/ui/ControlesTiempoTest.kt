package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.TiempoPreciso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlesTiempoTest {

    @Test fun elDeslizadorRedondeaEnVezDeTruncar() {
        // Con Float y toLong salia 4 999
        assertEquals(4_999L, (0.005f * 1e6).toLong())
        assertEquals(5_000L, TiempoCampo.usDeSegundos(0.005f))
        assertEquals(1_000L, TiempoCampo.usDeSegundos(0.001f))
        for (ms in 0..5_000) assertEquals("$ms ms", ms * 1_000L, TiempoCampo.usDeSegundos(ms / 1_000f))
        assertEquals(0L, TiempoCampo.usDeSegundos(Float.NaN))
        assertEquals(0L, TiempoCampo.usDeSegundos(-0.5f))
    }

    @Test fun deslizadorSinFotogramasAlMilisegundoYConFotogramasAlCuadro() {
        // Bloque de 3 s: paso de 1 ms
        assertEquals(5_000L, TiempoCampo.deDeslizador(0.0051f, 0, 3_000_000, fotogramas = false))
        assertEquals(5_000L, TiempoCampo.deDeslizador(0.005f, 0, 3_000_000, fotogramas = false))
        assertEquals(0L, TiempoCampo.deDeslizador(0.005f, 0, 3_000_000, fotogramas = true))
        assertEquals(33_333L, TiempoCampo.deDeslizador(0.03f, 0, 3_000_000, fotogramas = true))
        // Tramo corto: el paso baja para no perder resolucion
        assertEquals(100L, TiempoCampo.pasoDeslizador(50_000))
        assertEquals(1L, TiempoCampo.pasoDeslizador(2_000))
        assertEquals(1_234L, TiempoCampo.deDeslizador(0.001234f, 0, 2_000, fotogramas = false))
        // Acotado y nunca negativo
        assertEquals(3_000_000L, TiempoCampo.deDeslizador(9f, 0, 3_000_000, fotogramas = false))
        assertEquals(1_500L, TiempoCampo.deDeslizador(0f, 1_500, 3_000_000, fotogramas = false))
    }

    @Test fun elAjusteAFotogramasCuentaDesdeElInicioDelBloque() {
        // Bloque que empieza a mitad de fotograma (0,050 s): sus cuadros exportados
        // caen en 66 666 - 50 000 = 16 666, 49 999...
        val origen = 50_000L
        assertEquals(16_666L, TiempoCampo.alFotograma(10_000, origen))
        assertEquals(Linea.tiempoDe(3) - origen, TiempoCampo.alFotograma(40_000, origen))
        // El comienzo del bloque va a su primer cuadro exportado (66 666)
        assertEquals(16_666L, TiempoCampo.alFotograma(0, origen))
        // Si el cuadro mas cercano queda antes del bloque, se queda en 0, nunca negativo
        assertEquals(0L, TiempoCampo.alFotograma(0, 40_000))
        assertEquals(0L, TiempoCampo.alFotograma(-7, 40_000))
        // Con origen 0 es exactamente TiempoPreciso.aFotograma
        for (us in longArrayOf(0, 5_000, 16_666, 16_667, 33_333, 1_234_567)) {
            assertEquals(TiempoPreciso.aFotograma(us), TiempoCampo.alFotograma(us))
        }
    }

    @Test fun losBotonesVanAlCuadroDeAlLadoOMuevenUnPaso() {
        val f1 = Linea.tiempoDe(1)
        val f2 = Linea.tiempoDe(2)
        // En un cuadro: al siguiente y al anterior
        assertEquals(f2, TiempoCampo.paso(f1, 1, true, 1_000, 0, 3_000_000))
        assertEquals(0L, TiempoCampo.paso(f1, -1, true, 1_000, 0, 3_000_000))
        // Entre dos cuadros: al de ese lado
        assertEquals(f1, TiempoCampo.paso(5_000, 1, true, 1_000, 0, 3_000_000))
        assertEquals(0L, TiempoCampo.paso(5_000, -1, true, 1_000, 0, 3_000_000))
        assertEquals(0L, TiempoCampo.paso(0, -1, true, 1_000, 0, 3_000_000))
        // Sin fotogramas: 1 ms o 10 ms, exactos
        assertEquals(6_000L, TiempoCampo.paso(5_000, 1, false, 1_000, 0, 3_000_000))
        assertEquals(4_000L, TiempoCampo.paso(5_000, -1, false, 1_000, 0, 3_000_000))
        assertEquals(15_000L, TiempoCampo.paso(5_000, 1, false, 10_000, 0, 3_000_000))
        assertEquals(0L, TiempoCampo.paso(5_000, -1, false, 10_000, 0, 3_000_000))
        assertEquals(3_000_000L, TiempoCampo.paso(2_999_500, 1, false, 1_000, 0, 3_000_000))
        // Con origen: el siguiente cuadro exportado
        assertEquals(Linea.tiempoDe(2) - 50_000, TiempoCampo.paso(0, 1, true, 1_000, 0, 3_000_000, origenUs = 50_000))
    }

    @Test fun elCampoAceptaComaYPuntoYAvisaLoQueNoEntiende() {
        val l = TiempoCampo.leer("0,005", 0, 3_000_000, fotogramas = false)
        assertEquals(5_000L, l.us)
        assertNull(l.error)
        assertNull(l.nota)
        assertEquals(5_000L, TiempoCampo.leer("0.005", 0, 3_000_000, fotogramas = false).us)
        assertEquals(1_000L, TiempoCampo.leer("0.001", 0, 3_000_000, fotogramas = false).us)
        assertEquals(5_000L, TiempoCampo.leer("5 ms", 0, 3_000_000, fotogramas = false).us)

        val negativo = TiempoCampo.leer("-0,005", 0, 3_000_000, fotogramas = false)
        assertNull(negativo.us)
        assertEquals("Sin tiempos negativos", negativo.error)
        val raro = TiempoCampo.leer("abc", 0, 3_000_000, fotogramas = false)
        assertNull(raro.us)
        assertNotNull(raro.error)
    }

    @Test fun elCampoAcotaYDiceSiPegoAlFotograma() {
        val pegado = TiempoCampo.leer("0,005", 0, 3_000_000, fotogramas = true)
        assertEquals(0L, pegado.us)
        assertNotNull(pegado.nota)
        val fuera = TiempoCampo.leer("9", 0, 3_000_000, fotogramas = false)
        assertEquals(3_000_000L, fuera.us)
        assertEquals("Va de 0.000 s a 3.000 s", fuera.nota)
        val bajo = TiempoCampo.leer("0.001", 33_333, 3_000_000, fotogramas = false)
        assertEquals(33_333L, bajo.us)
        // Un minimo mayor que el maximo no da limites al reves
        assertEquals(10_000L, TiempoCampo.leer("0", 10_000, 5_000, fotogramas = false).us)
    }

    @Test fun elTextoParaEditarNoLlevaUnidad() {
        assertEquals("0.005", TiempoCampo.textoEditable(5_000))
        assertEquals("0.033333", TiempoCampo.textoEditable(33_333))
        // En pantalla el fotograma sale redondeado, pero lo que se ofrece para editar es exacto
        assertEquals("≈1.067 s", TiempoPreciso.texto(1_066_666))
        assertEquals("1.066666", TiempoCampo.textoEditable(1_066_666))
        // Lo que se ofrece se lee igual
        for (us in longArrayOf(0, 1_000, 5_000, 33_333, 1_066_666, 2_500_000)) {
            assertEquals(us, TiempoCampo.leer(TiempoCampo.textoEditable(us), 0, 10_000_000, fotogramas = false).us)
        }
    }

    @Test fun laToleranciaDeClavesDependeDelAjuste() {
        assertEquals(Edicion.TOLERANCIA_CLAVE, TiempoCampo.toleranciaClaves(true))
        assertEquals(Edicion.TOLERANCIA_FINA, TiempoCampo.toleranciaClaves(false))
        assertEquals(200L, Edicion.TOLERANCIA_FINA)
        // 0,001 s y 0,005 s son claves distintas sin el ajuste
        assertTrue(5_000L - 1_000L > TiempoCampo.toleranciaClaves(false))
    }

    @Test fun elAjusteAFotogramasEstaEncendidoDeFabrica() {
        assertEquals(true, AjustesEditor.ajustarFotogramas)
    }
}
