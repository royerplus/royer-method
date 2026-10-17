package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoOrigen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        // El primer fotograma del bloque, nunca antes de su comienzo
        assertEquals(Linea.tiempoDe(31) - ini, ClavesLinea.destino(5_000, -1e9, fino, ini, lim, fotogramas = true))
        // Si entre las vecinas no cae ningun fotograma, no se mueve
        assertEquals(1_000L, ClavesLinea.destino(1_000, 500.0, fino, ini, 900L..2_000L, fotogramas = true))
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
}
