package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.AjustesEfecto
import com.royermethod.app.editor.modelo.AnimacionEnEfecto
import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.MomentoAnimacion
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.conIdsEfectos
import com.royermethod.app.editor.ui.TramosEfecto.Agarre
import com.royermethod.app.editor.ui.TramosEfecto.Barra
import com.royermethod.app.editor.ui.TramosEfecto.Rotulo
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Las cuentas del panel de efectos: franja, arrastre, campos de tiempo e ids. */
class TramosEfectoTest {

    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, 4_000_000, 30f)
    private val medidor = Medidor { _, _ -> 1080f to 1920f }
    private val dur = 4_000_000L
    private val f1 = Linea.tiempoDe(1)

    private fun ef(tipo: TipoEfectoVideo, ini: Long, fin: Long, id: Long = 0) = EfectoVideo(tipo, ini, fin, id = id)

    /** Un efecto con todo lo que tiene reloj: puntos, rapidez, desfase negativo, base, fundidos. */
    private fun conReloj() = EfectoVideo(
        TipoEfectoVideo.MOVER, 1_000_000, 1_600_000, intensidad = 0.8f, rapidez = 1.7f, desfaseUs = -33_333,
        fundidoEntradaUs = 100_000, fundidoSalidaUs = 150_000, duracionBaseUs = 900_000, id = 4,
        progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(250_000, 0.7f), ProgresoEfecto(800_000, 1f)),
        ajustes = CatalogoEfectos.ajustesDe(TipoEfectoVideo.MOVER).copy(curva = Curva.REBOTE, angulo = 33f)
    )

    // ---- Carriles ----

    @Test
    fun losQueNoSeSolapanCompartenCarrilYLosSolapadosNo() {
        val e = listOf(
            ef(TipoEfectoVideo.SACUDIDA, 0, 1_000_000),
            ef(TipoEfectoVideo.FLASH, 1_000_000, 2_000_000),
            ef(TipoEfectoVideo.ZOOM, 200_000, 400_000),
            ef(TipoEfectoVideo.GIRAR, 500_000, 900_000),
            ef(TipoEfectoVideo.RGB, 300_000, 600_000)
        )
        val c = TramosEfecto.carriles(e)
        // Lo que empieza justo al acabar otro va en su carril (los tramos son [inicio, fin))
        assertEquals(c[0], c[1])
        assertEquals(0, c[0])
        // Zoom y Girar no se tocan: comparten; RGB pisa a los dos y va aparte
        assertEquals(c[2], c[3])
        assertNotEquals(c[2], c[4])
        assertEquals(3, TramosEfecto.visibles(c))
    }

    @Test
    fun elRepartoNuncaJuntaSolapadosYUsaTantosCarrilesComoEfectosALaVez() {
        val r = Random(7)
        repeat(300) {
            val n = r.nextInt(1, 12)
            val e = List(n) {
                val a = r.nextLong(0, dur - 1)
                ef(TipoEfectoVideo.values()[r.nextInt(TipoEfectoVideo.values().size)], a, (a + r.nextLong(1, 1_500_000)).coerceAtMost(dur))
            }
            val c = TramosEfecto.carriles(e)
            for (i in e.indices) for (j in e.indices) {
                if (i < j && e[i].inicioUs < e[j].finUs && e[j].inicioUs < e[i].finUs) assertNotEquals("$e", c[i], c[j])
            }
            // El mayor numero de efectos a la vez: siempre en el inicio de alguno
            val aLaVez = e.maxOf { a -> e.count { it.inicioUs <= a.inicioUs && a.inicioUs < it.finUs } }
            assertEquals(aLaVez, (c.maxOrNull() ?: 0) + 1)
        }
    }

    @Test
    fun conMuchosSolapesSeCompactaEnPocosCarriles() {
        val e = List(7) { ef(TipoEfectoVideo.SACUDIDA, it * 10_000L, 2_000_000) }
        val c = TramosEfecto.carriles(e)
        assertEquals((0..6).toList(), c.toList())
        assertEquals(TramosEfecto.MAX_CARRILES, TramosEfecto.visibles(c))
        assertTrue(c.all { TramosEfecto.carrilVisible(it) in 0 until TramosEfecto.MAX_CARRILES })
        assertEquals(1, TramosEfecto.visibles(TramosEfecto.carriles(emptyList())))
    }

    // ---- De tiempo a pixeles y vuelta ----

    @Test
    fun laFranjaVaDeCeroALaDuracionConMargenYVuelveAlMismoTiempo() {
        val ancho = 1000f
        val m = 20f
        assertEquals(m, TramosEfecto.x(0, dur, ancho, m), 0f)
        assertEquals(ancho - m, TramosEfecto.x(dur, dur, ancho, m), 0f)
        assertEquals(500f, TramosEfecto.x(dur / 2, dur, ancho, m), 1e-3f)
        // Fuera del bloque se queda en sus bordes
        assertEquals(m, TramosEfecto.x(-5, dur, ancho, m), 0f)
        assertEquals(0L, TramosEfecto.tiempo(-50f, dur, ancho, m))
        assertEquals(dur, TramosEfecto.tiempo(5000f, dur, ancho, m))
        // Un pixel son (dur / 960) us: la vuelta cae a menos de medio pixel
        val pxUs = dur / (ancho - 2 * m)
        for (t in listOf(0L, 1L, 33_333L, 1_234_567L, 3_999_999L, dur)) {
            val vuelta = TramosEfecto.tiempo(TramosEfecto.x(t, dur, ancho, m), dur, ancho, m)
            assertTrue("$t -> $vuelta", kotlin.math.abs(vuelta - t) <= pxUs)
        }
        assertEquals(dur / 2, TramosEfecto.delta(480f, dur, ancho, m))
        assertEquals(-dur / 4, TramosEfecto.delta(-240f, dur, ancho, m))
        assertEquals(0L, TramosEfecto.delta(Float.NaN, dur, ancho, m))
        assertEquals(0L, TramosEfecto.tiempo(300f, 0, ancho, m))
    }

    // ---- Tocar y agarrar ----

    @Test
    fun losBordesRecortanYElCentroMueveInclusoEnBarrasEstrechas() {
        val asa = 40f
        assertEquals(Agarre.INICIO, TramosEfecto.agarre(95f, 100f, 400f, asa))
        assertEquals(Agarre.INICIO, TramosEfecto.agarre(130f, 100f, 400f, asa))
        assertEquals(Agarre.MOVER, TramosEfecto.agarre(250f, 100f, 400f, asa))
        assertEquals(Agarre.FIN, TramosEfecto.agarre(390f, 100f, 400f, asa))
        assertEquals(Agarre.FIN, TramosEfecto.agarre(435f, 100f, 400f, asa))
        assertNull(TramosEfecto.agarre(50f, 100f, 400f, asa))
        assertNull(TramosEfecto.agarre(450f, 100f, 400f, asa))
        // 6 px de ancho: hay centro para moverla y bordes para recortarla
        assertEquals(Agarre.MOVER, TramosEfecto.agarre(103f, 100f, 106f, asa))
        assertEquals(Agarre.INICIO, TramosEfecto.agarre(80f, 100f, 106f, asa))
        assertEquals(Agarre.FIN, TramosEfecto.agarre(130f, 100f, 106f, asa))
    }

    @Test
    fun tocarEligeLaDelCarrilDelDedoYEnVacioNinguna() {
        val barras = listOf(Barra(1, 0f, 300f, 0), Barra(2, 100f, 200f, 1), Barra(3, 250f, 500f, 2))
        val alto = 20f
        assertEquals(1L, TramosEfecto.tocada(barras, 150f, 5f, alto, 10f, null)?.id)
        assertEquals(2L, TramosEfecto.tocada(barras, 150f, 25f, alto, 10f, null)?.id)
        // En el carril 2 no hay nada en x = 150: la mas cercana es la del carril 1
        assertEquals(2L, TramosEfecto.tocada(barras, 150f, 45f, alto, 10f, null)?.id)
        assertNull(TramosEfecto.tocada(barras, 700f, 5f, alto, 10f, null))
        // A igual distancia gana la elegida
        assertEquals(3L, TramosEfecto.tocada(barras, 270f, 25f, alto, 10f, 3)?.id)
        assertEquals(1L, TramosEfecto.tocada(barras, 270f, 25f, alto, 10f, 1)?.id)
        assertNull(TramosEfecto.tocada(emptyList(), 0f, 0f, alto, 10f, null))
    }

    @Test
    fun laElegidaSeAgarraAunqueElDedoCaigaEnElCarrilDeAlLado() {
        val barras = listOf(Barra(1, 0f, 300f, 0), Barra(2, 100f, 200f, 1))
        val alto = 12f
        // El dedo en el carril 0, pero la elegida es la del 1
        assertEquals(2L to Agarre.MOVER, TramosEfecto.agarrada(barras, 150f, 6f, alto, 14f, 2)?.let { it.first.id to it.second })
        // Sin eleccion, la del carril del dedo
        assertEquals(1L, TramosEfecto.agarrada(barras, 150f, 6f, alto, 14f, null)?.first?.id)
        // Dos carriles mas alla ya no
        val tres = barras + Barra(3, 0f, 400f, 3)
        assertEquals(3L, TramosEfecto.agarrada(tres, 150f, 40f, alto, 14f, 1)?.first?.id)
        assertNull(TramosEfecto.agarrada(barras, 900f, 6f, alto, 14f, 2))
    }

    @Test
    fun elNombreVaDentroSiCabeSiNoALaDerechaSiHayHueco() {
        assertEquals(Rotulo.DENTRO, TramosEfecto.rotulo(40f, 60f, 8f, 0f, 18f))
        assertEquals(Rotulo.FUERA, TramosEfecto.rotulo(40f, 30f, 8f, 100f, 18f))
        assertEquals(Rotulo.CORTADO, TramosEfecto.rotulo(80f, 50f, 8f, 20f, 18f))
        assertEquals(Rotulo.NADA, TramosEfecto.rotulo(80f, 20f, 4f, 20f, 18f))
        val barras = listOf(Barra(1, 0f, 100f, 0), Barra(2, 160f, 300f, 0), Barra(3, 120f, 200f, 1))
        assertEquals(60f, TramosEfecto.libreDerecha(barras, 0, 1000f), 0f)
        assertEquals(700f, TramosEfecto.libreDerecha(barras, 1, 1000f), 0f)
        assertEquals(800f, TramosEfecto.libreDerecha(barras, 2, 1000f), 0f)
    }

    // ---- Iman ----

    @Test
    fun elImanPegaAlFotogramaQueSeExportaYSinElAlMilisegundo() {
        // Un bloque que empieza a mitad de fotograma: el tiempo pegado cae en un cuadro de la linea
        val origen = 1_250_000L
        for (us in listOf(0L, 10_000L, 499_999L, 1_234_567L)) {
            val v = TramosEfecto.pegar(us, true, origen)
            assertEquals(Linea.tiempoDe(Linea.fotogramaEn(origen + v)), origen + v)
            assertTrue(kotlin.math.abs(v - us) <= f1 / 2 + 1)
        }
        assertEquals(1_235_000L, TramosEfecto.pegar(1_234_567L, false, origen))
        assertEquals(1_234_000L, TramosEfecto.pegar(1_234_499L, false, origen))
        assertEquals(0L, TramosEfecto.pegar(-7_000L, false, 0))
        assertEquals(0L, TramosEfecto.pegar(-7_000L, true, 0))
        // El cabezal sin iman se queda en su microsegundo
        assertEquals(1_234_567L, TramosEfecto.pegarCabezal(1_234_567L, false, origen))
        assertEquals(f1, TramosEfecto.duracionMinima(true, dur))
        assertEquals(1_000L, TramosEfecto.duracionMinima(false, dur))
        assertEquals(500L, TramosEfecto.duracionMinima(true, 500))
        // Uno escrito mas corto que el minimo no se obliga a crecer
        assertEquals(5_000L, TramosEfecto.minimo(ef(TipoEfectoVideo.FLASH, 0, 5_000), true, dur))
    }

    // ---- Arrastrar: mover ----

    @Test
    fun moverElTramoConservaDuracionRelojPuntosYFundidosYSeDesplazaExacto() {
        val base = conReloj()
        val d = 700_000L
        val m = TramosEfecto.arrastrar(base, Agarre.MOVER, d, dur, false, 0)
        assertEquals(base.inicioUs + d, m.inicioUs)
        assertEquals(base.duracionUs, m.duracionUs)
        assertEquals(base.copy(inicioUs = m.inicioUs, finUs = m.finUs), m)
        // Lo que pasaba en t pasa ahora en t + d, bit a bit
        var t = base.inicioUs - 50_000
        while (t < base.finUs + 50_000) {
            assertEquals(base.activoEn(t), m.activoEn(t + d))
            assertEquals(base.fuerzaEn(t), m.fuerzaEn(t + d), 0f)
            assertEquals(base.progresoEn(t), m.progresoEn(t + d), 0f)
            assertEquals(base.segundosEn(t), m.segundosEn(t + d), 0f)
            assertEquals(base.valorEn(t), m.valorEn(t + d), 0f)
            t += 3_331
        }
        // Puntos de progreso: los mismos y elegidos igual (el reloj no cambia)
        for (p in base.progresos) {
            assertEquals(base.localDelPunto(p) + d, m.localDelPunto(p))
            assertEquals(base.puntoEnFase(base.faseControl(base.localDelPunto(p))), m.puntoEnFase(m.faseControl(m.localDelPunto(p))))
        }
    }

    @Test
    fun moverEnElMotorDaElMismoCuadroCorridoYLosDemasNoCambian() {
        val otros = listOf(
            EfectoVideo(TipoEfectoVideo.SACUDIDA, 200_000, 3_000_000, id = 1),
            EfectoVideo(TipoEfectoVideo.CIRCULO, 0, 1_500_000, id = 2, ajustes = AjustesEfecto(centroX = 0.2f, grosor = 0.1f))
        )
        val base = conReloj()
        val clip = Clip(1, origen = video, efectos = otros + base)
        val d = -400_000L
        val m = TramosEfecto.arrastrar(base, Agarre.MOVER, d, dur, false, 0)
        val movido = clip.conEfecto(base.id) { m }
        assertEquals(otros, movido.efectos.take(2))
        val solo = Clip(1, origen = video, efectos = listOf(base))
        val soloMovido = Clip(1, origen = video, efectos = listOf(m))
        val escena = Escena(Proyecto(principal = listOf(solo)), 576, 1024, medidor)
        var t = base.inicioUs
        while (t < base.finUs) {
            assertEquals(escena.efectosVideo(solo, t), escena.efectosVideo(soloMovido, t + d))
            t += 7_919
        }
    }

    @Test
    fun moverSeQuedaDentroDelBloqueYConElImanEmpiezaEnUnCuadro() {
        val base = conReloj()
        assertEquals(0L, TramosEfecto.arrastrar(base, Agarre.MOVER, -5_000_000, dur, false, 0).inicioUs)
        val alFinal = TramosEfecto.arrastrar(base, Agarre.MOVER, 5_000_000, dur, false, 0)
        assertEquals(dur, alFinal.finUs)
        assertEquals(base.duracionUs, alFinal.duracionUs)
        val origen = 2_010_000L
        val pegado = TramosEfecto.arrastrar(base, Agarre.MOVER, 123_456, dur, true, origen)
        assertEquals(Linea.tiempoDe(Linea.fotogramaEn(origen + pegado.inicioUs)), origen + pegado.inicioUs)
        assertEquals(base.duracionUs, pegado.duracionUs)
        // Sin iman, al milisegundo
        assertEquals(0L, TramosEfecto.arrastrar(base, Agarre.MOVER, 123_456, dur, false, 0).inicioUs % 1_000)
        // Sin moverse, el mismo efecto
        assertSame(base, TramosEfecto.arrastrar(base, Agarre.MOVER, 0, dur, true, 0))
    }

    @Test
    fun unArrastreSeCalculaDesdeLaBaseIdaYVueltaLoDejaIgual() {
        val base = conReloj().copy(inicioUs = 1_000_123, finUs = 1_600_123)
        for (agarre in Agarre.values()) for (fotogramas in listOf(false, true)) {
            var ultimo = base
            for (dx in listOf(10_000L, 250_000L, -900_000L, 40_000L)) ultimo = TramosEfecto.arrastrar(base, agarre, dx, dur, fotogramas, 0)
            assertNotEquals(base, ultimo)
            assertEquals(base, TramosEfecto.arrastrar(base, agarre, 0, dur, fotogramas, 0))
        }
    }

    // ---- Arrastrar: recortar ----

    @Test
    fun recortarUnBordeDejaElRelojQuietoYElMinimo() {
        val base = conReloj()
        val antes = TramosEfecto.arrastrar(base, Agarre.INICIO, -200_000, dur, false, 0)
        assertEquals(800_000L, antes.inicioUs)
        assertEquals(base.finUs, antes.finUs)
        // El reloj no se mueve: el desfase puede quedar negativo (no se acota)
        assertEquals(base.desfaseUs - 200_000, antes.desfaseUs)
        val despues = TramosEfecto.arrastrar(base, Agarre.FIN, 900_000, dur, false, 0)
        assertEquals(2_500_000L, despues.finUs)
        // La base crece para que el fundido de salida y el progreso lleguen al nuevo borde
        assertEquals(maxOf(base.duracionBaseUs, despues.desfaseUs + despues.duracionUs), despues.duracionBaseUs)
        for (e in listOf(antes, despues)) {
            var t = base.inicioUs
            while (t < base.finUs) {
                assertEquals(base.progresoEn(t), e.progresoEn(t), 0f)
                assertEquals(base.segundosEn(t), e.segundosEn(t), 0f)
                t += 9_973
            }
        }
        // Acotados: nunca antes de 0, nunca pasado el bloque, al menos el minimo
        assertEquals(0L, TramosEfecto.arrastrar(base, Agarre.INICIO, -9_000_000, dur, false, 0).inicioUs)
        assertEquals(dur, TramosEfecto.arrastrar(base, Agarre.FIN, 9_000_000, dur, false, 0).finUs)
        assertEquals(1_000L, TramosEfecto.arrastrar(base, Agarre.INICIO, 9_000_000, dur, false, 0).duracionUs)
        assertEquals(1_000L, TramosEfecto.arrastrar(base, Agarre.FIN, -9_000_000, dur, false, 0).duracionUs)
        assertEquals(f1, TramosEfecto.arrastrar(base, Agarre.FIN, -9_000_000, dur, true, 0).duracionUs)
    }

    @Test
    fun recortarElUltimoFotogramaDeUnBloqueAceleradoNoLanza() {
        // Como en ControlesEfectoTest: al final de un bloque de 1.5 s hay menos de un fotograma libre
        val corto = 1_500_000L
        val e = EfectoVideo(TipoEfectoVideo.FLASH, 1_483_333, corto)
        for (a in Agarre.values()) for (d in listOf(-50_000L, -1L, 1L, 50_000L)) {
            val r = TramosEfecto.arrastrar(e, a, d, corto, true, 0)
            assertTrue(r.inicioUs >= 0 && r.finUs <= corto && r.finUs > r.inicioUs)
        }
        // Un efecto roto (fin <= inicio) no se toca
        val roto = EfectoVideo(TipoEfectoVideo.FLASH, 500, 500)
        assertSame(roto, TramosEfecto.arrastrar(roto, Agarre.FIN, 10_000, corto, false, 0))
        assertSame(roto, TramosEfecto.conRango(roto, 0, 0))
    }

    // ---- Campos de tiempo ----

    @Test
    fun inicioMueveDuracionYFinRecortanElFinalYRecortarInicioElPrincipio() {
        val base = conReloj()
        val m = 1_000L
        // Inicio = mover entero, tambien a 0,005 s exactos
        val inicio = TramosEfecto.conInicio(base, 5_000, dur)
        assertEquals(5_000L, inicio.inicioUs)
        assertEquals(base.duracionUs, inicio.duracionUs)
        assertEquals(base.desfaseUs, inicio.desfaseUs)
        assertEquals(dur - base.duracionUs, TramosEfecto.conInicio(base, 9_000_000, dur).inicioUs)
        assertEquals(0L, TramosEfecto.conInicio(base, -10, dur).inicioUs)
        // Duracion: el principio se queda y el reloj tambien
        val largo = TramosEfecto.conDuracion(base, 1_234_567, dur, m)
        assertEquals(base.inicioUs, largo.inicioUs)
        assertEquals(1_234_567L, largo.duracionUs)
        assertEquals(base.desfaseUs, largo.desfaseUs)
        assertEquals(dur, TramosEfecto.conDuracion(base, 99_000_000, dur, m).finUs)
        assertEquals(m, TramosEfecto.conDuracion(base, 0, dur, m).duracionUs)
        assertEquals(1_001_000L, TramosEfecto.conFin(base, 0, dur, m).finUs)
        // Recortar inicio: el final se queda y el reloj tambien
        val recortado = TramosEfecto.conInicioRecortado(base, 1_200_000, m)
        assertEquals(1_200_000L, recortado.inicioUs)
        assertEquals(base.finUs, recortado.finUs)
        assertEquals(base.progresoEn(1_300_000), recortado.progresoEn(1_300_000), 0f)
        assertEquals(base.finUs - m, TramosEfecto.conInicioRecortado(base, 9_000_000, m).inicioUs)
        val todo = TramosEfecto.todoElBloque(base, dur)
        assertEquals(0L to dur, todo.inicioUs to todo.finUs)
        assertEquals(base.progresoEn(1_300_000), todo.progresoEn(1_300_000), 0f)
    }

    // ---- Ids y acciones sobre el bloque ----

    @Test
    fun enUnProyectoViejoLosIdsSonSiempreLosMismosYNoSeEscribenSinCambios() {
        val viejo = Clip(1, origen = video, efectos = listOf(
            ef(TipoEfectoVideo.SACUDIDA, 0, 500_000), ef(TipoEfectoVideo.FLASH, 100_000, 400_000), ef(TipoEfectoVideo.RGB, 0, 900_000)
        ))
        val vista = viejo.conIdsEfectos()
        assertEquals(vista, viejo.conIdsEfectos())
        assertEquals(listOf(1L, 2L, 3L), vista.efectos.map { it.id })
        // Cambiar nada no escribe ids ni gasta un paso
        assertSame(viejo, viejo.conEfecto(2) { it })
        assertSame(viejo, viejo.conEfecto(99) { it.copy(intensidad = 2f) })
        val cambiado = viejo.conEfecto(2) { it.copy(intensidad = 2f) }
        assertEquals(listOf(1L, 2L, 3L), cambiado.efectos.map { it.id })
        assertEquals(2f, cambiado.efectos[1].intensidad, 0f)
        assertEquals(viejo.efectos[0], cambiado.efectos[0].copy(id = 0))
        assertEquals(viejo.efectos[2], cambiado.efectos[2].copy(id = 0))
        // El cambio no puede tocar el id
        assertEquals(2L, viejo.conEfecto(2) { it.copy(id = 50, rapidez = 2f) }.efectos[1].id)
    }

    @Test
    fun agregarDaIdNuevoConLosAjustesDeSuTipo() {
        val c = Clip(1, origen = video, efectos = listOf(ef(TipoEfectoVideo.SACUDIDA, 0, 500_000, id = 7)))
        val (n, id) = c.conEfectoNuevo(TipoEfectoVideo.LATIGAZO, 1_000_000)
        assertEquals(8L, id)
        assertEquals(CatalogoEfectos.nuevo(TipoEfectoVideo.LATIGAZO, 1_000_000, dur, 8), n.efectos.last())
        assertEquals(c.efectos, n.efectos.dropLast(1))
        // Al final del bloque se adelanta para caber entero
        val (alFinal, _) = c.conEfectoNuevo(TipoEfectoVideo.ZOOM, dur)
        assertEquals(dur, alFinal.efectos.last().finUs)
    }

    @Test
    fun unaCombinacionSonEfectosSueltosDeUnGrupoQueSeEditanPorSeparado() {
        val c = Clip(1, origen = video, efectos = listOf(ef(TipoEfectoVideo.SACUDIDA, 0, 500_000)))
        val latigazo = CatalogoEfectos.combinaciones.first { it.nombre == "Latigazo con desenfoque" }
        val (n, primero) = c.conCombinacion(latigazo, 1_000_000)
        val nuevos = n.efectos.drop(1)
        assertEquals(listOf(TipoEfectoVideo.LATIGAZO, TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, TipoEfectoVideo.BORDES_ESPEJO), nuevos.map { it.tipo })
        assertEquals(nuevos.first().id, primero)
        assertEquals(1, nuevos.map { it.grupo }.toSet().size)
        assertTrue(nuevos.first().grupo.isNotEmpty())
        assertEquals(1, TramosEfecto.vecesCombinacion(n.efectos, latigazo))
        assertEquals(2, TramosEfecto.vecesCombinacion(n.conCombinacion(latigazo, 0).first.efectos, latigazo))
        // Cambiar o quitar uno no toca a los demas
        val desenfoque = nuevos[1].id
        val cambiado = n.conEfecto(desenfoque) { TramosEfecto.conInicio(it, 2_000_000, dur) }
        assertEquals(nuevos[0], cambiado.efectos[1])
        assertEquals(nuevos[2], cambiado.efectos[3])
        val sin = n.sinElEfecto(desenfoque)
        assertEquals(n.efectos.filter { it.id != desenfoque }, sin.efectos)
        assertSame(n, n.sinElEfecto(999))
        // Separar solo le quita el grupo
        val separado = n.separadoDelGrupo(desenfoque)
        assertEquals(nuevos[1].copy(grupo = ""), separado.efectos[2])
        // Mover el grupo: todos el mismo tiempo, el resto quieto
        val movido = n.conGrupoMovido(nuevos[2].id, 500_000)
        assertEquals(n.efectos[0], movido.efectos[0])
        nuevos.forEachIndexed { i, e -> assertEquals(e.desplazado(500_000, dur), movido.efectos[i + 1]) }
        assertEquals(1_000_000L, n.inicioDelGrupo(desenfoque))
        assertSame(n, n.conGrupoMovido(n.efectos[0].id, 500_000))
    }

    @Test
    fun duplicarHaceUnaCopiaSueltaJustoDespues() {
        val c = Clip(1, origen = video, efectos = listOf(conReloj().copy(grupo = "Algo 1")))
        val (n, id) = c.conDuplicado(4)!!
        val copia = n.efectos.last()
        assertEquals(5L, id)
        assertEquals(id, copia.id)
        assertEquals("", copia.grupo)
        assertEquals(c.efectos[0].finUs, copia.inicioUs)
        assertEquals(c.efectos[0].copy(id = 5, grupo = "", inicioUs = copia.inicioUs, finUs = copia.finUs), copia)
        assertNull(c.conDuplicado(99))
    }

    @Test
    fun alAbrirSaleElPedidoOElDeEncimaDelCabezal() {
        val e = listOf(ef(TipoEfectoVideo.SACUDIDA, 0, 1_000_000, 1), ef(TipoEfectoVideo.FLASH, 500_000, 800_000, 2),
            ef(TipoEfectoVideo.RGB, 2_000_000, 3_000_000, 3))
        assertEquals(2L, TramosEfecto.elegidoAlAbrir(e, 600_000))
        assertEquals(1L, TramosEfecto.elegidoAlAbrir(e, 900_000))
        assertEquals(3L, TramosEfecto.elegidoAlAbrir(e, 1_500_000))
        assertEquals(1L, TramosEfecto.elegidoAlAbrir(e, 3_500_000))
        assertEquals(1L, TramosEfecto.elegidoAlAbrir(e, null))
        assertEquals(3L, TramosEfecto.elegidoAlAbrir(e, 600_000, pedido = 3))
        assertEquals(2L, TramosEfecto.elegidoAlAbrir(e, 600_000, pedido = 42))
        assertNull(TramosEfecto.elegidoAlAbrir(emptyList(), 0, pedido = 1))
        assertEquals(1L, TramosEfecto.vecinoAlQuitar(e, 2))
        assertEquals(2L, TramosEfecto.vecinoAlQuitar(e, 1))
        assertNull(TramosEfecto.vecinoAlQuitar(e.take(1), 1))
    }

    @Test
    fun nombresYMarcasDelCatalogo() {
        val anim = EfectoVideo(TipoEfectoVideo.ANIMACION, 0, 500_000,
            animacion = AnimacionEnEfecto("Zoom que entra", MomentoAnimacion.ENTRADA, emptyList(), emptyList()))
        assertEquals("Zoom que entra", TramosEfecto.nombre(anim))
        assertEquals("Animacion", TramosEfecto.nombre(anim.copy(animacion = null)))
        assertEquals("Rastro", TramosEfecto.nombreCorto(ef(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 1)))
        val cuantos = TramosEfecto.cuantosPorTipo(listOf(ef(TipoEfectoVideo.SACUDIDA, 0, 1), ef(TipoEfectoVideo.SACUDIDA, 0, 1), ef(TipoEfectoVideo.RGB, 0, 1)))
        assertEquals(mapOf(TipoEfectoVideo.SACUDIDA to 2, TipoEfectoVideo.RGB to 1), cuantos)
        // Todos los tipos del catalogo tienen nombre corto no vacio
        CatalogoEfectos.grupos.flatMap { it.second }.forEach { assertTrue(TramosEfecto.nombreCorto(ef(it, 0, 1)).isNotBlank()) }
        assertArrayEquals(intArrayOf(), TramosEfecto.carriles(emptyList()))
    }
}
