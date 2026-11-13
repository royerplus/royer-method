package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TransicionesLuzTest {
    private val origen = Origen("video", "video", TipoOrigen.VIDEO, 1080, 1300, duracionUs = 4_000_000)
    private val medidor = Medidor { _, _ -> 1080f to 1300f }
    private val luz = LuzTransicion(girar = true, anguloMedio = 0f, anguloFinal = 90f, halo = 2.6f, nucleo = .8f, textura = .6f)

    private fun proyecto(l: LuzTransicion = luz, curva: Curva = Curva.LINEAL) = Proyecto(principal = listOf(
        Clip(1, origen = origen, transicion = Transicion.LINEA, duracionTransicionUs = 1_000_000,
            ajusteTransicion = AjusteTransicion(curva = curva, angulo = 135f, color = 0xFF9B5CFF.toInt(), luz = l)),
        Clip(2, origen = origen)
    ), fondo = Fondo.NEGRO, siguienteId = 3)

    @Test fun desactivadoConservaLaDireccionAnterior() {
        val l = LuzTransicion()
        for (p in listOf(0f,.25f,.5f,.75f,1f)) assertEquals(166f,l.anguloEn(166f,p),0f)
    }

    @Test fun tresOrientacionesRecorrenDiagonalVerticalYHorizontal() {
        assertEquals(135f,luz.anguloEn(135f,0f),0f)
        assertEquals(0f,luz.anguloEn(135f,.5f),0f)
        assertEquals(90f,luz.anguloEn(135f,1f),0f)
        assertEquals(67.5f,luz.anguloEn(135f,.25f),1e-6f)
        assertEquals(45f,luz.anguloEn(135f,.75f),1e-6f)
    }

    @Test fun interpolarCruzaCeroPorElArcoCorto() {
        val l=LuzTransicion(girar=true,anguloMedio=10f,anguloFinal=350f)
        assertEquals(360f,l.anguloEn(350f,.25f),1e-6f)
        assertEquals(0f,l.anguloEn(350f,.75f),1e-6f)
    }

    @Test fun mascaraYLuzCompartenNormalYBordesDuranteTodoElGiro() {
        val e=Escena(proyecto(),576,1024,medidor)
        for (t in 3_050_000L..3_950_000L step 50_000L) {
            val pasos=e.cuadroEn(t).pasos
            val r=pasos.filterIsInstance<PasoLinea>().single()
            val b=pasos.filterIsInstance<PasoGrupo>().single()
            val m=b.mascara!!
            assertEquals(m.dirX,r.dirX,0f)
            assertEquals(m.dirY,r.dirY,0f)
            assertArrayEquals(b.bordes,r.bordes,0f)
            assertEquals(1f,r.dirX*r.dirX+r.dirY*r.dirY,1e-5f)
            assertEquals(luz,r.luz)
        }
    }

    @Test fun elRitmoModificaElGiroYNoSoloElDesplazamiento() {
        fun direccion(c:Curva)=Escena(proyecto(curva=c),576,1024,medidor).cuadroEn(3_250_000).pasos.filterIsInstance<PasoLinea>().single().dirX
        assertTrue(abs(direccion(Curva.LINEAL)-direccion(Curva.ENTRADA))>.05f)
    }

    @Test fun principioYFinalConservanEscenasCompletas() {
        val e=Escena(proyecto(),576,1024,medidor)
        assertTrue(e.cuadroEn(2_900_000).clips.all { it.clip.id==1L })
        assertTrue(e.cuadroEn(4_000_000).clips.all { it.clip.id==2L })
        // Nunca se sustituye la escena A por una franja negra para hacer el barrido.
        assertTrue(e.cuadroEn(3_500_000).clips.map { it.clip.id }.containsAll(listOf(1L,2L)))
    }

    @Test fun guardarConservaEstiloYRecorridoSinCambiarLaTransicionAnterior() {
        val p=proyecto(luz.copy(arcoiris=true))
        assertEquals(p,Persistencia.deTexto(Persistencia.aTexto(p)))
        val viejo=Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"ajusteTransicion":{"angulo":166}}]}""")!!
        assertEquals(LuzTransicion(),viejo.principal.single().ajusteTransicion.luz)
    }
}
