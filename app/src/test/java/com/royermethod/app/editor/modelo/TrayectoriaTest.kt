package com.royermethod.app.editor.modelo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/** Evaluar, comparar y simplificar trayectorias de fotogramas clave. */
class TrayectoriaTest {

    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)
    private val medidor = Medidor { _, _ -> 1000f to 1000f }

    /** Lo que exporta un proyecto por defecto: vertical a 576. */
    private val w = 576f
    private val h = 1024f

    private val fin = Encuadre(escala = 1.2f, x = 0.25f, y = -0.1f, rotacion = 12f, opacidad = 0.6f)

    /** Un movimiento de dos claves con [curva], muestreado en cada fotograma con claves lineales. */
    private fun porFotograma(curva: Curva, fotogramas: Int = 60, desde: Encuadre = Encuadre(), hasta: Encuadre = fin): List<Clave> {
        val total = Linea.tiempoDe((fotogramas - 1).toLong())
        return List(fotogramas) { n ->
            val t = Linea.tiempoDe(n.toLong())
            Clave(t, desde.hacia(hasta, Escena.curva(curva, t.toFloat() / total)), Curva.LINEAL)
        }
    }

    private fun instantes(claves: List<Clave>, dur: Long = 2_000_000) = Trayectoria.instantes(dur, 8, claves)

    // ------------------------------------------------------------------
    // Evaluar
    // ------------------------------------------------------------------

    private fun encuadreAlAzar(r: Random) = Encuadre(
        escala = 0.2f + r.nextFloat() * 2f, x = r.nextFloat() - 0.5f, y = r.nextFloat() - 0.5f,
        rotacion = r.nextFloat() * 720f - 360f, opacidad = r.nextFloat(),
        ajuste = if (r.nextBoolean()) Ajuste.CUBRIR else Ajuste.ENCAJAR, espejo = r.nextBoolean(),
        recorte = if (r.nextInt(4) == 0) Recorte(0.1f, 0f, 0.05f, 0.2f) else Recorte()
    )

    /** Claves desordenadas, a veces repetidas en el mismo tiempo, a veces muy juntas o negativas. */
    private fun clavesAlAzar(r: Random): List<Clave> {
        val tiempos = listOf(-20_000L, 0L, 1L, 1_000L, 5_000L, 33_333L, 500_000L, 1_000_000L)
        return List(r.nextInt(10)) {
            val t = if (r.nextInt(3) == 0) tiempos[r.nextInt(tiempos.size)] else r.nextInt(2_000_000).toLong() - 50_000
            val bezier = if (r.nextInt(3) == 0) Bezier(r.nextFloat(), r.nextFloat() * 1.6f - 0.3f, r.nextFloat(), r.nextFloat() * 1.6f - 0.3f) else null
            Clave(t, encuadreAlAzar(r), Curva.values()[r.nextInt(Curva.values().size)], bezier)
        }
    }

    @Test
    fun laPoseEsLaMismaQueLaDelBloqueYLaDeUnaParte() {
        val r = Random(20261017)
        repeat(600) {
            val claves = clavesAlAzar(r)
            val base = encuadreAlAzar(r)
            val clip = Clip(1, origen = foto, hastaUs = 2_000_000, encuadre = base, claves = claves)
            val pieza = PiezaVideo("p", RegionUv(), encuadre = base, claves = claves)
            val ts = claves.flatMap { listOf(it.tiempoUs - 1, it.tiempoUs, it.tiempoUs + 1) } +
                List(12) { r.nextInt(2_200_000).toLong() - 100_000 }
            for (t in ts) {
                val delBloque = Trayectoria.poseEn(claves, t, base, quitarRepetidas = false)
                    .copy(ajuste = base.ajuste, recorte = base.recorte, espejo = base.espejo)
                assertEquals(clip.encuadreEn(t), delBloque)
                assertEquals(pieza.encuadreEn(t), Trayectoria.poseEn(claves, t, base, quitarRepetidas = true))
            }
        }
    }

    @Test
    fun losInstantesSonLosQueSeDibujan() {
        val claves = listOf(Clave(-5, Encuadre()), Clave(1_000, Encuadre()), Clave(150_000, Encuadre()), Clave(300_000, Encuadre()))
        val dur = 200_000L
        val exp = 1_000_000.0 / Linea.FPS * 0.5
        for (inicio in listOf(0L, 10_000L, 2_000_000L)) for (k in listOf(1, 4, 8)) {
            val ins = Trayectoria.instantes(dur, k, claves, inicio)
            val esperados = sortedSetOf<Long>()
            var n = Linea.fotogramaDe(inicio)
            while (Linea.tiempoDe(n) < inicio + dur) {
                val t = Linea.tiempoDe(n)
                esperados.add(t - inicio)
                // La cuenta de Escena.instantes, acotada como en Escena.dibujo
                if (k > 1) for (i in 0 until k) esperados.add(((t + ((i + 0.5) / k - 0.5) * exp).toLong() - inicio).coerceIn(0, dur - 1))
                n++
            }
            esperados.add(1_000)
            esperados.add(150_000)
            assertArrayEquals(esperados.toLongArray(), ins)
        }
        assertEquals(0, Trayectoria.instantes(0, 8, claves).size)
    }

    // ------------------------------------------------------------------
    // Simplificar
    // ------------------------------------------------------------------

    private fun comprobar(original: List<Clave>, s: Simplificacion, ins: LongArray, quitarRepetidas: Boolean = false) {
        // El error del informe es el de verdad
        val (px, op) = Trayectoria.comparar(original, s.claves, ins, w, h, quitarRepetidas)
        assertEquals(px, s.errorMaxPx, 0f)
        assertEquals(op, s.errorMaxOpacidad, 0f)
        assertTrue("${s.errorMaxPx} px", s.errorMaxPx <= s.tolerancia.px)
        assertTrue("${s.errorMaxOpacidad}", s.errorMaxOpacidad <= s.tolerancia.opacidad)
        assertEquals(original.size, s.antes)
        assertEquals(s.claves.size, s.despues)
        assertTrue(s.despues <= s.antes)
        assertEquals(ins.toSet().size, s.instantes)
        // Las que quedan son de las de antes: mismo tiempo y misma pose
        val ordenadas = original.sortedBy { it.tiempoUs }
        for (k in s.claves) assertTrue(ordenadas.any { it.tiempoUs == k.tiempoUs && it.encuadre == k.encuadre })
        assertEquals(s.claves.sortedBy { it.tiempoUs }, s.claves)
        // La primera y la ultima se quedan tal cual
        if (original.isNotEmpty()) {
            assertEquals(ordenadas.first().tiempoUs, s.claves.first().tiempoUs)
            assertEquals(ordenadas.first().encuadre, s.claves.first().encuadre)
            assertEquals(ordenadas.last().tiempoUs, s.claves.last().tiempoUs)
            assertEquals(ordenadas.last().encuadre, s.claves.last().encuadre)
            assertEquals(ordenadas.last().curva, s.claves.last().curva)
        }
    }

    @Test
    fun unMovimientoSuaveMuestreadoPorFotogramaQuedaEnDosOTresClaves() {
        for (curva in listOf(Curva.SUAVE, Curva.ENTRADA, Curva.SALIDA, Curva.REBOTE, Curva.LINEAL)) {
            val claves = porFotograma(curva)
            assertEquals(60, claves.size)
            val ins = instantes(claves)
            val s = Trayectoria.simplificar(claves, ins, w, h)
            comprobar(claves, s, ins)
            assertTrue("$curva: ${s.despues} claves", s.despues in 2..3)
            if (s.despues == 2 && curva != Curva.LINEAL) {
                // Con su curva con nombre, no una Bezier
                assertEquals(curva, s.claves[0].curva)
                assertNull(s.claves[0].bezier)
            }
            assertTrue(s.instantePeorUs in 0 until 2_000_000)
        }
    }

    @Test
    fun unMovimientoConBezierTambienSeReduce() {
        val b = Bezier(0.2f, 0.7f, 0.4f, 1f)
        val total = Linea.tiempoDe(44)
        val claves = List(45) { n ->
            val t = Linea.tiempoDe(n.toLong())
            Clave(t, Encuadre().hacia(fin, b.en(t.toFloat() / total)), Curva.LINEAL)
        }
        val ins = instantes(claves)
        val s = Trayectoria.simplificar(claves, ins, w, h)
        comprobar(claves, s, ins)
        assertTrue("${s.despues} claves", s.despues <= 4)
    }

    @Test
    fun clavesRepetidasYColinealesSeQuitanSinError() {
        // A ritmo constante y en linea recta: con dos basta
        val recta = List(31) { n -> Clave(n * 50_000L, Encuadre(x = n / 60f, escala = 1f + n / 100f), Curva.LINEAL) }
        val ins = instantes(recta)
        val s = Trayectoria.simplificar(recta, ins, w, h, Tolerancia(0f, 0f).copy(px = 1e-3f))
        comprobar(recta, s, ins)
        assertEquals(2, s.despues)
        assertTrue(s.errorMaxPx <= 1e-3f)

        // Quieto, un movimiento, quieto, un salto (dos claves en 500 ms, con
        // una de en medio que no se ve) y quieto otra vez
        val a = Encuadre()
        val b = Encuadre(x = 0.4f, rotacion = 30f)
        val c = Encuadre(y = 0.3f, opacidad = 0.5f)
        val repetidas = listOf(
            Clave(0, a, Curva.LINEAL), Clave(100_000, a, Curva.ELASTICO), Clave(200_000, a, Curva.LINEAL),
            Clave(300_000, b, Curva.LINEAL), Clave(400_000, b), Clave(500_000, b, Curva.LINEAL),
            Clave(500_000, Encuadre(escala = 3f)), Clave(500_000, c, Curva.LINEAL), Clave(600_000, c), Clave(700_000, c)
        )
        val ins2 = instantes(repetidas, 800_000)
        val r = Trayectoria.simplificar(repetidas, ins2, w, h)
        comprobar(repetidas, r, ins2)
        assertEquals(0f, r.errorMaxPx)
        assertEquals(0f, r.errorMaxOpacidad)
        assertEquals(-1L, r.instantePeorUs)
        assertEquals(0f to 0f, Trayectoria.comparar(repetidas, r.claves, ins2, w, h))
        assertTrue("${r.despues} claves", r.despues <= 6)
        // El salto sigue: llega con b y sale con c
        assertEquals(listOf(b, c), r.claves.filter { it.tiempoUs == 500_000L }.map { it.encuadre })

        // Con tolerancia cero solo se quita lo que no cambia nada
        val cero = Trayectoria.simplificar(repetidas, ins2, w, h, Tolerancia(0f, 0f))
        comprobar(repetidas, cero, ins2)
        assertEquals(0f, cero.errorMaxPx)
        assertTrue(cero.despues < repetidas.size)
    }

    @Test
    fun unZigzagConservaLoNecesario() {
        val zigzag = List(40) { n -> Clave(n * 50_000L, Encuadre(x = if (n % 2 == 0) 0f else 0.1f), Curva.LINEAL) }
        val ins = instantes(zigzag)
        val s = Trayectoria.simplificar(zigzag, ins, w, h)
        comprobar(zigzag, s, ins)
        assertEquals(zigzag, s.claves)
        assertEquals(0f, s.errorMaxPx)

        // Un tramo suave delante del zigzag: se simplifica el tramo y el zigzag se queda
        val suave = porFotograma(Curva.SUAVE, 30, hasta = Encuadre(x = 0.3f))
        val desde = suave.last().tiempoUs
        val dientes = List(12) { n -> Clave(desde + (n + 1) * 40_000L, Encuadre(x = if (n % 2 == 0) 0.2f else 0.3f), Curva.LINEAL) }
        val mezcla = suave + dientes
        val ins2 = instantes(mezcla, 1_600_000)
        val m = Trayectoria.simplificar(mezcla, ins2, w, h)
        comprobar(mezcla, m, ins2)
        assertTrue(m.claves.containsAll(dientes))
        assertTrue("${m.despues} claves", m.despues <= dientes.size + 3)
    }

    @Test
    fun nuncaSePasaDeLaToleranciaYElErrorEsElReal() {
        val r = Random(4242)
        val tolerancias = listOf(Tolerancia(), Tolerancia(0f, 0f), Tolerancia(2f, 2f / 255f), Tolerancia(10f, 0.05f), Tolerancia(0.1f, 1f))
        repeat(60) { vez ->
            // Trayectorias de todo tipo: suaves, con ruido, con saltos y repetidas
            val n = 3 + r.nextInt(40)
            var t = 0L
            var e = Encuadre()
            val claves = List(n) {
                val k = Clave(t, e, Curva.values()[r.nextInt(Curva.values().size)],
                    if (r.nextInt(5) == 0) Bezier(r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()) else null)
                t += if (r.nextInt(8) == 0) 0 else 1 + r.nextInt(80_000)
                if (r.nextInt(3) != 0) e = e.copy(x = e.x + (r.nextFloat() - 0.5f) * 0.05f, escala = (e.escala + (r.nextFloat() - 0.5f) * 0.02f),
                    opacidad = (e.opacidad + (r.nextFloat() - 0.5f) * 0.01f).coerceIn(0f, 1f))
                k
            }.shuffled(r)
            val dur = t + 1 + r.nextInt(100_000)
            val repetidas = vez % 2 == 0
            val ins = Trayectoria.instantes(dur, listOf(1, 4, 8)[vez % 3], claves)
            for (tol in tolerancias) {
                val s = Trayectoria.simplificar(claves, ins, w, h, tol, repetidas)
                val ordenadas = Trayectoria.ordenadas(claves, repetidas)
                comprobar(ordenadas, s.copy(antes = ordenadas.size), ins, repetidas)
                assertEquals(claves.size, s.antes)
            }
        }
    }

    @Test
    fun conLaToleranciaMasGrandeNuncaQuedanMas() {
        val claves = porFotograma(Curva.RAPIDA, 90)
        val ins = instantes(claves, 3_000_000)
        val fina = Trayectoria.simplificar(claves, ins, w, h, Tolerancia(0.1f))
        val normal = Trayectoria.simplificar(claves, ins, w, h)
        val gruesa = Trayectoria.simplificar(claves, ins, w, h, Tolerancia(3f, 3f / 255f))
        comprobar(claves, fina, ins)
        comprobar(claves, normal, ins)
        comprobar(claves, gruesa, ins)
        assertTrue(fina.despues >= normal.despues && normal.despues >= gruesa.despues)
        assertTrue(normal.despues < claves.size)
        // Una tolerancia que no es un numero es tolerancia cero
        val rara = Trayectoria.simplificar(claves, ins, w, h, Tolerancia(Float.NaN, -1f))
        assertEquals(Tolerancia(0f, 0f), rara.tolerancia)
        comprobar(claves, rara, ins)
    }

    @Test
    fun casosPequenos() {
        val ins = Trayectoria.instantes(1_000_000, 8, emptyList())
        val vacia = Trayectoria.simplificar(emptyList(), ins, w, h)
        assertEquals(emptyList<Clave>(), vacia.claves)
        val una = listOf(Clave(5_000, fin))
        assertEquals(una, Trayectoria.simplificar(una, ins, w, h).claves)
        val dos = listOf(Clave(0, Encuadre(), Curva.REBOTE), Clave(500_000, fin))
        assertEquals(dos, Trayectoria.simplificar(dos, ins, w, h).claves)
        // Sin instantes no hay con que medir: no se toca nada
        val muchas = porFotograma(Curva.SUAVE, 20)
        assertEquals(muchas, Trayectoria.simplificar(muchas, LongArray(0), w, h).claves)
    }

    @Test
    fun trescientasClavesEnMenosDeDosSegundos() {
        val ondas = List(300) { n ->
            val t = Linea.tiempoDe(n.toLong())
            val s = t / 1e6
            Clave(t, Encuadre(
                x = (0.2 * sin(2 * PI * s / 3)).toFloat(), y = (0.1 * sin(2 * PI * s / 2 + 1)).toFloat(),
                escala = (1 + 0.1 * sin(2 * PI * s / 5)).toFloat(), rotacion = (10 * sin(2 * PI * s / 4)).toFloat()
            ), Curva.LINEAL)
        }
        val r = Random(3)
        val ruido = List(300) { n -> Clave(n * 33_333L, Encuadre(x = r.nextFloat() * 0.01f, y = r.nextFloat() * 0.01f), Curva.LINEAL) }
        val suave = porFotograma(Curva.SUAVE, 300)
        // Los casos caros: todo en una recta (pasan el primer filtro y hay que buscar curva)
        val aTirones = suave.map { it.copy(curva = Curva.SUAVE) }
        val zigzag = List(300) { n -> Clave(n * 33_333L, Encuadre(x = if (n % 2 == 0) 0f else 0.05f), Curva.LINEAL) }
        val ruidoEnX = List(300) { n -> Clave(n * 33_333L, Encuadre(x = n / 600f + r.nextFloat() * 0.003f), Curva.LINEAL) }
        for (claves in listOf(ondas, ruido, suave, aTirones, zigzag, ruidoEnX)) {
            val ins = instantes(claves, 10_000_000)
            val antes = System.nanoTime()
            val s = Trayectoria.simplificar(claves, ins, w, h)
            val ms = (System.nanoTime() - antes) / 1e6
            assertTrue("$ms ms", ms < 2_000)
            comprobar(claves, s, ins)
        }
    }

    // ------------------------------------------------------------------
    // En el proyecto: simplificar, restaurar, descartar
    // ------------------------------------------------------------------

    private fun proyecto(claves: List<Clave>) =
        Proyecto(principal = listOf(Clip(1, origen = foto, hastaUs = 2_000_000, claves = claves)), siguienteId = 2)

    @Test
    fun simplificarYRestaurarDejanElOriginalExacto() {
        val claves = porFotograma(Curva.SUAVE)
        val p = proyecto(claves)
        val (q, s) = Edicion.simplificarClaves(p, 1)!!
        val c = q.principal[0]
        assertEquals(s.claves, c.claves)
        assertTrue(c.claves.size <= 3)
        assertEquals(claves, c.clavesOriginales)
        assertTrue(s.errorMaxPx <= 0.5f)
        // Medido en los instantes de este proyecto: 8 muestras por fotograma a 576 x 1024
        val ins = Trayectoria.instantes(c.duracionUs, 8, claves)
        assertEquals(Trayectoria.comparar(claves, c.claves, ins, 576f, 1024f).first, s.errorMaxPx, 0f)
        assertEquals(p, Edicion.restaurarClaves(q, 1))
        // Lo mismo en una capa que empieza a mitad de un fotograma
        val (capa, id) = Edicion.agregarSuperpuesto(Proyecto(), foto, 1_010_000)
        val r = Edicion.cambiar(capa, id) { it.copy(claves = claves) }
        val (r2, s2) = Edicion.simplificarClaves(r, id)!!
        assertTrue(s2.despues < claves.size)
        assertEquals(r, Edicion.restaurarClaves(r2, id))
    }

    @Test
    fun loQueSeExportaNoSeApartaMasDeLaTolerancia() {
        // Solo desplazamiento: cada punto de la imagen se mueve lo mismo, y
        // eso es justo lo que mide la tolerancia
        val solo = porFotograma(Curva.SALIDA, 60, hasta = Encuadre(x = 0.4f, y = -0.3f))
        val p = proyecto(solo).copy(fondo = Fondo.NEGRO)
        val (q, s) = Edicion.simplificarClaves(p, 1)!!
        assertTrue(s.despues <= 3)
        val (ancho, alto) = p.formato.medidas(p.salida.ladoCorto)
        val ea = Escena(p, ancho, alto, medidor)
        val eb = Escena(q, ancho, alto, medidor)
        var peor = 0.0
        for (n in 0 until Linea.fotogramas(p)) {
            val ma = ea.cuadro(n).clips.single().matrices
            val mb = eb.cuadro(n).clips.single().matrices
            assertEquals(ma.size, mb.size)
            for (i in ma.indices) {
                val (xa, ya) = dondeSeVeElCentro(ma[i])
                val (xb, yb) = dondeSeVeElCentro(mb[i])
                peor = maxOf(peor, hypot(xa - xb, ya - yb))
            }
        }
        assertTrue("$peor px", peor <= 0.5 + 1e-3)
    }

    /** Pixel del lienzo donde cae el centro de la imagen con una matriz lienzo -> textura. */
    private fun dondeSeVeElCentro(m: FloatArray): Pair<Double, Double> {
        val a = m[0].toDouble(); val b = m[3].toDouble(); val d = m[1].toDouble(); val f = m[4].toDouble()
        val u = 0.5 - m[6]; val v = 0.5 - m[7]
        val det = a * f - b * d
        return (f * u - b * v) / det to (a * v - d * u) / det
    }

    @Test
    fun unaSegundaSimplificacionNoPisaElOriginal() {
        val claves = porFotograma(Curva.RAPIDA, 60)
        val p = proyecto(claves)
        val (q1, s1) = Edicion.simplificarClaves(p, 1)!!
        val (q2, s2) = Edicion.simplificarClaves(q1, 1, Tolerancia(4f, 4f / 255f))!!
        assertTrue(s2.despues <= s1.despues)
        assertEquals(claves, q2.principal[0].clavesOriginales)
        assertEquals(p, Edicion.restaurarClaves(q2, 1))
        // Para empezar de cero con otra tolerancia: restaurar y simplificar en el mismo paso
        val (q3, _) = Edicion.simplificarClaves(Edicion.restaurarClaves(q2, 1), 1, Tolerancia(4f, 4f / 255f))!!
        assertEquals(claves, q3.principal[0].clavesOriginales)
    }

    @Test
    fun descartarSeQuedaConLasSimplificadas() {
        val p = proyecto(porFotograma(Curva.SUAVE))
        val (q, s) = Edicion.simplificarClaves(p, 1)!!
        val d = Edicion.descartarOriginales(q, 1)
        assertNull(d.principal[0].clavesOriginales)
        assertEquals(s.claves, d.principal[0].claves)
        assertSame(d, Edicion.descartarOriginales(d, 1))
        assertSame(d, Edicion.restaurarClaves(d, 1))
        assertSame(p, Edicion.restaurarClaves(p, 1))
    }

    @Test
    fun sinClavesOSinNadaQueQuitar() {
        assertNull(Edicion.simplificarClaves(proyecto(emptyList()), 1))
        assertNull(Edicion.simplificarClaves(proyecto(porFotograma(Curva.SUAVE)), 99))
        val dos = proyecto(listOf(Clave(0, Encuadre()), Clave(1_000_000, fin)))
        val (q, s) = Edicion.simplificarClaves(dos, 1)!!
        assertSame(dos, q)
        assertEquals(2, s.despues)
        assertNull(q.principal[0].clavesOriginales)
    }

    private val mitad = PiezaVideo("Superior", RegionUv(abajo = 0.5f))

    @Test
    fun unaParteSeSimplificaYSeRestaura() {
        // Con claves fuera del bloque (las que encierran un tramo recortado) y repetidas
        val dentro = porFotograma(Curva.SUAVE, 60, hasta = Encuadre(y = -0.5f))
        val claves = listOf(Clave(-100_000, Encuadre(y = 2f), Curva.LINEAL)) + dentro + dentro[30] +
            Clave(2_100_000, Encuadre(y = 3f))
        val otra = PiezaVideo("Inferior", RegionUv(arriba = 0.5f), claves = listOf(Clave(0, Encuadre()), Clave(500_000, fin)))
        val p = Proyecto(principal = listOf(Clip(1, origen = foto, hastaUs = 2_000_000, piezas = listOf(mitad.copy(claves = claves), otra))), siguienteId = 2)
        val (q, s) = Edicion.simplificarPieza(p, 1, 0)!!
        val pz = q.principal[0].piezas[0]
        assertEquals(claves, pz.clavesOriginales)
        assertEquals(s.claves, pz.claves)
        assertTrue("${s.despues} claves", s.despues <= 6)
        assertTrue(s.errorMaxPx <= 0.5f)
        // Las de fuera del bloque siguen
        assertTrue(pz.claves.contains(claves.first()))
        assertTrue(pz.claves.contains(claves.last()))
        assertEquals(otra, q.principal[0].piezas[1])
        // Restaurar deja exactamente lo de antes
        assertEquals(p, Edicion.restaurarPieza(q, 1, 0))
        val d = Edicion.descartarOriginalesPieza(q, 1, 0)
        assertNull(d.principal[0].piezas[0].clavesOriginales)
        assertEquals(s.claves, d.principal[0].piezas[0].claves)
        assertSame(d, Edicion.restaurarPieza(d, 1, 0))
        assertSame(d, Edicion.descartarOriginalesPieza(d, 1, 0))
        // Una segunda vez no pisa las originales
        val (q2, _) = Edicion.simplificarPieza(q, 1, 0, Tolerancia(5f, 5f / 255f))!!
        assertEquals(claves, q2.principal[0].piezas[0].clavesOriginales)
        // Lo que no existe
        assertNull(Edicion.simplificarPieza(p, 1, 5))
        assertNull(Edicion.simplificarPieza(p, 7, 0))
        assertNull(Edicion.simplificarPieza(Edicion.cambiar(p, 1) { it.copy(piezas = listOf(mitad)) }, 1, 0))
        assertSame(p, Edicion.restaurarPieza(p, 1, 0))
        assertSame(p, Edicion.restaurarPieza(p, 1, 9))
    }

    @Test
    fun lasOriginalesDeUnaParteSiguenAlRecortarYAlCambiarLaVelocidad() {
        val claves = porFotograma(Curva.SUAVE, 60)
        val p = Proyecto(principal = listOf(Clip(1, origen = foto, hastaUs = 2_000_000, piezas = listOf(mitad.copy(claves = claves)))), siguienteId = 2)
        val (q, _) = Edicion.simplificarPieza(p, 1, 0)!!
        val pz = q.principal[0].piezas[0]
        val recortada = pz.recortada(500_000, 1_500_000)
        assertNotNull(recortada.clavesOriginales)
        assertEquals(mitad.copy(claves = claves).recortada(500_000, 1_500_000).claves, recortada.clavesOriginales)
        val lenta = pz.tiempoEscalado(2.0)
        assertEquals(mitad.copy(claves = claves).tiempoEscalado(2.0).claves, lenta.clavesOriginales)
        assertFalse(lenta.claves == lenta.clavesOriginales)
    }
}
