package com.royermethod.app.editor.motor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaVideoTest {
    private val fotograma = 33_333L

    /** Un paquete: instante, si el indice del archivo lo da por sync y si de verdad se puede empezar en el. */
    private class Paquete(val pts: Long, val sync: Boolean, val entrada: Boolean?)

    /** Un archivo leido como lo lee MediaExtractor de un MP4. */
    private class Archivo(val paquetes: List<Paquete>) : EntradaVideo.Muestras {
        var actual = -1
        var leidas = 0
        var avances = 0

        override fun buscar(t: Long): Long {
            // El paquete que se ve en t (el de mayor instante que no pasa) y,
            // en el orden de decodificar, el sync anterior
            val i = paquetes.indices.filter { paquetes[it].pts <= t }.maxByOrNull { paquetes[it].pts }
                ?: paquetes.indices.minByOrNull { paquetes[it].pts } ?: return (-1L).also { actual = -1 }
            actual = (i downTo 0).firstOrNull { paquetes[it].sync } ?: 0
            return paquetes[actual].pts
        }

        override fun avanzar(): Boolean {
            avances++
            if (actual < 0 || actual + 1 >= paquetes.size) { actual = -1; return false }
            actual++
            return true
        }

        override val instante: Long get() = if (actual < 0) -1 else paquetes[actual].pts

        override fun entrada(): Boolean? {
            leidas++
            return paquetes[actual].entrada
        }

        /** Decodificando desde donde quedo, ¿se pasa por un punto de entrada antes que por [t]? */
        fun pasaPorEntradaAntesDe(t: Long): Boolean {
            for (i in actual until paquetes.size) {
                if (paquetes[i].entrada == true) return true
                if (paquetes[i].pts == t) return false
            }
            return false
        }
    }

    /**
     * Como sale de un codificador con un B entre cada dos P (orden de
     * decodificar P2, B1, P4, B3...), con un IDR cada [grupo] fotogramas en el
     * sitio de un P. [colgados]: detras de cada IDR llega el B anterior, como en
     * las exportaciones con el fallo de los cortes; si no, ese B va antes del
     * IDR. [sync] decide que da el indice por sync a partir de (instante, es
     * IDR, es el B colgado de detras del IDR).
     */
    private fun archivo(segundos: Int, grupo: Int, colgados: Boolean, sync: (Long, Boolean, Boolean) -> Boolean): Archivo {
        val n = segundos * 30
        val lista = ArrayList<Paquete>()
        fun poner(k: Int, idr: Boolean, detrasDeIdr: Boolean) {
            val t = k * fotograma
            lista.add(Paquete(t, sync(t, idr, detrasDeIdr), idr))
        }
        poner(0, idr = true, detrasDeIdr = false)
        var k = 2
        while (k < n) {
            val idr = k % grupo == 0
            if (idr && !colgados) {
                // Bien hecho: el grupo se cierra antes del IDR
                poner(k - 1, false, false)
                poner(k, true, false)
            } else {
                poner(k, idr, false)
                poner(k - 1, false, idr)
            }
            k += 2
        }
        return Archivo(lista)
    }

    @Test
    fun enUnArchivoBienHechoSoloSeMiraElPaqueteDondeSeCae() {
        val a = archivo(10, 60, colgados = false) { _, idr, _ -> idr }
        assertEquals(120 * fotograma, EntradaVideo.posicionar(a, 150 * fotograma))
        assertEquals(1, a.leidas)
        assertEquals(0L, EntradaVideo.posicionar(a, 0))
    }

    @Test
    fun conElBColgadoMarcadoComoSyncSeVaAlIdrDeVerdad() {
        // Las exportaciones de antes: el indice daba por sync el IDR y el B de detras
        val a = archivo(10, 60, colgados = true) { _, idr, detras -> idr || detras }
        // Sin revisar se caeria en el B (119), saltandose el IDR (120)
        assertEquals(119 * fotograma, a.buscar(150 * fotograma))
        for (objetivo in listOf(121, 150, 179, 200, 239)) {
            val t = EntradaVideo.posicionar(a, objetivo * fotograma)
            val idr = objetivo / 60 * 60
            assertEquals("objetivo $objetivo", idr * fotograma, t)
            assertTrue(a.pasaPorEntradaAntesDe(objetivo * fotograma))
        }
    }

    @Test
    fun justoEnElBColgadoSeEmpiezaEnElGrupoAnterior() {
        val a = archivo(10, 60, colgados = true) { _, idr, detras -> idr || detras }
        val t = EntradaVideo.posicionar(a, 119 * fotograma)
        assertEquals(60 * fotograma, t)
        assertTrue(a.pasaPorEntradaAntesDe(119 * fotograma))
    }

    @Test
    fun sinIndiceSeRetrocedeHastaElIdr() {
        // Archivo sin tabla de sync: todos lo son
        val a = archivo(20, 60, colgados = false) { _, _, _ -> true }
        assertEquals(157 * fotograma, a.buscar(157 * fotograma))
        assertEquals(120 * fotograma, EntradaVideo.posicionar(a, 157 * fotograma))
        assertEquals(540 * fotograma, EntradaVideo.posicionar(a, 599 * fotograma))
        // Con IDR cada 8 segundos hay que ir mas atras
        val largo = archivo(20, 240, colgados = false) { _, _, _ -> true }
        assertEquals(240 * fotograma, EntradaVideo.posicionar(largo, 479 * fotograma))
        assertTrue(largo.pasaPorEntradaAntesDe(479 * fotograma))
    }

    @Test
    fun sinIndiceEnUnPNoSeRecorreElArchivoEntero() {
        // Cayendo en el P 158, el sync de justo antes (el B 157) se decodifica
        // despues de el: desde ahi no se vuelve a pasar por el 158
        val a = archivo(20, 60, colgados = false) { _, _, _ -> true }
        assertEquals(120 * fotograma, EntradaVideo.posicionar(a, 158 * fotograma))
        assertTrue(a.pasaPorEntradaAntesDe(158 * fotograma))
        assertTrue("avances ${a.avances}", a.avances < 200)
    }

    @Test
    fun loQueNoSeEntiendeSeDejaComoSiempre() {
        val raro = Archivo(List(300) { Paquete(it * fotograma, it % 30 == 0, null) })
        assertEquals(150 * fotograma, EntradaVideo.posicionar(raro, 160 * fotograma))
        assertEquals(1, raro.leidas)
    }

    @Test
    fun sinNingunPuntoDeEntradaTerminaYSeQuedaDondeSiempre() {
        val roto = Archivo(List(3000) { Paquete(it * fotograma, true, false) })
        assertEquals(2500 * fotograma, EntradaVideo.posicionar(roto, 2500 * fotograma))
        assertTrue(roto.leidas <= 1 + EntradaVideo.MAX_INTENTOS * EntradaVideo.MAX_MUESTRAS)
        assertEquals(-1L, EntradaVideo.posicionar(Archivo(emptyList()), 0))
    }
}
