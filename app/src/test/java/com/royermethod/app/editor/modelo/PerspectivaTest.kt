package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Inclinar un bloque en 3D: la matriz del sombreador y el dibujo del panel. */
class PerspectivaTest {

    private val origen = Origen("content://v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 4_000_000, fps = 30f)
    private val medidor = Medidor { c, _ -> (c.origen!!.anchoVisible.toFloat()) to c.origen.altoVisible.toFloat() }

    private fun escena(clip: Clip) = Escena(Proyecto(principal = listOf(clip), fondo = Fondo.NEGRO), 576, 1024, medidor)

    /** Igual que el sombreador: por columnas y dividiendo por z. */
    private fun uv(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val z = m[2] * x + m[5] * y + m[8]
        return (m[0] * x + m[3] * y + m[6]) / z to (m[1] * x + m[4] * y + m[7]) / z
    }

    @Test
    fun casiSinInclinarEsLaMatrizDeSiempre() {
        val e = Encuadre(ajuste = Ajuste.ENCAJAR, escala = 0.7f, x = 0.1f, y = -0.05f, rotacion = 25f, espejo = true,
            recorte = Recorte(0.1f, 0.05f, 0f, 0.2f))
        val c = Clip(id = 1, origen = origen, encuadre = e)
        val plana = escena(c).matriz(c, e, 1920f, 1080f)
        val casi = escena(c).matriz(c, e.copy(inclinacionX = 1e-4f), 1920f, 1080f)
        for ((x, y) in listOf(0f to 0f, 300f to 200f, 576f to 1024f, 100f to 900f)) {
            val a = uv(plana, x, y)
            val b = uv(casi, x, y)
            assertEquals(a.first, b.first, 1e-4f)
            assertEquals(a.second, b.second, 1e-4f)
        }
        // Y sin inclinar no cambia nada: tercera fila 0 0 1
        assertEquals(0f, plana[2], 0f)
        assertEquals(0f, plana[5], 0f)
    }

    @Test
    fun lasEsquinasCaenDondeDiceElDibujo() {
        val e = Encuadre(ajuste = Ajuste.ENCAJAR, escala = 0.6f, inclinacionX = 35f, inclinacionY = -20f)
        val c = Clip(id = 1, origen = origen, encuadre = e)
        val es = escena(c)
        val m = es.matriz(c, e, 1920f, 1080f)
        val marco = es.marco(c, e)
        val esq = Perspectiva.esquinas(e.inclinacionX, e.inclinacionY, marco.ancho, marco.alto)
        val esperadas = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
        esq.forEachIndexed { i, (dx, dy) ->
            val (u, v) = uv(m, marco.cx + dx, marco.cy + dy)
            assertEquals("esquina $i u", esperadas[i].first, u, 2e-3f)
            assertEquals("esquina $i v", esperadas[i].second, v, 2e-3f)
        }
        // El centro sigue en el centro
        val (u, v) = uv(m, marco.cx, marco.cy)
        assertEquals(0.5f, u, 1e-4f)
        assertEquals(0.5f, v, 1e-4f)
    }

    @Test
    fun inclinarHaceUnLadoMasChicoQueElOtro() {
        val (a, b, cc, d) = Perspectiva.esquinas(40f, 0f, 400f, 200f)
        val arriba = abs(b.first - a.first)
        val abajo = abs(cc.first - d.first)
        assertTrue("arriba $arriba abajo $abajo", abs(arriba - abajo) > 20f)
        // Sin inclinar, el rectangulo tal cual
        val plano = Perspectiva.esquinas(0f, 0f, 400f, 200f)
        assertEquals(listOf(-200f to -100f, 200f to -100f, 200f to 100f, -200f to 100f), plano)
    }

    @Test
    fun laInclinacionSeAnimaConLasClaves() {
        val a = Encuadre(inclinacionX = 0f, inclinacionY = 40f)
        val b = Encuadre(inclinacionX = 30f, inclinacionY = 0f)
        val m = a.hacia(b, 0.5f)
        assertEquals(15f, m.inclinacionX, 1e-5f)
        assertEquals(20f, m.inclinacionY, 1e-5f)
    }

    @Test
    fun laInversaDeshaceLaProyeccion() {
        val p = Perspectiva.proyeccion(25f, -50f, 300f, 300f)
        val i = Perspectiva.inversa(p)!!
        val id = Perspectiva.por(p, i)
        for (k in 0 until 9) assertEquals(if (k % 4 == 0) 1f else 0f, id[k], 1e-4f)
    }
}
