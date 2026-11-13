package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Proyecto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Zoom de la linea de tiempo: pulsacion, limites, regla y paso de pixeles a tiempo. */
class ZoomLineaTest {

    private val densidad = 2.75f

    @Test fun elProgresoDeLaPulsacionVaDe0A1EnDosSegundos() {
        assertEquals(2_000, ZoomLinea.PULSACION_FINO_MS)
        assertEquals(0f, ZoomLinea.progreso(0), 0f)
        assertEquals(0.25f, ZoomLinea.progreso(500), 1e-6f)
        assertEquals(0.5f, ZoomLinea.progreso(1_000), 1e-6f)
        assertEquals(1f, ZoomLinea.progreso(2_000), 0f)
        // Ni mas de 1 ni negativo
        assertEquals(1f, ZoomLinea.progreso(9_000), 0f)
        assertEquals(0f, ZoomLinea.progreso(-30), 0f)
    }

    @Test fun ampliarSeParaEnElLimiteNormalYSoloElFinoSigue() {
        val max = EstadoEditor.ZOOM_MAX
        val fino = EstadoEditor.ZOOM_FINO_MAX
        assertEquals(96f, ZoomLinea.ampliado(64f, max), 0f)
        // Justo en el limite normal, no se lo salta
        assertEquals(max, ZoomLinea.ampliado(800f, max), 0f)
        assertEquals(max, ZoomLinea.ampliado(max, max), 0f)
        // Con el fino, del limite normal en adelante x2 hasta 1 ms = 60 dp
        assertEquals(1_800f, ZoomLinea.ampliado(max, fino), 0f)
        assertEquals(fino, ZoomLinea.ampliado(40_000f, fino), 0f)
        assertEquals(fino, ZoomLinea.ampliado(fino, fino), 0f)
        // Reducir es el camino de vuelta y tambien se para en el limite normal
        assertEquals(30_000f, ZoomLinea.reducido(fino), 0f)
        assertEquals(max, ZoomLinea.reducido(1_200f), 0f)
        assertEquals(600f, ZoomLinea.reducido(max), 0f)
        assertEquals(EstadoEditor.ZOOM_MIN, ZoomLinea.reducido(EstadoEditor.ZOOM_MIN), 0f)
        assertEquals(EstadoEditor.ZOOM_MIN, ZoomLinea.reducido(10f), 0f)
    }

    @Test fun losBotonesYElPellizcoRespetanElLimiteVigente() {
        val e = EstadoEditor(Proyecto())
        // Pulsando "+" sin el fino: nunca pasa del limite normal
        repeat(30) { e.ponerZoom(ZoomLinea.ampliado(e.dpPorSegundo, e.zoomMaximo)) }
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
        e.ponerZoom(1e7f)
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
        // Desbloqueado (lo que hace mantener "+" dos segundos): llega a 60 000 en pocos toques
        e.zoomFino = true
        var toques = 0
        while (e.dpPorSegundo < EstadoEditor.ZOOM_FINO_MAX) {
            e.ponerZoom(ZoomLinea.ampliado(e.dpPorSegundo, e.zoomMaximo))
            toques++
            assertTrue(toques < 10)
        }
        assertEquals(EstadoEditor.ZOOM_FINO_MAX, e.dpPorSegundo, 0f)
        e.ponerZoom(1e9f)
        assertEquals(EstadoEditor.ZOOM_FINO_MAX, e.dpPorSegundo, 0f)
        // "Zoom normal" vuelve al limite y lo vuelve a cerrar
        e.zoomNormal()
        assertFalse(e.zoomFino)
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
        repeat(60) { e.ponerZoom(ZoomLinea.reducido(e.dpPorSegundo)) }
        assertEquals(EstadoEditor.ZOOM_MIN, e.dpPorSegundo, 0f)
    }

    /** La regla de antes (sin el paso de fotograma, al que no se llegaba: pedia 2 100 dp/s). */
    private fun pasoDeAntes(dp: Float): Long {
        val pasos = longArrayOf(100_000, 200_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 15_000_000, 30_000_000, 60_000_000, 120_000_000, 300_000_000)
        return pasos.firstOrNull { it / 1_000_000f * dp * densidad >= 70f * densidad } ?: pasos.last()
    }

    @Test fun enElZoomNormalLaReglaEsLaDeSiempreYEnElFinoLlegaAlMilisegundo() {
        var dp = EstadoEditor.ZOOM_MIN
        while (dp <= EstadoEditor.ZOOM_MAX) {
            assertEquals("$dp dp/s", pasoDeAntes(dp), ZoomLinea.pasoRegla(dp))
            dp += 0.5f
        }
        assertEquals(2_000_000L, ZoomLinea.pasoRegla(64f))
        assertEquals(100_000L, ZoomLinea.pasoRegla(EstadoEditor.ZOOM_MAX))
        // Zoom fino: 1 ms = 60 dp en el maximo
        assertEquals(1_000L, ZoomLinea.pasoRegla(EstadoEditor.ZOOM_FINO_MAX))
        assertEquals(2_000L, ZoomLinea.pasoRegla(30_000f))
        assertEquals(10_000L, ZoomLinea.pasoRegla(6_000f))
        // Cada paso deja sitio a su etiqueta
        for (z in listOf(1_000f, 3_000f, 12_000f, 45_000f, EstadoEditor.ZOOM_FINO_MAX)) {
            val p = ZoomLinea.pasoRegla(z)
            assertTrue(p / 1_000_000.0 * z >= ZoomLinea.separacionDp(p))
        }
        // Puntos entre marcas
        assertEquals(200L, ZoomLinea.menorRegla(1_000))
        assertEquals(500L, ZoomLinea.menorRegla(2_000))
        assertEquals(1_000L, ZoomLinea.menorRegla(5_000))
        assertEquals(200_000L, ZoomLinea.menorRegla(1_000_000))
        assertEquals(3_000_000L, ZoomLinea.menorRegla(15_000_000))
    }

    @Test fun lasEtiquetasLlevanMilesimasPorDebajoDeUnaDecima() {
        assertEquals("0.005s", ZoomLinea.etiquetaRegla(5_000, 5_000))
        assertEquals("1.001s", ZoomLinea.etiquetaRegla(1_001_000, 1_000))
        assertEquals("12.340s", ZoomLinea.etiquetaRegla(12_340_000, 10_000))
        assertEquals("1.5s", ZoomLinea.etiquetaRegla(1_500_000, 500_000))
        assertEquals("0.1s", ZoomLinea.etiquetaRegla(100_000, 100_000))
        assertEquals("1:05", ZoomLinea.etiquetaRegla(65_000_000, 5_000_000))
    }

    @Test fun lasFronterasDeFotogramaSonLasDeLaLineaYSoloConSitio() {
        // A 64 dp/s un fotograma son 2 dp: no se dibujan
        assertEquals(0, ZoomLinea.fronteras(0, 10_000_000, ZoomLinea.pxPorUs(64f, densidad), 6 * densidad).size)
        // A 900 dp/s, 30 dp: si
        val pxUs = ZoomLinea.pxPorUs(EstadoEditor.ZOOM_MAX, densidad)
        assertArrayEquals(longArrayOf(0, 33_333, 66_666, 100_000), ZoomLinea.fronteras(0, 100_000, pxUs, 6 * densidad))
        // Empiezan en la primera dentro de lo visible y nunca antes de 0
        assertArrayEquals(longArrayOf(33_333, 66_666), ZoomLinea.fronteras(1, 99_999, pxUs, 6 * densidad))
        assertArrayEquals(longArrayOf(0, 33_333), ZoomLinea.fronteras(-50_000, 40_000, pxUs, 6 * densidad))
        // En un bloque que empieza a mitad de fotograma, las de la linea (no las del bloque)
        val f = ZoomLinea.fronteras(1_028_000, 1_040_000, ZoomLinea.pxPorUs(EstadoEditor.ZOOM_FINO_MAX, densidad), 40 * densidad)
        assertArrayEquals(longArrayOf(Linea.tiempoDe(31)), f)
        assertEquals(1_033_333L, f[0])
        assertEquals(0, ZoomLinea.fronteras(5, 2, pxUs, 1f).size)
    }

    @Test fun aZoomFinoPixelesYTiemposVanYVuelvenSinPerderUnMicrosegundo() {
        val pxUs = ZoomLinea.pxPorUs(EstadoEditor.ZOOM_FINO_MAX, densidad)
        // 1 ms = 60 dp
        assertEquals(60.0 * densidad, 1_000 * pxUs, 1e-9)
        // Con el cabezal a los 10 minutos, cada microsegundo de los 3 ms de alrededor
        val t0 = 600_000_000L
        val centro = 540f
        for (t in (t0 - 3_000)..(t0 + 3_000)) {
            val x = ZoomLinea.xDe(t, t0, centro, pxUs)
            assertEquals(t, ZoomLinea.tiempoEnX(x, t0, centro, pxUs))
        }
        // 0,001 s y 0,005 s quedan a 660 px: se tocan por separado
        val x1 = ZoomLinea.xDe(t0 + 1_000, t0, centro, pxUs)
        val x5 = ZoomLinea.xDe(t0 + 5_000, t0, centro, pxUs)
        assertEquals(660f, x5 - x1, 1e-3f)
        // Redondea al microsegundo mas cercano, no trunca
        assertEquals(t0 + 1, ZoomLinea.tiempoEnX(centro + (0.9 * pxUs).toFloat(), t0, centro, pxUs))
        assertEquals(t0 - 1, ZoomLinea.tiempoEnX(centro - (0.9 * pxUs).toFloat(), t0, centro, pxUs))
    }
}
