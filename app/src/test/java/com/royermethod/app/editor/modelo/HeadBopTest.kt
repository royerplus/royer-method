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
    private fun destino(m: FloatArray, u: Float, v: Float): Pair<Float, Float> {
        val det = m[0] * m[4] - m[1] * m[3]
        val x = u - m[6]; val y = v - m[7]
        return (x * m[4] - y * m[3]) / det to (m[0] * y - m[1] * x) / det
    }

    @Test fun naturalidadCeroConservaLasMatricesDelBorradorV4() {
        // Muestras guardadas del perfil V4: incluye el arrastre y ambos brazos.
        val esperadas = listOf(
            floatArrayOf(0.9962708f, -0.08628198f, 0f, 0.08628198f, 0.9962708f, 0f, -0.051327273f, 0.05812335f, 1f),
            floatArrayOf(0.9931257f, -0.117053054f, 0f, 0.117053054f, 0.9931257f, 0f, -0.05854421f, 0.07529034f, 1f),
            floatArrayOf(0.99992687f, 0.01209422f, 0f, -0.01209422f, 0.99992687f, 0f, 0.003628551f, -0.003025383f, 1f),
            floatArrayOf(0.99503434f, -0.09953203f, 0f, 0.09953203f, 0.99503434f, 0f, -0.026879119f, 0.056230873f, 1f),
            floatArrayOf(0.9989248f, 0.04635957f, 0f, -0.04635957f, 0.9989248f, 0f, 0.0142136775f, -0.017862588f, 1f))
        RolHeadBop.values().forEachIndexed { i, rol ->
            assertArrayEquals(esperadas[i], matriz(h.copy(rol = rol), 375_000), 1e-7f)
            assertArrayEquals(matriz(h.copy(rol = rol), 375_000),
                matriz(h.copy(rol = rol, naturalidad = Float.NaN), 375_000), 0f)
        }
    }

    @Test fun perfilNaturalEsContinuoYRepiteElCompasSinDeriva() {
        for (rol in RolHeadBop.values()) {
            val natural = h.copy(rol = rol, naturalidad = 0.9f, desfaseUs = -1_000_000)
            igual(matriz(natural, 175_000), matriz(natural, 2_175_000))
            for (t in listOf(250_000L, 500_000L, 750_000L, 1_000_000L, 1_500_000L, 2_000_000L)) {
                val antes = matriz(natural, t - 100)
                val despues = matriz(natural, t + 100)
                assertTrue(despues.all { it.isFinite() })
                for (i in antes.indices) assertEquals(antes[i], despues[i], 0.001f)
            }
        }
        val natural = h.copy(naturalidad = 1f)
        assertFalse(matriz(natural, 175_000).contentEquals(matriz(natural, 1_175_000)))
    }

    @Test fun perfilNaturalDescribeUnArcoConMenosDesplazamientoLateral() {
        val natural = h.copy(naturalidad = 1f)
        val puntos = (0..40).map { destino(matriz(natural, it * 50_000L), h.cuelloX, h.cuelloY) }
        val lateral = puntos.maxOf { it.first } - puntos.minOf { it.first }
        val vertical = puntos.maxOf { it.second } - puntos.minOf { it.second }
        assertTrue(lateral < 0.006f)
        assertTrue(vertical > 0.01f && vertical < 0.025f)
    }

    @Test fun proyeccionDeCabeceoMantieneElCuelloYNoSuperaDoceMilesimas() {
        val natural = h.copy(naturalidad = 1f, giro = 0f, rebote = 0f)
        val m = matriz(natural, 250_000)
        val cuello = destino(m, natural.cuelloX, natural.cuelloY)
        assertEquals(natural.cuelloX, cuello.first, 1e-6f)
        assertEquals(natural.cuelloY, cuello.second, 1e-6f)
        val coronilla = destino(m, natural.cuelloX, natural.cuelloY - 0.4f)
        val proyeccion = (cuello.second - coronilla.second) / 0.4f
        assertTrue(proyeccion < 0.999f && proyeccion >= 0.988f)
        assertEquals(natural.cuelloX, coronilla.first, 1e-6f)
    }

    @Test fun cabelloNaturalHeredaLaProyeccionInclusoConOtroEncuadre() {
        val cabeza = h.copy(naturalidad = 0.9f)
        val pelo = cabeza.copy(rol = RolHeadBop.CABELLO, cuelloX = 0.7f, cuelloY = 1.02f, arrastre = 0f)
        val baseCabeza = floatArrayOf(0.001f, 0f, 0f, 0f, 0.001f, 0f, 0f, 0f, 1f)
        val basePelo = floatArrayOf(0.002f, 0f, 0f, 0f, 0.002f, 0f, -0.3f, -0.14f, 1f)
        for (t in listOf(125_000L, 375_000L, 1_150_000L)) {
            val a = cabeza.matrizEn(baseCabeza, t, 1000f, 1000f, 1000f, 1000f)
            val b = pelo.matrizEn(basePelo, t, 1000f, 1000f, 1000f, 1000f)
            for ((x, y) in listOf(500f to 580f, 450f to 600f, 600f to 300f)) {
                val ca = destino(a, x / 1000, y / 1000)
                val cb = destino(b, x / 500 - 0.3f, y / 500 - 0.14f)
                assertEquals(ca.first, cb.first, 0.001f)
                assertEquals(ca.second, cb.second, 0.001f)
            }
            igual(matriz(cabeza, t), matriz(cabeza.copy(rol = RolHeadBop.CABELLO, retardoUs = 0), t))
        }
    }

    @Test fun arrastreNaturalConservaLaRaizSobreLaCabezaQueCabecea() {
        val cabeza = h.copy(naturalidad = 0.9f)
        val pelo = cabeza.copy(rol = RolHeadBop.CABELLO, pivoteX = 0.35f, pivoteY = 0.22f)
        for (t in listOf(125_000L, 375_000L, 1_150_000L)) {
            val raizCabeza = destino(matriz(cabeza, t), pelo.pivoteX, pelo.pivoteY)
            val raizPelo = destino(matriz(pelo, t), pelo.pivoteX, pelo.pivoteY)
            assertEquals(raizCabeza.first, raizPelo.first, 1e-5f)
            assertEquals(raizCabeza.second, raizPelo.second, 1e-5f)
        }
    }

    @Test fun recorteYVelocidadConservanElNuevoPerfilSinCrearClaves() {
        val natural = h.copy(naturalidad = 0.9f, fundidoUs = 400_000, desfaseUs = 90_000)
        val recortado = natural.recortado(400_000, 6_000_000)!!
        val lento = natural.tiempoEscalado(2.0)
        assertEquals(0.9f, recortado.naturalidad, 0f)
        for (t in listOf(80_000L, 600_000L, 1_500_000L)) {
            igual(matriz(natural, t + 400_000), matriz(recortado, t))
            igual(matriz(natural, t), matriz(lento, t * 2))
        }
    }

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
