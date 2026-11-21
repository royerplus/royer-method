package com.royermethod.app.editor.ui

import androidx.compose.ui.geometry.Offset
import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PanelLucesLogicaTest {
    private val cuadro = 1_000_000L / Linea.FPS

    /** Un elemento de 2 s que empieza a los 2 s, con el reloj de sus luces adelantado 0,7 s. */
    private fun estado(luz: LuzTrazo = luzNueva(1, TipoLuz.CONTORNO)): EstadoEditor {
        val c = Clip(2, texto = Texto("Luz"), hastaUs = 2_000_000, desfasePropiedadesUs = 700_000, luces = listOf(luz))
        return EstadoEditor(Proyecto(principal = listOf(Clip(1, texto = Texto("Antes"), hastaUs = 2_000_000), c,
            Clip(3, texto = Texto("Después"), hastaUs = 2_000_000)), siguienteId = 4)).also {
            it.elegir(Seleccion.Bloque(2)); it.luzElegida = 1
        }
    }

    @Test fun laLuzDeContornoNaceEnElContornoEnteroYLasDemasEnElCentro() {
        val c = luzNueva(3, TipoLuz.CONTORNO)
        assertTrue(c.entera)
        assertEquals("Contorno 3", c.nombre)
        assertTrue(AspectoContorno.LINEA.es(c.estilo))
        assertEquals(AlcanceLuz.TODO, c.alcance)
        val f = luzNueva(4, TipoLuz.FOCO)
        assertFalse(f.entera)
        assertEquals(2, f.puntos.size)
        assertEquals(EstiloLuz(), f.estilo)
        val camino = listOf(PuntoLuz(0f, 0f), PuntoLuz(1f, 1f))
        assertEquals(camino, luzNueva(5, TipoLuz.TRAZO, camino).puntos)
    }

    @Test fun elAnguloDelSolEsDeDondeVieneLaLuz() {
        val centro = Offset(100f, 100f)
        assertEquals(0f, anguloDeLuz(centro, Offset(100f, 20f)), 1e-3f)
        assertEquals(90f, anguloDeLuz(centro, Offset(180f, 100f)), 1e-3f)
        assertEquals(-90f, anguloDeLuz(centro, Offset(20f, 100f)), 1e-3f)
        assertEquals(180f, abs(anguloDeLuz(centro, Offset(100f, 180f))), 1e-3f)
        assertEquals(45f, anguloDeLuz(centro, Offset(150f, 50f)), 1e-3f)
    }

    @Test fun unAnguloCualquieraSeEnsenaEntreMenos180Y180() {
        assertEquals(0f, enVuelta(360f), 1e-4f)
        assertEquals(-170f, enVuelta(190f), 1e-4f)
        assertEquals(170f, enVuelta(-190f), 1e-4f)
        assertEquals(90f, enVuelta(810f), 1e-3f)
        assertEquals(-180f, enVuelta(180f), 1e-4f)
        assertEquals(-45f, enVuelta(-45f), 1e-4f)
    }

    @Test fun elSolSigueAlDedoPorElCaminoCorto() {
        // Al pasar por abajo no salta de un extremo al otro
        assertEquals(185f, anguloSeguido(175f, -175f), 1e-3f)
        assertEquals(-185f, anguloSeguido(-175f, 175f), 1e-3f)
        assertEquals(30f, anguloSeguido(10f, 30f), 1e-3f)
        assertEquals(370f, anguloSeguido(350f, 10f), 1e-3f)
        // Tras muchas vueltas vuelve al angulo simple en vez de salirse de lo que guarda el modelo
        assertEquals(-170f, anguloSeguido(895f, -170f), 1e-3f)
        assertEquals(anguloSeguido(895f, -170f), EstiloLuz(angulo = anguloSeguido(895f, -170f)).acotado().angulo, 0f)
        assertEquals(899f, EstiloLuz(angulo = 899f).acotado().angulo, 0f)
    }

    @Test fun elDestelloSePoneEnElCabezalConElRelojDeLaLuzYSeDeshaceDeUnaVez() {
        val e = estado(); val p = e.proyecto
        e.pausarEn(2_400_000)
        animacionLuz(e, 4)
        val ks = e.clipElegido!!.luces.single().claves
        assertEquals(listOf(1_100_000 - cuadro, 1_100_000L, 1_500_000L), ks.map { it.tiempoUs })
        assertEquals(1f, ks[1].estilo.opacidad, 0f)
        assertEquals(0f, ks[0].estilo.opacidad, 0f); assertEquals(0f, ks[2].estilo.opacidad, 0f)
        e.deshacer(); assertEquals(p, e.proyecto)
    }

    @Test fun elDestelloNoSeSaleDelElementoNiSePoneConElCabezalFuera() {
        val e = estado()
        // A 0,1 s del final: se apaga justo al acabar el elemento
        e.pausarEn(3_900_000)
        animacionLuz(e, 4)
        assertEquals(listOf(2_600_000 - cuadro, 2_600_000L, 2_700_000L), e.clipElegido!!.luces.single().claves.map { it.tiempoUs })
        val p = e.proyecto
        for (t in listOf(0L, 5_000_000L)) {
            e.pausarEn(t)
            assertNull(e.localElegido)
            animacionLuz(e, 4)
            assertEquals(p, e.proyecto)
        }
    }

    @Test fun variosDestellosSeguidosQuedanCadaUnoConSuGolpe() {
        val e = estado()
        for (t in listOf(2_200_000L, 2_500_000L, 2_800_000L)) { e.pausarEn(t); animacionLuz(e, 4) }
        val l = e.clipElegido!!.luces.single()
        // Cada golpe: apagada un fotograma antes y encendida entera en el
        for (t in listOf(900_000L, 1_200_000L, 1_500_000L)) {
            assertEquals(0f, l.estiloEn(t - cuadro).opacidad, 0f)
            assertEquals(1f, l.estiloEn(t).opacidad, 0f)
            assertTrue(AspectoContorno.LINEA.es(l.estiloEn(t)))
        }
        assertEquals(0f, l.estiloEn(1_900_000).opacidad, 0f)
    }

    @Test fun recorrerUnaZonaDibujadaMueveSuTramoDeUnExtremoAlOtro() {
        val zona = luzNueva(1, TipoLuz.CONTORNO).copy(puntos = listOf(PuntoLuz(.2f, .2f), PuntoLuz(.8f, .2f)))
        val e = estado(zona)
        e.pausarEn(2_400_000)
        animacionLuz(e, 1)
        val l = e.clipElegido!!.luces.single()
        assertEquals(listOf(1_100_000L, 1_700_000L), l.claves.map { it.tiempoUs })
        val a = l.zonaContorno(l.estiloEn(1_100_000), 1f, 32)!!
        val b = l.zonaContorno(l.estiloEn(1_700_000), 1f, 32)!!
        assertEquals(.2f, a.first().x, 1e-5f)
        assertEquals(.8f, b.last().x, 1e-5f)
        assertTrue(a.last().x < b.first().x)
    }

    @Test fun losNombresDeLasLucesSeLeenConSusTildes() {
        assertEquals("Detrás", Rotulos.de(AlcanceLuz.DETRAS.titulo))
        assertEquals("Neón", Rotulos.de(AspectoContorno.NEON.titulo))
        assertEquals("Línea", Rotulos.de(AspectoContorno.LINEA.titulo))
        assertEquals("Contraluz", Rotulos.de(AspectoContorno.CONTRALUZ.titulo))
    }
}
