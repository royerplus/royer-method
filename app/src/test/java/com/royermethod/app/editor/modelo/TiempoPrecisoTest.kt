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
        assertEquals("0.001500 s", TiempoPreciso.texto(1_500))
        assertEquals("0.000001 s", TiempoPreciso.texto(1))
        assertEquals("0.999999 s", TiempoPreciso.texto(999_999))
        assertEquals("125.000 s", TiempoPreciso.texto(125_000_000))
        // Nunca un tiempo negativo en pantalla
        assertEquals("0.000 s", TiempoPreciso.texto(-3))
    }

    @Test fun losFotogramasSalenConTresDecimalesYSinMentir() {
        // Un fotograma que no cae en un milisegundo: tres decimales y "≈" (no es 1,067 s escrito)
        assertEquals("≈0.033 s", TiempoPreciso.texto(33_333))
        assertEquals("≈1.067 s", TiempoPreciso.texto(1_066_666))
        assertEquals("≈0.067 s", TiempoPreciso.texto(66_666))
        // La resta de dos fotogramas de la linea puede salir un microsegundo por encima
        assertEquals("≈0.067 s", TiempoPreciso.texto(100_000 - 33_333))
        // 1,067 s escrito a mano se ve distinto del fotograma 32
        assertEquals("1.067 s", TiempoPreciso.texto(1_067_000))
        // Los que caen en un milisegundo, como siempre
        assertEquals("0.100 s", TiempoPreciso.texto(100_000))
        for (n in 0L..900L) {
            val t = TiempoPreciso.texto(Linea.tiempoDe(n))
            assertEquals(t, n % 3 != 0L, t.startsWith("≈"))
            assertEquals(t, 3, t.removePrefix("≈").removeSuffix(" s").substringAfter('.').length)
        }
        // Lo exacto, para escribirlo y leerlo igual
        assertEquals("0.033333 s", TiempoPreciso.textoExacto(33_333))
        assertEquals("0.005 s", TiempoPreciso.textoExacto(5_000))
        assertEquals("0.000 s", TiempoPreciso.textoExacto(-3))
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
            assertEquals(us, TiempoPreciso.leer(TiempoPreciso.textoExacto(us)))
        }
        // Lo que se ensena tambien, salvo los fotogramas redondeados
        for (us in listOf(0L, 1L, 999L, 1_000L, 1_500L, 5_000L, 1_000_000L, 12_345_678L)) {
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
