package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test

class PropiedadesTest {
    private fun base() = Clip(1, texto = Texto("Hola", sombra = true, sombraOpacidad = 0f), hastaUs = 4_000_000)
    private fun animado() = base().copy(propiedades = mapOf(
        Propiedad.OPACIDAD_SOMBRA_TEXTO to listOf(ClaveValor(0, 0.0, Curva.LINEAL), ClaveValor(2_000_000, 1.0)),
        Propiedad.TAMANO to listOf(ClaveValor(0, .04, Curva.ENTRADA), ClaveValor(2_000_000, .12))
    ))

    @Test fun sombraYMovimientoSeAnimanAlMismoTiempo() {
        val c = animado().copy(claves = listOf(Clave(0, Encuadre(x = -.3f), Curva.LINEAL), Clave(2_000_000, Encuadre(x = .3f))))
        assertEquals(.5f, c.propiedadesEn(1_000_000).texto!!.sombraOpacidad, 1e-6f)
        assertEquals(0f, c.encuadreEn(1_000_000).x, 1e-6f)
        // Cada canal conserva su curva: tamaño acelera mientras la sombra es lineal.
        assertTrue(c.propiedadesEn(1_000_000).texto!!.tamano < .08f)
    }
    @Test fun cambiarUnCanalNoPisaLosOtrosNiSusCurvas() {
        val c = animado()
        val d = c.editarPropiedades(1_000_000) { it.copy(texto = it.texto!!.copy(sombraOpacidad = .2f)) }
        assertEquals(c.propiedades[Propiedad.TAMANO], d.propiedades[Propiedad.TAMANO])
        assertEquals(.2f, d.propiedadesEn(1_000_000).texto!!.sombraOpacidad, 1e-6f)
        assertEquals(Curva.LINEAL, d.propiedades.getValue(Propiedad.OPACIDAD_SOMBRA_TEXTO).first().curva)
    }
    @Test fun fuenteYSubrayadoCambianEnLaClaveExacta() {
        val c = base().copy(propiedades = mapOf(
            Propiedad.FUENTE to listOf(ClaveValor(0, 0.0), ClaveValor(1_000_000, 2.0)),
            Propiedad.SUBRAYADO to listOf(ClaveValor(0, 0.0), ClaveValor(1_000_000, 1.0))))
        assertEquals(Fuente.SANS, c.propiedadesEn(999_999).texto!!.fuente)
        assertFalse(c.propiedadesEn(999_999).texto!!.subrayado)
        assertEquals(Fuente.SERIF, c.propiedadesEn(1_000_000).texto!!.fuente)
        assertTrue(c.propiedadesEn(1_000_000).texto!!.subrayado)
    }
    @Test fun losColoresInterpolanCanalesSinPerderBytes() {
        val a = 0xFF123456.toInt(); val b = 0xFFABCDEF.toInt()
        val c = base().copy(propiedades = mapOf(Propiedad.COLOR_TEXTO to listOf(ClaveValor(0, a.toDouble(), Curva.LINEAL), ClaveValor(2_000_000, b.toDouble()))))
        assertEquals(a, c.propiedadesEn(0).texto!!.color)
        assertEquals(b, c.propiedadesEn(2_000_000).texto!!.color)
        assertEquals(0xFF5F81A3.toInt(), c.propiedadesEn(1_000_000).texto!!.color)
    }
    @Test fun dividirYRecortarConservaLaCurvaPersonalizadaCompleta() {
        val c = animado().copy(propiedades = mapOf(Propiedad.OPACIDAD_SOMBRA_TEXTO to listOf(
            ClaveValor(0, 0.0, bezier = Bezier(.2f, .1f, .7f, 1.2f)), ClaveValor(3_000_000, 1.0))))
        val p = Proyecto(principal = listOf(c), siguienteId = 2)
        val dividido = Edicion.dividir(p, 1, 1_000_000)!!.first
        val b = dividido.principal[1]
        val recortado = Edicion.recortar(p, 1, 1_000_000, 4_000_000).principal.single()
        for (t in listOf(0L, 200_000, 800_000, 1_999_999)) {
            val esperado = c.propiedadesEn(t + 1_000_000).texto!!.sombraOpacidad
            assertEquals(esperado, b.propiedadesEn(t).texto!!.sombraOpacidad, 1e-6f)
            assertEquals(esperado, recortado.propiedadesEn(t).texto!!.sombraOpacidad, 1e-6f)
        }
    }
    @Test fun velocidadSigueElContenidoTrasUnRecorte() {
        val origen = Origen("v", "v", TipoOrigen.VIDEO, 1280, 720, 4_000_000)
        val c = Clip(1, origen = origen, desfasePropiedadesUs = 500_000, propiedades = mapOf(
            Propiedad.SATURACION to listOf(ClaveValor(0, -1.0, Curva.LINEAL), ClaveValor(4_000_000, 1.0))))
        val rapido = c.conVelocidad(2f)
        assertEquals(c.propiedadesEn(1_000_000).color.saturacion, rapido.propiedadesEn(500_000).color.saturacion, 1e-6f)
    }
    @Test fun guardarConservaMarcoCanalesCurvasYReloj() {
        val c = animado().copy(marco = MarcoVisual(.015f, .08f, 0xFF00FFFF.toInt(), .7f, .5f), desfasePropiedadesUs = 111_000)
        val ajuste = Clip(2, esAjuste = true, color = AjustesColor(claridad = .4f), propiedades = mapOf(
            Propiedad.SATURACION to listOf(ClaveValor(0, 0.0, bezier = Bezier.SUAVE), ClaveValor(2_000_000, .8))))
        val p = Proyecto(principal = listOf(c), capas = listOf(Capa(100_000, 0, ajuste)), siguienteId = 3)
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
    }
    @Test fun losProyectosViejosSeAbrenSinMarcoNiCanales() {
        val p = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"Hola"}}]}""")!!
        assertFalse(p.principal.single().marco.activo)
        assertTrue(p.principal.single().propiedades.isEmpty())
    }
    @Test fun capaDeAjusteAfectaElMontajeSoloDentroDelTramo() {
        val ajuste = Clip(2, esAjuste = true, hastaUs = 1_000_000, color = AjustesColor(nitidez = .5f), propiedades = mapOf(
            Propiedad.SATURACION to listOf(ClaveValor(0, -1.0, Curva.LINEAL), ClaveValor(1_000_000, 1.0))))
        val p = Proyecto(principal = listOf(base()), capas = listOf(Capa(1_000_000, 0, ajuste)))
        val e = Escena(p, 576, 1024, Medidor { _, _ -> 300f to 100f })
        assertTrue(e.cuadroEn(999_999).ajustes.isEmpty())
        assertEquals(.5f, e.cuadroEn(1_500_000).ajustes.single().nitidez, 1e-6f)
        assertEquals(0f, e.cuadroEn(1_500_000).ajustes.single().saturacion, 1e-6f)
        assertFalse(e.cuadroEn(1_500_000).clips.any { it.clip.esAjuste })
        assertTrue(e.cuadroEn(2_000_000).ajustes.isEmpty())
    }
    @Test fun elMotorRecibeElTextoYMarcoDelInstante() {
        val c = animado().copy(propiedades = animado().propiedades + (Propiedad.BORDE_ANCHO to listOf(
            ClaveValor(0, 0.0, Curva.LINEAL), ClaveValor(2_000_000, .1))))
        val e = Escena(Proyecto(principal = listOf(c), fondo = Fondo.NEGRO), 576, 1024, Medidor { _, _ -> 300f to 100f })
        val paso = e.cuadroEn(1_000_000).clips.single()
        assertEquals(.5f, paso.clip.texto!!.sombraOpacidad, 1e-6f)
        assertEquals(.05f, paso.clip.marco.ancho, 1e-6f)
    }
    @Test fun laPrimeraClaveFijaElValorInicial() {
        val c = base().ponerPropiedades(1_000_000, listOf(Propiedad.OPACIDAD_SOMBRA_TEXTO))
            .editarPropiedades(1_000_000) { it.copy(texto = it.texto!!.copy(sombraOpacidad = 1f)) }
        assertEquals(0f, c.propiedadesEn(0).texto!!.sombraOpacidad, 1e-6f)
        assertEquals(1f, c.propiedadesEn(1_000_000).texto!!.sombraOpacidad, 1e-6f)
    }
}
