package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class HeadBopPersistenciaTest {
    @Test fun todosLosRolesYParametrosSobrevivenGuardarYAbrir() {
        val capas = RolHeadBop.entries.mapIndexed { i, rol ->
            Capa(100_000L * i, i, Clip(i.toLong() + 1, texto = Texto(rol.nombre),
                headBop = HeadBop(rol, 200_000, 2_900_000, 123.4f, 0.7f, -33_000,
                    1.5f, 75_000, 0.33f, 0.67f, 0.22f, 0.11f, 90_000,
                    activo = false, recorteUs = 53_000, duracionBaseUs = 3_000_000, giro = 8.5f, rebote = 0.04f,
                    arrastre = 0.35f, naturalidad = 0.9f)))
        }
        val p = Proyecto(capas = capas, siguienteId = 6)
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
    }

    @Test fun proyectoAnteriorYRolDesconocidoSiguenAbriendo() {
        val anterior = """{"principal":[{"id":1,"texto":{"contenido":"x"}}]}"""
        assertNull(Persistencia.deTexto(anterior)!!.principal.single().headBop)
        val desconocido = """{"principal":[{"id":1,"texto":{"contenido":"x"},"headBop":{"rol":"FUTURO","inicio":0,"fin":1000000}}]}"""
        assertNull(Persistencia.deTexto(desconocido)!!.principal.single().headBop)
    }

    @Test fun conservaPivotesExternosParaPiezasRecortadas() {
        val h = HeadBop(RolHeadBop.CABELLO, finUs = 3_000_000,
            cuelloX = 0.693f, cuelloY = 1.02f, pivoteX = -0.25f, pivoteY = 1.3f)
        val p = Proyecto(principal = listOf(Clip(1, texto = Texto("flequillo"), headBop = h)))
        assertEquals(h, Persistencia.deTexto(Persistencia.aTexto(p))!!.principal.single().headBop)
    }

    @Test fun lecturaRechazaTramosVaciosYAcotaParametros() {
        fun leer(datos: String) = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"headBop":$datos}]}""")!!.principal.single().headBop
        assertNull(leer("""{"rol":"CABEZA","inicio":100,"fin":100}"""))
        val h = leer("""{"rol":"CABELLO","inicio":-5,"fin":1000000,"bpm":-1,"intensidad":8,"pivoteX":4,"cuelloY":-2,"retardo":-50,"desfase":-1000}""")!!
        assertEquals(0L, h.inicioUs)
        assertEquals(1f, h.bpm, 0f)
        assertEquals(3f, h.intensidad, 0f)
        assertEquals(2f, h.pivoteX, 0f)
        assertEquals(-1f, h.cuelloY, 0f)
        assertEquals(0L, h.retardoUs)
        assertEquals(-1000L, h.desfaseUs)
    }

    @Test fun naturalidadAusenteConservaMovimientoAnteriorYAcotaValoresNuevos() {
        fun leer(naturalidad: String = "") = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"headBop":{"rol":"CABEZA","inicio":0,"fin":1000000$naturalidad}}]}""")!!.principal.single().headBop!!
        assertEquals(0f, leer().naturalidad, 0f)
        assertEquals(0f, leer(",\"naturalidad\":-3").naturalidad, 0f)
        assertEquals(1f, leer(",\"naturalidad\":4").naturalidad, 0f)
        assertEquals(0.9f, leer(",\"naturalidad\":0.9").naturalidad, 0f)
    }

    @Test fun copiarRitmoComparteNaturalidadSinCambiarPivotesNiIntensidad() {
        val cabeza = Clip(1, texto = Texto("cabeza"), headBop = HeadBop(finUs = 1_000_000,
            bpm = 107.7f, naturalidad = 0.9f))
        val cabello = Clip(2, texto = Texto("cabello"), headBop = HeadBop(RolHeadBop.CABELLO,
            finUs = 1_000_000, intensidad = 0.6f, cuelloX = 0.3f, pivoteY = 0.2f))
        val p = Proyecto(principal = listOf(cabeza), capas = listOf(Capa(0, 1, cabello)))
        val sincronizado = EdicionHeadBop.sincronizar(p, cabeza.id).capas.single().clip.headBop!!
        assertEquals(0.9f, sincronizado.naturalidad, 0f)
        assertEquals(107.7f, sincronizado.bpm, 0f)
        assertEquals(0.6f, sincronizado.intensidad, 0f)
        assertEquals(0.3f, sincronizado.cuelloX, 0f)
        assertEquals(0.2f, sincronizado.pivoteY, 0f)
    }
}
