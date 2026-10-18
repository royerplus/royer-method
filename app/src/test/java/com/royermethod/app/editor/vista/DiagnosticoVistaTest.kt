package com.royermethod.app.editor.vista

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticoVistaTest {
    private val ms = 1_000_000L
    private val base = 5_000_000_000L

    @Test
    fun laSerieDaMediaP95MaximoYLosQueSeSalenDelRitmo() {
        val s = Serie()
        // 100 intervalos: 97 de 33 ms, uno de 20, uno de 50 y uno de 68 (sin cajas, crece sola)
        repeat(97) { s.add(33 * ms) }
        s.add(20 * ms); s.add(50 * ms); s.add(68 * ms)
        assertEquals(100, s.n)
        assertEquals(68 * ms, s.max())
        assertEquals(33 * ms, s.p95())
        assertEquals(3, s.fuera(Serie.RITMO_MIN_NS, Serie.RITMO_MAX_NS))
        assertEquals((97 * 33 + 20 + 50 + 68) / 100.0, s.media() / ms, 1e-9)
        assertTrue(s.ritmo(), s.ritmo().contains("fuera de 25-42 ms: 3 de 100 (3.0%)"))
        assertEquals("-", Serie().ms())
        assertEquals("sin datos", Serie().ritmo())
    }

    @Test
    fun separaArranqueTrabajoDeCadaCuadroYRitmo() {
        val s = SesionVista("play", base, 1080, 1210, 4, conGpu = false, comienzos = longArrayOf(0, 43, 90))
        s.sonido(12 * ms, 3)
        // El reloj consulta el sonido en cada vsync: arranca el cabezal, llega la marca y avanza
        s.audio(base + 30 * ms, 0, 4096, null, 0)
        s.audio(base + 120 * ms, 960, 8192, null, 0)
        s.audio(base + 130 * ms, 1440, 8192, 0, 0)
        s.audio(base + 190 * ms, 4320, 9216, 2400, 0)
        s.audio(base + 205 * ms, 5040, 9216, null, 120)
        // El primer cuadro sale a los 18 ms y se repite el fotograma inicial hasta que suena (214 ms)
        s.publicado(base + 18 * ms, 30, 1 * ms, 3 * ms, -1, 1 * ms, 0)
        s.publicado(base + 214 * ms, 31, 1 * ms, 3 * ms, -1, 1 * ms, 0)
        var t = 214L
        var n = 31L
        repeat(10) {
            t += 33; n++
            s.publicado(base + t * ms, n, 1 * ms, 4 * ms, -1, 2 * ms, 0)
        }
        // Un tiron: se salta dos fotogramas y tarda 100 ms
        t += 100; n += 3
        s.publicado(base + t * ms, n, 1 * ms, 30 * ms, -1, 2 * ms, 0)
        s.presentado(base - 5 * ms)   // de antes de pulsar play: no cuenta
        s.presentado(base + 230 * ms)
        s.presentado(base + 263 * ms)
        val l = s.resumen(base + 1000 * ms, "pausa", "1 recogidas (3 ms)")
        val todo = l.joinToString("\n")
        assertTrue(todo, l[0].startsWith("play 1080x1210 k=4 hasta pausa: 13 cuadros en 1.00 s"))
        assertTrue(todo, l[0].contains("1 saltos de fotograma (2 cuadros sin dibujar)"))
        assertTrue(todo, l[1].contains("sonido listo en 12.0 ms (3 tramos)"))
        assertTrue(todo, l[1].contains("primer cuadro publicado 18.0 ms"))
        assertTrue(todo, l[1].contains("primer movimiento 214.0 ms"))
        assertTrue(todo, l[1].contains("primera presentacion 230.0 ms"))
        assertEquals("sonido (a un vsync): el cabezal arranca a 120.0 ms con 8192 frames escritos, " +
            "primera marca a 190.0 ms, lo que suena avanza a 205.0 ms", l[2])
        assertTrue(todo, l[3].contains("gpu sin medir"))
        assertTrue(todo, l[3].contains("cpu 5.85 30.00 30.00"))
        // El de 30 ms cae en el fotograma 44, el segundo del bloque que empieza en 43
        assertTrue(todo, l[4].startsWith("peores cuadros por cpu: 44 (30.0 ms al empezar un bloque), 32 (4.0 ms), 33 (4.0 ms)"))
        // 12 intervalos: 196 ms (esperando al sonido), 10 de 33 y el tiron de 100: dos fuera de 25-42
        assertTrue(todo, l[5].contains("fuera de 25-42 ms: 2 de 12 (16.7%)"))
        assertTrue(todo, l[6].contains("presentacion: 2 cuadros"))
        assertEquals("gc: 1 recogidas (3 ms)", l[7])
    }

    @Test
    fun sinPresentacionesLoDiceYConGpuLaMide() {
        val s = SesionVista("busqueda", base, 576, 1024, 4, conGpu = true)
        s.publicado(base + 40 * ms, 10, 1 * ms, 2 * ms, 8 * ms, 1 * ms, 0)
        val l = s.resumen(base + 100 * ms, "final", null)
        assertTrue(l[1].contains("primer movimiento -"))
        assertTrue(l[2].contains("gpu 8.00 8.00 8.00"))
        assertEquals("peores cuadros por cpu: 10 (2.0 ms)", l[3])
        assertTrue(l[5].contains("sin datos"))
        assertEquals(6, l.size)
    }

    @Test
    fun enPausaCuentaLosDibujosRepetidos() {
        val p = PausaVista(base)
        assertTrue(p.vacia)
        p.cambio(2 * ms); p.dibujado(25 * ms, false, 1080, 1210, 8)
        p.cambio(30 * ms); p.dibujado(25 * ms, false, 1080, 1210, 8)
        p.cambio(50 * ms); p.dibujado(25 * ms, true, 1080, 1210, 8)
        assertFalse(p.vacia)
        val l = p.resumen(base + 2_000 * ms)
        assertEquals("pausa 1080x1210 k=8 en 2.00 s: 3 cambios, 3 dibujos (1 repetidos: mismo proyecto e instante que el anterior)", l[0])
        assertTrue(l[1], l[1].contains("espera en cola tras un cambio 27.33 50.00 50.00"))
        assertTrue(l[1], l[1].contains("dibujo 25.00 25.00 25.00"))
    }
}
