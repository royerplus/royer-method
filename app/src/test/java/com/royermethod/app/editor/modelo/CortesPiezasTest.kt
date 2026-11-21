package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CortesPiezasTest {
    private val clip = Clip(1, origen = Origen("foto", "foto", TipoOrigen.IMAGEN, 600, 800))
    private val identidad = floatArrayOf(1f / 300, 0f, 0f, 0f, 1f / 400, 0f, 0f, 0f, 1f)
    private val superior = RegionUv(abajo = 0.5f)
    private val inferior = RegionUv(arriba = 0.5f)

    private fun paso(r: RegionUv, matrices: List<FloatArray> = listOf(identidad),
                     opacidad: Float = 1f, fuente: Clip = clip) = PasoClip(
        clip = fuente, tiempoArchivoUs = 0, exposicionUs = 0, matrices = matrices,
        opacidades = FloatArray(matrices.size) { opacidad }, mascara = null,
        bordes = FloatArray(matrices.size), color = AjustesColor(), mezcla = Mezcla.NORMAL,
        desenfoque = 0f, esFondo = false, anchoEnLienzo = 300f, altoEnLienzo = 400f,
        escalaTexto = 1f, recorteFuente = r)

    @Test
    fun bordeExpuestoConservaSuRegionYNoSeMarcaCompartido() {
        val giro = floatArrayOf(.003f, .001f, 0f, -.001f, .003f, 0f, .1f, -.1f, 1f)
        val r = RegionUv(.2f, .2f, .8f, .8f)
        val corte = PasoPartes(listOf(paso(r, listOf(giro)))).cortesEn(0).single()
        assertEquals(r, corte.region)
        assertEquals(BordesPieza.NINGUNO, corte.compartidos)
    }

    @Test
    fun juntaSeDecideEnCadaMuestraSinCambiarLaTrayectoria() {
        val desplazada = identidad.copyOf().also { it[7] = .1f }
        val a = paso(superior, listOf(identidad, identidad, identidad))
        val b = paso(inferior, listOf(identidad.copyOf(), desplazada, identidad.copyOf()))
        val conjunto = PasoPartes(listOf(a, b))
        for (i in 0..2) {
            val cortes = conjunto.cortesEn(i)
            assertEquals(2, cortes.size)
            assertEquals(i != 1, cortes[0].compartidos.abajo)
            assertEquals(i != 1, cortes[1].compartidos.arriba)
        }
        assertEquals(.1f, b.matrices[1][7], 0f)
    }

    @Test
    fun vecinoParcialSoloEndureceElTramoCompartidoYNoDuplicaArea() {
        val a = RegionUv(.1f, .1f, .5f, .9f)
        val b = RegionUv(.5f, .4f, .9f, .7f)
        val cortes = PasoPartes(listOf(paso(a), paso(b))).cortesEn(0)
        val izquierda = cortes.filter { it.parte == 0 }
        assertEquals(3, izquierda.size)
        assertEquals(listOf(false, true, false), izquierda.map { it.compartidos.derecha })
        assertEquals(listOf(false, true, true), izquierda.map { it.compartidos.arriba })
        assertEquals(listOf(true, true, false), izquierda.map { it.compartidos.abajo })
        assertEquals(a.ancho * a.alto, izquierda.sumOf {
            val r = requireNotNull(it.region)
            (r.ancho * r.alto).toDouble()
        }.toFloat(), 1e-6f)
        for (x in izquierda.indices) for (y in x + 1 until izquierda.size) {
            assertEquals(null, izquierda[x].region!!.interseccion(izquierda[y].region!!))
        }
        assertTrue(cortes.single { it.parte == 1 }.compartidos.izquierda)
    }

    @Test
    fun variosVecinosCubrenLaJuntaSinSuavizarNingunTramoInterior() {
        val partes = listOf(paso(RegionUv(derecha = .5f)),
            paso(RegionUv(izquierda = .5f, abajo = .4f)),
            paso(RegionUv(izquierda = .5f, arriba = .4f)))
        val cortes = PasoPartes(partes).cortesEn(0)
        assertTrue(cortes.filter { it.parte == 0 }.all { it.compartidos.derecha })
        assertTrue(cortes.filter { it.parte != 0 }.all { it.compartidos.izquierda })
    }

    @Test
    fun huecosSolapesYVecinosInvisiblesNoSonJuntas() {
        for (b in listOf(paso(RegionUv(arriba = .6f)), paso(RegionUv(arriba = .4f)),
            paso(inferior, opacidad = 0f), paso(inferior, fuente = clip.copy(id = 2)))) {
            assertFalse(PasoPartes(listOf(paso(superior), b)).cortesEn(0).first().compartidos.abajo)
        }
    }

    @Test
    fun mismoGiroDePiezaNoImplicaLaMismaMatrizPorqueElPivoteCambia() {
        val piezas = listOf(PiezaVideo("a", superior, Encuadre(rotacion = 17f)),
            PiezaVideo("b", inferior, Encuadre(rotacion = 17f)))
        val p = Proyecto(principal = listOf(clip.copy(piezas = piezas)), fondo = Fondo.NEGRO,
            salida = AjustesSalida(desenfoqueMovimiento = false))
        val cuadro = Escena(p, 300, 400, Medidor { _, _ -> 600f to 800f }).cuadroEn(0)
        val partes = cuadro.pasos.filterIsInstance<PasoPartes>().single()
        assertTrue(partes.cortesEn(0).all { it.compartidos == BordesPieza.NINGUNO })
    }
}
