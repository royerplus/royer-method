package com.royermethod.app.editor.modelo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.abs

/** Claves por debajo de la centesima: ponerlas, moverlas y que se vean. */
class ClavesFinasTest {

    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)
    private val medidor = Medidor { _, _ -> 1000f to 1000f }
    private val fina = Edicion.TOLERANCIA_FINA

    private fun proyecto(claves: List<Clave> = emptyList()) =
        Proyecto(principal = listOf(Clip(1, origen = foto, hastaUs = 3_000_000, claves = claves)), siguienteId = 2)

    private fun claves(p: Proyecto) = p.principal[0].claves
    private fun tiempos(p: Proyecto) = claves(p).map { it.tiempoUs }

    private fun escena(c: Clip, blur: Boolean) = Escena(
        Proyecto(principal = listOf(c), fondo = Fondo.NEGRO, salida = AjustesSalida(desenfoqueMovimiento = blur)),
        300, 400, medidor
    )

    private val cuatro = listOf(0L, 1_000L, 5_000L, 10_000L).map { Clave(it, Encuadre(x = it / 10_000f), Curva.LINEAL) }

    // ------------------------------------------------------------------
    // Poner, quitar y cambiar con tolerancia
    // ------------------------------------------------------------------

    @Test
    fun conToleranciaFinaQuedanLasCuatroClaves() {
        var p = proyecto()
        val xs = listOf(0f, 0.1f, 0.2f, 0.3f)
        val ts = listOf(0L, 1_000L, 5_000L, 10_000L)
        for (i in ts.indices) p = Edicion.ponerClave(p, 1, ts[i], Encuadre(x = xs[i]), fina)
        assertEquals(ts, tiempos(p))
        assertEquals(xs, claves(p).map { it.encuadre.x })
        // Al reves tambien: la primera lejos del 0 deja la del 0 y esa se cambia despues
        var q = proyecto()
        for (i in ts.indices.reversed()) q = Edicion.ponerClave(q, 1, ts[i], Encuadre(x = xs[i]), fina)
        assertEquals(ts, tiempos(q))
        assertEquals(xs, claves(q).map { it.encuadre.x })
    }

    @Test
    fun conLaToleranciaDeSiempreLaDe0005PisaLaDel0() {
        var p = Edicion.ponerClave(proyecto(), 1, 0, Encuadre(x = 0f))
        p = Edicion.ponerClave(p, 1, 5_000, Encuadre(x = 0.5f))
        assertEquals(listOf(Clave(0, Encuadre(x = 0.5f))), claves(p))
        // Y la primera a menos de medio fotograma no pone la del 0
        val q = Edicion.ponerClave(proyecto(), 1, 5_000, Encuadre(x = 0.5f))
        assertEquals(listOf(Clave(5_000, Encuadre(x = 0.5f))), claves(q))
    }

    @Test
    fun ponerSobreUnaFinaCambiaLaMasCercanaYConservaSuTiempo() {
        val p = proyecto(cuatro)
        val q = Edicion.ponerClave(p, 1, 5_120, Encuadre(x = 9f), fina)
        assertEquals(listOf(0L, 1_000L, 5_000L, 10_000L), tiempos(q))
        assertEquals(9f, claves(q)[2].encuadre.x)
        assertEquals(Curva.LINEAL, claves(q)[2].curva)
        // A 0,0003 de ella ya es otra
        assertEquals(listOf(0L, 1_000L, 5_000L, 5_300L, 10_000L), tiempos(Edicion.ponerClave(p, 1, 5_300, Encuadre(), fina)))
        // Con la de siempre, entre 0,001 y 0,005 manda la mas cercana, no la primera
        val r = Edicion.ponerClave(p, 1, 4_000, Encuadre(x = 7f))
        assertEquals(7f, claves(r)[2].encuadre.x)
        assertEquals(0.1f, claves(r)[1].encuadre.x)
    }

    @Test
    fun quitarRespetaLaTolerancia() {
        val p = proyecto(cuatro)
        assertEquals(listOf(0L, 1_000L, 10_000L), tiempos(Edicion.quitarClave(p, 1, 5_000, fina)))
        assertEquals(listOf(0L, 1_000L, 10_000L), tiempos(Edicion.quitarClave(p, 1, 5_150, fina)))
        assertEquals(p, Edicion.quitarClave(p, 1, 5_300, fina))
        // La de siempre quita la mas cercana y solo esa
        assertEquals(listOf(0L, 1_000L, 10_000L), tiempos(Edicion.quitarClave(p, 1, 5_000)))
        assertEquals(listOf(1_000L, 5_000L, 10_000L), tiempos(Edicion.quitarClave(p, 1, 0)))
        assertEquals(p, Edicion.quitarClave(p, 1, 40_000))
    }

    @Test
    fun cambiarRespetaLaTolerancia() {
        val p = proyecto(cuatro)
        val rebote: (Clave) -> Clave = { it.copy(curva = Curva.REBOTE) }
        val l = Curva.LINEAL
        val r = Curva.REBOTE
        assertEquals(listOf(l, r, l, l), claves(Edicion.cambiarClave(p, 1, 1_000, fina, rebote)).map { it.curva })
        assertEquals(listOf(l, r, l, l), claves(Edicion.cambiarClave(p, 1, 1_190, fina, rebote)).map { it.curva })
        assertEquals(p, Edicion.cambiarClave(p, 1, 1_400, fina, rebote))
        // La de siempre (sin tolerancia en la llamada): la mas cercana
        assertEquals(listOf(l, l, r, l), claves(Edicion.cambiarClave(p, 1, 4_000, rebote)).map { it.curva })
        assertEquals(listOf(l, l, l, r), claves(Edicion.cambiarClave(p, 1, 9_000) { it.copy(curva = Curva.REBOTE) }).map { it.curva })
    }

    // ------------------------------------------------------------------
    // Mover
    // ------------------------------------------------------------------

    private val bezier = Bezier(0.1f, 0.2f, 0.3f, 0.4f)
    private val dos = listOf(Clave(0, Encuadre()), Clave(10_000, Encuadre(x = 1f, escala = 2f), Curva.REBOTE, bezier))

    @Test
    fun moverAExactamente0005ConservaPoseYCurva() {
        val q = Edicion.moverClave(proyecto(dos), 1, 10_000, 5_000, fina)
        assertEquals(listOf(Clave(0, Encuadre()), Clave(5_000, Encuadre(x = 1f, escala = 2f), Curva.REBOTE, bezier)), claves(q))
        // Y de ahi a 0,001
        val r = Edicion.moverClave(q, 1, 5_000, 1_000, fina)
        assertEquals(listOf(0L, 1_000L), tiempos(r))
        assertEquals(bezier, claves(r)[1].bezier)
    }

    @Test
    fun moverNoPisaOtraClave() {
        val p = proyecto(dos)
        assertSame(p, Edicion.moverClave(p, 1, 10_000, 100, fina))
        assertSame(p, Edicion.moverClave(p, 1, 10_000, 0, fina))
        // Con la tolerancia de siempre, 0,005 esta a menos de medio fotograma de la del 0
        assertSame(p, Edicion.moverClave(p, 1, 10_000, 5_000))
        // Sin clave en el origen no hace nada
        assertSame(p, Edicion.moverClave(p, 1, 7_000, 5_000, fina))
        // Pasar por encima de otra la reordena
        val tres = proyecto(dos + Clave(20_000, Encuadre(x = 3f)))
        assertEquals(listOf(10_000L, 20_000L, 25_000L), tiempos(Edicion.moverClave(tres, 1, 0, 25_000, fina)))
    }

    @Test
    fun moverNuncaDejaTiemposNegativosNiFueraDelBloque() {
        val p = proyecto(listOf(Clave(3_000, Encuadre(x = 1f)), Clave(1_000_000, Encuadre())))
        assertEquals(listOf(0L, 1_000_000L), tiempos(Edicion.moverClave(p, 1, 3_000, -5_000, fina)))
        assertEquals(listOf(3_000L, 3_000_000L), tiempos(Edicion.moverClave(p, 1, 1_000_000, 9_000_000, fina)))
    }

    @Test
    fun moverEnUnaCapaUsaTiemposDeLaLinea() {
        var p = Edicion.agregarSuperpuesto(Proyecto(), foto, 1_000_000).let { it.first }
        val id = p.capas[0].id
        p = Edicion.cambiar(p, id) { it.copy(claves = dos) }
        val q = Edicion.moverClave(p, id, 1_010_000, 1_005_000, fina)
        assertEquals(listOf(0L, 5_000L), q.capas[0].clip.claves.map { it.tiempoUs })
        // Poner antes de que empiece la capa cae en su 0, nunca en negativo
        val r = Edicion.ponerClave(p, id, 400_000, Encuadre(x = 5f), fina)
        assertEquals(listOf(0L, 10_000L), r.capas[0].clip.claves.map { it.tiempoUs })
        assertEquals(5f, r.capas[0].clip.claves[0].encuadre.x)
    }

    @Test
    fun moverElBloqueEnElLienzoConToleranciaFinaPoneOtraClave() {
        var p = proyecto(listOf(Clave(0, Encuadre()), Clave(1_000, Encuadre(x = 0.1f))))
        p = Edicion.ajustarEncuadre(p, 1, 5_000, fina) { it.copy(x = 0.5f) }
        assertEquals(listOf(0L, 1_000L, 5_000L), tiempos(p))
        assertEquals(0.5f, claves(p)[2].encuadre.x)
        // Con la de siempre cambia la de 0,001
        val q = Edicion.ajustarEncuadre(proyecto(listOf(Clave(0, Encuadre()), Clave(1_000, Encuadre(x = 0.1f)))), 1, 5_000) { it.copy(x = 0.5f) }
        assertEquals(listOf(0L, 1_000L), tiempos(q))
        assertEquals(0.5f, claves(q)[1].encuadre.x)
    }

    @Test
    fun laClaveCercanaEsLaMasProxima() {
        assertEquals(5_000L, Edicion.claveCercana(cuatro, 4_000, Edicion.TOLERANCIA_CLAVE)?.tiempoUs)
        assertEquals(1_000L, Edicion.claveCercana(cuatro, 2_000, Edicion.TOLERANCIA_CLAVE)?.tiempoUs)
        // A la misma distancia, la primera
        assertEquals(1_000L, Edicion.claveCercana(cuatro, 3_000, Edicion.TOLERANCIA_CLAVE)?.tiempoUs)
        assertNull(Edicion.claveCercana(cuatro, 3_000, fina))
        assertNull(Edicion.claveCercana(emptyList(), 0, fina))
    }

    // ------------------------------------------------------------------
    // Que se vea: evaluacion, vista previa y exportacion
    // ------------------------------------------------------------------

    private val fino = Clip(
        1, origen = foto, hastaUs = 3_000_000,
        claves = listOf(Clave(0, Encuadre(x = 0f), Curva.LINEAL), Clave(5_000, Encuadre(x = 1f)))
    )

    private fun matrizQuieta(x: Float): FloatArray =
        escena(fino.copy(claves = emptyList(), encuadre = Encuadre(x = x)), false).cuadroEn(0).clips.single().matrices.single()

    @Test
    fun laPoseEntreClavesFinasEsLaInterpolacion() {
        assertEquals(0.5f, fino.encuadreEn(2_500).x, 0f)
        assertEquals(0.2f, fino.encuadreEn(1_000).x, 1e-6f)
        assertEquals(1f, fino.encuadreEn(5_000).x, 0f)
        assertEquals(0f, fino.encuadreEn(0).x, 0f)
        // La vista previa en pausa pide el instante exacto
        val sin = escena(fino, false)
        assertArrayEquals(matrizQuieta(0.5f), sin.cuadroEn(2_500).clips.single().matrices.single(), 0f)
        assertArrayEquals(matrizQuieta(fino.encuadreEn(1_000).x), sin.cuadroEn(1_000).clips.single().matrices.single(), 0f)
        assertFalse(sin.cuadroEn(1_000).clips.single().matrices.single().contentEquals(sin.cuadroEn(2_500).clips.single().matrices.single()))
    }

    @Test
    fun laExportacionVeLasClavesFinasEnLasMuestrasDelObturador() {
        val sin = escena(fino, false)
        val paso = escena(fino, true).cuadro(0).clips.single()
        assertEquals(8, paso.matrices.size)
        val exp = 1_000_000.0 / Linea.FPS * 0.5
        val ins = Trayectoria.instantes(fino.duracionUs, 8, fino.claves)
        for (i in 0 until 8) {
            val ti = (((i + 0.5) / 8 - 0.5) * exp).toLong().coerceAtLeast(0)
            assertTrue(ti in ins)
            assertArrayEquals(sin.cuadroEn(ti).clips.single().matrices.single(), paso.matrices[i], 0f)
        }
        // Las muestras de despues del 0 ya se han movido: 0,001, 0,003 y 0,005 dentro del primer fotograma
        assertFalse(paso.matrices[4].contentEquals(paso.matrices[5]))
        assertFalse(paso.matrices[5].contentEquals(paso.matrices[6]))
        assertArrayEquals(matrizQuieta(1f), paso.matrices[6], 0f)
        // Y en el fotograma siguiente todo esta quieto en la pose final
        val siguiente = escena(fino, true).cuadro(1).clips.single()
        assertEquals(1, siguiente.matrices.size)
        assertArrayEquals(matrizQuieta(1f), siguiente.matrices.single(), 0f)
    }

    // ------------------------------------------------------------------
    // Con lo de siempre, nada cambia
    // ------------------------------------------------------------------

    /** Las tres operaciones tal y como eran antes de la tolerancia. */
    private object Antes {
        const val TOL = 16_000L

        fun poner(p: Proyecto, id: Long, tUs: Long, encuadre: Encuadre): Proyecto {
            val ini = Edicion.inicio(p, id) ?: return p
            val local = (tUs - ini).coerceAtLeast(0)
            return Edicion.cambiar(p, id) { c ->
                val previa = c.claves.firstOrNull { abs(it.tiempoUs - local) <= TOL }
                val sin = c.claves.filter { it !== previa }
                val base = if (sin.isEmpty() && previa == null && local > TOL) listOf(Clave(0, c.encuadre)) else emptyList()
                val nueva = previa?.copy(encuadre = encuadre) ?: Clave(local, encuadre)
                c.copy(claves = (base + sin + nueva).sortedBy { it.tiempoUs })
            }
        }

        fun quitar(p: Proyecto, id: Long, tUs: Long): Proyecto {
            val ini = Edicion.inicio(p, id) ?: return p
            val local = tUs - ini
            return Edicion.cambiar(p, id) { c -> c.copy(claves = c.claves.filter { abs(it.tiempoUs - local) > TOL }) }
        }

        fun cambiar(p: Proyecto, id: Long, tUs: Long, f: (Clave) -> Clave): Proyecto {
            val ini = Edicion.inicio(p, id) ?: return p
            val local = tUs - ini
            return Edicion.cambiar(p, id) { c -> c.copy(claves = c.claves.map { if (abs(it.tiempoUs - local) <= TOL) f(it) else it }) }
        }

        fun ajustar(p: Proyecto, id: Long, tUs: Long, f: (Encuadre) -> Encuadre): Proyecto {
            val c = Edicion.clip(p, id) ?: return p
            if (c.claves.isEmpty()) return Edicion.cambiar(p, id) { it.copy(encuadre = f(it.encuadre)) }
            val ini = Edicion.inicio(p, id) ?: return p
            val local = (tUs - ini).coerceIn(0, c.duracionUs)
            val ahora = c.encuadreEn(local)
            val nuevo = f(ahora)
            val q = Edicion.cambiar(p, id) {
                it.copy(encuadre = it.encuadre.copy(ajuste = nuevo.ajuste, recorte = nuevo.recorte, espejo = nuevo.espejo))
            }
            val animado = { e: Encuadre -> listOf(e.escala, e.x, e.y, e.rotacion, e.opacidad) }
            return if (animado(ahora) == animado(nuevo)) q else poner(q, id, ini + local, nuevo)
        }

        fun ponerPose(pz: PiezaVideo, local: Long, duracion: Long, cambio: (Encuadre) -> Encuadre): PiezaVideo {
            val previa = pz.claves.firstOrNull { it.tiempoUs in 0L..duracion && abs(it.tiempoUs - local) <= TOL }
            val t = previa?.tiempoUs ?: local.coerceIn(0L, duracion)
            val base = if (pz.claves.isEmpty() && t > TOL) listOf(Clave(0, pz.encuadre)) else emptyList()
            val nueva = (previa ?: Clave(t, pz.encuadreEn(t))).copy(encuadre = cambio(pz.encuadreEn(t)))
            return pz.copy(claves = (pz.claves.filter { it.tiempoUs != t } + base + nueva).sortedBy { it.tiempoUs })
        }

        fun recortada(pz: PiezaVideo, desdeUs: Long, hastaUs: Long): PiezaVideo {
            if (pz.claves.isEmpty()) return pz
            val dentro = pz.claves.filter { it.tiempoUs in desdeUs..hastaUs }
            val anterior = pz.claves.filter { it.tiempoUs < desdeUs }.maxByOrNull { it.tiempoUs }
            val posterior = pz.claves.filter { it.tiempoUs > hastaUs }.minByOrNull { it.tiempoUs }
            return pz.copy(claves = (listOfNotNull(anterior) + dentro + listOfNotNull(posterior))
                .sortedBy { it.tiempoUs }.map { it.copy(tiempoUs = it.tiempoUs - desdeUs) })
        }

        fun escalada(pz: PiezaVideo, f: Double): PiezaVideo =
            pz.copy(claves = pz.claves.map { it.copy(tiempoUs = (it.tiempoUs * f).toLong()) })
    }

    /** Claves como las que deja la interfaz con la tolerancia de siempre: separadas mas de un fotograma. */
    private fun separadas(r: Random, n: Int): List<Clave> {
        var t = if (r.nextBoolean()) 0L else r.nextInt(200_000).toLong()
        return List(n) {
            val k = Clave(t, Encuadre(x = r.nextFloat(), escala = 0.5f + r.nextFloat()), Curva.values()[r.nextInt(Curva.values().size)])
            t += 32_001 + r.nextInt(300_000)
            k
        }
    }

    @Test
    fun conLaToleranciaDeSiempreDanLoMismoQueAntes() {
        val r = Random(20261016)
        repeat(300) {
            val claves = if (r.nextInt(5) == 0) emptyList() else separadas(r, 1 + r.nextInt(8))
            var p = proyecto(claves)
            if (r.nextBoolean()) {
                // Tambien en una capa que empieza mas tarde
                val (q, id) = Edicion.agregarSuperpuesto(Proyecto(), foto, r.nextInt(2_000_000).toLong())
                p = Edicion.cambiar(q, id) { it.copy(claves = claves) }
            }
            val id = p.principal.firstOrNull()?.id ?: p.capas[0].id
            val ini = Edicion.inicio(p, id)!!
            val candidatos = claves.flatMap { listOf(it.tiempoUs, it.tiempoUs + 16_000, it.tiempoUs - 16_000, it.tiempoUs + 16_001, it.tiempoUs + 7) } +
                List(6) { r.nextInt(3_200_000).toLong() - 100_000 }
            for (local in candidatos) {
                val t = ini + local
                val e = Encuadre(y = r.nextFloat())
                assertEquals(Antes.poner(p, id, t, e), Edicion.ponerClave(p, id, t, e))
                assertEquals(Antes.quitar(p, id, t), Edicion.quitarClave(p, id, t))
                val f: (Clave) -> Clave = { it.copy(curva = Curva.ELASTICO, bezier = Bezier(0.2f, 0.1f, 0.7f, 0.9f)) }
                assertEquals(Antes.cambiar(p, id, t, f), Edicion.cambiarClave(p, id, t, f))
                val g: (Encuadre) -> Encuadre = { it.copy(x = it.x + 0.1f, espejo = !it.espejo) }
                assertEquals(Antes.ajustar(p, id, t, g), Edicion.ajustarEncuadre(p, id, t, g))
            }
        }
    }

    // ------------------------------------------------------------------
    // Partes
    // ------------------------------------------------------------------

    private val superior = PiezaVideo("Superior", RegionUv(abajo = 0.5f), encuadre = Encuadre(y = 0.1f))

    @Test
    fun laPoseDeUnaParteConLaToleranciaDeSiempreEsLaDeAntes() {
        val r = Random(7)
        repeat(200) {
            val pz = superior.copy(claves = if (r.nextInt(4) == 0) emptyList() else separadas(r, 1 + r.nextInt(6)))
            val dur = 3_000_000L
            val candidatos = pz.claves.flatMap { listOf(it.tiempoUs, it.tiempoUs + 15_999, it.tiempoUs - 16_000) } + List(5) { r.nextInt(3_100_000).toLong() }
            for (local in candidatos) {
                val cambio: (Encuadre) -> Encuadre = { it.copy(x = it.x + 0.25f) }
                assertEquals(Antes.ponerPose(pz, local, dur, cambio), pz.conPose(local, dur, cambio = cambio))
            }
        }
    }

    @Test
    fun unaParteAdmiteClavesFinas() {
        var pz = superior
        for ((i, t) in listOf(0L, 1_000L, 5_000L).withIndex()) pz = pz.conPose(t, 3_000_000, fina) { it.copy(x = i.toFloat()) }
        assertEquals(listOf(0L, 1_000L, 5_000L), pz.claves.map { it.tiempoUs })
        assertEquals(listOf(0f, 1f, 2f), pz.claves.map { it.encuadre.x })
        assertEquals(1_000L, pz.claveCercana(1_150, fina, 3_000_000)?.tiempoUs)
        assertEquals(listOf(0L, 5_000L), pz.sinClave(1_100, fina).claves.map { it.tiempoUs })
        assertSame(pz, pz.sinClave(1_300, fina))
        // Mover sin pisar y sin salir del bloque
        assertEquals(listOf(0L, 1_000L, 3_000L), pz.conClaveMovida(5_000, 3_000, 3_000_000, fina).claves.map { it.tiempoUs })
        assertSame(pz, pz.conClaveMovida(5_000, 1_100, 3_000_000, fina))
        assertEquals(listOf(0L, 1_000L, 3_000_000L), pz.conClaveMovida(5_000, 9_000_000, 3_000_000, fina).claves.map { it.tiempoUs })
        val sinCero = pz.sinClave(0, fina)
        assertEquals(listOf(0L, 5_000L), sinCero.conClaveMovida(1_000, -3_000, 3_000_000, fina).claves.map { it.tiempoUs })
        // Las que encierran un tramo recortado (fuera del bloque) no se mueven desde ahi
        val conFuera = pz.copy(claves = listOf(Clave(-2_000, Encuadre(x = 9f))) + pz.claves)
        assertSame(conFuera, conFuera.conClaveMovida(-2_000, 2_000, 3_000_000, fina))
        assertEquals(listOf(-2_000L, 0L, 1_000L, 3_000L), conFuera.conClaveMovida(5_000, 3_000, 3_000_000, fina).claves.map { it.tiempoUs })
    }

    @Test
    fun recortarYEscalarUnaParteTransformanTambienLasOriginales() {
        val r = Random(11)
        repeat(100) {
            val claves = separadas(r, 2 + r.nextInt(8))
            val pz = superior.copy(claves = claves)
            val desde = r.nextInt(1_000_000).toLong()
            val hasta = desde + 33_333 + r.nextInt(2_000_000)
            val f = listOf(0.5, 2.0, 1.0 / 3, 1.7)[r.nextInt(4)]
            // Sin originales, igual que antes
            assertEquals(Antes.recortada(pz, desde, hasta), pz.recortada(desde, hasta))
            assertEquals(Antes.escalada(pz, f), pz.tiempoEscalado(f))
            // Con originales, cada lista igual que si fuera la unica
            val otras = separadas(r, 2 + r.nextInt(30))
            val con = pz.copy(clavesOriginales = otras)
            assertEquals(pz.recortada(desde, hasta).claves, con.recortada(desde, hasta).claves)
            assertEquals(pz.copy(claves = otras).recortada(desde, hasta).claves, con.recortada(desde, hasta).clavesOriginales)
            assertEquals(pz.copy(claves = otras).tiempoEscalado(f).claves, con.tiempoEscalado(f).clavesOriginales)
            assertEquals(pz.tiempoEscalado(f).claves, con.tiempoEscalado(f).claves)
        }
        // Una parte sin claves pero con originales tambien las recorta
        val vacia = superior.copy(clavesOriginales = listOf(Clave(-10_000, Encuadre()), Clave(500_000, Encuadre(x = 1f)), Clave(900_000, Encuadre())))
        assertEquals(listOf(-110_000L, 400_000L), vacia.recortada(100_000, 450_000).clavesOriginales!!.map { it.tiempoUs })
        assertSame(superior, superior.recortada(100_000, 450_000))
    }
}
