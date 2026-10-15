package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineaTest {

    private fun video(id: Long, segundos: Double, fps: Float = 30f) = Clip(
        id = id,
        origen = Origen(
            uri = "content://v$id", nombre = "v$id", tipo = TipoOrigen.VIDEO,
            ancho = 1920, alto = 1080, duracionUs = (segundos * 1_000_000).toLong(), fps = fps,
            bytes = 10_000_000
        )
    )

    @Test
    fun treintaFpsExactosSinDeriva() {
        // 30 fps no cabe en microsegundos enteros: el fotograma 3 tiene que
        // caer en 100000 exacto, no en 3 * 33333 = 99999.
        assertEquals(0L, Linea.tiempoDe(0))
        assertEquals(100_000L, Linea.tiempoDe(3))
        assertEquals(1_000_000L, Linea.tiempoDe(30))
        assertEquals(60_000_000L, Linea.tiempoDe(1800))
        for (n in 0L..10_000L) {
            assertEquals(n, Linea.fotogramaDe(Linea.tiempoDe(n)))
            assertEquals(n, Linea.fotogramaEn(Linea.tiempoDe(n)))
        }
    }

    @Test
    fun diezSegundosSonTrescientosFotogramas() {
        val p = Proyecto(principal = listOf(video(1, 4.0), video(2, 6.0)))
        assertEquals(10_000_000L, Linea.duracion(p))
        assertEquals(300L, Linea.fotogramas(p))
    }

    @Test
    fun laTransicionSolapaYAcortaElTotal() {
        val a = video(1, 4.0).copy(transicion = Transicion.FUNDIDO, duracionTransicionUs = 1_000_000)
        val b = video(2, 4.0)
        val p = Proyecto(principal = listOf(a, b))
        val ini = Linea.inicios(p.principal)
        assertEquals(0L, ini[0])
        assertEquals(3_000_000L, ini[1])
        assertEquals(7_000_000L, Linea.duracion(p))

        val antes = Linea.principalEn(p.principal, ini, 2_999_999)!!
        assertNull(antes.segundo)
        val enMedio = Linea.principalEn(p.principal, ini, 3_500_000)!!
        assertNotNull(enMedio.segundo)
        assertEquals(0.5f, enMedio.progreso, 1e-4f)
        assertEquals(3_500_000L, enMedio.localPrimero)
        assertEquals(500_000L, enMedio.localSegundo)
        val despues = Linea.principalEn(p.principal, ini, 4_000_000)!!
        assertNull(despues.segundo)
        assertEquals(b.id, despues.primero.id)
    }

    @Test
    fun laTransicionNoPasaDeLaMitadDelBloqueMasCorto() {
        val a = video(1, 1.0).copy(transicion = Transicion.LINEA, duracionTransicionUs = 3_000_000)
        val b = video(2, 5.0)
        assertEquals(500_000L, Linea.solape(a, b))
    }

    @Test
    fun laTransicionEmpiezaEnFotogramaEntero() {
        val a = video(1, 4.0).copy(transicion = Transicion.FUNDIDO, duracionTransicionUs = 510_000)
        val s = Linea.solape(a, video(2, 4.0))
        assertEquals(Linea.tiempoDe(Linea.fotogramaEn(s)), s)
    }

    @Test
    fun loCubreTodoSinHuecosNiDobles() {
        // Cada fotograma tiene exactamente un bloque delante (o dos si es
        // transicion), y nunca ninguno.
        val clips = listOf(
            video(1, 2.0).copy(transicion = Transicion.LINEA, duracionTransicionUs = 400_000),
            video(2, 1.5),
            video(3, 3.0).copy(velocidad = 2f, transicion = Transicion.ZOOM),
            video(4, 2.0)
        )
        val p = Proyecto(principal = clips)
        val ini = Linea.inicios(clips)
        for (n in 0 until Linea.fotogramas(p)) {
            val en = Linea.principalEn(clips, ini, Linea.tiempoDe(n))
            assertNotNull("fotograma $n sin bloque", en)
        }
        assertNull(Linea.principalEn(clips, ini, Linea.duracion(p)))
    }

    @Test
    fun laVelocidadCambiaLaDuracionYElTiempoDelArchivo() {
        val c = video(1, 4.0).copy(velocidad = 2f)
        assertEquals(2_000_000L, c.duracionUs)
        assertEquals(2_000_000L, c.tiempoArchivo(1_000_000))
        // Nunca pide el final exacto, que no tiene fotograma
        assertEquals(3_999_999L, c.tiempoArchivo(2_000_000))
    }

    @Test
    fun cortesSoloDondeNoHayTransicion() {
        val clips = listOf(
            video(1, 2.0),
            video(2, 2.0).copy(transicion = Transicion.FUNDIDO),
            video(3, 2.0)
        )
        val cortes = Linea.cortes(Proyecto(principal = clips))
        assertEquals(setOf(60L), cortes)
    }

    @Test
    fun losTramosNoCambianElPesoTotal() {
        val p = Proyecto(
            principal = listOf(
                video(1, 3.0).copy(transicion = Transicion.FUNDIDO),
                Clip(id = 2, origen = Origen("content://i", "i", TipoOrigen.IMAGEN, 1000, 1000)),
                video(3, 2.0)
            )
        )
        val tramos = Linea.tramos(p)
        val total = Linea.duracion(p)
        val media = tramos.sumOf { (it.hastaUs - it.desdeUs) * it.factor.toDouble() } / total
        assertEquals(1.0, media, 1e-3)
        // La imagen quieta recibe menos que la transicion
        val imagen = tramos.first { it.desdeUs >= 3_000_000 && it.hastaUs <= 5_500_000 && it.desdeUs > 2_600_000 }
        val transicion = tramos.first { it.desdeUs < 3_000_000 && it.hastaUs > 2_600_000 }
        assertTrue(imagen.factor < transicion.factor)
    }

    @Test
    fun filaLibreEvitaPisar() {
        val img = Origen("content://i", "i", TipoOrigen.IMAGEN, 100, 100)
        val capas = listOf(
            Capa(0, 0, Clip(id = 1, origen = img)),
            Capa(1_000_000, 1, Clip(id = 2, origen = img))
        )
        assertEquals(2, Linea.filaLibre(capas, 1_500_000, 2_000_000))
        assertEquals(0, Linea.filaLibre(capas, 3_000_000, 4_000_000))
    }
}
