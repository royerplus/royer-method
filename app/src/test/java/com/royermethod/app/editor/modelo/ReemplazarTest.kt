package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.ui.avisoReemplazo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReemplazarTest {
    private val animacion = Origen("content://viejo", "animacion.mp4", TipoOrigen.VIDEO, 1080, 1080, duracionUs = 6_000_000, fps = 30f, conAudio = true)
    private val nueva = Origen("content://nuevo", "animacion-v2.mp4", TipoOrigen.VIDEO, 1080, 1080, duracionUs = 6_000_000, fps = 30f, conAudio = true)
    private val otro = Origen("content://otro", "otro.mp4", TipoOrigen.VIDEO, 720, 1280, duracionUs = 3_000_000, fps = 30f)
    private val foto = Origen("content://foto", "foto.png", TipoOrigen.IMAGEN, 1000, 1000)

    /** Como la edicion de Royer: el video partido en tres con Dividir, cada trozo retocado, mas otro video, una capa y un sonido del mismo archivo. */
    private fun edicion(): Proyecto {
        var (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(animacion, otro))
        p = Edicion.dividir(p, ids[0], 2_000_000)!!.first
        p = Edicion.dividir(p, p.principal[1].id, 4_000_000)!!.first
        p = p.copy(principal = p.principal.mapIndexed { i, c ->
            if (c.origen?.uri != animacion.uri) c
            else c.copy(
                encuadre = Encuadre(escala = 1.2f + i * 0.1f, x = 0.05f * i),
                velocidad = if (i == 1) 2f else 1f,
                efectos = listOf(EfectoVideo(TipoEfectoVideo.SACUDIDA, 0, 300_000)),
                transicion = if (i == 0) Transicion.entries.last() else c.transicion
            )
        })
        p = Edicion.agregarSuperpuesto(p, animacion, 1_000_000).first
        p = Edicion.agregarAudio(p, animacion, 0).first
        return p
    }

    @Test
    fun cambiaElArchivoEnTodosLosBloquesYNoTocaNadaMas() {
        val p = edicion()
        val r = Edicion.reemplazar(p, animacion.uri, nueva)
        assertTrue(r is Edicion.Reemplazo.Hecho)
        val (q, usos, recortados) = r as Edicion.Reemplazo.Hecho
        // Tres trozos de la principal, la capa y el sonido
        assertEquals(5, usos)
        assertEquals(0, recortados)
        assertTrue(q.principal.none { it.origen?.uri == animacion.uri })
        assertTrue(q.capas.all { it.clip.origen == nueva })
        assertTrue(q.audios.all { it.origen == nueva })
        // Todo lo demas, igual: solo cambia el archivo
        fun sinOrigen(x: Proyecto) = x.copy(
            principal = x.principal.map { if (it.origen?.uri == otro.uri) it else it.copy(origen = null, texto = Texto("")) },
            capas = x.capas.map { it.copy(clip = it.clip.copy(origen = null, texto = Texto(""))) },
            audios = x.audios.map { it.copy(origen = otro) }
        )
        assertEquals(sinOrigen(p), sinOrigen(q))
        assertEquals(Linea.duracion(p), Linea.duracion(q))
        assertEquals(otro, q.principal.last().origen)
    }

    @Test
    fun siElNuevoEsUnPocoMasCortoSeRecortaElFinal() {
        val p = edicion()
        val corto = nueva.copy(duracionUs = 5_950_000)
        val (q, usos, recortados) = Edicion.reemplazar(p, animacion.uri, corto) as Edicion.Reemplazo.Hecho
        assertEquals(5, usos)
        // El ultimo trozo de la principal, la capa y el sonido llegaban al final
        assertEquals(3, recortados)
        assertTrue(q.principal.filter { it.origen == corto }.all { it.hastaUs <= 5_950_000 })
        assertEquals(5_950_000L, q.audios.single().hastaUs)
        // Lo de antes no se mueve
        assertEquals(p.principal.take(2).map { it.desdeUs to it.hastaUs }, q.principal.take(2).map { it.desdeUs to it.hastaUs })
    }

    @Test
    fun siEsMuchoMasCortoNoSeCambiaNada() {
        val p = edicion()
        val r = Edicion.reemplazar(p, animacion.uri, nueva.copy(duracionUs = 5_000_000))
        assertEquals(Edicion.Reemplazo.Corto(5_000_000, 6_000_000), r)
        assertTrue(avisoReemplazo(r, animacion).startsWith("El video nuevo dura"))
    }

    @Test
    fun siEsMasLargoLosTramosSeQuedanIgual() {
        val p = edicion()
        val (q, _, recortados) = Edicion.reemplazar(p, animacion.uri, nueva.copy(duracionUs = 9_000_000)) as Edicion.Reemplazo.Hecho
        assertEquals(0, recortados)
        assertEquals(p.principal.map { it.desdeUs to it.hastaUs }, q.principal.map { it.desdeUs to it.hastaUs })
        assertEquals(6_000_000L, q.audios.single().hastaUs)
    }

    @Test
    fun loQueNoSePuedeCambiarSeDice() {
        val p = edicion()
        assertEquals(Edicion.Reemplazo.Mismo, Edicion.reemplazar(p, animacion.uri, animacion))
        assertEquals(Edicion.Reemplazo.OtroTipo, Edicion.reemplazar(p, animacion.uri, foto))
        assertEquals(Edicion.Reemplazo.SinUso, Edicion.reemplazar(p, "content://nada", nueva))
        assertEquals("Elige un video: este bloque es un video", avisoReemplazo(Edicion.Reemplazo.OtroTipo, animacion))
    }

    @Test
    fun unaFotoSeCambiaPorOtraSinMirarDuraciones() {
        var p = Edicion.agregarPrincipal(Proyecto(), listOf(foto)).first
        p = Edicion.cambiar(p, p.principal[0].id) { it.copy(hastaUs = 1_200_000) }
        val otra = Origen("content://foto2", "foto2.png", TipoOrigen.IMAGEN, 800, 1200)
        val r = Edicion.reemplazar(p, foto.uri, otra) as Edicion.Reemplazo.Hecho
        assertEquals(otra, r.proyecto.principal[0].origen)
        assertEquals(1_200_000L, r.proyecto.principal[0].hastaUs)
        assertEquals("Foto cambiada. Puedes deshacer", avisoReemplazo(r, foto))
    }

    @Test
    fun elAvisoDiceCuantosBloques() {
        val r = Edicion.reemplazar(edicion(), animacion.uri, nueva)
        assertEquals("Video cambiado en 5 bloques. Puedes deshacer", avisoReemplazo(r, animacion))
    }
}
