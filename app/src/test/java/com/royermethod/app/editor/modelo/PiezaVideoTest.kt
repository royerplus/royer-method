package com.royermethod.app.editor.modelo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class PiezaVideoTest {
    private val fuente = Origen("foto", "foto", TipoOrigen.IMAGEN, 600, 800)
    private val superior = PiezaVideo("Superior", RegionUv(abajo = 0.5f))
    private val inferior = PiezaVideo("Inferior", RegionUv(arriba = 0.5f))
    private val medidor = Medidor { _, _ -> 600f to 800f }

    private fun clip(piezas: List<PiezaVideo> = emptyList()) = Clip(1, origen = fuente, hastaUs = 4_000_000, piezas = piezas)
    private fun escena(c: Clip, blur: Boolean = false, fondo: Fondo = Fondo.NEGRO) =
        Escena(Proyecto(principal = listOf(c), fondo = fondo,
            salida = AjustesSalida(desenfoqueMovimiento = blur)), 300, 400, medidor)
    private fun uv(m: FloatArray, x: Float, y: Float) =
        (m[0] * x + m[3] * y + m[6]) to (m[1] * x + m[4] * y + m[7])

    @Test
    fun dosMitadesIdentidadConservanExactamenteLaGeometriaCompleta() {
        val entero = clip().copy(encuadre = Encuadre(escala = 1.3f, x = 0.1f, y = -0.03f,
            rotacion = 17f, espejo = true, recorte = Recorte(0.05f, 0.02f, 0.03f, 0.04f)))
        val matriz = escena(entero).cuadroEn(1_000_000).clips.single().matrices.single()
        val pasos = escena(entero.copy(piezas = listOf(superior, inferior))).cuadroEn(1_000_000).clips
        assertEquals(1, pasos.size)
        for (p in pasos) assertArrayEquals(matriz, p.matrices.single(), 0f)
        assertNull(pasos.single().recorteFuente)
    }

    @Test
    fun cadaMitadTrasladaSuRegionSinVolverALlenarElLienzo() {
        val a = superior.copy(claves = listOf(Clave(0, Encuadre(y = -0.5f), Curva.LINEAL), Clave(1_000_000, Encuadre())))
        val b = inferior.copy(claves = listOf(Clave(0, Encuadre(y = 0.5f), Curva.LINEAL), Clave(1_000_000, Encuadre())))
        val c = clip(listOf(a, b))
        val mitad = escena(c).cuadroEn(500_000).clips
        val ma = mitad[0].matrices.single()
        val mb = mitad[1].matrices.single()
        assertEquals(0.25f, ma[7], 1e-6f)
        assertEquals(-0.25f, mb[7], 1e-6f)
        assertEquals(0.5f, uv(ma, 150f, 50f).first, 1e-6f)
        assertEquals(0.375f, uv(ma, 150f, 50f).second, 1e-6f)
        assertEquals(0.625f, uv(mb, 150f, 350f).second, 1e-6f)
        val quieta = escena(clip()).cuadroEn(1_000_000).clips.single().matrices.single()
        escena(c).cuadroEn(1_000_000).clips.forEach { assertArrayEquals(quieta, it.matrices.single(), 0f) }
    }

    @Test
    fun giroYEscalaUsanElCentroDeLaPiezaEnLaImagenCompleta() {
        val p = superior.copy(encuadre = Encuadre(escala = 2f, rotacion = 90f))
        val m = escena(clip(listOf(p))).cuadroEn(0).clips.single().matrices.single()
        assertEquals(0.5f, uv(m, 150f, 100f).first, 1e-5f)
        assertEquals(0.25f, uv(m, 150f, 100f).second, 1e-5f)
        assertEquals(2f / 3f, uv(m, 150f, 200f).first, 1e-5f)
        assertEquals(0.25f, uv(m, 150f, 200f).second, 1e-5f)
    }

    @Test
    fun elDesenfoqueDeMovimientoMuestreaCadaTrayectoriaIndependiente() {
        val a = superior.copy(claves = listOf(Clave(0, Encuadre(y = -0.5f), Curva.LINEAL), Clave(1_000_000, Encuadre())))
        val b = inferior.copy(claves = listOf(Clave(0, Encuadre(y = 0.5f), Curva.LINEAL), Clave(1_000_000, Encuadre())))
        val pasos = escena(clip(listOf(a, b)), blur = true).cuadroEn(500_000).clips
        assertEquals(8, pasos[0].muestras)
        assertEquals(8, pasos[1].muestras)
        assertTrue(pasos[0].matrices.first()[7] > pasos[0].matrices.last()[7])
        assertTrue(pasos[1].matrices.first()[7] < pasos[1].matrices.last()[7])
    }

    @Test
    fun dividirYRecortarNoCambianUnaTrayectoriaBezier() {
        val p = superior.copy(claves = listOf(
            Clave(0, Encuadre(y = -0.5f, escala = 1.8f), bezier = Bezier(0.15f, -0.2f, 0.7f, 1.1f)),
            Clave(3_000_000, Encuadre(y = 0.1f, rotacion = 37f))))
        val c = clip(listOf(p))
        val proyecto = Proyecto(principal = listOf(c), siguienteId = 2)
        val q = Edicion.dividir(proyecto, 1, 1_000_000)!!.first
        val primera = q.principal[0].piezas.single()
        val segunda = q.principal[1].piezas.single()
        assertEquals(p.encuadreEn(750_000), primera.encuadreEn(750_000))
        assertEquals(p.encuadreEn(1_250_000), segunda.encuadreEn(250_000))
        assertEquals(p.encuadreEn(2_500_000), segunda.encuadreEn(1_500_000))
        val recortado = Edicion.recortar(proyecto, 1, 750_000, 2_750_000).principal.single().piezas.single()
        assertEquals(p.encuadreEn(1_250_000), recortado.encuadreEn(500_000))
        assertEquals(p.encuadreEn(2_500_000), recortado.encuadreEn(1_750_000))
    }

    @Test
    fun velocidadEscalaLosTiemposDeLasPiezasYConservaSuRecorrido() {
        val p = superior.copy(claves = listOf(Clave(0, Encuadre(y = -0.5f)), Clave(3_000_000, Encuadre())))
        val c = clip(listOf(p)).copy(origen = fuente.copy(tipo = TipoOrigen.VIDEO, duracionUs = 4_000_000))
        val rapida = c.conVelocidad(2f).piezas.single()
        assertEquals(listOf(0L, 1_500_000L), rapida.claves.map { it.tiempoUs })
        assertEquals(p.encuadreEn(1_000_000), rapida.encuadreEn(500_000))
    }

    @Test
    fun elFondoSeDibujaUnaSolaVezParaElConjunto() {
        val piezas = listOf(superior.copy(encuadre = Encuadre(y = -0.1f)), inferior.copy(encuadre = Encuadre(y = 0.1f)))
        val pasos = escena(clip(piezas), fondo = Fondo.DESENFOCADO).cuadroEn(1_000_000).clips
        assertEquals(1, pasos.count { it.esFondo })
        assertEquals(2, pasos.count { !it.esFondo })
        assertNull(pasos.single { it.esFondo }.recorteFuente)
    }

    @Test
    fun regionesYClavesEditadasQuedanAcotadas() {
        val r = RegionUv(-1f, Float.NaN, 2f, -1f).acotada()
        assertEquals(0f, r.izquierda, 0f)
        assertEquals(0f, r.arriba, 0f)
        assertEquals(1f, r.derecha, 0f)
        assertTrue(r.alto > 0f)
        assertNull(superior.region.interseccion(RegionUv(arriba = 0.6f)))
        val p = superior.conClave(-10, 1_000_000, Encuadre(y = 1f)).conClave(2_000_000, 1_000_000, Encuadre())
        assertEquals(listOf(0L, 1_000_000L), p.claves.map { it.tiempoUs })
    }

    @Test
    fun traslacionComunConMotionBlurSeFusionaSinPromediarLasMitadesPorSeparado() {
        val claves = listOf(Clave(0, Encuadre(), Curva.LINEAL), Clave(1_000_000, Encuadre(y = 0.2f)))
        val c = clip(listOf(superior.copy(claves = claves), inferior.copy(claves = claves)))
        val paso = escena(c, blur = true).cuadroEn(500_000).clips.single()
        assertNull(paso.recorteFuente)
        assertEquals(8, paso.muestras)
    }

    @Test
    fun noFusionaRegionesConHuecosOSolapeNiGirosSobrePivotesDistintos() {
        for (piezas in listOf(
            listOf(superior, inferior.copy(region = RegionUv(arriba = 0.6f))),
            listOf(superior, inferior.copy(region = RegionUv(arriba = 0.4f))),
            listOf(superior.copy(encuadre = Encuadre(rotacion = 10f)), inferior.copy(encuadre = Encuadre(rotacion = 10f)))
        )) assertEquals(2, escena(clip(piezas)).cuadroEn(500_000).clips.size)
    }
}
