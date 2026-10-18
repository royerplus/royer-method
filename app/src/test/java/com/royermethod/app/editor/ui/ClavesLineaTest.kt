package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.EjemplosMovimiento
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoOrigen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tocar y arrastrar claves en la linea de tiempo, y su "Momento" en los paneles. */
class ClavesLineaTest {

    private val densidad = 2.75f
    private val fino = ZoomLinea.pxPorUs(EstadoEditor.ZOOM_FINO_MAX, densidad)
    private val normal = ZoomLinea.pxPorUs(EstadoEditor.ZOOM_MAX, densidad)
    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)

    private fun k(t: Long, x: Float = 0f) = Clave(t, Encuadre(x = x), Curva.SALIDA)

    @Test fun seCogeElRomboMasCercanoNoElPrimero() {
        // A zoom normal 0,001 s y 0,005 s quedan a 10 px: los dos encajan en el dedo
        val xs = floatArrayOf(100f, 109.9f, 300f)
        assertEquals(1, ClavesLinea.masCercana(xs, 108f, 33f))
        assertEquals(0, ClavesLinea.masCercana(xs, 101f, 33f))
        assertEquals(2, ClavesLinea.masCercana(xs, 290f, 33f))
        assertEquals(-1, ClavesLinea.masCercana(xs, 200f, 33f))
        // A igual distancia, el primero; los que no se ven (NaN) no cuentan
        assertEquals(0, ClavesLinea.masCercana(floatArrayOf(90f, 110f), 100f, 33f))
        assertEquals(1, ClavesLinea.masCercana(floatArrayOf(Float.NaN, 110f), 100f, 33f))
    }

    @Test fun losLimitesDejanSitioALasVecinasYNuncaSonNegativos() {
        val claves = listOf(k(0), k(1_000), k(5_000), k(1_000_000))
        assertEquals(201L..4_799L, ClavesLinea.limites(claves, 1_000, 2_000_000, 200))
        assertEquals(0L..799L, ClavesLinea.limites(claves, 0, 2_000_000, 200))
        assertEquals(5_201L..2_000_000L, ClavesLinea.limites(claves, 1_000_000, 2_000_000, 200))
        // Con medio fotograma de tolerancia, 0,001 s no cabe entre 0 y 0,005 s
        assertNull(ClavesLinea.limites(claves, 1_000, 2_000_000, Edicion.TOLERANCIA_CLAVE))
        // Un salto (dos claves en el mismo tiempo) se mueve entero: no se cuenta como vecina
        val salto = listOf(k(0), k(500_000), k(500_000, 1f), k(900_000))
        assertEquals(201L..899_799L, ClavesLinea.limites(salto, 500_000, 1_000_000, 200))
    }

    @Test fun elPasoDelArrastreSinImanEsElDelPixel() {
        assertEquals(10L, ClavesLinea.pasoArrastre(1.0 / fino))
        assertEquals(500L, ClavesLinea.pasoArrastre(1.0 / normal))
        assertEquals(1L, ClavesLinea.pasoArrastre(0.3))
        assertEquals(1L, ClavesLinea.pasoArrastre(1.0))
        assertEquals(2L, ClavesLinea.pasoArrastre(1.2))
        assertEquals(20_000L, ClavesLinea.pasoArrastre(11_364.0))
    }

    @Test fun sinImanLaClaveVaA0005ExactoYNoPasaDeSusVecinas() {
        val lim = 201L..999_799L
        // 4 ms a la derecha en el zoom fino maximo son 660 px
        assertEquals(5_000L, ClavesLinea.destino(1_000, 660.0, fino, 0, lim, fotogramas = false))
        // Casi 4 ms: redondea a 10 us (lo que da un pixel), no trunca
        assertEquals(5_000L, ClavesLinea.destino(1_000, 659.4, fino, 0, lim, fotogramas = false))
        assertEquals(4_990L, ClavesLinea.destino(1_000, 658.0, fino, 0, lim, fotogramas = false))
        // Hacia atras mas alla del 0: se queda en su limite, nunca negativo
        assertEquals(201L, ClavesLinea.destino(1_000, -1e6, fino, 0, lim, fotogramas = false))
        assertEquals(0L, ClavesLinea.destino(1_000, -1e9, fino, 0, 0L..999_799L, fotogramas = false))
        // Y no pasa por encima de la siguiente
        assertEquals(999_799L, ClavesLinea.destino(1_000, 1e9, fino, 0, lim, fotogramas = false))
    }

    @Test fun conImanCaeEnLosFotogramasDeLaLineaAunqueElBloqueEmpieceAMitad() {
        val ini = 1_028_000L
        val lim = 0L..2_000_000L
        // Clave en 0,005 s del bloque (1,033 s de la linea): el fotograma 31 esta en 1,033333
        val d = ClavesLinea.destino(5_000, 30.0, fino, ini, lim, fotogramas = true)
        assertEquals(Linea.tiempoDe(31) - ini, d)
        assertEquals(5_333L, d)
        // Arrastrada lejos, sigue en fotogramas de la linea
        val d2 = ClavesLinea.destino(5_000, 1_000.0 * normal * 100, normal, ini, lim, fotogramas = true)
        assertEquals(Linea.tiempoDe(Linea.fotogramaEn(ini + d2)), ini + d2)
        assertTrue(d2 in lim)
        // Hacia atras no hay ningun fotograma del bloque antes de 0,005 s: se
        // queda (nunca antes de su comienzo, y tampoco salta adelante contra el dedo)
        assertEquals(5_000L, ClavesLinea.destino(5_000, -1e9, fino, ini, lim, fotogramas = true))
        // Si entre las vecinas no cae ningun fotograma, no se mueve
        assertEquals(1_000L, ClavesLinea.destino(1_000, 500.0, fino, ini, 900L..2_000L, fotogramas = true))
        // Cogerla sin mover el dedo no la lleva al fotograma ni la redondea
        assertEquals(5_003L, ClavesLinea.destino(5_003, 0.0, fino, ini, lim, fotogramas = true))
        assertEquals(5_003L, ClavesLinea.destino(5_003, 0.0, normal, ini, lim, fotogramas = false))
    }

    @Test fun laPosicionDelCabezalEntreLasClaves() {
        val t = listOf(0L, 1_000L, 5_000L)
        assertEquals(ClavesLinea.Posicion(1, 0L, 5_000L), ClavesLinea.posicion(t, 1_000, 200))
        assertEquals(ClavesLinea.Posicion(1, 0L, 5_000L), ClavesLinea.posicion(t, 1_150, 200))
        assertEquals(ClavesLinea.Posicion(-1, 1_000L, 5_000L), ClavesLinea.posicion(t, 3_000, 200))
        assertEquals(ClavesLinea.Posicion(2, 1_000L, null), ClavesLinea.posicion(t, 5_000, 200))
        // Con medio fotograma de tolerancia, la mas cercana
        assertEquals(1, ClavesLinea.posicion(t, 2_000, Edicion.TOLERANCIA_CLAVE).indice)
        assertEquals(ClavesLinea.Posicion(-1, null, 0L), ClavesLinea.posicion(t, null, 200))
        assertEquals(ClavesLinea.Posicion(-1, null, null), ClavesLinea.posicion(emptyList(), 0, 200))
    }

    /**
     * Lo que hace la linea al arrastrar un rombo: cada evento calcula desde
     * el proyecto de cuando se cogio, y todo el arrastre es un paso de deshacer.
     */
    @Test fun arrastrarUnaClaveEsUnPasoDeDeshacerCalculadoDesdeLaBase() {
        val claves = listOf(k(0), k(1_000, 0.2f), k(5_000, 0.5f), k(1_000_000, 1f))
        val p = Proyecto(principal = listOf(Clip(1, origen = foto, hastaUs = 2_000_000, claves = claves)), siguienteId = 2)
        val e = EstadoEditor(p)
        val tol = Edicion.TOLERANCIA_FINA
        val ini = Edicion.inicio(p, 1)!!
        val lim = ClavesLinea.limites(claves, 1_000, 2_000_000, tol)!!
        val base = e.proyecto
        e.empezarGesto()
        var acumulado = 0.0
        // Muchos eventos pequenos: 495 px (3 ms) a la derecha y 165 px de vuelta, queda en 0,003 s
        for (dx in List(99) { 5.0 } + List(33) { -5.0 }) {
            acumulado += dx
            val d = ClavesLinea.destino(1_000, acumulado, fino, ini, lim, fotogramas = false)
            e.ajustar { Edicion.moverClave(base, 1, ini + 1_000, ini + d, tol) }
        }
        e.terminarGesto()
        val movidas = e.proyecto.principal[0].claves
        assertEquals(listOf(0L, 3_000L, 5_000L, 1_000_000L), movidas.map { it.tiempoUs })
        // Conserva pose y curva
        assertEquals(claves[1].copy(tiempoUs = 3_000), movidas[1])
        // Un solo paso: deshacer deja el proyecto exacto
        e.deshacer()
        assertEquals(p, e.proyecto)
        e.rehacer()
        assertEquals(movidas, e.proyecto.principal[0].claves)
    }

    /**
     * Las claves de 1,000 y 1,005 s del ejemplo de barrido y zoom estan a 5 ms.
     * Con el iman (tolerancia de medio fotograma) su propio tiempo no cabe en
     * el hueco que les dejan sus vecinas: antes limites daba ese hueco igual y
     * destino las metia en el primer fotograma que cabia, asi que 1,000 se iba
     * a 0,967 y 1,005 a 1,033 nada mas cogerlas, fuera cual fuera el sentido
     * del dedo. Ahora no hay hueco: la linea avisa y no las mueve.
     */
    @Test fun unaClavePegadaASuVecinaNoSaltaAlCogerlaConElIman() {
        val c = EjemplosMovimiento.barridoYZoom(1, foto)
        for (t in listOf(EjemplosMovimiento.GOLPE_US, EjemplosMovimiento.GOLPE_ARRIBA_US)) {
            assertNull(ClavesLinea.limites(c.claves, t, c.duracionUs, Edicion.TOLERANCIA_CLAVE))
            // Sin el iman si se mueven, con el dedo y lo que se mueve el dedo
            val lim = ClavesLinea.limites(c.claves, t, c.duracionUs, Edicion.TOLERANCIA_FINA)!!
            assertTrue(t in lim)
            // 1 ms en el zoom fino maximo son 165 px
            assertEquals(t + 1_000, ClavesLinea.destino(t, 165.0, fino, 6_400_000, lim, fotogramas = false))
            assertEquals(t - 1_000, ClavesLinea.destino(t, -165.0, fino, 6_400_000, lim, fotogramas = false))
        }
        // La de la vuelta (1,4 s) esta lejos: con el iman se mueve a fotogramas
        val vuelta = ClavesLinea.limites(c.claves, EjemplosMovimiento.GOLPE_VUELTA_US, c.duracionUs, Edicion.TOLERANCIA_CLAVE)!!
        assertTrue(EjemplosMovimiento.GOLPE_VUELTA_US in vuelta)
    }

    /**
     * Con cualquier reparto de claves: si hay hueco contiene la clave, y
     * arrastrarla (con o sin iman) nunca la lleva al otro lado de donde va el
     * dedo ni fuera de su hueco.
     */
    @Test fun arrastrarNuncaVaContraElDedo() {
        val r = Random(7)
        repeat(400) {
            val tiempos = List(2 + r.nextInt(5)) { r.nextLong(0, 300_000) }.distinct().sorted()
            val claves = tiempos.map { k(it) }
            val ini = r.nextLong(0, 2_000_000)
            for (tol in listOf(Edicion.TOLERANCIA_FINA, Edicion.TOLERANCIA_CLAVE)) for (t in tiempos) {
                val lim = ClavesLinea.limites(claves, t, 300_000, tol) ?: continue
                assertTrue(t in lim)
                for (dx in listOf(-500.0, -40.0, -3.0, -0.4, 0.4, 3.0, 40.0, 500.0)) for (fotogramas in listOf(false, true)) {
                    val d = ClavesLinea.destino(t, dx, normal * 20, ini, lim, fotogramas)
                    assertTrue(d in lim)
                    if (dx > 0) assertTrue("dx=$dx t=$t d=$d", d >= t) else assertTrue("dx=$dx t=$t d=$d", d <= t)
                }
            }
        }
    }

    @Test fun soloSeCogeUnRomboConSitioAlrededor() {
        val sep = 16 * densidad
        // Una clave por fotograma a 64 dp/s: 2,1 dp entre rombos
        val paso = 64f * densidad / 30f
        val xs = FloatArray(30) { 100f + it * paso }
        val t = LongArray(30) { Linea.tiempoDe(it.toLong()) }
        assertTrue((0 until 30).none { ClavesLinea.aislada(xs, t, it, sep) })
        // Acercando el zoom (unos 600 dp/s) ya caben
        val lejos = FloatArray(30) { 100f + it * 20 * densidad }
        assertTrue((0 until 30).all { ClavesLinea.aislada(lejos, t, it, sep) })
        // Las que no se ven no cuentan, y un salto (dos claves al mismo tiempo) tampoco
        assertTrue(ClavesLinea.aislada(floatArrayOf(100f, 105f, Float.NaN), longArrayOf(0, 0, 1), 0, sep))
        assertTrue(ClavesLinea.aislada(floatArrayOf(100f, Float.NaN), longArrayOf(0, 1), 0, sep))
        assertTrue(!ClavesLinea.aislada(floatArrayOf(100f, 120f), longArrayOf(0, 1), 1, sep))
        assertTrue(!ClavesLinea.aislada(floatArrayOf(Float.NaN), longArrayOf(0), 0, sep))
        // Las del ejemplo, a 5 ms: juntas a zoom normal, separadas en el fino
        val ejemplo = longArrayOf(EjemplosMovimiento.GOLPE_US, EjemplosMovimiento.GOLPE_ARRIBA_US)
        val enNormal = FloatArray(2) { (ejemplo[it] * normal).toFloat() }
        val enFino = FloatArray(2) { (ejemplo[it] * fino).toFloat() }
        assertTrue(!ClavesLinea.aislada(enNormal, ejemplo, 0, sep))
        assertTrue(ClavesLinea.aislada(enFino, ejemplo, 0, sep))
    }
}
