package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.assertEquals
import org.junit.Test

class PersistenciaTest {

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
                    mantenerTono = false, fundidoEntradaUs = 200_000, color = AjustesColor(brillo = 0.1f, vineta = 0.3f),
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
}
