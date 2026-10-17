package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.AnimacionAplicada
import com.royermethod.app.editor.modelo.Animacion
import com.royermethod.app.editor.modelo.AnimacionContinua
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Cuadros
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.MomentoAnimacion
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.duracionSugeridaUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que hace el panel "Animar" (LogicaAnimacion) sobre un EstadoEditor de
 * verdad: lista de puestas, sumar y sustituir, cabezal al aplicar, ajustes
 * que no lo mueven, pasos de deshacer y convertir en efecto.
 */
class PanelAnimacionLogicaTest {

    private val video = Origen("content://v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 4_000_000, fps = 30f)

    private fun comb(nombre: String) = Combinaciones.entrada.first { it.nombre == nombre }
    private fun opcion(nombre: String, momento: MomentoAnimacion = MomentoAnimacion.ENTRADA) =
        LogicaAnimacion.Opcion(nombre, momento, piezas = comb(nombre).piezas)
    private fun siempre(nombre: String) =
        LogicaAnimacion.Opcion(nombre, MomentoAnimacion.SIEMPRE, continuas = Combinaciones.continuas.first { it.first == nombre }.second)

    /**
     * Dos bloques de 4 s seguidos; el segundo (el que se anima, empieza en el
     * segundo 4) trae lo de antes: entrada "Acercar", salida "Shake de camara"
     * y continua "Pulso", como un proyecto guardado con la app vieja.
     */
    private fun conHeredadas(): Pair<EstadoEditor, Long> {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video, video))
        val q = Edicion.cambiar(p, ids[1]) {
            it.copy(
                entrada = comb("Acercar").piezas, duracionEntradaUs = 500_000,
                salida = comb("Shake de camara").piezas, duracionSalidaUs = 400_000,
                continua = listOf(Continuo(AnimacionContinua.PULSO))
            )
        }
        val e = EstadoEditor(q)
        e.seleccion = Seleccion.Bloque(ids[1])
        return e to ids[1]
    }

    /** El segundo bloque sin ninguna animacion. */
    private fun sinNada(): Pair<EstadoEditor, Long> {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video, video))
        val e = EstadoEditor(p)
        e.seleccion = Seleccion.Bloque(ids[1])
        return e to ids[1]
    }

    private fun EstadoEditor.puestas() = LogicaAnimacion.aplicadas(clipElegido!!)

    @Test
    fun listaLasDeAntesYLasNuevasYLasCuentaTodas() {
        val (e, _) = conHeredadas()
        e.cambiarClip(false) { it.copy(animaciones = listOf(AnimacionAplicada(7, "Flash", MomentoAnimacion.ENTRADA, comb("Flash").piezas, duracionUs = 300_000))) }
        val l = e.puestas()
        assertEquals(listOf("Acercar", "Shake de camara", "Pulso", "Flash"), l.map { it.nombre })
        assertEquals(listOf(MomentoAnimacion.ENTRADA, MomentoAnimacion.SALIDA, MomentoAnimacion.SIEMPRE, MomentoAnimacion.ENTRADA), l.map { it.momento })
        // Ids deterministas: la nueva conserva el suyo y las de antes van detras
        assertEquals(listOf(8L, 9L, 10L, 7L), l.map { it.id })
        assertEquals(4, l.size)
        assertEquals("Entrada · 0.500 s", LogicaAnimacion.detalle(l[0]))
        assertEquals("Siempre · todo el bloque", LogicaAnimacion.detalle(l[2]))
        // Ensenarlas no cambia el bloque
        assertTrue(e.clipElegido!!.entrada.isNotEmpty())
        assertEquals(MomentoAnimacion.ENTRADA, LogicaAnimacion.momentoInicial(l))
    }

    @Test
    fun conMasSeSumaConservandoLasAnterioresYEnUnSoloPasoConLaMigracion() {
        val (e, _) = conHeredadas()
        val antes = e.proyecto
        val acercar = e.puestas().first()
        val r = LogicaAnimacion.aplicar(e, opcion("Flash"), acercar.id, agregando = true, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.SUMADA, r.accion)
        val l = e.puestas()
        assertEquals(listOf("Acercar", "Shake de camara", "Pulso", "Flash"), l.map { it.nombre })
        // La de antes conserva su duracion; la nueva, la sugerida (flash: 0.3 s)
        assertEquals(500_000L, l[0].duracionUs)
        assertEquals(300_000L, l[3].duracionUs)
        assertEquals(r.elegidaId, l[3].id)
        // Las de antes ya estan en la lista (migradas en este mismo paso)
        val c = e.clipElegido!!
        assertTrue(c.entrada.isEmpty() && c.salida.isEmpty() && c.continua.isEmpty())
        e.deshacer()
        assertEquals(antes, e.proyecto)
        assertFalse(e.puedeDeshacer)
    }

    @Test
    fun migrarAlPrimerCambioNoCambiaLoQueSeVe() {
        val (e, id) = conHeredadas()
        val antes = e.proyecto
        LogicaAnimacion.aplicar(e, opcion("Flash"), null, agregando = true, fotogramas = true)
        val flash = e.puestas().last()
        // El mismo bloque sin migrar, con la nueva al final: tiene que verse igual bit a bit
        val sinMigrar = Edicion.cambiar(antes, id) { it.copy(animaciones = listOf(flash)) }
        Cuadros.iguales(sinMigrar, e.proyecto, "migrar al sumar")
    }

    @Test
    fun sinMasYConUnaElegidaLaSustituyeConservandoSuDuracion() {
        val (e, _) = conHeredadas()
        val acercar = e.puestas().first()
        // El usuario le habia dado su duracion
        LogicaAnimacion.cambiarDuracion(e, acercar.id, 733_333, arrastre = false)
        val conSuDuracion = e.proyecto
        val r = LogicaAnimacion.aplicar(e, opcion("Zoom rapido"), acercar.id, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.SUSTITUIDA, r.accion)
        assertEquals(acercar.id, r.elegidaId)
        val l = e.puestas()
        assertEquals(3, l.size)
        val nueva = l.first { it.id == acercar.id }
        assertEquals("Zoom rapido", nueva.nombre)
        assertEquals(comb("Zoom rapido").piezas, nueva.piezas)
        assertEquals(MomentoAnimacion.ENTRADA, nueva.momento)
        assertEquals(733_333L, nueva.duracionUs)
        // Las demas, intactas
        assertEquals(listOf("Shake de camara", "Pulso"), l.filter { it.id != acercar.id }.map { it.nombre })
        e.deshacer()
        assertEquals(conSuDuracion, e.proyecto)
    }

    @Test
    fun sinNingunaElegidaLaSumaConLaDuracionSugerida() {
        val (e, _) = sinNada()
        val r = LogicaAnimacion.aplicar(e, opcion("Zoom rapido"), null, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.SUMADA, r.accion)
        val a = e.puestas().single()
        assertEquals("Zoom rapido", a.nombre)
        assertEquals(duracionSugeridaUs(a.piezas, MomentoAnimacion.ENTRADA, 4_000_000), a.duracionUs)
        assertEquals(500_000L, a.duracionUs)
        // En la pestana Salida no hay elegida: tocar una la suma
        val s = LogicaAnimacion.aplicar(e, opcion("Shake de camara", MomentoAnimacion.SALIDA), a.id, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.SUMADA, s.accion)
        assertEquals(listOf(MomentoAnimacion.ENTRADA, MomentoAnimacion.SALIDA), e.puestas().map { it.momento })
        assertEquals(400_000L, e.puestas()[1].duracionUs)
    }

    @Test
    fun tocarUnaYaPuestaLaEligeYTocarLaElegidaNoCambiaNada() {
        val (e, _) = sinNada()
        LogicaAnimacion.aplicar(e, opcion("Acercar"), null, agregando = false, fotogramas = true)
        val flash = LogicaAnimacion.aplicar(e, opcion("Flash"), null, agregando = true, fotogramas = true)!!.elegidaId
        val acercar = e.puestas().first().id
        val p = e.proyecto
        val r = LogicaAnimacion.aplicar(e, opcion("Acercar"), flash, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.ELEGIDA, r.accion)
        assertEquals(acercar, r.elegidaId)
        assertSame(p, e.proyecto)
        val otra = LogicaAnimacion.aplicar(e, opcion("Acercar"), acercar, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.REPETIDA, otra.accion)
        assertSame(p, e.proyecto)
        // Con + si se suma otra igual
        LogicaAnimacion.aplicar(e, opcion("Acercar"), acercar, agregando = true, fotogramas = true)
        assertEquals(listOf("Acercar", "Flash", "Acercar"), e.puestas().map { it.nombre })
    }

    /** Una vista previa que cuenta pausas y busquedas, como la de EditorScreen. */
    private class Vista(e: EstadoEditor) {
        var pausas = 0
        val busquedas = mutableListOf<Long>()
        init {
            e.alPausar = { pausas++ }
            e.alBuscar = { busquedas += it }
        }
    }

    @Test
    fun aplicarParaYDejaElCabezalAlComienzoDelBloque() {
        val (e, id) = conHeredadas()
        assertEquals(4_000_000L, e.inicioElegido)
        val v = Vista(e)
        val acciones = mutableListOf<LogicaAnimacion.Accion>()
        for ((o, mas) in listOf(opcion("Flash") to true, opcion("Flash") to false, opcion("Acercar") to false, opcion("Zoom rapido") to false)) {
            e.irA(6_543_210)
            e.reproduciendo = true
            val pausas = v.pausas
            val r = LogicaAnimacion.aplicar(e, o, e.puestas().lastOrNull { it.momento == MomentoAnimacion.ENTRADA }?.id, mas, fotogramas = true)!!
            acciones += r.accion
            assertFalse("${r.accion}", e.reproduciendo)
            assertEquals("${r.accion}", 4_000_000L, e.tiempo)
            assertEquals(4_000_000L, v.busquedas.last())
            assertTrue(v.pausas > pausas)
        }
        // Sumar, la misma otra vez, elegir una puesta y sustituir: todas paran al comienzo
        assertEquals(
            listOf(LogicaAnimacion.Accion.SUMADA, LogicaAnimacion.Accion.REPETIDA, LogicaAnimacion.Accion.ELEGIDA, LogicaAnimacion.Accion.SUSTITUIDA),
            acciones
        )
        assertEquals(id, (e.seleccion as Seleccion.Bloque).id)
    }

    @Test
    fun losAjustesNoMuevenElCabezalNiPisanLaDuracion() {
        val (e, _) = sinNada()
        val v = Vista(e)
        val id = LogicaAnimacion.aplicar(e, opcion("Acercar"), null, agregando = false, fotogramas = true)!!.elegidaId
        e.irA(4_250_000)
        val busquedas = v.busquedas.size
        val pausas = v.pausas
        LogicaAnimacion.cambiarDuracion(e, id, 900_000, arrastre = false)
        LogicaAnimacion.cambiarPiezas(e, id, e.puestas().single().piezas.map { it.copy(intensidad = 2f) }, arrastre = false)
        e.reproduciendo = true
        LogicaAnimacion.cambiarDuracion(e, id, 933_333, arrastre = false)
        assertEquals(4_250_000L, e.tiempo)
        assertEquals(busquedas, v.busquedas.size)
        assertEquals(pausas, v.pausas)
        assertTrue(e.reproduciendo)
        e.reproduciendo = false
        // Ni retocar ni volver a tocar la elegida le cambian la duracion
        val a = e.puestas().single()
        assertEquals(933_333L, a.duracionUs)
        assertEquals(2f, a.piezas.first().intensidad)
        LogicaAnimacion.aplicar(e, opcion("Acercar"), id, agregando = false, fotogramas = true)
        assertEquals(933_333L, e.puestas().single().duracionUs)
        // La retocada sigue marcada en el catalogo, por su nombre
        assertEquals(LogicaAnimacion.Marca.ELEGIDA, LogicaAnimacion.marca(opcion("Acercar"), e.puestas(), e.puestas().single()))
    }

    @Test
    fun arrastrarLaDuracionEsUnSoloPaso() {
        val (e, _) = sinNada()
        val id = LogicaAnimacion.aplicar(e, opcion("Acercar"), null, agregando = false, fotogramas = true)!!.elegidaId
        val antes = e.proyecto
        e.empezarGesto()
        repeat(20) { i -> LogicaAnimacion.cambiarDuracion(e, id, 100_000L + i * 1_000L, arrastre = true) }
        e.terminarGesto()
        assertEquals(119_000L, e.puestas().single().duracionUs)
        e.deshacer()
        assertEquals(antes, e.proyecto)
        e.deshacer()
        assertTrue(e.puestas().isEmpty())
        assertFalse(e.puedeDeshacer)
    }

    @Test
    fun laDuracionInicialSePegaAlFotogramaSoloConElAjuste() {
        // Bloque de 0.9 s: la sugerida es la mitad, 0.45 s, que cae entre fotogramas
        val o = opcion("Acercar")
        assertEquals(466_666L, LogicaAnimacion.duracionInicialUs(o, 900_000, fotogramas = true))
        assertEquals(450_000L, LogicaAnimacion.duracionInicialUs(o, 900_000, fotogramas = false))
        assertEquals(500_000L, LogicaAnimacion.duracionInicialUs(o, 4_000_000, fotogramas = true))
        // Los golpes de luz son mas cortos que los desplazamientos
        assertEquals(300_000L, LogicaAnimacion.duracionInicialUs(opcion("Flash"), 4_000_000, fotogramas = true))
        // Siempre: todo el bloque
        assertEquals(900_000L, LogicaAnimacion.duracionInicialUs(siempre("Pulso"), 900_000, fotogramas = true))
        // Un bloque de menos de un fotograma: nunca mas que el bloque
        assertEquals(20_000L, LogicaAnimacion.duracionInicialUs(o, 20_000, fotogramas = true))
        assertEquals(Linea.tiempoDe(1) to 4_000_000L, LogicaAnimacion.limitesDuracion(4_000_000))
    }

    @Test
    fun convertirEsUnPasoQuitaLaAnimacionYAbreEfectosConElNuevoElegido() {
        val (e, _) = conHeredadas()
        // Un efecto de antes, de un proyecto viejo (sin id)
        e.cambiarClip { it.copy(efectos = listOf(EfectoVideo(TipoEfectoVideo.FLASH, 1_000_000, 1_500_000))) }
        val antes = e.proyecto
        val pasos = e.puedeDeshacer
        e.panel = Panel.ANIMACION
        val acercar = e.puestas().first()
        val ef = LogicaAnimacion.convertir(e, acercar.id)
        assertNotNull(ef)
        assertEquals(Panel.EFECTOS, e.panel)
        assertEquals(ef, e.efectoAElegir)
        val c = e.clipElegido!!
        val nuevo = c.efectos.single { it.id == ef }
        assertEquals(TipoEfectoVideo.ANIMACION, nuevo.tipo)
        assertEquals("Acercar", nuevo.animacion!!.nombre)
        assertEquals(0L to 500_000L, nuevo.inicioUs to nuevo.finUs)
        // Nunca dos copias: ni en la lista ni como entrada de antes
        assertTrue(c.entrada.isEmpty())
        assertEquals(listOf("Shake de camara", "Pulso"), LogicaAnimacion.aplicadas(c).map { it.nombre })
        // Los ids de los efectos no se repiten
        assertEquals(c.efectos.size, c.efectos.map { it.id }.toSet().size)
        e.deshacer()
        assertEquals(antes, e.proyecto)
        assertEquals(pasos, e.puedeDeshacer)
        e.rehacer()
        assertTrue(e.clipElegido!!.efectos.any { it.id == ef && it.tipo == TipoEfectoVideo.ANIMACION })
    }

    @Test
    fun loQueNoSePuedeConvertirNoCambiaNada() {
        val (e, _) = sinNada()
        e.cambiarClip { it.copy(animaciones = listOf(AnimacionAplicada(1, "Vacia", MomentoAnimacion.ENTRADA))) }
        val p = e.proyecto
        e.panel = Panel.ANIMACION
        val a = e.puestas().single()
        assertNotNull(LogicaAnimacion.motivoNoConvertible(a))
        assertNull(LogicaAnimacion.convertir(e, a.id))
        assertSame(p, e.proyecto)
        assertEquals(Panel.ANIMACION, e.panel)
        assertNull(e.efectoAElegir)
        // Una que hace algo si se puede
        assertNull(LogicaAnimacion.motivoNoConvertible(AnimacionAplicada(2, "Flash", MomentoAnimacion.ENTRADA, comb("Flash").piezas)))
        assertNull(LogicaAnimacion.motivoNoConvertible(AnimacionAplicada(3, "Pulso", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.PULSO)))))
    }

    @Test
    fun quitarYLaUltimaPiezaQuitaLaAnimacion() {
        val (e, _) = conHeredadas()
        val antes = e.proyecto
        val l = e.puestas()
        LogicaAnimacion.quitar(e, l[1].id)
        assertEquals(listOf("Acercar", "Pulso"), e.puestas().map { it.nombre })
        e.deshacer()
        assertEquals(antes, e.proyecto)
        // Sin piezas no queda una animacion vacia
        LogicaAnimacion.cambiarPiezas(e, l[0].id, emptyList(), arrastre = false)
        assertEquals(listOf("Shake de camara", "Pulso"), e.puestas().map { it.nombre })
        LogicaAnimacion.cambiarContinuas(e, l[2].id, emptyList(), arrastre = false)
        assertEquals(listOf("Shake de camara"), e.puestas().map { it.nombre })
        // Quitada la elegida, pasa a serlo la ultima de su momento (o ninguna)
        assertNull(LogicaAnimacion.elegida(e.puestas(), l[0].id, MomentoAnimacion.ENTRADA))
        assertEquals(l[1].id, LogicaAnimacion.elegida(e.puestas(), 999, MomentoAnimacion.SALIDA)!!.id)
    }

    @Test
    fun elCatalogoTieneLasCategoriasYMarcaLasPuestas() {
        assertEquals(Combinaciones.entradaPorGrupo.map { it.first }, LogicaAnimacion.grupos(MomentoAnimacion.ENTRADA).map { it.first })
        assertEquals(Combinaciones.entradaPorGrupo.map { it.first }, LogicaAnimacion.grupos(MomentoAnimacion.SALIDA).map { it.first })
        assertEquals(Combinaciones.continuasPorGrupo.map { it.first }, LogicaAnimacion.grupos(MomentoAnimacion.SIEMPRE).map { it.first })
        val mia = Combinaciones.Combinacion("Mia 1", listOf(Movimiento(Animacion.GIRO)))
        assertEquals("Mias", LogicaAnimacion.grupos(MomentoAnimacion.ENTRADA, mias = listOf(mia)).last().first)
        assertEquals(Combinaciones.entrada.size, LogicaAnimacion.grupos(MomentoAnimacion.ENTRADA).sumOf { it.second.size })

        val (e, _) = sinNada()
        LogicaAnimacion.aplicar(e, opcion("Acercar"), null, agregando = false, fotogramas = true)
        LogicaAnimacion.aplicar(e, opcion("Flash"), null, agregando = true, fotogramas = true)
        val l = e.puestas()
        val elegida = LogicaAnimacion.elegida(l, null, MomentoAnimacion.ENTRADA)
        assertEquals("Flash", elegida!!.nombre)
        assertEquals(LogicaAnimacion.Marca.ELEGIDA, LogicaAnimacion.marca(opcion("Flash"), l, elegida))
        assertEquals(LogicaAnimacion.Marca.PUESTA, LogicaAnimacion.marca(opcion("Acercar"), l, elegida))
        assertEquals(LogicaAnimacion.Marca.DISPONIBLE, LogicaAnimacion.marca(opcion("Zoom rapido"), l, elegida))
        // En Salida no cuentan las de entrada
        assertEquals(LogicaAnimacion.Marca.DISPONIBLE, LogicaAnimacion.marca(opcion("Acercar", MomentoAnimacion.SALIDA), l, null))
    }

    @Test
    fun elFiltroEnsenaUnaCategoriaYMarcaLasQueTienenPuestas() {
        val entrada = LogicaAnimacion.grupos(MomentoAnimacion.ENTRADA)
        assertEquals(entrada, LogicaAnimacion.filtrar(entrada, null))
        val zoom = LogicaAnimacion.filtrar(entrada, "Zoom")
        assertEquals(listOf("Zoom"), zoom.map { it.first })
        assertTrue(zoom.single().second.any { it.nombre == "Acercar" })
        // Una categoria de otra pestana (o que ya no existe, como "Mias" borradas): todas
        assertEquals(entrada, LogicaAnimacion.filtrar(entrada, "Al ritmo"))
        val siempre = LogicaAnimacion.grupos(MomentoAnimacion.SIEMPRE)
        assertEquals(listOf("Al ritmo"), LogicaAnimacion.filtrar(siempre, "Al ritmo").map { it.first })

        val (e, _) = sinNada()
        LogicaAnimacion.aplicar(e, opcion("Acercar"), null, agregando = false, fotogramas = true)
        LogicaAnimacion.aplicar(e, opcion("Flash"), null, agregando = true, fotogramas = true)
        LogicaAnimacion.aplicar(e, siempre("Pulso"), null, agregando = true, fotogramas = true)
        val l = e.puestas()
        assertEquals(setOf("Zoom", "Flash y color"), LogicaAnimacion.categoriasConPuestas(entrada, l))
        // Las de entrada no marcan la pestana Salida, y Siempre tiene las suyas
        assertEquals(emptySet<String>(), LogicaAnimacion.categoriasConPuestas(LogicaAnimacion.grupos(MomentoAnimacion.SALIDA), l))
        val pulso = siempre.first { (_, ops) -> ops.any { it.nombre == "Pulso" } }.first
        assertEquals(setOf(pulso), LogicaAnimacion.categoriasConPuestas(siempre, l))
    }

    @Test
    fun enSiempreSeSumaYSeSustituyeSinTocarLasDeEntrada() {
        val (e, _) = conHeredadas()
        val pulso = e.puestas().first { it.momento == MomentoAnimacion.SIEMPRE }
        val otra = Combinaciones.continuas.first { it.first != "Pulso" }
        val o = LogicaAnimacion.Opcion(otra.first, MomentoAnimacion.SIEMPRE, continuas = otra.second)
        val r = LogicaAnimacion.aplicar(e, o, pulso.id, agregando = false, fotogramas = true)!!
        assertEquals(LogicaAnimacion.Accion.SUSTITUIDA, r.accion)
        val l = e.puestas()
        assertEquals(listOf("Acercar", "Shake de camara", otra.first), l.map { it.nombre })
        assertEquals(otra.second, l[2].continuas)
        assertEquals(pulso.id, l[2].id)
        // Convertir la de Siempre: efecto de todo el bloque y la animacion se va
        e.panel = Panel.ANIMACION
        val antes = e.proyecto
        val ef = LogicaAnimacion.convertir(e, pulso.id)!!
        val c = e.clipElegido!!
        val efecto = c.efectos.single { it.id == ef }
        assertEquals(0L to c.duracionUs, efecto.inicioUs to efecto.finUs)
        assertEquals(listOf("Acercar", "Shake de camara"), LogicaAnimacion.aplicadas(c).map { it.nombre })
        e.deshacer()
        assertEquals(antes, e.proyecto)
    }
}
