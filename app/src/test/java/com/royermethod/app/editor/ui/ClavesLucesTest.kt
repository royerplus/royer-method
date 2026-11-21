package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class ClavesLucesTest {
    private fun estado():EstadoEditor {
        val l=LuzTrazo(1,"Luz",TipoLuz.TRAZO,listOf(PuntoLuz(.1f,.2f),PuntoLuz(.8f,.5f)))
        val c=Clip(2,texto=Texto("Luz"),hastaUs=2_000_000,desfasePropiedadesUs=700_000,luces=listOf(l))
        return EstadoEditor(Proyecto(principal=listOf(Clip(1,texto=Texto("Antes"),hastaUs=2_000_000),c,
            Clip(3,texto=Texto("Después"),hastaUs=2_000_000)),siguienteId=4)).also {
            it.elegir(Seleccion.Bloque(2));it.luzElegida=1
        }
    }
    @Test fun cabezalFueraDelClipNoCreaClavesAlInicioNiReemplazaLasExistentes() {
        val e=estado();val p=e.proyecto
        for(t in listOf(0L,5_000_000L)) {
            e.pausarEn(t)
            assertNull(e.localElegido)
            ponerClaveLuz(e,false)
            animacionLuz(e,0)
            assertEquals(p,e.proyecto)
            assertFalse(e.puedeDeshacer)
        }
    }
    @Test fun claveYPresetDentroDelClipUsanElDesfaseYSeDeshacenPorAccion() {
        val e=estado();val p=e.proyecto
        e.pausarEn(2_400_000)
        ponerClaveLuz(e,false)
        assertEquals(1_100_000L,e.clipElegido!!.luces.single().claves.single().tiempoUs)
        e.deshacer();assertEquals(p,e.proyecto)
        animacionLuz(e,0)
        val ks=e.clipElegido!!.luces.single().claves
        assertEquals(listOf(1_100_000L,1_700_000L),ks.map { it.tiempoUs })
        assertEquals(0f,ks.first().estilo.fin,0f)
        assertEquals(1f,ks.last().estilo.fin,0f)
        e.deshacer();assertEquals(p,e.proyecto)
    }
}
