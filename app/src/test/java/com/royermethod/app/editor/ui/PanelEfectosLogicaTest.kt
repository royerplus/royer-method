package com.royermethod.app.editor.ui

import androidx.compose.runtime.mutableStateOf
import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.conIdsEfectos
import com.royermethod.app.editor.ui.TramosEfecto.Agarre
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El panel de efectos con un estado del editor de verdad: lo que hace cada
 * accion al proyecto, cuantos pasos de deshacer gasta y que queda elegido.
 */
class PanelEfectosLogicaTest {

    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, 4_000_000, 30f)
    private val medidor = Medidor { _, _ -> 1080f to 1920f }
    /** El bloque 2 empieza a mitad de fotograma (3,01 s): el iman cuenta desde ahi. */
    private val inicio2 = 3_010_000L

    private var fotogramasAntes = true

    @Before fun guardar() { fotogramasAntes = AjustesEditor.ajustarFotogramas }
    @After fun restaurar() { AjustesEditor.ajustarFotogramas = fotogramasAntes }

    private fun proyecto(efectos: List<EfectoVideo>) = Proyecto(
        principal = listOf(Clip(1, origen = video, hastaUs = inicio2), Clip(2, origen = video, efectos = efectos)),
        siguienteId = 3
    )

    private fun tres(conIds: Boolean) = listOf(
        EfectoVideo(TipoEfectoVideo.SACUDIDA, 200_000, 1_000_000, fundidoSalidaUs = 200_000, id = if (conIds) 1 else 0),
        EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, 500_000, 1_100_000, rapidez = 1.5f, desfaseUs = -33_333,
            progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(300_000, 0.4f), ProgresoEfecto(900_000, 1f)), id = if (conIds) 2 else 0),
        EfectoVideo(TipoEfectoVideo.FLASH, 800_000, 2_000_000, id = if (conIds) 3 else 0)
    )

    private fun estado(p: Proyecto) = EstadoEditor(p).also { it.seleccion = Seleccion.Bloque(2) }

    private fun bloque(e: EstadoEditor): Clip = e.clipElegido!!

    private fun panel(e: EstadoEditor, eleccion: EleccionEfectos = EleccionEfectos(null, false)) =
        PanelFx(e, bloque(e).conIdsEfectos(), e.inicioElegido!!, eleccion, mutableStateOf<Long?>(null))

    /** Cuantos pasos se pueden deshacer (deshace todo y lo vuelve a hacer). */
    private fun pasos(e: EstadoEditor): Int {
        var n = 0
        while (e.puedeDeshacer) { e.deshacer(); n++ }
        repeat(n) { e.rehacer() }
        return n
    }

    @Test
    fun elBloqueEmpiezaAMitadDeFotograma() {
        val e = estado(proyecto(tres(true)))
        assertEquals(inicio2, e.inicioElegido)
        assertNotEquals(Linea.tiempoDe(Linea.fotogramaEn(inicio2)), inicio2)
    }

    // ---- Arrastres en la franja ----

    @Test
    fun unArrastreEnteroEsUnPasoDeDeshacerYSeCalculaDesdeLaBase() {
        val p0 = proyecto(tres(true))
        val e = estado(p0)
        val base = bloque(e).efectos[1]
        val a = ArrastreTramo(e, base, Agarre.MOVER, bloque(e).duracionUs, inicio2)
        a.empezar()
        for (dx in listOf(5_000L, 40_000L, 120_000L, 333_333L, 600_000L)) a.seguir(dx, true)
        // De vuelta al punto de partida: igual que al agarrarlo, sin restos de los pasos
        a.seguir(0, true)
        assertEquals(base, bloque(e).efectos[1])
        a.seguir(250_000, true)
        a.terminar()
        val movido = bloque(e).efectos[1]
        assertEquals(TramosEfecto.arrastrar(base, Agarre.MOVER, 250_000, bloque(e).duracionUs, true, inicio2), movido)
        assertEquals(base.duracionUs, movido.duracionUs)
        assertEquals(base.desfaseUs, movido.desfaseUs)
        assertEquals(base.progresos, movido.progresos)
        // Empieza en un fotograma que se exporta
        assertEquals(Linea.tiempoDe(Linea.fotogramaEn(inicio2 + movido.inicioUs)), inicio2 + movido.inicioUs)
        // Los demas no cambian
        assertEquals(p0.principal[1].efectos[0], bloque(e).efectos[0])
        assertEquals(p0.principal[1].efectos[2], bloque(e).efectos[2])
        assertEquals(1, pasos(e))
        e.deshacer()
        assertEquals(p0, e.proyecto)
    }

    @Test
    fun recortarEnLaFranjaTambienEsUnPasoYNuncaDejaTiemposNegativos() {
        AjustesEditor.ajustarFotogramas = false
        val p0 = proyecto(tres(true))
        val e = estado(p0)
        val base = bloque(e).efectos[0]
        val a = ArrastreTramo(e, base, Agarre.INICIO, bloque(e).duracionUs, inicio2)
        a.empezar()
        for (dx in listOf(-10_000L, -100_000L, -500_000L)) a.seguir(dx, AjustesEditor.ajustarFotogramas)
        a.terminar()
        val r = bloque(e).efectos[0]
        assertEquals(0L, r.inicioUs)
        assertEquals(base.finUs, r.finUs)
        // El reloj se queda: el desfase pasa a negativo y lo que habia sigue igual
        assertEquals(base.desfaseUs - 200_000, r.desfaseUs)
        assertEquals(base.segundosEn(600_000), r.segundosEn(600_000), 0f)
        assertEquals(1, pasos(e))
        // Otro arrastre es otro paso
        val b = ArrastreTramo(e, r, Agarre.FIN, bloque(e).duracionUs, inicio2)
        b.empezar(); b.seguir(1_000_000, false); b.terminar()
        assertEquals(2_000_000L, bloque(e).efectos[0].finUs)
        assertEquals(2, pasos(e))
        e.deshacer(); e.deshacer()
        assertEquals(p0, e.proyecto)
    }

    @Test
    fun enUnProyectoViejoSeEligeYSeMueveIgualSinCambiarLaImagen() {
        val p0 = proyecto(tres(false))
        val e = estado(p0)
        val pnl = panel(e)
        // Los ids son los de la vista: el segundo es el 2
        val base = pnl.vista.efectos[1]
        assertEquals(2L, base.id)
        val a = ArrastreTramo(e, base, Agarre.MOVER, pnl.dur, inicio2)
        a.empezar(); a.seguir(-300_000, false); a.terminar()
        val c = bloque(e)
        // Todos tienen ya id, en el mismo paso; los otros solo han ganado el id
        assertEquals(listOf(1L, 2L, 3L), c.efectos.map { it.id })
        assertEquals(p0.principal[1].efectos[0], c.efectos[0].copy(id = 0))
        assertEquals(p0.principal[1].efectos[2], c.efectos[2].copy(id = 0))
        assertEquals(200_000L, c.efectos[1].inicioUs)
        assertEquals(1, pasos(e))
        e.deshacer()
        assertEquals(p0, e.proyecto)
        // Los ids no cambian lo que se ve
        val viejo = p0.principal[1]
        val conIds = viejo.conIdsEfectos()
        val escena = Escena(p0, 576, 1024, medidor)
        var t = 0L
        while (t < viejo.duracionUs) {
            assertEquals(escena.efectosVideo(viejo, t), escena.efectosVideo(conIds, t))
            t += 16_667
        }
    }

    // ---- Agregar, quitar, duplicar ----

    @Test
    fun agregarPoneElEfectoEnElCabezalEnUnPasoYLoDejaElegido() {
        AjustesEditor.ajustarFotogramas = true
        val e = estado(proyecto(tres(true)))
        e.irA(inicio2 + 1_234_567)
        val eleccion = EleccionEfectos(null, true)
        panel(e, eleccion).agregar(TipoEfectoVideo.LATIGAZO)
        val nuevo = bloque(e).efectos.last()
        assertEquals(4L, nuevo.id)
        assertEquals(4L, eleccion.id)
        assertFalse(eleccion.catalogo)
        val desde = TramosEfecto.pegarCabezal(1_234_567, true, inicio2)
        assertEquals(CatalogoEfectos.nuevo(TipoEfectoVideo.LATIGAZO, desde, bloque(e).duracionUs, 4), nuevo)
        assertEquals(Linea.tiempoDe(Linea.fotogramaEn(inicio2 + nuevo.inicioUs)), inicio2 + nuevo.inicioUs)
        assertEquals(1, pasos(e))
        // Sin iman, en el microsegundo del cabezal
        AjustesEditor.ajustarFotogramas = false
        e.irA(inicio2 + 5_000)
        panel(e, eleccion).agregar(TipoEfectoVideo.FLASH)
        assertEquals(5_000L, bloque(e).efectos.last().inicioUs)
        assertEquals(5L, eleccion.id)
    }

    @Test
    fun unaCombinacionDejaElegidoSuPrimeroYSusPiezasSonIndependientes() {
        val e = estado(proyecto(tres(true)))
        e.irA(inicio2 + 2_000_000)
        val eleccion = EleccionEfectos(null, true)
        val sacudida = CatalogoEfectos.combinaciones.first { it.nombre == "Sacudida con desenfoque" }
        panel(e, eleccion).agregar(sacudida)
        val nuevos = bloque(e).efectos.drop(3)
        assertEquals(listOf(TipoEfectoVideo.SACUDIDA, TipoEfectoVideo.DESENFOQUE), nuevos.map { it.tipo })
        assertEquals(nuevos[0].id, eleccion.id)
        assertFalse(eleccion.catalogo)
        assertEquals(1, pasos(e))
        // La sacudida es la de siempre, sin tocar: solo su tramo, id y grupo
        val s = nuevos[0]
        assertEquals(EfectoVideo(TipoEfectoVideo.SACUDIDA, s.inicioUs, s.inicioUs + 500_000, id = s.id, grupo = s.grupo), s)
        // Se quita una sola y la otra sigue
        panel(e, eleccion).quitar(s.id)
        assertEquals(listOf(TipoEfectoVideo.DESENFOQUE), bloque(e).efectos.drop(3).map { it.tipo })
        assertEquals(3L, eleccion.id)
        assertEquals(2, pasos(e))
    }

    @Test
    fun conElCabezalFueraDelBloqueAgregarLoPoneEnElBordeYLlevaElCabezalAlli() {
        AjustesEditor.ajustarFotogramas = false
        val e = estado(proyecto(tres(true)))
        // En el bloque 1, antes del 2: el efecto va al principio del 2 y el cabezal con el
        e.irA(1_000_000)
        e.reproduciendo = true
        val eleccion = EleccionEfectos(null, true)
        panel(e, eleccion).agregar(TipoEfectoVideo.SACUDIDA)
        val nuevo = bloque(e).efectos.last()
        assertEquals(0L, nuevo.inicioUs)
        assertEquals(inicio2 + nuevo.inicioUs, e.tiempo)
        assertFalse(e.reproduciendo)
        assertEquals(1, pasos(e))
        // Con el cabezal dentro no se mueve
        val sacudida = CatalogoEfectos.combinaciones.first { it.nombre == "Sacudida con desenfoque" }
        e.irA(inicio2 + 2_000_000)
        panel(e, eleccion).agregar(sacudida)
        assertEquals(inicio2 + 2_000_000, e.tiempo)
        // Una combinacion con el cabezal fuera: al principio de su grupo
        e.irA(500_000)
        panel(e, eleccion).agregar(sacudida)
        val grupo = bloque(e).efectos.last().grupo
        assertEquals(inicio2 + bloque(e).efectos.filter { it.grupo == grupo }.minOf { it.inicioUs }, e.tiempo)
        assertEquals(3, pasos(e))
    }

    @Test
    fun quitarYDuplicarSonUnPasoCadaUnoYCambianLaEleccion() {
        val e = estado(proyecto(tres(true)))
        val eleccion = EleccionEfectos(2, false)
        panel(e, eleccion).duplicar(2)
        assertEquals(4L, eleccion.id)
        assertEquals(TipoEfectoVideo.REVELAR_HORIZONTAL, bloque(e).efectos.last().tipo)
        assertEquals(1_100_000L, bloque(e).efectos.last().inicioUs)
        panel(e, eleccion).quitar(4)
        assertEquals(3L, eleccion.id)
        panel(e, eleccion).quitar(1)
        assertEquals(2L, eleccion.id)
        assertEquals(listOf(2L, 3L), bloque(e).efectos.map { it.id })
        assertEquals(3, pasos(e))
    }

    // ---- Elegir ----

    @Test
    fun tocarEligeYLlevaElCabezalDentroSoloSiEstabaFuera() {
        AjustesEditor.ajustarFotogramas = false
        val e = estado(proyecto(tres(true)))
        e.irA(inicio2)
        e.reproduciendo = true
        val eleccion = EleccionEfectos(null, true)
        panel(e, eleccion).tocar(3)
        assertEquals(3L, eleccion.id)
        assertFalse(eleccion.catalogo)
        assertFalse(e.reproduciendo)
        val local = e.tiempo - inicio2
        assertTrue(local in 800_000 until 2_000_000)
        // Ya dentro: no se mueve
        e.irA(inicio2 + 900_000)
        panel(e, eleccion).tocar(3)
        assertEquals(inicio2 + 900_000, e.tiempo)
        // Vacio: nada elegido
        panel(e, eleccion).tocar(null)
        assertNull(eleccion.id)
        assertEquals(0, pasos(e))
    }

    @Test
    fun unPedidoDeOtroPanelEligeEseEfectoSiEstaEnElBloque() {
        val vista = proyecto(tres(true)).principal[1]
        val eleccion = EleccionEfectos(1, true)
        assertFalse(eleccion.atender(null, vista))
        assertFalse(eleccion.atender(99, vista))
        assertEquals(1L, eleccion.id)
        assertTrue(eleccion.catalogo)
        assertTrue(eleccion.atender(3, vista))
        assertEquals(3L, eleccion.id)
        assertFalse(eleccion.catalogo)
        // Un proyecto viejo: el id es el de la vista
        val viejo = proyecto(tres(false)).principal[1].conIdsEfectos()
        assertTrue(EleccionEfectos(null, false).atender(2, viejo))
    }

    // ---- Campos de tiempo ----

    @Test
    fun cadaCampoEsUnPasoYUnArrastreDeCampoTambien() {
        AjustesEditor.ajustarFotogramas = false
        val p0 = proyecto(tres(true))
        val e = estado(p0)
        val eleccion = EleccionEfectos(2, false)
        val dur = bloque(e).duracionUs
        // Inicio exacto a 0,005 s: se mueve entero
        panel(e, eleccion).cambiar { TramosEfecto.conInicio(it, 5_000, dur) }
        assertEquals(5_000L, bloque(e).efectos[1].inicioUs)
        assertEquals(600_000L, bloque(e).efectos[1].duracionUs)
        // Duracion de 0,001 s mas
        panel(e, eleccion).cambiar { TramosEfecto.conDuracion(it, 601_000, dur, 1_000) }
        assertEquals(606_000L, bloque(e).efectos[1].finUs)
        assertEquals(2, pasos(e))
        // Un deslizador entero (intensidad) es un paso
        e.empezarGesto()
        for (v in listOf(0.5f, 0.8f, 1.2f, 2f)) panel(e, eleccion).cambiar(true) { it.copy(intensidad = v) }
        e.terminarGesto()
        assertEquals(2f, bloque(e).efectos[1].intensidad, 0f)
        assertEquals(3, pasos(e))
        // Sin nada elegido no se cambia nada
        panel(e, EleccionEfectos(null, false)).cambiar { it.copy(intensidad = 0f) }
        assertEquals(3, pasos(e))
        repeat(3) { e.deshacer() }
        assertEquals(p0, e.proyecto)
    }
}
