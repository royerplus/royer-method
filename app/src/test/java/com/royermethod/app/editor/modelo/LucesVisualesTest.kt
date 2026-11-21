package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import com.royermethod.app.editor.motor.PintorLuces
import org.junit.Assert.*
import org.junit.Test

class LucesVisualesTest {
    private fun luz()=LuzTrazo(1,"Cabello",TipoLuz.TRAZO,listOf(PuntoLuz(.1f,.3f),PuntoLuz(.5f,.3f),PuntoLuz(.5f,.7f)))
    private fun clip(l: LuzTrazo=luz())=Clip(1,origen=Origen("foto","foto",TipoOrigen.IMAGEN,400,200),hastaUs=4_000_000,luces=listOf(l))

    @Test fun proyectosAntiguosNoGeneranLuces() {
        val p=Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"}}]}""")!!
        assertTrue(p.principal.single().luces.isEmpty())
    }
    @Test fun recortarTrazoUsaLongitudFisicaYNoIndiceDePuntos() {
        val p=luz().recorrido(EstiloLuz(inicio=.5f,fin=.75f),2f)
        assertEquals(.4f,p.first().x,1e-6f)
        assertEquals(.3f,p.first().y,1e-6f)
        assertEquals(.5f,p.last().x,1e-6f)
        assertEquals(.4f,p.last().y,1e-6f)
        assertTrue(luz().recorrido(EstiloLuz(inicio=.8f,fin=.2f),1f).isEmpty())
    }
    @Test fun giroConservaLaDistanciaRealEnUnElementoPanoramico() {
        val l=luz().copy(puntos=listOf(PuntoLuz(.2f,.5f),PuntoLuz(.8f,.5f)))
        val p=l.recorrido(EstiloLuz(giro=90f,x=.1f),2f)
        assertEquals(.6f,p.first().x,1e-6f)
        assertEquals(-.1f,p.first().y,1e-6f)
        assertEquals(1.1f,p.last().y,1e-6f)
    }
    @Test fun cadaTrazoInterpolaSuColorMovimientoYReveladoConSuCurva() {
        val a=EstiloLuz(color=0xFFFF0000.toInt(),x=0f,fin=0f)
        val b=EstiloLuz(color=0xFF0000FF.toInt(),x=.8f,fin=1f)
        val l=luz().copy(claves=listOf(ClaveLuz(0,a,Curva.LINEAL),ClaveLuz(2_000_000,b)))
        val mitad=l.estiloEn(1_000_000)
        assertEquals(.4f,mitad.x,1e-6f)
        assertEquals(.5f,mitad.fin,1e-6f)
        assertEquals(0xFF800080.toInt(),mitad.color)
        assertEquals(a,l.estiloEn(-1))
        assertEquals(b,l.estiloEn(3_000_000))
    }
    @Test fun editarUnaClaveNoMueveLasOtrasLucesNiLosPuntos() {
        val l=luz().ponerClave(0).ponerClave(2_000_000)
        val edit=l.editar(2_000_000) { it.copy(x=.7f,halo=.1f) }
        assertEquals(0f,edit.estiloEn(0).x,0f)
        assertEquals(.7f,edit.estiloEn(2_000_000).x,0f)
        assertEquals(l.puntos,edit.puntos)
        assertEquals(.7f,edit.quitarClave(0).estiloEn(0).x,0f)
    }
    @Test fun guardarYAbrirConservaLosTrazosYCurvas() {
        val l=luz().copy(claves=listOf(ClaveLuz(0,EstiloLuz(fin=0f),bezier=Bezier.SUAVE),
            ClaveLuz(3_000_000,EstiloLuz(color=0xFF998877.toInt(),x=.2f,halo=.04f))))
        val p=Proyecto(principal=listOf(clip(l)),siguienteId=2)
        assertEquals(p,Persistencia.deTexto(Persistencia.aTexto(p)))
    }
    @Test fun dividirYRecortarConservanLaFaseDeLaCurvaDelTrazo() {
        val l=luz().copy(claves=listOf(ClaveLuz(0,EstiloLuz(fin=0f),bezier=Bezier(.2f,.1f,.7f,1.2f)),ClaveLuz(3_000_000,EstiloLuz(fin=1f))))
        val c=clip(l);val p=Proyecto(principal=listOf(c),siguienteId=2)
        val b=Edicion.dividir(p,1,1_000_000)!!.first.principal[1]
        val r=Edicion.recortar(p,1,1_000_000,4_000_000).principal.single()
        for(t in listOf(0L,200_000,1_500_000)) {
            val esperado=l.estiloEn(t+1_000_000)
            assertEquals(esperado,b.luces.single().estiloEn(t+b.desfasePropiedadesUs))
            assertEquals(esperado,r.luces.single().estiloEn(t+r.desfasePropiedadesUs))
        }
    }
    @Test fun velocidadEscalaTambienElRelojDeLasLuces() {
        val l=luz().copy(claves=listOf(ClaveLuz(0,EstiloLuz(fin=0f),Curva.LINEAL),ClaveLuz(4_000_000,EstiloLuz(fin=1f))))
        val c=Clip(1,origen=Origen("video","video",TipoOrigen.VIDEO,400,200,4_000_000),hastaUs=4_000_000,luces=listOf(l),desfasePropiedadesUs=100_000)
        val b=c.conVelocidad(2f)
        assertEquals(50_000L,b.desfasePropiedadesUs)
        assertEquals(l.estiloEn(1_000_000).fin,b.luces.single().estiloEn(500_000).fin,1e-6f)
    }
    @Test fun elPasoDeDibujoLlevaElRelojLocalYLasLucesDelClip() {
        val c=clip();val p=Proyecto(principal=listOf(c))
        val paso=Escena(p,576,1024,Medidor { _,_->400f to 200f }).cuadroEn(1_200_000).clips.first { !it.esFondo }
        assertEquals(1_200_000L,paso.tiempoLocalUs)
        assertEquals(c.luces,paso.clip.luces)
    }
    @Test fun homografiaInversaConservaPerspectivaYRechazaMatricesDegeneradas() {
        val m=floatArrayOf(.004f,.001f,.0001f,-.001f,.003f,.0002f,-.2f,.1f,1f)
        val n=PintorLuces.inversa(m)!!
        for(col in 0..2)for(row in 0..2) {
            val v=(0..2).sumOf { k-> (m[k*3+row]*n[col*3+k]).toDouble() }.toFloat()
            assertEquals(if(row==col)1f else 0f,v,1e-5f)
        }
        assertNull(PintorLuces.inversa(FloatArray(9)))
    }
}
