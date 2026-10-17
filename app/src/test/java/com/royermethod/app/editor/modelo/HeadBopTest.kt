package com.royermethod.app.editor.modelo

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.hypot

class HeadBopTest {
    private val identidad = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    private val imagen = Origen("file:///personaje.png", "Cabeza", TipoOrigen.IMAGEN, 1000, 1000)
    private val h = HeadBop(finUs = 8_000_000, fundidoUs = 0)
    private fun matriz(h: HeadBop, t: Long) = h.matrizEn(identidad, t, 1000f, 1000f, 1f, 1f)
    private fun igual(a: FloatArray, b: FloatArray) = assertArrayEquals(a, b, 1e-5f)

    @Test fun sinMovimientoFueraDelIntervaloYConIntensidadCero() {
        val limitado = h.copy(inicioUs = 1_000_000, finUs = 2_000_000)
        assertSame(identidad, matriz(limitado, 999_999))
        assertSame(identidad, matriz(limitado, 2_000_000))
        assertSame(identidad, matriz(h.copy(intensidad = 0f), 300_000))
        assertSame(identidad, matriz(h.copy(activo = false), 300_000))
    }

    @Test fun bucleDeDosGolpesSinAcumularDeriva() {
        igual(matriz(h, 175_000), matriz(h, 1_175_000))
        assertFalse(matriz(h, 175_000).contentEquals(matriz(h, 425_000)))
    }

    @Test fun cambiarTempoYDesfaseControlaElMismoMovimiento() {
        igual(matriz(h, 300_000), matriz(h.copy(bpm = 60f), 600_000))
        igual(matriz(h, 300_000), matriz(h.copy(desfaseUs = 200_000), 100_000))
    }

    @Test fun cabelloSinRetardoHeredaExactamenteLaCabeza() {
        igual(matriz(h, 375_000), matriz(h.copy(rol = RolHeadBop.CABELLO, retardoUs = 0), 375_000))
    }

    @Test fun retardoAñadeMovimientoSecundarioSinDespegarLaRaiz() {
        val pelo = h.copy(rol = RolHeadBop.CABELLO, pivoteX = 0.35f, pivoteY = 0.22f)
        val cabeza = matriz(h, 375_000)
        val cabello = matriz(pelo, 375_000)
        assertFalse(cabeza.contentEquals(cabello))
        // Después de aplicar inversa de cabeza, el punto de la raíz queda fijo
        // por la inversa del giro secundario, aunque el resto del pelo cambie.
        fun inv(m: FloatArray, u: Float, v: Float): Pair<Float, Float> {
            val det = m[0] * m[4] - m[1] * m[3]
            val x = u - m[6]; val y = v - m[7]
            return (x * m[4] - y * m[3]) / det to (m[0] * y - m[1] * x) / det
        }
        val raizCabeza = inv(cabeza, pelo.pivoteX, pelo.pivoteY)
        val raizPelo = inv(cabello, pelo.pivoteX, pelo.pivoteY)
        assertEquals(raizCabeza.first, raizPelo.first, 1e-5f)
        assertEquals(raizCabeza.second, raizPelo.second, 1e-5f)
    }

    @Test fun rolesDeManosSonIndependientesYElCuerpoMasSutil() {
        val cabeza = matriz(h, 250_000)
        val cuerpo = matriz(h.copy(rol = RolHeadBop.CUERPO), 250_000)
        val izquierda = matriz(h.copy(rol = RolHeadBop.MANO_IZQUIERDA), 250_000)
        val derecha = matriz(h.copy(rol = RolHeadBop.MANO_DERECHA), 250_000)
        assertTrue(kotlin.math.abs(cuerpo[1]) < kotlin.math.abs(cabeza[1]))
        assertFalse(izquierda.contentEquals(derecha))
    }

    @Test fun pivoteDeRotacionNoDependeDelCentroDelPNG() {
        val mano = h.copy(rol = RolHeadBop.MANO_IZQUIERDA, pivoteX = 0.12f, pivoteY = 0.81f)
        val m = matriz(mano, 0) // Sin traslación vertical, pero con giro de anticipación.
        val u = m[0] * mano.pivoteX + m[3] * mano.pivoteY + m[6]
        val v = m[1] * mano.pivoteX + m[4] * mano.pivoteY + m[7]
        assertEquals(mano.pivoteX, u, 1e-6f)
        assertEquals(mano.pivoteY, v, 1e-6f)
    }

    @Test fun giroNoDeformaUnaFuenteRectangular() {
        val base = floatArrayOf(0.001f, 0f, 0f, 0f, 0.0005f, 0f, 0f, 0f, 1f)
        val m = h.matrizEn(base, 250_000, 1000f, 2000f, 1000f, 2000f)
        // 100 píxeles del lienzo siguen siendo 100 píxeles de la fuente.
        val horizontal = hypot(m[0] * 100 * 1000, m[1] * 100 * 2000)
        val vertical = hypot(m[3] * 100 * 1000, m[4] * 100 * 2000)
        assertEquals(100f, horizontal, 1e-3f)
        assertEquals(100f, vertical, 1e-3f)
    }

    @Test fun cabelloConOtroEncuadreMantieneLaMismaTransformacionDeCabeza() {
        val baseCabeza = floatArrayOf(0.001f, 0f, 0f, 0f, 0.001f, 0f, 0f, 0f, 1f)
        val basePelo = floatArrayOf(0.002f, 0f, 0f, 0f, 0.002f, 0f, -0.3f, -0.14f, 1f)
        // Flequillo recortado: el cuello anatómico queda bajo el PNG.
        val pelo = h.copy(rol = RolHeadBop.CABELLO, cuelloX = 0.7f, cuelloY = 1.02f, arrastre = 0f)
        val cabezaAnimada = h.matrizEn(baseCabeza, 375_000, 1000f, 1000f, 1000f, 1000f)
        val peloAnimado = pelo.matrizEn(basePelo, 375_000, 1000f, 1000f, 1000f, 1000f)
        fun destino(m: FloatArray, u: Float, v: Float): Pair<Float, Float> {
            val det = m[0] * m[4] - m[1] * m[3]
            val x = u - m[6]; val y = v - m[7]
            return (x * m[4] - y * m[3]) / det to (m[0] * y - m[1] * x) / det
        }
        for ((x, y) in listOf(500f to 580f, 450f to 600f, 600f to 300f)) {
            val a = destino(cabezaAnimada, x / 1000, y / 1000)
            val b = destino(peloAnimado, x / 500 - 0.3f, y / 500 - 0.14f)
            assertEquals(a.first, b.first, 0.001f)
            assertEquals(a.second, b.second, 0.001f)
        }
    }

    @Test fun arrastreSoloCambiaElMovimientoSecundarioDelCabello() {
        val pelo = h.copy(rol = RolHeadBop.CABELLO)
        igual(matriz(h, 375_000), matriz(pelo.copy(arrastre = 0f), 375_000))
        assertFalse(matriz(pelo.copy(arrastre = 0.3f), 375_000).contentEquals(matriz(pelo.copy(arrastre = 1.5f), 375_000)))
    }

    @Test fun recortarConservaMovimientoYEnvolventeOriginales() {
        val suave = h.copy(inicioUs = 200_000, fundidoUs = 500_000)
        val recortado = suave.recortado(400_000, 6_000_000)!!
        for (t in listOf(0L, 150_000L, 800_000L, 3_900_000L))
            igual(matriz(suave, t + 400_000), matriz(recortado, t))
        assertNull(suave.recortado(8_000_000, 9_000_000))
    }

    @Test fun cambiarVelocidadSigueAlContenidoSinCambiarLaFase() {
        val video = Clip(1, origen = imagen.copy(tipo = TipoOrigen.VIDEO, duracionUs = 8_000_000),
            hastaUs = 8_000_000, headBop = h.copy(fundidoUs = 400_000, desfaseUs = 90_000))
        val rapido = video.conVelocidad(2f)
        for (t in listOf(80_000L, 600_000L, 1_500_000L, 3_900_000L))
            igual(matriz(video.headBop!!, t * 2), matriz(rapido.headBop!!, t))
    }

    @Test fun dividirClipConservaContinuidadSinCrearClaves() {
        val clip = Clip(1, origen = imagen, hastaUs = 8_000_000, headBop = h)
        val p = Proyecto(principal = listOf(clip), siguienteId = 2)
        val dividido = Edicion.dividir(p, 1, 2_370_000)!!.first
        igual(matriz(h, 2_470_000), matriz(dividido.principal[1].headBop!!, 100_000))
        assertTrue(dividido.principal.all { it.claves.isEmpty() })
        val recortado = Edicion.recortar(p, 1, 420_000, 7_000_000).principal.single()
        igual(matriz(h, 720_000), matriz(recortado.headBop!!, 300_000))
    }

    @Test fun moverYRecortarElTramoTienenRelojesDistintosYPredecibles() {
        igual(matriz(h, 300_000), matriz(h.movido(1_000_000), 1_300_000))
        igual(matriz(h, 1_300_000), matriz(h.conRango(1_000_000, 7_000_000), 1_300_000))
    }

    @Test fun sincronizarRitmoRespetaIniciosAbsolutosYPivotesDeCadaParte() {
        val cabeza = Clip(1, origen = imagen, hastaUs = 8_000_000,
            headBop = h.copy(inicioUs = 200_000, desfaseUs = 130_000))
        val pelo = Clip(2, origen = imagen, hastaUs = 6_000_000,
            headBop = h.copy(rol = RolHeadBop.CABELLO, inicioUs = 100_000, bpm = 90f,
                retardoUs = 0, pivoteX = 0.2f))
        val p = Proyecto(capas = listOf(Capa(500_000, 0, cabeza), Capa(1_000_000, 1, pelo)))
        val alineado = EdicionHeadBop.sincronizar(p, 1)
        val otro = alineado.capas[1].clip.headBop!!
        assertEquals(0.2f, otro.pivoteX, 0f)
        assertEquals(RolHeadBop.CABELLO, otro.rol)
        igual(matriz(cabeza.headBop!!, 1_500_000), matriz(otro, 1_000_000))
    }

    @Test fun escenaEvaluaElRigEnCadaMuestraDePreviewYExportacion() {
        val clip = Clip(1, origen = imagen, hastaUs = 8_000_000, headBop = h)
        val p = Proyecto(formato = Formato.CUADRADO, fondo = Fondo.NEGRO, principal = listOf(clip))
        val escena = Escena(p, 1000, 1000, Medidor { _, _ -> 1000f to 1000f })
        val paso = escena.cuadroEn(375_000).clips.single()
        assertEquals(8, paso.muestras)
        assertFalse(paso.matrices.first().contentEquals(paso.matrices.last()))
        val sinBlur = Escena(p.copy(salida = p.salida.copy(desenfoqueMovimiento = false)),
            1000, 1000, Medidor { _, _ -> 1000f to 1000f })
        val cuadro = sinBlur.cuadroEn(375_000).clips.single()
        val base = escena.matriz(clip, clip.encuadre, 1000f, 1000f)
        igual(h.matrizEn(base, 375_000, 1000f, 1000f), cuadro.matrices.single())
    }
}
