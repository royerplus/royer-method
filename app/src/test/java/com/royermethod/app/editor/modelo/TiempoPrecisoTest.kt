package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TiempoPrecisoTest {

    @Test fun escribeMilisegundosYMicrosegundosSueltos() {
        assertEquals("0.005 s", TiempoPreciso.texto(5_000))
        assertEquals("0.001 s", TiempoPreciso.texto(1_000))
        assertEquals("0.000 s", TiempoPreciso.texto(0))
        assertEquals("1.250 s", TiempoPreciso.texto(1_250_000))
        assertEquals("0.033333 s", TiempoPreciso.texto(33_333))
        assertEquals("0.001500 s", TiempoPreciso.texto(1_500))
        assertEquals("125.000 s", TiempoPreciso.texto(125_000_000))
        // Nunca un tiempo negativo en pantalla
        assertEquals("0.000 s", TiempoPreciso.texto(-3))
    }

    @Test fun escribeConPuntoSeaCualSeaElIdioma() {
        val antes = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("es", "ES"))
            assertEquals("0.005 s", TiempoPreciso.texto(5_000))
        } finally {
            java.util.Locale.setDefault(antes)
        }
    }

    @Test fun leeComaPuntoYUnidades() {
        assertEquals(5_000L, TiempoPreciso.leer("0,005"))
        assertEquals(5_000L, TiempoPreciso.leer("0.005"))
        assertEquals(1_000L, TiempoPreciso.leer("0.001"))
        assertEquals(1_000L, TiempoPreciso.leer("0,001"))
        assertEquals(5_000L, TiempoPreciso.leer("0.005 s"))
        assertEquals(5_000L, TiempoPreciso.leer("5 ms"))
        assertEquals(5_000L, TiempoPreciso.leer("5ms"))
        assertEquals(1_200_000L, TiempoPreciso.leer("1,2s"))
        assertEquals(250L, TiempoPreciso.leer("250 us"))
        assertEquals(5_000L, TiempoPreciso.leer("  0,005  "))
        assertEquals(2_000_000L, TiempoPreciso.leer("2"))
        assertEquals(0L, TiempoPreciso.leer("0"))
    }

    @Test fun noLeeNegativosNiTextoSinNumero() {
        assertNull(TiempoPreciso.leer("-1"))
        assertNull(TiempoPreciso.leer("-0,005"))
        assertNull(TiempoPreciso.leer("abc"))
        assertNull(TiempoPreciso.leer(""))
        assertNull(TiempoPreciso.leer("   "))
        assertNull(TiempoPreciso.leer("1.2.3"))
        assertNull(TiempoPreciso.leer("ms"))
        assertNull(TiempoPreciso.leer("nan"))
        assertNull(TiempoPreciso.leer("infinity"))
    }

    @Test fun redondeaAlMicrosegundoSinTruncar() {
        // Cada milisegundo escrito con tres decimales vuelve exacto (0.007 * 1e6 = 7000.000000000001)
        for (ms in 0..3_000) {
            val texto = "%d.%03d".format(ms / 1000, ms % 1000)
            assertEquals(texto, ms * 1_000L, TiempoPreciso.leer(texto))
            assertEquals(texto, ms * 1_000L, TiempoPreciso.leer(texto.replace('.', ',')))
        }
        assertEquals(1L, TiempoPreciso.leer("0.0000006"))
        assertEquals(0L, TiempoPreciso.leer("0.0000004"))
        assertEquals(4_999L, TiempoPreciso.leer("0.004999"))
    }

    @Test fun loEscritoSeLeeIgual() {
        for (us in listOf(0L, 1L, 999L, 1_000L, 1_500L, 5_000L, 33_333L, 66_666L, 1_000_000L, 12_345_678L)) {
            assertEquals(us, TiempoPreciso.leer(TiempoPreciso.texto(us)))
        }
    }

    @Test fun ajustaAlFotogramaMasCercano() {
        assertEquals(0L, TiempoPreciso.aFotograma(5_000))
        assertEquals(0L, TiempoPreciso.aFotograma(16_666))
        assertEquals(33_333L, TiempoPreciso.aFotograma(16_667))
        assertEquals(33_333L, TiempoPreciso.aFotograma(20_000))
        assertEquals(66_666L, TiempoPreciso.aFotograma(66_666))
        assertEquals(1_000_000L, TiempoPreciso.aFotograma(1_000_000))
        assertEquals(0L, TiempoPreciso.aFotograma(-5))
        // Los instantes que salen exportados no se mueven
        for (n in 0L..300L) assertEquals(Linea.tiempoDe(n), TiempoPreciso.aFotograma(Linea.tiempoDe(n)))
    }

    @Test fun ajustarSoloSiSePide() {
        assertEquals(5_000L, TiempoPreciso.ajustar(5_000, aFotogramas = false))
        assertEquals(0L, TiempoPreciso.ajustar(5_000, aFotogramas = true))
        assertEquals(1_000L, TiempoPreciso.ajustar(1_000, aFotogramas = false))
        assertEquals(0L, TiempoPreciso.ajustar(-1, aFotogramas = false))
        assertEquals(0L, TiempoPreciso.ajustar(-1, aFotogramas = true))
    }
}
