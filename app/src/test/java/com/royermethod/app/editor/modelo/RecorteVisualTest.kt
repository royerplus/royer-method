package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test

class RecorteVisualTest {
    private fun clip() = Clip(1, origen = Origen("foto","foto",TipoOrigen.IMAGEN,400,200), hastaUs = 4_000_000)

    @Test fun desactivadoConservaElProyectoAnteriorYElFondo() {
        val p = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"}}]}""")!!
        assertFalse(p.principal.single().recorteVisual.activo)
        assertEquals(RecorteVisual(),p.principal.single().recorteVisual)
        assertFalse(RecorteVisual(croma=Croma(activo=true,intensidad=0f)).activo)
    }
    @Test fun circuloYCuadradoUsanElLadoCortoSinAplastarse() {
        for(tipo in listOf(FormaRecorte.CIRCULO,FormaRecorte.CUADRADO)) {
            val m=MascaraForma(tipo=tipo,ancho=.8f,alto=.4f)
            assertEquals(.4f,m.semiejes(2f to 1f).first,0f)
            assertEquals(.4f,m.semiejes(2f to 1f).second,0f)
            assertEquals(.4f,m.semiejes(1f to 2f).first,0f)
        }
        assertEquals(.8f,MascaraForma(tipo=FormaRecorte.RECTANGULO).semiejes(2f to 1f).first,0f)
    }
    @Test fun asasSiguenCentroTamanoYGiroEnCoordenadasDelElemento() {
        val m=MascaraForma(tipo=FormaRecorte.CUADRADO,x=.4f,y=.6f,ancho=.4f,giro=90f)
        val esquina=m.asas(2f to 1f).first()
        assertEquals(.5f,esquina.first,1e-6f)
        assertEquals(.4f,esquina.second,1e-6f)
    }
    @Test fun lasFormasDecorativasTienenVerticesFinitosYCompartenGeometria() {
        assertEquals(10,MascaraForma(tipo=FormaRecorte.ESTRELLA).poligono().size)
        assertEquals(24,MascaraForma(tipo=FormaRecorte.CORAZON).poligono().size)
        for(tipo in FormaRecorte.values()) {
            val puntos=MascaraForma(tipo=tipo).contorno(2f to 1f)
            assertTrue(puntos.size>=4)
            assertTrue(puntos.all { it.first.isFinite() && it.second.isFinite() })
        }
    }
    @Test fun guardarYAbrirConservaColorFormasYCurvasIndependientes() {
        val c=clip().copy(recorteVisual=RecorteVisual(Croma(true,0xFF13579B.toInt(),.75f,.3f,.07f,.8f,true),
            MascaraForma(FormaRecorte.CORAZON,.3f,.6f,.7f,.4f,43f,.05f,.1f,true)),
            propiedades=mapOf(Propiedad.MASCARA_X to listOf(ClaveValor(0,.3,bezier=Bezier.SUAVE),ClaveValor(2_000_000,.7))))
        val p=Proyecto(principal=listOf(c),siguienteId=2)
        assertEquals(p,Persistencia.deTexto(Persistencia.aTexto(p)))
    }
    @Test fun cromaNoDejaQueElFondoDesenfocadoRelleneLaZonaBorrada() {
        val c=clip().copy(encuadre=Encuadre(escala=.4f))
        fun dibujar(x:Clip)=Escena(Proyecto(principal=listOf(x),fondo=Fondo.DESENFOCADO),576,1024,
            Medidor { _,_->400f to 200f }).cuadroEn(500_000)
        assertTrue(dibujar(c).clips.any { it.esFondo })
        val k=c.copy(recorteVisual=RecorteVisual(croma=Croma(activo=true)))
        assertTrue(dibujar(k).clips.none { it.esFondo })
        val m=c.copy(recorteVisual=RecorteVisual(mascara=MascaraForma(tipo=FormaRecorte.CIRCULO)))
        assertTrue(dibujar(m).clips.none { it.esFondo })
    }
    @Test fun clavesDeMascaraYCromaSeAnimanSinCambiarElValorBase() {
        val c=clip().copy(recorteVisual=RecorteVisual(croma=Croma(activo=true)),propiedades=mapOf(
            Propiedad.MASCARA_X to listOf(ClaveValor(0,.2,Curva.LINEAL),ClaveValor(2_000_000,.8)),
            Propiedad.CROMA_INTENSIDAD to listOf(ClaveValor(0,0.0,Curva.ENTRADA),ClaveValor(2_000_000,1.0))))
        val d=c.propiedadesEn(1_000_000).recorteVisual
        assertEquals(.5f,d.mascara.x,1e-6f)
        assertTrue(d.croma.intensidad<.5f)
        assertEquals(1f,c.recorteVisual.croma.intensidad,0f)
    }
    @Test fun cortarYRecortarConservaElRelojDeLaMascara() {
        val c=clip().copy(propiedades=mapOf(Propiedad.MASCARA_ANCHO to listOf(
            ClaveValor(0,.1,bezier=Bezier(.2f,.1f,.7f,1.2f)),ClaveValor(3_000_000,1.0))))
        val p=Proyecto(principal=listOf(c),siguienteId=2)
        val b=Edicion.dividir(p,1,1_000_000)!!.first.principal[1]
        val r=Edicion.recortar(p,1,1_000_000,4_000_000).principal.single()
        for(t in listOf(0L,200_000,1_500_000)) {
            assertEquals(c.propiedadesEn(t+1_000_000).recorteVisual.mascara.ancho,b.propiedadesEn(t).recorteVisual.mascara.ancho,1e-6f)
            assertEquals(c.propiedadesEn(t+1_000_000).recorteVisual.mascara.ancho,r.propiedadesEn(t).recorteVisual.mascara.ancho,1e-6f)
        }
    }
    @Test fun formaEInversionCambianJustoAlLlegarALaClave() {
        val c=clip().copy(propiedades=mapOf(
            Propiedad.MASCARA_FORMA to listOf(ClaveValor(0,0.0),ClaveValor(1_000_000,FormaRecorte.CIRCULO.ordinal.toDouble())),
            Propiedad.MASCARA_INVERTIR to listOf(ClaveValor(0,0.0),ClaveValor(1_000_000,1.0))))
        assertEquals(FormaRecorte.NINGUNA,c.propiedadesEn(999_999).recorteVisual.mascara.tipo)
        assertFalse(c.propiedadesEn(999_999).recorteVisual.mascara.invertir)
        assertEquals(FormaRecorte.CIRCULO,c.propiedadesEn(1_000_000).recorteVisual.mascara.tipo)
        assertTrue(c.propiedadesEn(1_000_000).recorteVisual.mascara.invertir)
    }
    @Test fun valoresGuardadosSeAcotanSinRomperProyectosAntiguos() {
        val p=Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"recorteVisual":{"croma":{"intensidad":5,"tolerancia":-2},"mascara":{"tipo":"FUTURA","ancho":0,"alto":9}}}]}""")!!
        val r=p.principal.single().recorteVisual
        assertEquals(1f,r.croma.intensidad,0f)
        assertEquals(0f,r.croma.tolerancia,0f)
        assertEquals(FormaRecorte.NINGUNA,r.mascara.tipo)
        assertEquals(.02f,r.mascara.ancho,0f)
        assertEquals(2f,r.mascara.alto,0f)
    }
}
