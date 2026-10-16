package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EfectoVideoTest {
    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, 4_000_000, 30f)
    private val medidor = Medidor { _, _ -> 1080f to 1920f }

    private fun escena(c: Clip) = Escena(Proyecto(principal = listOf(c), fondo = Fondo.NEGRO), 576, 1024, medidor)

    @Test
    fun ampliarElFinExtiendeElFundidoYElProgresoHastaElNuevoBorde() {
        val flash = EfectoVideo(TipoEfectoVideo.FLASH, finUs = 500_000,
            fundidoSalidaUs = 250_000).conRango(0, 3_000_000)
        assertEquals(3_000_000L, flash.duracionBaseUs)
        assertEquals(1f, flash.fuerzaEn(2_500_000), 0f)
        assertEquals(0.5f, flash.fuerzaEn(2_875_000), 0f)
        val iris = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, finUs = 500_000)
            .conRango(0, 3_000_000)
        assertEquals(0.5f, iris.progresoEn(1_500_000), 0f)
        // Un efecto ya recortado conserva el tiempo recorrido en su nueva base.
        val recortado = flash.recortado(1_000_000, 3_000_000)!!.conRango(0, 4_000_000)
        assertEquals(5_000_000L, recortado.duracionBaseUs)
        assertEquals(1f, recortado.fuerzaEn(3_500_000), 0f)
        assertEquals(0.5f, recortado.fuerzaEn(3_875_000), 0f)
    }

    @Test
    fun moverInicioAtrasYAdelanteNoDesplazaElRelojNiLosPuntos() {
        val original = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 1_000_000, 3_000_000,
            duracionBaseUs = 2_000_000, fundidoEntradaUs = 200_000,
            progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(2_000_000, 1f)))
        val antes = original.conRango(966_667, original.finUs)
        assertEquals(-33_333L, antes.desfaseUs)
        for (t in listOf(1_000_000L, 1_050_000L, 2_500_000L)) {
            assertEquals(original.segundosEn(t), antes.segundosEn(t), 0f)
            assertEquals(original.progresoEn(t), antes.progresoEn(t), 0f)
            assertEquals(original.fuerzaEn(t), antes.fuerzaEn(t), 0f)
        }
        assertEquals(original, antes.conRango(original.inicioUs, original.finUs))
    }

    @Test
    fun unaLineaQueAcabaYUnIrisQueEmpiezaEnElMismoFotogramaNoMezclanSusBordes() {
        // Revela hasta 1 s y desde ahi cierra en circulo. Con desenfoque de
        // movimiento, el fotograma de 1 s tiene muestras de las dos.
        val c = Clip(1, origen = video, efectos = listOf(
            EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, 0, 1_000_000),
            EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 1_000_000, 2_000_000)))
        fun escenaConBlur() = Escena(Proyecto(principal = listOf(c), fondo = Fondo.NEGRO,
            salida = AjustesSalida(desenfoqueMovimiento = true)), 576, 1024, medidor)

        // Manda el iris: la linea casi abierta (borde ~288) no puede leerse
        // como un radio, que dejaria un circulo pequeno durante un fotograma.
        val iris = escenaConBlur().cuadroEn(1_002_000).clips.single()
        assertTrue(iris.mascara!!.radial)
        assertTrue(iris.bordes.size > 1)
        for (b in iris.bordes) assertTrue("radio $b", b > 500f)

        // Manda la linea: las muestras del iris no la cierran.
        val linea = escenaConBlur().cuadroEn(998_000).clips.single()
        assertFalse(linea.mascara!!.radial)
        for (b in linea.bordes) assertTrue("linea $b", b in 250f..300f)
    }

    @Test
    fun losEfectosSoloActuanEnSuIntervalo() {
        val efecto = EfectoVideo(TipoEfectoVideo.RGB, 1_000_000, 2_000_000)
        val c = Clip(1, origen = video, efectos = listOf(efecto))
        val escena = escena(c)
        assertEquals(Efecto(), escena.efectosVideo(c, 999_999))
        assertTrue(escena.efectosVideo(c, 1_000_000).rgb > 0f)
        assertEquals(Efecto(), escena.efectosVideo(c, 2_000_000))
        assertEquals(0f, escena.cuadroEn(500_000).clips.single().rgb, 0f)
        assertTrue(escena.cuadroEn(1_500_000).clips.single().rgb > 0f)
    }

    @Test
    fun intensidadCeroYDesactivarSonIdentidadParaTodosLosTipos() {
        for (tipo in TipoEfectoVideo.values()) {
            val cero = Clip(1, origen = video, efectos = listOf(EfectoVideo(tipo, finUs = 3_000_000, intensidad = 0f)))
            assertEquals(tipo.name, Efecto(), escena(cero).efectosVideo(cero, 500_000))
            val apagado = cero.copy(efectos = cero.efectos.map { it.copy(intensidad = 2f, activo = false) })
            assertEquals(tipo.name, Efecto(), escena(apagado).efectosVideo(apagado, 500_000))
        }
    }

    @Test
    fun rapidezCambiaElPatronSinMoverElTramo() {
        val e = EfectoVideo(TipoEfectoVideo.ZOOM_RITMO, 1_000_000, 3_000_000)
        val a = Clip(1, origen = video, efectos = listOf(e))
        val b = a.copy(efectos = listOf(e.copy(rapidez = 2f)))
        assertEquals(e.inicioUs, b.efectos.single().inicioUs)
        assertEquals(e.finUs, b.efectos.single().finUs)
        assertTrue(escena(a).efectosVideo(a, 1_100_000).escala > escena(b).efectosVideo(b, 1_100_000).escala)
    }

    @Test
    fun dividirConservaPatronYFundidosEnAmbasMitades() {
        val e = EfectoVideo(TipoEfectoVideo.SACUDIDA, 500_000, 3_500_000,
            fundidoEntradaUs = 1_000_000, fundidoSalidaUs = 1_000_000)
        val c = Clip(1, origen = video, efectos = listOf(e))
        val p = Proyecto(principal = listOf(c), siguienteId = 2)
        val q = Edicion.dividir(p, 1, 1_500_000)!!.first
        val a = q.principal[0]
        val b = q.principal[1]
        assertEquals(escena(c).efectosVideo(c, 1_250_000), escena(a).efectosVideo(a, 1_250_000))
        assertEquals(escena(c).efectosVideo(c, 1_750_000), escena(b).efectosVideo(b, 250_000))
        assertEquals(escena(c).efectosVideo(c, 3_000_000), escena(b).efectosVideo(b, 1_500_000))
        // La primera mitad no inventa un fundido de salida antes del corte.
        assertEquals(e.fuerzaEn(1_499_999), a.efectos.single().fuerzaEn(1_499_999), 0f)
    }

    @Test
    fun recortarConservaElPatronYDescartaLoQueNoSeVe() {
        val e = EfectoVideo(TipoEfectoVideo.GLITCH, 500_000, 3_500_000,
            fundidoEntradaUs = 1_000_000, fundidoSalidaUs = 1_000_000)
        val c = Clip(1, origen = video, efectos = listOf(e,
            EfectoVideo(TipoEfectoVideo.RGB, 0, 500_000)))
        val q = Edicion.recortar(Proyecto(principal = listOf(c)), 1, 1_000_000, 3_000_000)
        val recortado = q.principal.single()
        assertEquals(1, recortado.efectos.size)
        assertEquals(0L, recortado.efectos.single().inicioUs)
        assertEquals(2_000_000L, recortado.efectos.single().finUs)
        val original = c.copy(efectos = listOf(e))
        assertEquals(escena(original).efectosVideo(original, 1_250_000), escena(recortado).efectosVideo(recortado, 250_000))
        assertEquals(escena(original).efectosVideo(original, 2_750_000), escena(recortado).efectosVideo(recortado, 1_750_000))
    }

    @Test
    fun cambiarVelocidadEscalaIntervalosYFundidos() {
        val e = EfectoVideo(TipoEfectoVideo.RGB, 1_000_000, 3_000_000,
            rapidez = 1.7f, fundidoEntradaUs = 200_000, fundidoSalidaUs = 400_000)
        val c = Clip(1, origen = video, efectos = listOf(e)).conVelocidad(2f)
        val actual = c.efectos.single()
        assertEquals(500_000L, actual.inicioUs)
        assertEquals(1_500_000L, actual.finUs)
        assertEquals(100_000L, actual.fundidoEntradaUs)
        assertEquals(200_000L, actual.fundidoSalidaUs)
        assertEquals(1.7f, actual.rapidez, 0f)
    }

    @Test
    fun losEfectosSeCombinanEnLaSalidaRealDelMotor() {
        val c = Clip(1, origen = video, efectos = listOf(
            EfectoVideo(TipoEfectoVideo.FLASH, finUs = 3_000_000),
            EfectoVideo(TipoEfectoVideo.RGB, finUs = 3_000_000),
            EfectoVideo(TipoEfectoVideo.DESENFOQUE, finUs = 3_000_000)))
        val paso = escena(c).cuadroEn(100_000).clips.single()
        assertTrue(paso.luz > 0f)
        assertTrue(paso.rgb > 0f)
        assertTrue(paso.desenfoque > 0f)
    }

    @Test
    fun elBitrateSoloSubeMientrasHayEfectoActivo() {
        val c = Clip(1, texto = Texto("foto"), hastaUs = 4_000_000,
            efectos = listOf(EfectoVideo(TipoEfectoVideo.SACUDIDA, 1_000_000, 2_000_000)))
        val tramos = Linea.tramos(Proyecto(principal = listOf(c)), normalizar = false)
        assertEquals(listOf(0L, 1_000_000L, 2_000_000L), tramos.map { it.desdeUs })
        assertTrue(tramos[1].factor > tramos[0].factor)
        assertEquals(tramos[0].factor, tramos[2].factor, 0f)
        val sinEfecto = c.copy(efectos = c.efectos.map { it.copy(activo = false) })
        assertEquals(1, Linea.tramos(Proyecto(principal = listOf(sinEfecto))).size)
        assertFalse(c.efectos.single().activoEn(2_000_000))
    }

    @Test
    fun losIrisLleganALaMascaraYConservanProgresoAlRecortar() {
        for (tipo in listOf(TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.CIRCULO_ABRIR)) {
            val c = Clip(1, origen = video, efectos = listOf(EfectoVideo(tipo, finUs = 4_000_000)))
            val temprano = escena(c).cuadroEn(1_000_000).clips.single()
            val tarde = escena(c).cuadroEn(3_000_000).clips.single()
            assertTrue(temprano.mascara!!.radial)
            assertEquals(tipo == TipoEfectoVideo.CIRCULO_ABRIR, temprano.mascara.invertida)
            assertTrue(temprano.bordes.average() > tarde.bordes.average())
            val q = Edicion.recortar(Proyecto(principal = listOf(c)), 1, 1_000_000, 4_000_000)
            val recortado = q.principal.single()
            assertEquals(escena(c).efectosVideo(c, 3_000_000), escena(recortado).efectosVideo(recortado, 2_000_000))
        }
    }

    @Test
    fun blurDireccionalYBordesEspejoLleganAlPasoDeDibujo() {
        val c = Clip(1, origen = video, efectos = listOf(
            EfectoVideo(TipoEfectoVideo.DESENFOQUE_HORIZONTAL, finUs = 4_000_000),
            EfectoVideo(TipoEfectoVideo.BORDES_ESPEJO, finUs = 4_000_000, intensidad = 0.5f)))
        val paso = escena(c).cuadroEn(1_000_000).clips.single()
        assertTrue(paso.desenfoqueX > 0f)
        assertEquals(0f, paso.desenfoqueY, 0f)
        assertEquals(0.5f, paso.espejoBordes, 0f)
    }

    @Test
    fun elCierreVerticalDeUnaCapaSoloComprimeSuAlto() {
        val c = Clip(1, origen = video, efectos = listOf(EfectoVideo(TipoEfectoVideo.CERRAR_VERTICAL, finUs = 4_000_000)))
        val p = Proyecto(capas = listOf(Capa(0, 0, c)), fondo = Fondo.NEGRO,
            salida = AjustesSalida(desenfoqueMovimiento = false))
        val paso = Escena(p, 576, 1024, medidor).cuadroEn(2_000_000).clips.single()
        val m = paso.matrices.single()
        assertEquals(0f, m[6], 1e-5f)
        assertEquals(-0.5f, m[7], 1e-5f)
        assertEquals(0.5f, m[0] * 288f + m[6], 1e-5f)
        assertEquals(0.5f, m[4] * 512f + m[7], 1e-5f)
    }

    @Test
    fun aplastarMuestraLaEntranteDebajoSinFundirYComprimeElEjeElegido() {
        for (angulo in listOf(0f, 90f)) {
            val a = Clip(1, origen = video, transicion = Transicion.APLASTAR, duracionTransicionUs = 1_000_000,
                ajusteTransicion = AjusteTransicion(curva = Curva.LINEAL, angulo = angulo))
            val b = Clip(2, origen = video)
            val p = Proyecto(principal = listOf(a, b), fondo = Fondo.NEGRO,
                salida = AjustesSalida(desenfoqueMovimiento = false))
            val cuadro = Escena(p, 576, 1024, medidor).cuadroEn(3_500_000)
            val grupo = cuadro.pasos.filterIsInstance<PasoGrupo>().single()
            val entrante = cuadro.pasos.filterIsInstance<PasoClip>().single()
            assertEquals(2L, entrante.clip.id)
            assertEquals(1L, grupo.pasos.filterIsInstance<PasoClip>().single().clip.id)
            assertTrue(cuadro.pasos.indexOf(entrante) < cuadro.pasos.indexOf(grupo))
            assertTrue(grupo.opacidades.all { it == 1f })
            val m = grupo.matrices.single()
            assertEquals(0.5f, m[0] * 288f + m[6], 1e-5f)
            assertEquals(0.5f, m[4] * 512f + m[7], 1e-5f)
            assertEquals(if (angulo == 0f) -0.5f else 0f, m[6], 1e-5f)
            assertEquals(if (angulo == 90f) -0.5f else 0f, m[7], 1e-5f)
            assertFalse(grupo.extender)
        }
    }

    @Test
    fun sePuedeDividirEnBloquesDeUnFotograma() {
        val c = Clip(1, origen = video, hastaUs = 66_666)
        val q = Edicion.dividir(Proyecto(principal = listOf(c), siguienteId = 2), 1, 33_333)!!.first
        assertEquals(listOf(33_333L, 33_333L), q.principal.map { it.duracionUs })
    }

    @Test
    fun lasMuestrasFueraDeUnIrisInvertidoQuedanCompletas() {
        val c = Clip(1, origen = video, efectos = listOf(EfectoVideo(TipoEfectoVideo.CIRCULO_ABRIR,
            inicioUs = 1_000_000, finUs = 3_000_000)))
        val paso = escena(c).cuadroEn(1_000_000).clips.single()
        assertTrue(paso.mascara!!.invertida)
        assertTrue(paso.bordes.any { it == -Escena.SIN_BORDE })
        assertTrue(paso.bordes.any { it > 0f })
    }

    @Test
    fun losBordesEspejoNoRellenanElHuecoDeUnCierre() {
        val c = Clip(1, origen = video, efectos = listOf(
            EfectoVideo(TipoEfectoVideo.BORDES_ESPEJO, finUs = 4_000_000),
            EfectoVideo(TipoEfectoVideo.CERRAR_VERTICAL, finUs = 4_000_000)))
        val paso = escena(c).cuadroEn(2_000_000).clips.single()
        assertEquals(0f, paso.espejoBordes, 0f)
    }

    @Test
    fun elProgresoMedidoInterpolaYConservaSuRelojAlRecortarYAcelerar() {
        val e = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 1_000_000, 3_000_000,
            progresos = listOf(ProgresoEfecto(0, 0.2f), ProgresoEfecto(500_000, 0.8f), ProgresoEfecto(2_000_000, 1f)))
        assertEquals(0.5f, e.progresoEn(1_250_000), 1e-6f)
        assertEquals(0.2f, e.progresoEn(1_000_000), 0f)
        assertEquals(1f, e.progresoEn(3_000_000), 0f)
        val recortado = e.recortado(1_200_000, 2_500_000)!!
        assertEquals(e.progresoEn(1_250_000), recortado.progresoEn(50_000), 0f)
        val escalado = e.tiempoEscalado(0.5)
        assertEquals(e.progresoEn(1_250_000), escalado.progresoEn(625_000), 0f)
        assertEquals(e.progresoEn(1_250_000), e.copy(rapidez = 2f).progresoEn(1_125_000), 0f)
    }

    @Test
    fun revelarHorizontalUsaCoordenadasCanvasYLosExtremosSonCompletos() {
        val e = EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, finUs = 3_000_000,
            progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(1_000_000, 0.25f), ProgresoEfecto(2_000_000, 1f)))
        val c = Clip(1, origen = video, efectos = listOf(e))
        val escena = escena(c)
        val inicio = escena.efectosVideo(c, 0)
        val cuarto = escena.efectosVideo(c, 1_000_000)
        val final = escena.efectosVideo(c, 2_000_000)
        assertEquals(-289f, inicio.borde, 0f)
        assertEquals(-144f, cuarto.borde, 0f)
        assertEquals(289f, final.borde, 0f)
        assertEquals(Mascara(1f, 0f, suave = 2f), cuarto.mascara)
        // La exposicion del primer cuadro no deja escapar la fuente entera
        // con muestras negativas que ignoren el efecto iniciado en cero.
        val paso = escena.cuadroEn(0).clips.single()
        assertTrue(paso.bordes.all { it < 0f && it != Escena.SIN_BORDE })
    }
}
