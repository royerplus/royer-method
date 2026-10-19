package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Texto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EstadoEditorTest {

    private fun proyecto() = Proyecto(principal = listOf(Clip(1, texto = Texto("x"), hastaUs = 4_000_000)), siguienteId = 2)

    /**
     * Imita lo que importa del Previsualizador: sonando, el reloj sigue
     * corriendo y cada cuadro avisa del instante; una busqueda con la
     * reproduccion en marcha vuelve a arrancar el sonido desde ahi; la pausa
     * se resuelve despues (en su hilo) y deja el cabezal donde iba el reloj,
     * salvo que entretanto se haya buscado otro instante. Los avisos viejos
     * (de antes de una busqueda) se descartan, como en avisarTiempo.
     */
    private class VistaFalsa(val estado: EstadoEditor) {
        var reproduciendo = false
        var reloj = 0L
        var busquedas = 0
        val llamadas = mutableListOf<String>()
        private val pendientes = mutableListOf<() -> Unit>()

        fun reproducir() { reproduciendo = true }
        fun pausar() {
            llamadas += "pausar"
            if (!reproduciendo) return
            reproduciendo = false
            val b = busquedas
            pendientes += { if (busquedas == b) estado.tiempo = reloj }
        }
        fun irA(t: Long) {
            llamadas += "irA $t"
            busquedas++
            reloj = t
        }
        /** Pasa un cuadro: si suena, el reloj avanza y avisa (el aviso llega despues). */
        fun avanzar(us: Long) {
            if (!reproduciendo) return
            reloj += us
            val b = busquedas
            val t = reloj
            pendientes += { if (busquedas == b) estado.tiempo = t }
        }
        /** Lo que hace EditorScreen al recomponer: LaunchedEffect(estado.reproduciendo). */
        fun seguirInterfaz() {
            if (estado.reproduciendo != reproduciendo) {
                if (estado.reproduciendo) reproducir() else pausar()
            }
        }
        /** Corre lo que estaba en cola (hilo de dibujo y avisos al hilo principal). */
        fun procesar() {
            val l = pendientes.toList()
            pendientes.clear()
            l.forEach { it() }
        }
    }

    private fun sonando(): Pair<EstadoEditor, VistaFalsa> {
        val e = EstadoEditor(proyecto())
        val v = VistaFalsa(e)
        e.alBuscar = { v.irA(it) }
        e.alPausar = { v.pausar() }
        e.irA(2_000_000)
        e.reproduciendo = true
        v.seguirInterfaz()
        v.avanzar(33_333)
        v.procesar()
        v.llamadas.clear()
        assertTrue(v.reproduciendo)
        assertEquals(2_033_333L, e.tiempo)
        return e to v
    }

    @Test fun pausarEnDejaElCabezalExactoAunqueEstuvieraSonando() {
        val (e, v) = sonando()
        // Un aviso del ultimo cuadro todavia en camino
        v.avanzar(33_333)
        e.pausarEn(1_000_000)
        assertFalse(e.reproduciendo)
        assertFalse(v.reproduciendo)
        assertEquals(listOf("pausar", "irA 1000000"), v.llamadas)
        // Pasan cuadros, se recompone la interfaz y se resuelve todo lo pendiente
        repeat(5) { v.avanzar(16_667); v.seguirInterfaz(); v.procesar() }
        assertEquals(1_000_000L, e.tiempo)
        assertEquals(1_000_000L, v.reloj)
    }

    @Test fun sinPausaEnElActoElCabezalSeCorria() {
        // Lo que pasaba con "reproduciendo = false; irA(t)": por esto existe pausarEn
        val (e, v) = sonando()
        e.reproduciendo = false
        e.irA(1_000_000)
        v.avanzar(16_667)
        v.seguirInterfaz()
        v.procesar()
        assertNotEquals(1_000_000L, e.tiempo)
        assertEquals(1_016_667L, e.tiempo)
    }

    @Test fun pausarEnSinReproducirSoloMueveElCabezal() {
        val e = EstadoEditor(proyecto())
        val v = VistaFalsa(e)
        e.alBuscar = { v.irA(it) }
        e.alPausar = { v.pausar() }
        e.pausarEn(5_000)
        assertFalse(e.reproduciendo)
        assertEquals(5_000L, e.tiempo)
        assertEquals(5_000L, v.reloj)
        assertEquals(listOf("pausar", "irA 5000"), v.llamadas)
        // No es un cambio del proyecto: nada que deshacer
        assertFalse(e.puedeDeshacer)
    }

    @Test fun pausarEnPideLaPausaAunqueLaInterfazYaDijeraPausa() {
        // El usuario toco pausa y la vista previa aun no se habia enterado
        val (e, v) = sonando()
        e.reproduciendo = false
        e.pausarEn(1_000_000)
        repeat(3) { v.avanzar(16_667); v.seguirInterfaz(); v.procesar() }
        assertEquals(1_000_000L, e.tiempo)
    }

    @Test fun pausarEnAcotaComoIrA() {
        val e = EstadoEditor(proyecto())
        e.pausarEn(-5)
        assertEquals(0L, e.tiempo)
        e.pausarEn(Long.MAX_VALUE)
        assertEquals(e.limiteCabezal, e.tiempo)
    }

    @Test fun elZoomNormalTieneLimiteYElFinoLlegaAlMilisegundo() {
        val e = EstadoEditor(proyecto())
        assertFalse(e.zoomFino)
        assertEquals(EstadoEditor.ZOOM_MAX, e.zoomMaximo, 0f)
        e.ponerZoom(5_000f)
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
        e.ponerZoom(1f)
        assertEquals(EstadoEditor.ZOOM_MIN, e.dpPorSegundo, 0f)
        e.ponerZoom(300f)
        assertEquals(300f, e.dpPorSegundo, 0f)

        e.zoomFino = true
        assertEquals(EstadoEditor.ZOOM_FINO_MAX, e.zoomMaximo, 0f)
        e.ponerZoom(5_000f)
        assertEquals(5_000f, e.dpPorSegundo, 0f)
        e.ponerZoom(1e9f)
        assertEquals(EstadoEditor.ZOOM_FINO_MAX, e.dpPorSegundo, 0f)
        // 1 ms = 60 dp en el maximo
        assertEquals(60f, EstadoEditor.ZOOM_FINO_MAX / 1_000f, 0f)
        // Un valor sin sentido no cambia nada
        e.ponerZoom(Float.NaN)
        assertEquals(EstadoEditor.ZOOM_FINO_MAX, e.dpPorSegundo, 0f)
    }

    @Test fun volverAlZoomNormal() {
        val e = EstadoEditor(proyecto())
        e.zoomFino = true
        e.ponerZoom(20_000f)
        e.zoomNormal()
        assertFalse(e.zoomFino)
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
        // Si no estaba mas alla del limite, se queda como estaba
        e.zoomFino = true
        e.ponerZoom(120f)
        e.zoomNormal()
        assertEquals(120f, e.dpPorSegundo, 0f)
        // Ya sin fino, el maximo vuelve a ser el normal
        e.ponerZoom(20_000f)
        assertEquals(EstadoEditor.ZOOM_MAX, e.dpPorSegundo, 0f)
    }

    @Test fun pedirUnEfectoElegido() {
        val e = EstadoEditor(proyecto())
        assertNull(e.efectoAElegir)
        e.efectoAElegir = 7L
        assertEquals(7L, e.efectoAElegir)
        // No es parte del proyecto ni del historial
        assertEquals(proyecto(), e.proyecto)
        assertFalse(e.puedeDeshacer)
    }
}
