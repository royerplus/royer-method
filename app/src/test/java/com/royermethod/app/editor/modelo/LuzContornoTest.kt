package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test

class LuzContornoTest {
    private val cuadro = 1_000_000L / Linea.FPS

    private fun contorno(vararg camino: Pair<Float, Float>, estilo: EstiloLuz = EstiloLuz()) =
        LuzTrazo(1, "Contorno 1", TipoLuz.CONTORNO,
            if (camino.isEmpty()) listOf(PuntoLuz(.5f, .5f)) else camino.map { PuntoLuz(it.first, it.second) }, estilo)

    @Test fun sinCaminoEnciendeElContornoEntero() {
        val l = contorno()
        assertTrue(l.entera)
        assertEquals(emptyList<PuntoLuz>(), l.zonaContorno(l.estilo, 1.5f, 32))
        // Con camino ya no, y las demas clases nunca lo son aunque tengan un solo punto
        assertFalse(contorno(.2f to .2f, .8f to .2f).entera)
        assertFalse(l.copy(tipo = TipoLuz.FOCO).entera)
    }

    @Test fun laZonaVaEnUnidadesDelLadoCortoDelElemento() {
        // Elemento el doble de ancho que alto: la x se dobla y la y se queda
        assertEquals(listOf(PuntoLuz(.5f, .5f), PuntoLuz(1.5f, .5f)),
            contorno(.25f to .5f, .75f to .5f).zonaContorno(EstiloLuz(), 2f, 32))
        // Y en uno el doble de alto que ancho, al reves
        assertEquals(listOf(PuntoLuz(.5f, .5f), PuntoLuz(.5f, 1.5f)),
            contorno(.5f to .25f, .5f to .75f).zonaContorno(EstiloLuz(), .5f, 32))
    }

    @Test fun elTramoRecortaLaZonaPorSuLongitudYSinTramoNoQuedaNada() {
        val l = contorno(.1f to .5f, .5f to .5f, .9f to .5f)
        val z = l.zonaContorno(EstiloLuz(inicio = .25f, fin = .75f), 1f, 32)!!
        assertEquals(.3f, z.first().x, 1e-5f)
        assertEquals(.7f, z.last().x, 1e-5f)
        assertNull(l.zonaContorno(EstiloLuz(inicio = .5f, fin = .5f), 1f, 32))
        assertNull(l.zonaContorno(EstiloLuz(inicio = .8f, fin = .2f), 1f, 32))
    }

    @Test fun laZonaSeMueveYCreceConElEstiloDeLaLuz() {
        val l = contorno(.2f to .5f, .4f to .5f)
        val movida = l.zonaContorno(EstiloLuz(x = .1f, y = -.2f), 1f, 32)!!
        assertEquals(.3f, movida.first().x, 1e-6f); assertEquals(.3f, movida.first().y, 1e-6f)
        assertEquals(.5f, movida.last().x, 1e-6f)
        // A escala crece desde su centro
        val doble = l.zonaContorno(EstiloLuz(escala = 2f), 1f, 32)!!
        assertEquals(.1f, doble.first().x, 1e-6f); assertEquals(.5f, doble.last().x, 1e-6f)
    }

    @Test fun unCaminoHechoConElDedoSeResumeAPuntosRepartidosPorIgual() {
        val largo = (0..99).map { PuntoLuz(it / 99f, .5f) }
        val r = LuzTrazo.resumir(largo, 32)
        assertEquals(32, r.size)
        assertEquals(largo.first(), r.first())
        assertEquals(1f, r.last().x, 1e-5f)
        r.zipWithNext().forEach { (a, b) -> assertEquals(1f / 31f, b.x - a.x, 1e-4f) }
        // Lo que ya cabe no se toca, y un punto solo se hace camino
        val corto = listOf(PuntoLuz(0f, 0f), PuntoLuz(1f, 1f))
        assertSame(corto, LuzTrazo.resumir(corto, 32))
        assertEquals(listOf(PuntoLuz(.3f, .3f), PuntoLuz(.3f, .3f)), LuzTrazo.resumir(listOf(PuntoLuz(.3f, .3f)), 32))
        // La zona de la luz nunca pasa de lo que acepta el sombreador
        val zona = contorno(*largo.map { it.x to it.y }.toTypedArray()).zonaContorno(EstiloLuz(), 1f, 32)!!
        assertEquals(32, zona.size)
    }

    @Test fun unAspectoCambiaComoSeVeYDejaColorFuerzaZonaYLado() {
        val base = EstiloLuz(color = 0xFF112233.toInt(), opacidad = .7f, zona = .2f, dirigida = 1f, angulo = 45f, inicio = .1f, fin = .9f)
        for (a in AspectoContorno.values()) {
            val e = a.en(base)
            assertTrue(a.titulo, a.es(e))
            assertEquals(a.titulo, base.copy(grosor = e.grosor, halo = e.halo, dentro = e.dentro, relleno = e.relleno), e)
            // Ninguno se confunde con otro ni se sale de lo que guarda el modelo
            assertEquals(a.titulo, 1, AspectoContorno.values().count { it.es(e) })
            assertEquals(a.titulo, e, e.acotado())
        }
        assertEquals(1f, AspectoContorno.SILUETA.en(base).relleno, 0f)
        assertFalse(AspectoContorno.LINEA.es(AspectoContorno.LINEA.en(base).copy(grosor = .02f)))
    }

    @Test fun lasMedidasDelContornoSeAnimanYSeAcotan() {
        val a = EstiloLuz(dentro = 0f, relleno = 0f, zona = .1f, dirigida = 0f, angulo = 0f)
        val b = EstiloLuz(dentro = .2f, relleno = 1f, zona = .3f, dirigida = 1f, angulo = 90f)
        val m = a.mezclar(b, .5f)
        assertEquals(.1f, m.dentro, 1e-6f); assertEquals(.5f, m.relleno, 1e-6f); assertEquals(.2f, m.zona, 1e-6f)
        assertEquals(.5f, m.dirigida, 1e-6f); assertEquals(45f, m.angulo, 1e-4f)
        val raro = EstiloLuz(grosor = -1f, dentro = 9f, relleno = -1f, zona = 0f, dirigida = 2f, angulo = Float.NaN).acotado()
        assertEquals(0f, raro.grosor, 0f); assertEquals(.3f, raro.dentro, 0f); assertEquals(0f, raro.relleno, 0f)
        assertEquals(.01f, raro.zona, 0f); assertEquals(1f, raro.dirigida, 0f); assertEquals(0f, raro.angulo, 0f)
    }

    @Test fun elDestelloApareceDeGolpeYSeApagaAfinandose() {
        val neon = AspectoContorno.NEON.en(EstiloLuz())
        val l = contorno(estilo = neon).conDestello(1_000_000, 1_400_000, 0)
        assertEquals(listOf(1_000_000 - cuadro, 1_000_000L, 1_400_000L), l.claves.map { it.tiempoUs })
        // Un fotograma antes esta apagada; en el golpe, como era
        assertEquals(0f, l.estiloEn(1_000_000 - cuadro).opacidad, 0f)
        assertEquals(neon, l.estiloEn(1_000_000))
        val fin = l.estiloEn(1_400_000)
        assertEquals(0f, fin.opacidad, 0f); assertEquals(0f, fin.grosor, 0f); assertEquals(0f, fin.dentro, 0f)
        // Por el camino va siendo mas fina y mas tenue
        val a = l.estiloEn(1_100_000); val b = l.estiloEn(1_300_000)
        assertTrue(a.grosor < neon.grosor && a.grosor > b.grosor && b.grosor > 0f)
        assertTrue(a.opacidad < 1f && a.opacidad > b.opacidad && b.opacidad > 0f)
        assertTrue(a.halo < neon.halo && a.halo > b.halo)
        // Lejos del destello no se ve
        assertEquals(0f, l.estiloEn(0).opacidad, 0f)
        assertEquals(0f, l.estiloEn(3_000_000).opacidad, 0f)
        // La zona y el color no cambian
        assertEquals(neon.color, b.color); assertEquals(neon.zona, b.zona, 0f)
    }

    @Test fun otroDestelloMientrasSeApagaElAnteriorSaleIgualDeFuerte() {
        val l = contorno(estilo = AspectoContorno.NEON.en(EstiloLuz()))
            .conDestello(1_000_000, 1_400_000, 0).conDestello(1_300_000, 1_700_000, 0)
        assertEquals(listOf(1_000_000 - cuadro, 1_000_000L, 1_300_000 - cuadro, 1_300_000L, 1_700_000L), l.claves.map { it.tiempoUs })
        assertEquals(l.estiloEn(1_000_000), l.estiloEn(1_300_000))
        assertEquals(0f, l.estiloEn(1_300_000 - cuadro).opacidad, 0f)
        // Y uno puesto donde ya estaba apagada sale como el ultimo
        val m = l.conDestello(2_500_000, 2_900_000, 0)
        assertEquals(l.estiloEn(1_000_000), m.estiloEn(2_500_000))
        assertEquals(8, m.claves.size)
    }

    @Test fun elDestelloNoPoneNadaAntesDeQueEmpieceElElementoNiSinSitio() {
        val l = contorno()
        assertEquals(listOf(700_000L, 1_100_000L), l.conDestello(700_000, 1_100_000, 700_000).claves.map { it.tiempoUs })
        assertSame(l, l.conDestello(700_000, 700_000, 0))
    }

    @Test fun elDestelloDeUnaLuzApagadaSeEnciendeEntero() {
        val l = contorno(estilo = AspectoContorno.LINEA.en(EstiloLuz(opacidad = 0f))).conDestello(1_000_000, 1_400_000, 0)
        assertEquals(1f, l.estiloEn(1_000_000).opacidad, 0f)
        assertTrue(AspectoContorno.LINEA.es(l.estiloEn(1_000_000)))
    }

    @Test fun elDestelloQuitaLasClavesQueQuedanDentroYDejaLasDeFuera() {
        val l = contorno().copy(claves = listOf(
            ClaveLuz(200_000, EstiloLuz(opacidad = .5f)), ClaveLuz(1_100_000, EstiloLuz(opacidad = .2f)), ClaveLuz(2_000_000, EstiloLuz(opacidad = .8f))))
            .conDestello(1_000_000, 1_400_000, 0)
        assertEquals(listOf(200_000L, 1_000_000 - cuadro, 1_000_000L, 1_400_000L, 2_000_000L), l.claves.map { it.tiempoUs })
        assertEquals(.5f, l.estiloEn(200_000).opacidad, 0f)
        assertEquals(.8f, l.estiloEn(2_000_000).opacidad, 0f)
    }

    @Test fun guardarYAbrirConservaElContornoSuZonaYDondeSeVeCadaLuz() {
        val l = contorno(.2f to .3f, .6f to .2f, .8f to .5f,
            estilo = EstiloLuz(grosor = 0f, dentro = .07f, relleno = .5f, zona = .15f, dirigida = 1f, angulo = -120f))
            .conDestello(1_000_000, 1_400_000, 0)
        val detras = LuzTrazo(2, "Foco 2", TipoLuz.FOCO, listOf(PuntoLuz(.4f, .4f), PuntoLuz(.6f, .6f)), alcance = AlcanceLuz.DETRAS)
        val dentro = detras.copy(id = 3, alcance = AlcanceLuz.DENTRO)
        val entera = contorno().copy(id = 4)
        val c = Clip(1, origen = Origen("foto", "foto", TipoOrigen.IMAGEN, 400, 200), hastaUs = 4_000_000, luces = listOf(l, detras, dentro, entera))
        val p = Proyecto(principal = listOf(c), siguienteId = 2)
        val abierto = Persistencia.deTexto(Persistencia.aTexto(p))
        assertEquals(p, abierto)
        assertTrue(abierto!!.principal.single().luces.last().entera)
    }

    @Test fun losProyectosDeAntesAbrenSusLucesComoEstaban() {
        // Sin "alcance" ni las medidas del contorno: se ve en todo, no entra ni rellena
        val texto = """{"principal":[{"id":1,"texto":{"contenido":"x"},"luces":[{"id":1,"nombre":"Luz","tipo":"TRAZO",
            "puntos":[0.1,0.2,0.8,0.5],"estilo":{"grosor":0.01,"halo":0.02}}]}]}"""
        val l = Persistencia.deTexto(texto)!!.principal.single().luces.single()
        assertEquals(AlcanceLuz.TODO, l.alcance)
        assertEquals(EstiloLuz(grosor = .01f, halo = .02f), l.estilo)
        assertFalse(l.entera)
        // Un alcance o una clase que esta version no conoce no rompe el proyecto
        val raro = texto.replace("\"tipo\":\"TRAZO\"", "\"tipo\":\"TRAZO\",\"alcance\":\"ENCIMA\"")
        assertEquals(AlcanceLuz.TODO, Persistencia.deTexto(raro)!!.principal.single().luces.single().alcance)
        assertTrue(Persistencia.deTexto(texto.replace("TRAZO", "RAYO"))!!.principal.single().luces.isEmpty())
    }
}
