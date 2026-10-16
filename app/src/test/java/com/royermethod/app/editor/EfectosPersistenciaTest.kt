package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files

class EfectosPersistenciaTest {
    @Test
    fun guardaTodosLosEfectosYLaContinuidadDeUnRecorte() {
        val efectos = TipoEfectoVideo.values().mapIndexed { i, tipo ->
            EfectoVideo(tipo, 100_000, 2_000_000, 1.5f, 2f,
                desfaseUs = 400_000, fundidoEntradaUs = 500_000, fundidoSalidaUs = 700_000,
                duracionBaseUs = 2_300_000, activo = i % 2 == 0,
                progresos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(700_000, 0.8f), ProgresoEfecto(2_300_000, 1f)))
        }
        val p = Proyecto(principal = listOf(Clip(1, texto = Texto("hola"), efectos = efectos)), siguienteId = 2)
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
    }

    @Test
    fun losProyectosAnterioresYLosTiposDesconocidosSiguenAbriendo() {
        val viejo = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"}}]}""")!!
        assertTrue(viejo.principal.single().efectos.isEmpty())
        val nuevo = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"efectos":[
            {"tipo":"DEL_FUTURO","inicio":0,"fin":2000000},
            {"tipo":"RGB","inicio":100,"fin":100},
            {"tipo":"RGB","inicio":0,"fin":2000000,"intensidad":-2,"rapidez":99}
        ]}]}""")!!
        val e = nuevo.principal.single().efectos.single()
        assertEquals(0f, e.intensidad, 0f)
        assertEquals(10f, e.rapidez, 0f)
        assertTrue(e.activo)
    }

    private val raros = AjustesEfecto(angulo = 237.5f, desde = -0.35f, hasta = 1.15f, curva = Curva.ELASTICO,
        bezier = Bezier(0.2f, -0.25f, 0.7f, 1.3f), centroX = 0.12f, centroY = -0.3f, grosor = 0.15f, suavidad = 0.02f,
        invertir = true, vertical = true)

    /** Todo lo nuevo con valores que no son los de por defecto. */
    private fun conTodo(): Proyecto {
        val video = Origen("content://v/1", "v.mp4", TipoOrigen.VIDEO, 1080, 1920, 4_000_000, 30f)
        val claves = listOf(Clave(0, Encuadre(x = 0.1f), Curva.LINEAL), Clave(33_333, Encuadre(y = 0.2f), bezier = Bezier(0.1f, 0f, 0.9f, 1f)),
            Clave(1_001, Encuadre(escala = 1.5f), Curva.RAPIDA))
        val nuevos = listOf(TipoEfectoVideo.MOVER, TipoEfectoVideo.ZOOM, TipoEfectoVideo.GIRAR, TipoEfectoVideo.LATIGAZO,
            TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, TipoEfectoVideo.CIRCULO, TipoEfectoVideo.BARRIDO, TipoEfectoVideo.MITADES)
            .mapIndexed { i, t -> EfectoVideo(t, 1_000L + i, 900_005L + i, intensidad = 0.7f, rapidez = 1.3f, desfaseUs = -5_001,
                fundidoEntradaUs = 1_001, fundidoSalidaUs = 5, duracionBaseUs = 950_000, activo = i % 3 != 0,
                progresos = listOf(ProgresoEfecto(5, 0.1f), ProgresoEfecto(950_000, 0.9f)),
                id = 10L + i, grupo = if (i < 4) "Latigazo con desenfoque 2" else "", ajustes = raros.copy(angulo = i * 45f + 0.5f)) }
        val animacion = EfectoVideo(TipoEfectoVideo.ANIMACION, 0, 500_000, id = 30, animacion = AnimacionEnEfecto("Shake con flash",
            MomentoAnimacion.ENTRADA, piezas = listOf(Movimiento(Animacion.SACUDIR, 1.4f, Curva.LINEAL, 0.1f, 0.8f), Movimiento(Animacion.FLASH))))
        val siempre = EfectoVideo(TipoEfectoVideo.ANIMACION, 0, 4_000_000, id = 31, animacion = AnimacionEnEfecto("Siempre",
            MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.PULSO, 0.5f, 2f))))
        val aplicadas = listOf(
            AnimacionAplicada(1, "Acercar", MomentoAnimacion.ENTRADA, piezas = listOf(Movimiento(Animacion.ZOOM_ENTRA, 0.8f)), duracionUs = 250_001),
            AnimacionAplicada(2, "Girar", MomentoAnimacion.SALIDA, piezas = listOf(Movimiento(Animacion.GIRO, 1.2f, Curva.REBOTE, 0f, 0.5f)), activa = false),
            AnimacionAplicada(3, "Respirar", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.RESPIRAR, 1.1f, 0.5f),
                Continuo(AnimacionContinua.TEMBLOR))),
            AnimacionAplicada(4, "", MomentoAnimacion.ENTRADA))
        val pieza = PiezaVideo("Arriba", RegionUv(abajo = 0.5f), claves = claves.take(2), clavesOriginales = claves)
        val c = Clip(1, origen = video, claves = claves.take(2), clavesOriginales = claves, efectos = nuevos + animacion + siempre,
            animaciones = aplicadas, piezas = listOf(pieza, PiezaVideo("Abajo", RegionUv(arriba = 0.5f), clavesOriginales = emptyList())))
        val capa = Capa(100_000, 1, Clip(2, texto = Texto("hola"), animaciones = aplicadas.take(1),
            efectos = listOf(nuevos.last().copy(id = 1)), clavesOriginales = emptyList()))
        return Proyecto(principal = listOf(c), capas = listOf(capa), siguienteId = 3)
    }

    @Test
    fun guardaLasHerramientasNuevasSinPerderNada() {
        val p = conTodo()
        assertEquals(p, Persistencia.deTexto(Persistencia.aTexto(p)))
        // Las listas vacias de claves originales no son "sin originales"
        val q = Persistencia.deTexto(Persistencia.aTexto(p))!!
        assertEquals(emptyList<Clave>(), q.capas.single().clip.clavesOriginales)
        assertEquals(emptyList<Clave>(), q.principal.single().piezas[1].clavesOriginales)
        assertNull(Persistencia.deTexto(Persistencia.aTexto(p.copy(principal = listOf(p.principal.single().copy(clavesOriginales = null)))))!!
            .principal.single().clavesOriginales)
    }

    @Test
    fun elPaqueteRoyerConservaLasHerramientasNuevas() {
        val p = conTodo()
        val out = ByteArrayOutputStream()
        PaqueteProyecto.escribir(p, out) { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }
        val dir = Files.createTempDirectory("royer-paquete-").toFile()
        try {
            val q = PaqueteProyecto.leer(ByteArrayInputStream(out.toByteArray()), dir)
            val sinOrigen = { c: Clip -> c.copy(origen = c.origen?.copy(uri = "")) }
            assertEquals(p.principal.map(sinOrigen), q.principal.map(sinOrigen))
            assertEquals(p.capas, q.capas)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun loQueEstaPorDefectoNoSeEscribeYUnArchivoViejoSeLeeIgual() {
        // Un proyecto sin nada nuevo se escribe con las mismas claves de siempre:
        // lo abre igual una version anterior.
        val viejo = Proyecto(principal = listOf(Clip(1, texto = Texto("x"), efectos = listOf(
            EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 0, 1_000_000, progresos = listOf(ProgresoEfecto(0, 0.5f)))),
            piezas = listOf(PiezaVideo("a", RegionUv(abajo = 0.5f))))), siguienteId = 2)
        val j = JSONObject(Persistencia.aTexto(viejo))
        val clip = j.getJSONArray("principal").getJSONObject(0)
        assertFalse(clip.has("animaciones"))
        assertFalse(clip.has("clavesOriginales"))
        assertFalse(clip.getJSONArray("piezas").getJSONObject(0).has("clavesOriginales"))
        assertEquals(setOf("tipo", "inicio", "fin", "intensidad", "rapidez", "desfase", "fundidoEntrada", "fundidoSalida",
            "duracionBase", "activo", "progresos"), clip.getJSONArray("efectos").getJSONObject(0).keys().asSequence().toSet())
        // Ajustes a medias: solo se escribe lo que cambia
        val medio = Proyecto(principal = listOf(Clip(1, texto = Texto("x"), efectos = listOf(EfectoVideo(TipoEfectoVideo.CIRCULO, 0, 1_000_000,
            ajustes = AjustesEfecto(grosor = 0.2f))), animaciones = listOf(AnimacionAplicada(7, "Aparecer", MomentoAnimacion.ENTRADA)))),
            siguienteId = 2)
        val m = JSONObject(Persistencia.aTexto(medio)).getJSONArray("principal").getJSONObject(0)
        assertEquals(setOf("grosor"), m.getJSONArray("efectos").getJSONObject(0).getJSONObject("ajustes").keys().asSequence().toSet())
        assertEquals(setOf("id", "nombre", "momento"), m.getJSONArray("animaciones").getJSONObject(0).keys().asSequence().toSet())
        assertEquals(medio, Persistencia.deTexto(Persistencia.aTexto(medio)))
        // Un archivo escrito antes de las herramientas se lee como antes
        val antes = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},"claves":[],"piezas":[{"nombre":"a",
            "region":{"izquierda":0,"arriba":0,"derecha":1,"abajo":0.5}}],"efectos":[{"tipo":"CIRCULO_CERRAR","inicio":0,"fin":1000000,
            "intensidad":1,"rapidez":1,"desfase":0,"fundidoEntrada":0,"fundidoSalida":0,"duracionBase":0,"activo":true,
            "progresos":[{"t":0,"valor":0.5}]}]}],"siguienteId":2}""")!!
        assertEquals(viejo, antes)
        val e = antes.principal.single().efectos.single()
        assertEquals(0L, e.id)
        assertEquals("", e.grupo)
        assertEquals(AjustesEfecto(), e.ajustes)
        assertNull(e.animacion)
        assertTrue(antes.principal.single().animaciones.isEmpty())
        assertNull(antes.principal.single().clavesOriginales)
        assertNull(antes.principal.single().piezas.single().clavesOriginales)
    }

    @Test
    fun loNuevoQueNoSeEntiendeSeIgnora() {
        val p = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"x"},
            "animaciones":[{"id":1,"nombre":"a","momento":"DEL_FUTURO"},{"id":2,"nombre":"b","momento":"SALIDA","duracion":-5,
                "piezas":[{"tipo":"NUEVA"},{"tipo":"GIRO"}]}],
            "efectos":[{"tipo":"CIRCULO","inicio":0,"fin":1000,"id":-3,"ajustes":{"angulo":"NaN","curva":"RARA","grosor":-1,
                "suavidad":-2,"desde":"x","bezier":[2,0.5,-1]}},
                {"tipo":"ANIMACION","inicio":0,"fin":1000,"animacion":{"nombre":"z","momento":"NUNCA"}}]}]}""")!!
        val c = p.principal.single()
        val a = c.animaciones.single()
        assertEquals(2L, a.id)
        assertEquals(0L, a.duracionUs)
        assertEquals(listOf(Movimiento(Animacion.GIRO)), a.piezas)
        val e = c.efectos.first()
        assertEquals(0L, e.id)
        assertEquals(AjustesEfecto(), e.ajustes)
        assertNull(c.efectos[1].animacion)
    }

    @Test
    fun unNumeroNoFinitoNoRompeElGuardado() {
        val p = Proyecto(principal = listOf(Clip(1, texto = Texto("x"), efectos = listOf(EfectoVideo(TipoEfectoVideo.MOVER, 0, 1_000,
            ajustes = AjustesEfecto(angulo = Float.NaN, desde = Float.POSITIVE_INFINITY, bezier = Bezier(Float.NaN, 0f, 1f, 1f)))))))
        val q = Persistencia.deTexto(Persistencia.aTexto(p))!!
        assertEquals(AjustesEfecto(), q.principal.single().efectos.single().ajustes)
    }
}
