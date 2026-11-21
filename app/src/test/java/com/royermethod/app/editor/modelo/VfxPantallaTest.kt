package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.Persistencia
import org.junit.Assert.*
import org.junit.Test

class VfxPantallaTest {
    private val foto=Origen("f","f",TipoOrigen.IMAGEN,1080,1300)
    private fun clip(vararg e:EfectoVideo)=Clip(1,origen=foto,hastaUs=4_000_000,efectos=e.toList())
    private fun efecto(tipo:TipoEfectoVideo)=EfectoVideo(tipo,finUs=2_000_000,ajustes=CatalogoEfectos.ajustesDe(tipo))

    @Test fun losEfectosViejosYLosInactivosConservanElCaminoNeutro() {
        assertSame(VfxPantalla.NEUTRO,clip().vfxPantallaEn(100_000))
        assertSame(VfxPantalla.NEUTRO,clip(efecto(TipoEfectoVideo.FLASH)).vfxPantallaEn(100_000))
        val e=efecto(TipoEfectoVideo.RESPLANDOR)
        assertSame(VfxPantalla.NEUTRO,clip(e.copy(activo=false)).vfxPantallaEn(100_000))
        assertSame(VfxPantalla.NEUTRO,clip(e.copy(intensidad=0f)).vfxPantallaEn(100_000))
        assertSame(VfxPantalla.NEUTRO,clip(e).vfxPantallaEn(e.finUs))
        assertFalse(VfxPantalla.NEUTRO.activo)
    }

    @Test fun cadaMitadSeEnciendeYSeApagaAntesDeLaOtra() {
        val c=clip(efecto(TipoEfectoVideo.PULSO_MITADES))
        val primera=c.vfxPantallaEn(83_333).mitades!!
        val segunda=c.vfxPantallaEn(250_000).mitades!!
        assertEquals(-1f,primera.lado,0f)
        assertEquals(1f,segunda.lado,0f)
        assertEquals(1f,primera.fuerza,.0001f)
        assertEquals(1f,segunda.fuerza,.0001f)
        assertEquals(0f,c.vfxPantallaEn(0).mitades!!.fuerza,0f)
        assertEquals(0f,c.vfxPantallaEn(166_667).mitades!!.fuerza,.0001f)
        assertEquals(primera.lado,c.vfxPantallaEn(416_667).mitades!!.lado,0f)
    }

    @Test fun invertirCambiaElOrdenSinAlterarLaIntensidad() {
        val e=efecto(TipoEfectoVideo.PULSO_MITADES)
        val a=clip(e).vfxPantallaEn(100_000).mitades!!
        val b=clip(e.copy(ajustes=e.ajustes.copy(invertir=true))).vfxPantallaEn(100_000).mitades!!
        assertEquals(a.copy(lado=-a.lado),b)
    }

    @Test fun laRapidezCambiaLaAlternanciaPeroNoLaGeometria() {
        val e=efecto(TipoEfectoVideo.PULSO_MITADES).copy(ajustes=AjustesEfecto(angulo=90f,centroX=.2f,centroY=-.1f,color=0xFF66AAFF.toInt(),halo=.9f,suavidad=.04f))
        val a=clip(e).vfxPantallaEn(250_000).mitades!!
        val b=clip(e.copy(rapidez=2f)).vfxPantallaEn(125_000).mitades!!
        assertEquals(a,b)
        assertEquals(0f,a.dirX,.00001f)
        assertEquals(1f,a.dirY,.00001f)
        assertEquals(.2f,a.centroX,0f)
        assertEquals(-.1f,a.centroY,0f)
        assertEquals(.9f,a.halo,0f)
        assertEquals(0xFF66AAFF.toInt(),a.color)
    }

    @Test fun recortarYMoverConservaLosPulsosYLaFaseDelArcoiris() {
        for(tipo in listOf(TipoEfectoVideo.PULSO_MITADES,TipoEfectoVideo.ARCOIRIS_BORDES,TipoEfectoVideo.RESPLANDOR,TipoEfectoVideo.SEMITONO)) {
            val e=efecto(tipo).copy(inicioUs=200_000,finUs=2_000_000,desfaseUs=70_000,rapidez=1.7f,
                duracionBaseUs=1_800_000,fundidoEntradaUs=100_000,fundidoSalidaUs=200_000)
            val original=clip(e)
            val recortado=clip(e.recortado(600_000,1_800_000)!!)
            val rango=clip(e.conRango(600_000,1_800_000))
            val movido=clip(e.desplazado(300_000,4_000_000))
            for(t in 600_000L until 1_800_000L step 81_111) {
                assertEquals("$tipo $t al cortar",original.vfxPantallaEn(t),recortado.vfxPantallaEn(t-600_000))
                assertEquals("$tipo $t al cambiar rango",original.vfxPantallaEn(t),rango.vfxPantallaEn(t))
                assertEquals("$tipo $t al mover",original.vfxPantallaEn(t),movido.vfxPantallaEn(t+300_000))
            }
        }
    }

    @Test fun fundidosYFuerzaSeAplicanATodosLosTipos() {
        for(tipo in listOf(TipoEfectoVideo.PULSO_MITADES,TipoEfectoVideo.ARCOIRIS_BORDES,TipoEfectoVideo.RESPLANDOR,TipoEfectoVideo.SEMITONO)) {
            val e=efecto(tipo).copy(intensidad=.8f,fundidoEntradaUs=500_000)
            val a=clip(e).vfxPantallaEn(250_000)
            val fuerza=a.mitades?.fuerza ?: a.resplandor?.fuerza ?: a.trama?.fuerza ?: a.arcoiris!!.fuerza
            assertEquals("$tipo",.4f,fuerza,.00001f)
        }
    }

    @Test fun cadaTipoSeCombinaYLaUltimaVersionActivaManda() {
        val m=efecto(TipoEfectoVideo.PULSO_MITADES)
        val b=efecto(TipoEfectoVideo.RESPLANDOR)
        val t=efecto(TipoEfectoVideo.SEMITONO)
        val r=efecto(TipoEfectoVideo.ARCOIRIS_BORDES)
        val segundo=b.copy(inicioUs=500_000,ajustes=b.ajustes.copy(color=0xFFFF4488.toInt(),umbral=.8f))
        val c=clip(m,b,t,r,segundo)
        assertTrue(c.vfxPantallaEn(100_000).activo)
        val v=c.vfxPantallaEn(600_000)
        assertNotNull(v.mitades);assertNotNull(v.resplandor);assertNotNull(v.trama);assertNotNull(v.arcoiris)
        assertEquals(b.ajustes.color,c.vfxPantallaEn(100_000).resplandor!!.color)
        assertEquals(segundo.ajustes.color,v.resplandor!!.color)
        assertEquals(.8f,v.resplandor.umbral,0f)
    }

    @Test fun parametrosNoFinitosOExtremosNoLleganAlShader() {
        val malos=AjustesEfecto(angulo=Float.NaN,centroX=Float.NaN,centroY=Float.POSITIVE_INFINITY,
            grosor=-100f,halo=Float.NaN,umbral=Float.NaN,suavidad=Float.NaN)
        val c=clip(*listOf(TipoEfectoVideo.PULSO_MITADES,TipoEfectoVideo.ARCOIRIS_BORDES,TipoEfectoVideo.RESPLANDOR,TipoEfectoVideo.SEMITONO)
            .map { efecto(it).copy(ajustes=malos) }.toTypedArray())
        val v=c.vfxPantallaEn(250_000)
        assertTrue(v.mitades!!.dirX.isFinite());assertTrue(v.mitades.dirY.isFinite())
        assertEquals(0f,v.mitades.centroX,0f);assertEquals(0f,v.mitades.centroY,0f)
        assertEquals(.5f,v.mitades.halo,0f);assertEquals(.02f,v.mitades.suavidad,0f)
        assertEquals(.001f,v.resplandor!!.radio,0f);assertEquals(.6f,v.resplandor.umbral,0f)
        assertEquals(.002f,v.trama!!.paso,0f);assertTrue(v.trama.angulo.isFinite())
        assertEquals(.001f,v.arcoiris!!.radio,0f);assertEquals(.08f,v.arcoiris.umbral,0f)
    }

    @Test fun catalogoOfreceSoloLosControlesQueCadaHerramientaUsa() {
        val m=CatalogoEfectos.parametros(TipoEfectoVideo.PULSO_MITADES)
        assertTrue(m.containsAll(listOf(Parametro.COLOR,Parametro.HALO,Parametro.CENTRO,Parametro.ANGULO,Parametro.RAPIDEZ)))
        val b=CatalogoEfectos.parametros(TipoEfectoVideo.RESPLANDOR)
        assertTrue(b.containsAll(listOf(Parametro.COLOR,Parametro.HALO,Parametro.UMBRAL)))
        assertFalse(b.contains(Parametro.RAPIDEZ))
        val t=CatalogoEfectos.parametros(TipoEfectoVideo.SEMITONO)
        assertTrue(t.containsAll(listOf(Parametro.LINEAS,Parametro.GROSOR,Parametro.ANGULO)))
        assertEquals(0xFF181020.toInt(),CatalogoEfectos.ajustesDe(TipoEfectoVideo.SEMITONO).color)
    }

    @Test fun guardarYReabrirConservaColorHaloUmbralYElRelojExactos() {
        val efectos=listOf(TipoEfectoVideo.PULSO_MITADES,TipoEfectoVideo.RESPLANDOR,TipoEfectoVideo.SEMITONO,TipoEfectoVideo.ARCOIRIS_BORDES)
            .mapIndexed { i,t -> efecto(t).copy(id=i+1L,desfaseUs=44_321,rapidez=1.7f,
                ajustes=CatalogoEfectos.ajustesDe(t).copy(color=0xFF173FEA.toInt(),halo=.83f,umbral=.37f,vertical=true)) }
        val p=Proyecto(principal=listOf(clip(*efectos.toTypedArray())),siguienteId=2)
        val abierto=Persistencia.deTexto(Persistencia.aTexto(p))!!
        assertEquals(p,abierto)
        for(t in 0L until 2_000_000L step 137_911)
            assertEquals(p.principal.single().vfxPantallaEn(t),abierto.principal.single().vfxPantallaEn(t))
    }

    @Test fun proyectosAnterioresConservanLosValoresOpcionalesNeutros() {
        val p=Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"efectos":[{"tipo":"FLASH","inicio":0,"fin":1000000,"ajustes":{"angulo":30}}]}]}""")!!
        val a=p.principal.single().efectos.single().ajustes
        assertEquals(0xFFFFFFFF.toInt(),a.color)
        assertEquals(.5f,a.halo,0f)
        assertEquals(.6f,a.umbral,0f)
        assertSame(VfxPantalla.NEUTRO,p.principal.single().vfxPantallaEn(500_000))
    }

    @Test fun limitesPersistidosSeNormalizanAntesDeDibujar() {
        val p=Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"efectos":[{"tipo":"RESPLANDOR","inicio":0,"fin":1000000,"ajustes":{"color":-65536,"halo":8,"umbral":-4}}]}]}""")!!
        val e=p.principal.single().efectos.single()
        assertEquals(1f,e.ajustes.halo,0f)
        assertEquals(0f,e.ajustes.umbral,0f)
        assertEquals(0xFFFF0000.toInt(),p.principal.single().vfxPantallaEn(500_000).resplandor!!.color)
    }

    @Test fun alcanceCompartidoConservaFaseEntreClipsYNoContaminaOtros() {
        val ef=efecto(TipoEfectoVideo.PULSO_MITADES)
        val ajuste=Clip(9,esAjuste=true,hastaUs=2_000_000,efectos=listOf(ef),afectaA=setOf(1))
        val capa=Capa(1_000_000,0,ajuste)
        val primero=clip().copy(id=1)
        val segundo=clip().copy(id=2)
        val p=Proyecto(principal=listOf(primero),capas=listOf(capa))
        val a=AlcanceCapas.conEfectos(p,primero,0)
        val b=AlcanceCapas.conEfectos(p,segundo,800_000)
        assertEquals(clip(ef).vfxPantallaEn(250_000),a.vfxPantallaEn(1_250_000))
        assertSame(VfxPantalla.NEUTRO,b.vfxPantallaEn(450_000))
        val todos=p.copy(capas=listOf(capa.copy(clip=ajuste.copy(afectaA=null))))
        val futuro=AlcanceCapas.conEfectos(todos,segundo,800_000)
        assertEquals(a.vfxPantallaEn(1_250_000),futuro.vfxPantallaEn(450_000))
        assertSame(VfxPantalla.NEUTRO,futuro.vfxPantallaEn(2_200_000))
        // Evaluar otra capa no deja indicadores ni estado mutable en la siguiente.
        a.vfxPantallaEn(1_100_000)
        assertSame(VfxPantalla.NEUTRO,b.vfxPantallaEn(450_000))
    }
}
