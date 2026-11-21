package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test

class AlcanceCapasTest {
    private fun visual(id: Long) = Clip(id, texto = Texto("Elemento $id"), hastaUs = 4_000_000)
    private fun ajuste(destinos: Set<Long>? = null) = Clip(10, esAjuste = true, hastaUs = 2_000_000,
        color = AjustesColor(saturacion = -.7f), afectaA = destinos)
    private val medidor = Medidor { _: Clip, _: Int -> 300f to 180f }
    private fun escena(p: Proyecto) = Escena(p.copy(fondo = Fondo.NEGRO), 576, 1024, medidor)

    @Test fun todosEsElPredeterminadoYTambienIncluyeElementosNuevos() {
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(500_000, 0, ajuste())))
        assertNull(ajuste().afectaA)
        assertTrue(AlcanceCapas.acepta(ajuste(), 99))
        val q = p.copy(capas = p.capas + Capa(0, 2, visual(99)))
        val cuadro = escena(q).cuadroEn(1_000_000)
        assertEquals(1, cuadro.ajustes.size)
        assertTrue(cuadro.clips.any { it.clip.id == 99L })
        assertTrue(cuadro.clips.all { it.ajustesCapa.isEmpty() })
    }
    @Test fun laSeleccionSoloFiltraLosElementosElegidosYRespetaElRango() {
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, visual(2)), Capa(1_000_000, 1, ajuste(setOf(2)))))
        val e = escena(p)
        assertTrue(e.cuadroEn(999_999).clips.all { it.ajustesCapa.isEmpty() })
        val dentro = e.cuadroEn(1_500_000)
        assertTrue(dentro.ajustes.isEmpty())
        assertTrue(dentro.clips.single { it.clip.id == 1L }.ajustesCapa.isEmpty())
        assertEquals(-.7f, dentro.clips.single { it.clip.id == 2L }.ajustesCapa.single().saturacion, 0f)
        assertTrue(e.cuadroEn(3_000_000).clips.all { it.ajustesCapa.isEmpty() })
    }
    @Test fun seleccionVaciaNoSeConvierteAccidentalmenteEnTodas() {
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, ajuste(emptySet()))))
        assertTrue(escena(p).cuadroEn(1_000_000).ajustes.isEmpty())
        assertTrue(escena(p).cuadroEn(1_000_000).clips.all { it.ajustesCapa.isEmpty() })
        assertFalse(AlcanceCapas.acepta(ajuste(emptySet()), 1))
    }
    @Test fun lasClavesAnimanElValorBaseEnElTramoCompartido() {
        val a = ajuste().copy(propiedades = mapOf(Propiedad.SATURACION to listOf(
            ClaveValor(0, -.7, Curva.LINEAL), ClaveValor(2_000_000, .5))))
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(1_000_000, 0, a)))
        assertEquals(-.7f, escena(p).cuadroEn(1_000_000).ajustes.single().saturacion, 1e-6f)
        assertEquals(-.1f, escena(p).cuadroEn(2_000_000).ajustes.single().saturacion, 1e-6f)
        assertEquals(-.7f, a.color.saturacion, 0f)
    }
    @Test fun elEfectoCompartidoConservaSuFaseEnClipsConDistintosInicios() {
        val efecto = EfectoVideo(TipoEfectoVideo.MOVER, 200_000, 1_800_000, ajustes = AjustesEfecto(desde = 0f, hasta = .5f, curva = Curva.LINEAL))
        val a = ajuste().copy(efectos = listOf(efecto))
        val p = Proyecto(capas = listOf(Capa(1_000_000, 0, a)))
        val primero = AlcanceCapas.conEfectos(p, visual(1), 0).efectos.single()
        val segundo = AlcanceCapas.conEfectos(p, visual(2), 1_500_000).efectos.single()
        assertEquals(efecto.valorEn(1_000_000), primero.valorEn(2_000_000), 1e-6f)
        assertEquals(efecto.valorEn(1_000_000), segundo.valorEn(500_000), 1e-6f)
        assertFalse(primero.activoEn(1_199_999))
        assertTrue(primero.activoEn(1_200_000))
        assertFalse(primero.activoEn(2_800_000))
    }
    @Test fun recortarElTramoDeEfectosNoReiniciaSuCurva() {
        val efecto = EfectoVideo(TipoEfectoVideo.MOVER, 0, 2_000_000, ajustes = AjustesEfecto(bezier = Bezier(.2f,.1f,.7f,1.2f)))
        val a = ajuste().copy(efectos = listOf(efecto))
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, a)))
        val q = Edicion.recortar(p, 10, 500_000, 2_000_000)
        val trasladado = AlcanceCapas.conEfectos(q, visual(1), 0).efectos.single()
        assertEquals(efecto.valorEn(1_000_000), trasladado.valorEn(500_000), 1e-6f)
    }
    @Test fun efectoGlobalSeleccionadoSeInyectaAntesDeComponerLasMitades() {
        val fx = ajuste(setOf(2)).copy(color = AjustesColor(), efectos = listOf(EfectoVideo(TipoEfectoVideo.MITADES, finUs = 2_000_000)))
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, visual(2)), Capa(0, 1, fx)))
        val pasos = escena(p).cuadroEn(500_000).pasos
        assertTrue(pasos.any { it is PasoPartes && it.partes.all { parte -> parte.clip.id == 2L } })
        assertTrue(pasos.any { it is PasoClip && it.clip.id == 1L })
    }
    @Test fun reordenarIntercambiaPistasCompletasSinCambiarDestinosNiTiempos() {
        val p = Proyecto(capas = listOf(Capa(0, 2, visual(1)), Capa(5_000_000, 2, visual(2)), Capa(0, 7, visual(3)), Capa(0, 10, ajuste(setOf(1)))))
        val q = AlcanceCapas.cambiarOrden(p, 2, true)
        assertEquals(listOf(7,7,2,10), q.capas.map { it.fila })
        assertEquals(p.capas.map { it.inicioUs }, q.capas.map { it.inicioUs })
        assertEquals(setOf(1L), q.capas.last().clip.afectaA)
        val dibujo = escena(q).cuadroEn(500_000).clips.filterNot { it.esFondo }.map { it.clip.id }
        assertTrue(dibujo.indexOf(3) < dibujo.indexOf(1))
    }
    @Test fun dividirYDuplicarMantieneLosDestinosExplicitos() {
        val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, ajuste(setOf(1)))), siguienteId = 20)
        val (dividido, nuevo) = Edicion.dividir(p, 1, 1_000_000)!!
        assertEquals(setOf(1L, nuevo), dividido.capas.single().clip.afectaA)
        val (duplicado, copia) = Edicion.duplicar(p, 1)!!
        assertEquals(setOf(1L, copia), duplicado.capas.single().clip.afectaA)
        val eliminado = Edicion.eliminar(p, 1)
        assertEquals(emptySet<Long>(), eliminado.capas.single().clip.afectaA)
    }
    @Test fun compartirUnEfectoExistenteLoMueveSinDuplicarloYConservaElTiempo() {
        val fx = EfectoVideo(TipoEfectoVideo.RGB, 500_000, 1_500_000, id = 3)
        val c = visual(1).copy(efectos = listOf(fx))
        val p = Proyecto(capas = listOf(Capa(2_000_000, 0, c)), siguienteId = 20)
        val (q, nuevo) = compartirEfecto(p, 1, 3)!!
        assertTrue(Edicion.clip(q, 1)!!.efectos.isEmpty())
        val compartida = q.capas.single { it.id == nuevo }
        assertEquals(2_000_000L, compartida.inicioUs)
        assertEquals(fx, compartida.clip.efectos.single())
        assertNull(compartida.clip.afectaA)
    }
    @Test fun movimientoCompartidoPideBitsYNingunDestinoNoCobraGrano() {
        val p = Proyecto(principal = listOf(visual(1)))
        val base = Linea.tramos(p, false).single().factor
        val fx = ajuste().copy(color = AjustesColor(), efectos = listOf(EfectoVideo(TipoEfectoVideo.MOVER, finUs = 2_000_000)))
        val animado = p.copy(capas = listOf(Capa(0, 0, fx)))
        assertTrue(Linea.tramos(animado, false).first().factor > base)
        val vacio = p.copy(capas = listOf(Capa(0, 0, ajuste(emptySet()).copy(color = AjustesColor(grano = 1f)))))
        assertEquals(base, Linea.tramos(vacio, false).first().factor, 0f)
    }

    @Test fun persistirDistingueTodasNingunaYUnaSeleccion() {
        for (seleccion in listOf(null, emptySet(), setOf(1L, 2L))) {
            val p = Proyecto(principal = listOf(visual(1)), capas = listOf(Capa(0, 0, ajuste(seleccion))), siguienteId = 11)
            assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
        }
    }
}
