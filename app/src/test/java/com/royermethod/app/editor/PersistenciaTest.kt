package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistenciaTest {

    @Test
    fun elDesfaseNegativoDelEfectoSobreviveAlGuardarYAbrir() {
        val efecto = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 1_000_000, 3_000_000)
            .conRango(966_667, 3_000_000)
        val p = Proyecto(siguienteId = 2, principal = listOf(Clip(1, texto = Texto("x"),
            hastaUs = 4_000_000, efectos = listOf(efecto))))
        val q = Persistencia.deTexto(Persistencia.aTexto(p))!!
        assertEquals(p, q)
        assertEquals(-33_333L, q.principal.single().efectos.single().desfaseUs)
    }

    @Test
    fun idaYVueltaSinPerderNada() {
        val video = Origen("content://v/1", "clip.mp4", TipoOrigen.VIDEO, 1920, 1080, 4_000_000, 23.976f, true, 90, 12345)
        val p = Proyecto(
            formato = Formato.CUADRADO,
            fondo = Fondo.BLANCO,
            salida = AjustesSalida(ladoCorto = 720, calidad = Calidad.ALTA, hevc = false, desenfoqueMovimiento = false, muestras = 4),
            principal = listOf(
                Clip(
                    id = 1, origen = video, desdeUs = 100, hastaUs = 3_000_000, velocidad = 1.5f, volumen = 0.7f,
                    mantenerTono = false, fundidoEntradaUs = 200_000, color = AjustesColor(brillo = 0.1f, vineta = 0.3f, claridad = -0.4f, vivacidad = 0.25f, desvanecer = 0.3f, grano = 0.2f),
                    filtro = Filtro.ANIME, intensidadFiltro = 0.5f,
                    encuadre = Encuadre(Ajuste.ENCAJAR, 1.2f, 0.1f, -0.2f, 15f, 0.9f, true, Recorte(0.1f, 0f, 0.05f, 0.2f)),
                    claves = listOf(Clave(0, Encuadre()), Clave(500_000, Encuadre(escala = 2f), Curva.ENTRADA)),
                    entrada = listOf(Movimiento(Animacion.REBOTE, 0.7f, Curva.ELASTICO, 0.1f, 0.9f), Movimiento(Animacion.APARECER)),
                    salida = listOf(Movimiento(Animacion.GIRO)),
                    continua = listOf(Continuo(AnimacionContinua.PULSO, 1.5f, 2f)),
                    ajusteTransicion = AjusteTransicion(Curva.LINEAL, 237f, 0xFF00FF00.toInt(), 1.4f),
                    transicion = Transicion.LINEA, duracionTransicionUs = 400_000, fluido = true, calidad = 0.6f
                )
            ),
            capas = listOf(Capa(1_000_000, 2, Clip(id = 2, texto = Texto("Hola\nmundo", contorno = 0.1f, fuente = Fuente.MONO, fondo = 0x80000000.toInt())), Mezcla.PANTALLA)),
            audios = listOf(PistaAudio(3, video.copy(tipo = TipoOrigen.AUDIO), 500, 1000, 2_000_000, 0.4f, 100, 200)),
            siguienteId = 4
        )
        val q = Persistencia.deTexto(Persistencia.aTexto(p))
        assertEquals(p, q)
    }

    @Test
    fun unColorGuardadoAntesDeLaClaridadSeAbreConLoNuevoACero() {
        // Los proyectos de antes guardaban 10 numeros; los ajustes nuevos van detras
        val q = Persistencia.deTexto(
            """{"principal":[{"id":1,"texto":{"contenido":"a"},"color":[0.2,0,0,0,0,0,0,0,0.5,0]}]}"""
        )!!
        assertEquals(AjustesColor(brillo = 0.2f, nitidez = 0.5f), q.principal[0].color)
        val neutro = Persistencia.deTexto("""{"principal":[{"id":1,"texto":{"contenido":"a"},"color":[0,0,0,0,0,0,0,0,0,0]}]}""")!!
        assertTrue(neutro.principal[0].color.neutro)
    }

    @Test
    fun elColorVaSiempreEnElMismoOrden() {
        // El arreglo se lee por posicion: un campo metido en medio cambiaria lo
        // que ven los proyectos viejos y las versiones viejas de la app
        val viejo = Persistencia.deTexto(
            """{"principal":[{"id":1,"texto":{"contenido":"a"},"color":[0.01,0.02,0.03,0.04,0.05,0.06,0.07,0.08,0.09,0.1]}]}"""
        )!!.principal[0].color
        assertEquals(AjustesColor(
            brillo = 0.01f, contraste = 0.02f, saturacion = 0.03f, exposicion = 0.04f, calidez = 0.05f,
            tinte = 0.06f, sombras = 0.07f, luces = 0.08f, nitidez = 0.09f, vineta = 0.1f
        ), viejo)
        val todos = viejo.copy(claridad = 0.11f, vivacidad = 0.12f, desvanecer = 0.13f, grano = 0.14f)
        val texto = Persistencia.aTexto(Proyecto(principal = listOf(Clip(id = 1, texto = Texto("a"), color = todos))))
        val arreglo = org.json.JSONObject(texto).getJSONArray("principal").getJSONObject(0).getJSONArray("color")
        assertEquals(14, arreglo.length())
        for (i in 0 until 14) assertEquals("posicion $i", (i + 1) / 100.0, arreglo.getDouble(i), 1e-6)
    }

    @Test
    fun loQueNoSeEntiendeSeIgnora() {
        val q = Persistencia.deTexto("""{"formato":"RARO","principal":[{"id":5,"texto":{"contenido":"x"},"transicion":"NUEVA"}]}""")!!
        assertEquals(Formato.VERTICAL, q.formato)
        assertEquals(Transicion.NINGUNA, q.principal[0].transicion)
        // El contador queda por encima de los ids que hay
        assertEquals(6L, q.siguienteId)
    }

    @Test
    fun lasDireccionesDeAntesPasanAAngulo() {
        val q = Persistencia.deTexto(
            """{"principal":[
                {"id":1,"texto":{"contenido":"a"},"transicion":"EMPUJAR","ajusteTransicion":{"direccion":"ARRIBA"}},
                {"id":2,"texto":{"contenido":"b"},"transicion":"LINEA","ajusteTransicion":{"direccion":"IZQUIERDA"}},
                {"id":3,"texto":{"contenido":"c"}}
            ]}"""
        )!!
        assertEquals(270f, q.principal[0].ajusteTransicion.angulo, 0f)
        // Los barridos de antes iban algo inclinados: se conserva
        assertEquals(AjusteTransicion.ANGULO_BARRIDO, q.principal[1].ajusteTransicion.angulo, 0f)
        assertEquals(1f, q.principal[2].calidad, 0f)
    }

    @Test
    fun elCeroNegativoSeLeeComoCero() {
        val texto = Persistencia.aTexto(Proyecto(principal = listOf(Clip(id = 1, texto = Texto("x"), encuadre = Encuadre(rotacion = 5f)))))
        val conNegativo = Regex("\"rotacion\":5(\\.0)?").replace(texto, "\"rotacion\":-0.0")
        assertTrue("No se encontro la rotacion en el texto", conNegativo != texto)
        val q = Persistencia.deTexto(conNegativo)!!
        assertEquals(0f.toRawBits(), q.principal[0].encuadre.rotacion.toRawBits())
        assertEquals(q, Persistencia.deTexto(Persistencia.aTexto(q)))
    }
}
