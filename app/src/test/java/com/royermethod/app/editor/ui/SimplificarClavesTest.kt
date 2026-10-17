package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PiezaVideo
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.RegionUv
import com.royermethod.app.editor.modelo.Simplificacion
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.Tolerancia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** El simplificador tal y como lo usan los paneles: probar, aplicar, deshacer, restaurar. */
class SimplificarClavesTest {

    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)

    /** Un movimiento suave de dos claves guardado como una clave lineal por fotograma (lo que trae una replica). */
    private fun porFotograma(n: Int = 61): List<Clave> {
        val fin = Encuadre(escala = 1.3f, x = 0.3f, y = -0.2f, rotacion = 10f)
        val total = Linea.tiempoDe((n - 1).toLong())
        return List(n) { i ->
            val t = Linea.tiempoDe(i.toLong())
            Clave(t, Encuadre().hacia(fin, Escena.curva(Curva.SUAVE, t.toFloat() / total)), Curva.LINEAL)
        }
    }

    private fun proyecto(claves: List<Clave> = porFotograma(), pieza: List<Clave> = emptyList()) = Proyecto(
        principal = listOf(
            Clip(1, origen = foto, hastaUs = 2_500_000, claves = claves,
                piezas = if (pieza.isEmpty()) emptyList() else listOf(PiezaVideo("Arriba", RegionUv(abajo = 0.5f), claves = pieza)))
        ),
        siguienteId = 2
    )

    @Test fun aplicarEsUnPasoYDeshacerDejaElOriginalExacto() {
        val p = proyecto()
        val e = EstadoEditor(p)
        val d = DuenoClaves.Bloque(1)
        val prueba = Simplificador.probar(e.proyecto, d)!!
        // Probar no cambia nada
        assertSame(p, e.proyecto)
        val s = prueba.informe
        assertEquals(61, s.antes)
        assertTrue("quedan ${s.despues}", s.despues <= 3)
        assertTrue(s.errorMaxPx <= 0.5f)
        assertTrue(s.errorMaxOpacidad <= 0.5f / 255f)

        e.hacer { Simplificador.aplicar(it, prueba) }
        val c = e.proyecto.principal[0]
        assertEquals(s.claves, c.claves)
        assertEquals(p.principal[0].claves, c.clavesOriginales)
        assertEquals(s.claves, Simplificador.claves(e.proyecto, d))
        assertEquals(p.principal[0].claves, Simplificador.originales(e.proyecto, d))

        e.deshacer()
        assertEquals(p, e.proyecto)
        e.rehacer()
        assertEquals(s.claves, e.proyecto.principal[0].claves)
    }

    @Test fun restaurarYOlvidarSonUnPasoCadaUno() {
        val p = proyecto()
        val e = EstadoEditor(p)
        val d = DuenoClaves.Bloque(1)
        e.hacer { Simplificador.aplicar(it, Simplificador.probar(it, d)!!) }
        val simplificado = e.proyecto

        e.hacer { Simplificador.restaurar(it, d) }
        assertEquals(p, e.proyecto)
        assertNull(Simplificador.originales(e.proyecto, d))
        e.deshacer()
        assertEquals(simplificado, e.proyecto)

        e.hacer { Simplificador.olvidar(it, d) }
        assertNull(e.proyecto.principal[0].clavesOriginales)
        assertEquals(simplificado.principal[0].claves, e.proyecto.principal[0].claves)
        e.deshacer()
        assertEquals(simplificado, e.proyecto)
        // Sin original, restaurar y olvidar no gastan un paso
        val sin = EstadoEditor(p)
        sin.hacer { Simplificador.restaurar(it, d) }
        sin.hacer { Simplificador.olvidar(it, d) }
        assertTrue(!sin.puedeDeshacer)
    }

    @Test fun lasClavesDeUnaParteIgual() {
        val p = proyecto(claves = emptyList(), pieza = porFotograma(40))
        val e = EstadoEditor(p)
        val d = DuenoClaves.Parte(1, 0)
        val prueba = Simplificador.probar(e.proyecto, d)!!
        assertTrue(prueba.informe.despues < 40)
        e.hacer { Simplificador.aplicar(it, prueba) }
        val pieza = e.proyecto.principal[0].piezas[0]
        assertEquals(prueba.informe.claves, pieza.claves)
        assertEquals(p.principal[0].piezas[0].claves, pieza.clavesOriginales)
        e.deshacer()
        assertEquals(p, e.proyecto)
        e.rehacer()
        e.hacer { Simplificador.restaurar(it, d) }
        assertEquals(p, e.proyecto)
        // Una parte que no existe no hace nada
        assertNull(Simplificador.probar(p, DuenoClaves.Parte(1, 3)))
        assertNull(Simplificador.claves(p, DuenoClaves.Parte(9, 0)))
    }

    @Test fun siElProyectoCambioDesdeLaPruebaSeVuelveASimplificarSobreElDeAhora() {
        val p = proyecto()
        val prueba = Simplificador.probar(p, DuenoClaves.Bloque(1))!!
        // Entretanto cambio otra cosa (el formato, que cambia los pixeles de exportacion)
        val otro = p.copy(formato = com.royermethod.app.editor.modelo.Formato.CUADRADO)
        val r = Simplificador.aplicar(otro, prueba)
        assertEquals(Simplificador.probar(otro, DuenoClaves.Bloque(1))!!.resultado, r)
        assertEquals(com.royermethod.app.editor.modelo.Formato.CUADRADO, r.formato)
        // Y si no cambio, el resultado de la prueba tal cual
        assertSame(prueba.resultado, Simplificador.aplicar(p, prueba))
    }

    @Test fun laComparacionConElOriginalEsLaDelInforme() {
        val p = proyecto()
        val prueba = Simplificador.probar(p, DuenoClaves.Bloque(1))!!
        assertNull(Simplificador.contraOriginal(p, DuenoClaves.Bloque(1)))
        val q = Simplificador.aplicar(p, prueba)
        val (px, op) = Simplificador.contraOriginal(q, DuenoClaves.Bloque(1))!!
        assertEquals(prueba.informe.errorMaxPx, px, 1e-4f)
        assertEquals(prueba.informe.errorMaxOpacidad, op, 1e-6f)
        assertTrue(px <= 0.5f)
    }

    @Test fun conMasToleranciaNuncaQuedanMasClaves() {
        val ruido = List(50) { i ->
            val t = Linea.tiempoDe(i.toLong())
            Clave(t, Encuadre(x = 0.002f * ((i * 7919) % 13 - 6), y = i / 200f), Curva.LINEAL)
        }
        val p = proyecto(ruido)
        var antes = Int.MAX_VALUE
        for (px in Simplificador.TOLERANCIAS) {
            val tol = Simplificador.tolerancia(px)
            assertEquals(px, tol.px, 0f)
            val s = Simplificador.probar(p, DuenoClaves.Bloque(1), tol)!!.informe
            assertTrue(s.despues <= antes)
            assertTrue(s.errorMaxPx <= px)
            antes = s.despues
        }
        // La de por defecto es la documentada en Trayectoria
        assertEquals(Tolerancia(), Simplificador.tolerancia(0.5f))
        assertEquals(1f / 255f, Simplificador.tolerancia(1f).opacidad, 0f)
    }

    @Test fun losTextosSonCortosYDicenLaTolerancia() {
        val s = Simplificacion(emptyList(), 61, 2, 0.42f, 0f, 10, 500, Tolerancia())
        assertEquals("61 -> 2 claves", Simplificador.resumen(s))
        assertEquals("error max 0.42 px (limite 0.5 px)", Simplificador.textoError(s.errorMaxPx, s.tolerancia))
        assertEquals("opacidad 0/255 (limite 0.5/255)", Simplificador.textoOpacidad(s.errorMaxOpacidad, s.tolerancia))
        assertEquals("opacidad 0.3/255 (limite 1/255)", Simplificador.textoOpacidad(0.3f / 255f, Simplificador.tolerancia(1f)))
        assertEquals("1 px", Simplificador.textoPx(1f))
        assertEquals("0.5 px", Simplificador.textoPx(0.5f))
        assertEquals("2 px", Simplificador.textoPx(2f))
    }

    @Test fun sinClavesNoHayNadaQueProbar() {
        val p = proyecto(claves = emptyList())
        assertNull(Simplificador.probar(p, DuenoClaves.Bloque(1)))
        assertNotNull(Simplificador.claves(p, DuenoClaves.Bloque(1)))
        assertNull(Simplificador.probar(p, DuenoClaves.Bloque(7)))
        // Unas pocas que no sobran: el proyecto queda igual
        val pocas = proyecto(listOf(Clave(0, Encuadre()), Clave(1_000_000, Encuadre(x = 0.5f))))
        val r = Simplificador.probar(pocas, DuenoClaves.Bloque(1))!!
        assertEquals(r.informe.antes, r.informe.despues)
        assertEquals(pocas, r.resultado)
        assertTrue(Edicion.clip(r.resultado, 1)!!.clavesOriginales == null)
    }
}
