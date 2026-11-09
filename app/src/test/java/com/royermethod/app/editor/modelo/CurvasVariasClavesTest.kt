package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Plantillas de curva, la grafica de velocidad y mover varias claves a la vez. */
class CurvasVariasClavesTest {

    private fun clave(t: Long, x: Float = 0f) = Clave(t, Encuadre(x = x))

    private fun proyecto(claves: List<Clave>, dur: Long = 2_000_000): Proyecto {
        val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)
        val c = Clip(id = 7, origen = foto, desdeUs = 0, hastaUs = dur, claves = claves)
        return Proyecto(principal = listOf(c), siguienteId = 8)
    }

    private fun claves(p: Proyecto) = Edicion.clip(p, 7)!!.claves

    // ---------------- Plantillas ----------------

    @Test
    fun lasPlantillasEmpiezanYTerminanEnSuSitio() {
        for (pl in PlantillasCurva.TODAS) {
            assertEquals(pl.nombre, 0f, pl.bezier.en(0f), 0f)
            assertEquals(pl.nombre, 1f, pl.bezier.en(1f), 0f)
        }
    }

    @Test
    fun lasPlantillasTienenNombresDistintosYCurvasDistintas() {
        assertEquals(PlantillasCurva.TODAS.size, PlantillasCurva.TODAS.map { it.nombre }.toSet().size)
        assertEquals(PlantillasCurva.TODAS.size, PlantillasCurva.TODAS.map { it.bezier }.toSet().size)
    }

    /**
     * Fluidas a 30 fps y con el obturador: entre muestras muy juntas la curva
     * no da saltos ni tiembla (la Bezier se resuelve por biseccion; con poca
     * precision la pose vibraria un poco de fotograma a fotograma).
     */
    @Test
    fun lasPlantillasSonFluidas() {
        val n = 2000
        for (pl in PlantillasCurva.TODAS) {
            val y = FloatArray(n + 1) { pl.bezier.en(it / n.toFloat()) }
            var mayorPaso = 0f
            var temblor = 0f
            for (i in 1..n) mayorPaso = maxOf(mayorPaso, abs(y[i] - y[i - 1]))
            for (i in 1 until n) temblor = maxOf(temblor, abs(y[i + 1] - 2 * y[i] + y[i - 1]))
            // Ni las mas bruscas (Impacto, Velocity) avanzan mas de un 1 % en una milesima del tramo
            assertTrue("${pl.nombre}: salto $mayorPaso", mayorPaso < 0.01f)
            assertTrue("${pl.nombre}: temblor $temblor", temblor < 2e-3f)
        }
    }

    @Test
    fun lasQueNoSePasanNuncaVanHaciaAtras() {
        val sinVolver = PlantillasCurva.TODAS.filter { p -> listOf(p.bezier.y1, p.bezier.y2).all { it in 0f..1f } }
        assertTrue(sinVolver.size >= 8)
        for (pl in sinVolver) {
            var antes = 0f
            for (i in 0..1000) {
                val v = pl.bezier.en(i / 1000f)
                assertTrue("${pl.nombre} en ${i / 1000f}", v >= antes - 1e-5f)
                antes = v
            }
        }
    }

    @Test
    fun aproximarSeParecaALaCurvaConNombre() {
        // El elastico no cabe en una Bezier; el resto queda cerca
        for (c in Curva.values().filter { it != Curva.ELASTICO && it != Curva.RAPIDA && it != Curva.REBOTE }) {
            val b = PlantillasCurva.aproximar(c)
            var peor = 0f
            for (i in 0..100) {
                val x = i / 100f
                peor = maxOf(peor, abs(b.en(x) - Escena.curva(c, x)))
            }
            assertTrue("$c se aparta $peor", peor < 0.06f)
        }
    }

    @Test
    fun laGraficaDeVelocidadMideLaPendiente() {
        val (lineal, picoLineal) = PlantillasCurva.grafica({ it })
        assertEquals(1f, picoLineal, 1e-4f)
        assertTrue(lineal.all { abs(it - 1f) < 1e-3f })
        // Suave: quieta en los bordes y lo mas rapida en medio
        val (v, pico) = PlantillasCurva.grafica({ Escena.curva(Curva.SUAVE, it) })
        assertTrue(v.first() < 0.05f && v.last() < 0.05f)
        assertEquals(3f, pico, 0.05f)
        // Anticipa: al principio va hacia atras
        val anticipa = PlantillasCurva.TODAS.first { it.nombre == "Anticipa" }.bezier
        assertTrue(PlantillasCurva.grafica({ anticipa.en(it) }).first.any { it < 0f })
    }

    @Test
    fun nombreDeSoloConLaPlantillaExacta() {
        val pl = PlantillasCurva.TODAS[2]
        assertEquals(pl.nombre, PlantillasCurva.nombreDe(pl.bezier))
        assertNull(PlantillasCurva.nombreDe(pl.bezier.copy(x1 = pl.bezier.x1 + 0.01f)))
        assertNull(PlantillasCurva.nombreDe(null))
    }

    // ---------------- Varias claves en el tiempo ----------------

    @Test
    fun margenDelGrupoNoCruzaALasDeFuera() {
        val ks = listOf(clave(0), clave(400_000), clave(500_000), clave(1_000_000), clave(2_000_000))
        // Las del medio: entre la de 0 y la de 1 s, a mas de la tolerancia
        val m = Edicion.margenGrupo(ks, setOf(400_000, 500_000), 2_000_000, 100)!!
        assertEquals(-400_000 + 101, m.first)
        assertEquals(1_000_000 - 100 - 1 - 500_000, m.last)
        // Con la primera dentro: el limite de la izquierda es el 0
        val m2 = Edicion.margenGrupo(ks, setOf(0, 400_000), 2_000_000, 100)!!
        assertEquals(0, m2.first)
        assertEquals(500_000 - 101 - 400_000, m2.last)
        // Todas: solo los bordes del bloque, que ya tocan
        assertEquals(0L..0L, Edicion.margenGrupo(ks, ks.map { it.tiempoUs }.toSet(), 2_000_000, 100))
        assertNull(Edicion.margenGrupo(ks, emptySet(), 2_000_000, 100))
        assertNull(Edicion.margenGrupo(ks, setOf(123), 2_000_000, 100))
    }

    @Test
    fun margenNullSiYaEstanDemasiadoJuntas() {
        val ks = listOf(clave(0), clave(1_000_000), clave(1_000_050), clave(2_000_000))
        assertNull(Edicion.margenGrupo(ks, setOf(1_000_000), 2_000_000, 100))
    }

    @Test
    fun moverClavesCorreLasElegidasConSuEncuadreYCurva() {
        val ks = listOf(
            clave(0, 0f),
            Clave(400_000, Encuadre(x = 0.1f), Curva.REBOTE),
            Clave(600_000, Encuadre(x = 0.2f), bezier = Bezier(0.1f, 0.9f, 0.9f, 0.1f)),
            clave(1_500_000, 0.3f)
        )
        val p = proyecto(ks)
        val q = Edicion.moverClaves(p, 7, setOf(400_000, 600_000), 300_000, 100)
        val r = claves(q)
        assertEquals(listOf(0L, 700_000L, 900_000L, 1_500_000L), r.map { it.tiempoUs })
        assertEquals(Curva.REBOTE, r[1].curva)
        assertEquals(0.1f, r[1].encuadre.x)
        assertEquals(Bezier(0.1f, 0.9f, 0.9f, 0.1f), r[2].bezier)
        // Mas de lo que cabe: se queda pegada a la de fuera, sin pasarla
        val s = claves(Edicion.moverClaves(p, 7, setOf(400_000, 600_000), 5_000_000, 100))
        assertEquals(1_500_000L - 101, s[2].tiempoUs)
        assertEquals(1_500_000L, s[3].tiempoUs)
        // Sin moverse: el mismo proyecto
        assertSame(p, Edicion.moverClaves(p, 7, setOf(400_000), 0, 100))
    }

    @Test
    fun moverClavesLlevaLosSaltosEnteros() {
        // Dos claves en el mismo tiempo son un salto: van juntas
        val ks = listOf(clave(0), clave(500_000, 0.1f), clave(500_000, 0.5f), clave(1_000_000))
        val r = claves(Edicion.moverClaves(proyecto(ks), 7, setOf(500_000), 100_000, 100))
        assertEquals(listOf(0L, 600_000L, 600_000L, 1_000_000L), r.map { it.tiempoUs })
        assertEquals(listOf(0.1f, 0.5f), r.filter { it.tiempoUs == 600_000L }.map { it.encuadre.x })
    }

    // ---------------- Varias claves en la imagen ----------------

    @Test
    fun desplazarTodasMueveElClipEnteroSinDeformarSuMovimiento() {
        val ks = listOf(
            Clave(0, Encuadre(x = 0f, y = 0f, escala = 1f, rotacion = 0f)),
            Clave(1_000_000, Encuadre(x = 0.3f, y = -0.1f, escala = 2f, rotacion = 30f))
        )
        val p = proyecto(ks)
        val q = Edicion.desplazarClaves(p, 7, null, 0.1f, 0.2f, 1.5f, 10f)
        val c = Edicion.clip(q, 7)!!
        val r = c.claves
        assertEquals(0.1f, r[0].encuadre.x, 1e-6f)
        assertEquals(0.4f, r[1].encuadre.x, 1e-6f)
        assertEquals(0.2f, r[0].encuadre.y, 1e-6f)
        assertEquals(0.1f, r[1].encuadre.y, 1e-6f)
        assertEquals(1.5f, r[0].encuadre.escala, 1e-6f)
        assertEquals(3f, r[1].encuadre.escala, 1e-6f)
        assertEquals(10f, r[0].encuadre.rotacion, 1e-6f)
        assertEquals(40f, r[1].encuadre.rotacion, 1e-6f)
        // El encuadre del bloque tambien, y el camino entre medias es el mismo corrido
        assertEquals(0.1f, c.encuadre.x, 1e-6f)
        val antes = Edicion.clip(p, 7)!!.encuadreEn(500_000)
        val ahora = c.encuadreEn(500_000)
        assertEquals(antes.x + 0.1f, ahora.x, 1e-5f)
        assertEquals(antes.y + 0.2f, ahora.y, 1e-5f)
    }

    @Test
    fun desplazarSoloLasElegidas() {
        val ks = listOf(clave(0, 0f), clave(500_000, 0f), clave(1_000_000, 0f))
        val q = Edicion.desplazarClaves(proyecto(ks), 7, setOf(500_000L, 1_000_000L), 0.25f, 0f)
        assertEquals(listOf(0f, 0.25f, 0.25f), claves(q).map { it.encuadre.x })
        // El encuadre del bloque no cambia: solo se mueven unas
        assertEquals(0f, Edicion.clip(q, 7)!!.encuadre.x)
    }

    @Test
    fun desplazarNoDejaEscalasImposibles() {
        val ks = listOf(clave(0), clave(1_000_000))
        val q = Edicion.desplazarClaves(proyecto(ks), 7, null, 0f, 0f, 0.0001f)
        assertTrue(claves(q).all { it.encuadre.escala == 0.05f })
        val nan = Edicion.desplazarClaves(proyecto(ks), 7, null, 0f, 0f, Float.NaN)
        assertTrue(claves(nan).all { it.encuadre.escala == 1f })
    }

    @Test
    fun curvaEnClavesEnTodasOEnLasElegidas() {
        val ks = listOf(clave(0), clave(500_000), clave(1_000_000))
        val b = PlantillasCurva.TODAS.first().bezier
        val todas = claves(Edicion.curvaEnClaves(proyecto(ks), 7, null, Curva.LINEAL, b))
        assertTrue(todas.all { it.bezier == b && it.curva == Curva.LINEAL })
        val unas = claves(Edicion.curvaEnClaves(proyecto(ks), 7, setOf(500_000L), Curva.RAPIDA, null))
        assertEquals(listOf(Curva.SUAVE, Curva.RAPIDA, Curva.SUAVE), unas.map { it.curva })
    }

    // ---------------- Curvas con puntos de velocidad ----------------

    private fun cv(vararg p: Float) = CurvaVelocidad(p.toList().chunked(2).map { PuntoVelocidad(it[0], it[1]) })

    @Test
    fun lasDeVelocidadVanDe0A1SinVolverYSinTemblar() {
        val todas = PlantillasVelocidad.TODAS.map { it.curva } + cv(0f, 0f, 1f, 6f) + cv(0f, 6f, 0.03f, 0f, 1f, 6f)
        for (c in todas) {
            assertEquals(0f, c.en(0f), 0f)
            assertEquals(1f, c.en(1f), 0f)
            val n = 3000
            val y = FloatArray(n + 1) { c.en(it / n.toFloat()) }
            var temblor = 0f
            for (i in 1..n) assertTrue("$c va hacia atras en $i", y[i] >= y[i - 1] - 1e-6f)
            for (i in 1 until n) temblor = maxOf(temblor, abs(y[i + 1] - 2 * y[i] + y[i - 1]))
            assertTrue("$c: temblor $temblor", temblor < 2e-4f)
        }
    }

    @Test
    fun laVelocidadDelAvanceEsLaDeLosPuntos() {
        // Pareja: lineal
        for (i in 0..20) assertEquals(i / 20f, CurvaVelocidad.PAREJA.en(i / 20f), 1e-4f)
        // Doble de rapido al final que al principio: la pendiente lo sigue
        val c = cv(0f, 1f, 1f, 2f)
        val (v, _) = PlantillasCurva.grafica({ c.en(it) })
        assertEquals(2f, v.last() / v.first(), 0.08f)
        // Todo a cero no se rompe: va pareja
        assertEquals(0.5f, cv(0f, 0f, 1f, 0f).en(0.5f), 1e-3f)
    }

    @Test
    fun losPuntosSeOrdenanYLosExtremosSeFijan() {
        val c = cv(0.2f, 1f, 0.9f, 3f, 0.5f, 2f, 0.51f, 9f, 0.5f, Float.NaN)
        assertEquals(0f, c.puntos.first().x)
        assertEquals(1f, c.puntos.last().x)
        assertTrue(c.puntos.zipWithNext().all { (a, b) -> b.x - a.x >= CurvaVelocidad.SEPARACION * 0.5f })
        assertTrue(c.puntos.all { it.v in 0f..CurvaVelocidad.MAXIMA })
        assertEquals(cv(0f, 1f, 1f, 1f), CurvaVelocidad(emptyList()))
        // Iguales por sus puntos: asi se reconoce la plantilla elegida
        assertEquals(PlantillasVelocidad.TODAS[0].curva, cv(*PlantillasVelocidad.TODAS[0].curva.puntos.flatMap { listOf(it.x, it.v) }.toFloatArray()))
    }

    @Test
    fun desdeUnaCurvaQuedaParecida() {
        for (cu in listOf(Curva.SUAVE, Curva.LINEAL, Curva.ENTRADA, Curva.SALIDA)) {
            val c = CurvaVelocidad.desde({ Escena.curva(cu, it) })
            var peor = 0f
            for (i in 0..100) peor = maxOf(peor, abs(c.en(i / 100f) - Escena.curva(cu, i / 100f)))
            assertTrue("$cu se aparta $peor", peor < 0.06f)
        }
    }

    @Test
    fun laClaveUsaLaVelocidadAntesQueLaBezier() {
        val v = PlantillasVelocidad.TODAS[1].curva
        val k = Clave(0, Encuadre(), Curva.LINEAL, Bezier(0.1f, 0.9f, 0.9f, 0.1f), v)
        assertEquals(v.en(0.3f), k.avance(0.3f), 0f)
        val q = Edicion.curvaEnClaves(proyecto(listOf(clave(0), clave(1_000_000))), 7, null, Curva.SUAVE, null, v)
        assertTrue(claves(q).all { it.velocidad == v })
    }

    // ---------------- Zoom de la vista ----------------

    @Test
    fun elZoomVaYVuelveYDejaElPuntoBajoLosDedos() {
        val z = ZoomVista(2.5f, 0.3f, 0.6f)
        val (sx, sy) = z.aPantalla(123f, 456f, 1000f, 800f)
        val (lx, ly) = z.aLienzo(sx, sy, 1000f, 800f)
        assertEquals(123f, lx, 1e-3f)
        assertEquals(456f, ly, 1e-3f)
        // Pellizco alrededor de un punto: lo que estaba bajo el se queda
        val antes = ZoomVista.NINGUNO.aLienzo(400f, 300f, 1000f, 800f)
        val n = ZoomVista.NINGUNO.mover(2f, 400f, 300f, 0f, 0f, 1000f, 800f)
        val despues = n.aLienzo(400f, 300f, 1000f, 800f)
        assertEquals(2f, n.escala, 1e-6f)
        assertEquals(antes.first, despues.first, 1e-2f)
        assertEquals(antes.second, despues.second, 1e-2f)
        // Sin pasarse ni salirse del lienzo
        val lejos = n.mover(100f, 0f, 0f, 1e6f, 1e6f, 1000f, 800f)
        assertEquals(ZoomVista.MAXIMO, lejos.escala, 0f)
        assertEquals(0.5f / ZoomVista.MAXIMO, lejos.x, 1e-6f)
        assertTrue(!ZoomVista.NINGUNO.mover(0.5f, 0f, 0f, 0f, 0f, 1000f, 800f).puesto)
    }
}
