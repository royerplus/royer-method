package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscenaTest {

    private val origen = Origen("content://v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 4_000_000, fps = 30f)
    private val medidor = Medidor { c, _ -> (c.origen!!.anchoVisible.toFloat()) to c.origen.altoVisible.toFloat() }

    /** Aplica la matriz (por columnas) a un pixel del lienzo. */
    private fun uv(m: FloatArray, x: Float, y: Float): Pair<Float, Float> =
        (m[0] * x + m[3] * y + m[6]) to (m[1] * x + m[4] * y + m[7])

    private fun escena(clip: Clip, w: Int = 576, h: Int = 1024) =
        Escena(Proyecto(principal = listOf(clip), fondo = Fondo.NEGRO), w, h, medidor)

    @Test
    fun elCentroDelLienzoEsElCentroDeLaImagen() {
        val c = Clip(id = 1, origen = origen)
        val e = escena(c)
        val m = e.matriz(c, c.encuadre, 1920f, 1080f)
        val (u, v) = uv(m, 288f, 512f)
        assertEquals(0.5f, u, 1e-4f)
        assertEquals(0.5f, v, 1e-4f)
    }

    @Test
    fun cubrirLlenaElAltoYRecortaLosLados() {
        // Un 16:9 en un lienzo 9:16 cubriendo: el alto entra justo, de lado
        // sobra. El borde de arriba del lienzo es el de arriba de la imagen.
        val c = Clip(id = 1, origen = origen)
        val m = escena(c).matriz(c, c.encuadre, 1920f, 1080f)
        assertEquals(0f, uv(m, 288f, 0f).second, 1e-4f)
        assertEquals(1f, uv(m, 288f, 1024f).second, 1e-4f)
        val izquierda = uv(m, 0f, 512f).first
        assertTrue(izquierda > 0.3f && izquierda < 0.5f)
    }

    @Test
    fun encajarDejaElVideoEnteroDentro() {
        val c = Clip(id = 1, origen = origen, encuadre = Encuadre(ajuste = Ajuste.ENCAJAR))
        val m = escena(c).matriz(c, c.encuadre, 1920f, 1080f)
        assertEquals(0f, uv(m, 0f, 512f).first, 1e-4f)
        assertEquals(1f, uv(m, 576f, 512f).first, 1e-4f)
        // Arriba del todo queda fuera de la imagen: franja de fondo
        assertTrue(uv(m, 288f, 0f).second < 0f)
    }

    @Test
    fun espejoYRecorte() {
        val c = Clip(
            id = 1, origen = origen,
            encuadre = Encuadre(ajuste = Ajuste.ENCAJAR, espejo = true, recorte = Recorte(izquierda = 0.25f, derecha = 0.25f))
        )
        val m = escena(c).matriz(c, c.encuadre, 1920f, 1080f)
        // Con espejo, el borde izquierdo del bloque es el derecho del recorte
        val bloqueAncho = 576f
        assertEquals(0.75f, uv(m, 0f, 512f).first, 1e-3f)
        assertEquals(0.25f, uv(m, bloqueAncho, 512f).first, 1e-3f)
    }

    @Test
    fun girarNoversaElCentro() {
        val c = Clip(id = 1, origen = origen, encuadre = Encuadre(rotacion = 37f))
        val m = escena(c).matriz(c, c.encuadre, 1920f, 1080f)
        val (u, v) = uv(m, 288f, 512f)
        assertEquals(0.5f, u, 1e-4f)
        assertEquals(0.5f, v, 1e-4f)
    }

    @Test
    fun sinMovimientoBastaUnaMuestra() {
        val c = Clip(id = 1, origen = origen)
        val cuadro = escena(c).cuadro(10)
        val paso = cuadro.clips.single()
        assertEquals(1, paso.muestras)
    }

    @Test
    fun enTransicionHayVariasMuestrasYAvanzan() {
        val a = Clip(id = 1, origen = origen, transicion = Transicion.EMPUJAR, duracionTransicionUs = 1_000_000)
        val b = Clip(id = 2, origen = origen)
        val e = Escena(Proyecto(principal = listOf(a, b), fondo = Fondo.NEGRO), 576, 1024, medidor)
        val cuadro = e.cuadro(105) // 3.5 s, mitad de la transicion
        assertEquals(2, cuadro.clips.size)
        // Cada lado se empuja como escena entera, con todas sus muestras
        val grupos = cuadro.pasos.filterIsInstance<PasoGrupo>()
        assertEquals(2, grupos.size)
        val escenaB = grupos[1]
        assertEquals(8, escenaB.matrices.size)
        // La que entra viene de la derecha: la traslacion de la matriz (cuanto
        // lienzo queda por recorrer) crece muestra a muestra.
        val us = escenaB.matrices.map { uv(it, 0f, 0f).first }
        for (i in 1 until us.size) assertTrue("muestra $i", us[i] > us[i - 1])
        // La que sale se alarga por el borde: en la junta no queda hueco
        assertTrue(grupos[0].extender)
        assertFalse(escenaB.extender)
    }

    @Test
    fun enLaLineaBlancaHayLineaYMascara() {
        val a = Clip(id = 1, origen = origen, transicion = Transicion.LINEA, duracionTransicionUs = 600_000)
        val b = Clip(id = 2, origen = origen)
        val e = Escena(Proyecto(principal = listOf(a, b), fondo = Fondo.NEGRO), 576, 1024, medidor)
        // La transicion ocupa de 3.4 s a 4 s: fotogramas 102 a 119
        fun escenaB(n: Long) = e.cuadro(n).pasos.filterIsInstance<PasoGrupo>().single()
        assertTrue(e.cuadro(111).pasos.last() is PasoLinea)
        assertTrue(escenaB(111).mascara != null)
        // Al principio de la transicion B no se ve; al final, entero
        val primero = escenaB(103)
        val ultimo = escenaB(119)
        assertTrue(primero.bordes.max() < ultimo.bordes.min())
    }

    @Test
    fun loTapadoNoSeDibuja() {
        // En el momento negro de un fundido a negro no hace falta ningun video
        val a = Clip(id = 1, origen = origen, transicion = Transicion.NEGRO, duracionTransicionUs = 1_000_000)
        val b = Clip(id = 2, origen = origen)
        val e = Escena(Proyecto(principal = listOf(a, b), fondo = Fondo.DESENFOCADO), 576, 1024, medidor)
        val centro = e.cuadro(Linea.fotogramaDe(3_500_000))
        assertTrue(centro.clips.isEmpty())
        // Pasado el cambio, A ya no se pide: la escena B, opaca, lo tapa
        val despues = e.cuadro(Linea.fotogramaDe(3_800_000))
        assertTrue(despues.clips.all { it.clip.id == 2L })
    }

    @Test
    fun elFondoSaleSoloSiHayHueco() {
        val lleno = Clip(id = 1, origen = origen)
        val e1 = Escena(Proyecto(principal = listOf(lleno), fondo = Fondo.DESENFOCADO), 576, 1024, medidor)
        assertTrue(e1.cuadro(10).clips.none { it.esFondo })
        // Un giro deja esquinas al aire: ahi si hace falta
        val girado = lleno.copy(encuadre = Encuadre(rotacion = 10f))
        val e2 = Escena(Proyecto(principal = listOf(girado), fondo = Fondo.DESENFOCADO), 576, 1024, medidor)
        assertTrue(e2.cuadro(10).clips.any { it.esFondo })
    }

    @Test
    fun elEmpujeEnDiagonalAvanzaEnLosDosEjes() {
        // 225 grados: la escena nueva entra desde abajo a la derecha
        val a = Clip(
            id = 1, origen = origen, transicion = Transicion.EMPUJAR, duracionTransicionUs = 1_000_000,
            ajusteTransicion = AjusteTransicion(angulo = 225f)
        )
        val b = Clip(id = 2, origen = origen)
        val e = Escena(Proyecto(principal = listOf(a, b), fondo = Fondo.NEGRO), 576, 1024, medidor)
        val grupos = e.cuadro(105).pasos.filterIsInstance<PasoGrupo>()
        val escenaB = grupos[1]
        val us = escenaB.matrices.map { uv(it, 0f, 0f) }
        for (i in 1 until us.size) {
            assertTrue("x muestra $i", us[i].first > us[i - 1].first)
            assertTrue("y muestra $i", us[i].second > us[i - 1].second)
        }
        // Al terminar ya no queda nada de A
        val fin = e.cuadro(Linea.fotogramaDe(4_000_000))
        assertTrue(fin.clips.all { it.clip.id == 2L })
    }

    @Test
    fun todasLasAnimacionesDanNumerosValidos() {
        val foto = Origen("content://i", "i", TipoOrigen.IMAGEN, 1000, 1000)
        fun valido(c: Clip) {
            val e = escena(c)
            for (n in 0L until 60L) {
                for (paso in e.cuadro(n).pasos.filterIsInstance<PasoClip>()) {
                    assertTrue("${c.entrada} ${c.continua} $n", paso.matrices.all { m -> m.all { it.isFinite() } })
                    assertTrue("${c.entrada} ${c.continua} $n", paso.opacidades.all { it in 0f..1f })
                }
            }
        }
        for (t in Animacion.entries) {
            valido(Clip(id = 1, origen = foto, hastaUs = 2_000_000, entrada = listOf(Movimiento(t)), salida = listOf(Movimiento(t, intensidad = 2f))))
        }
        for (t in AnimacionContinua.entries) {
            valido(Clip(id = 1, origen = foto, hastaUs = 2_000_000, continua = listOf(Continuo(t, intensidad = 2f))))
        }
    }
}
