package com.royermethod.app.editor.audio

import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.Transicion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class AudioTest {

    /** Un tono puro de [hz] a [frecuencia], [segundos] de largo. */
    private fun tono(hz: Int, frecuencia: Double, segundos: Double, amplitud: Float = 0.8f) = object : FuentePcm {
        val total = (hz * segundos).toInt()
        var n = 0
        override fun leer(destino: FloatArray, frames: Int): Int {
            val m = minOf(frames, total - n)
            for (i in 0 until m) {
                val v = (amplitud * sin(2 * PI * frecuencia * (n + i) / hz)).toFloat()
                destino[i * 2] = v; destino[i * 2 + 1] = v
            }
            n += m
            return m
        }
    }

    private fun todo(f: FuentePcm): FloatArray {
        val salida = ArrayList<Float>()
        val b = FloatArray(4096)
        while (true) {
            val n = f.leer(b, 2048)
            if (n <= 0) break
            for (i in 0 until n) salida.add(b[i * 2])
        }
        return salida.toFloatArray()
    }

    /** Frecuencia por cruces por cero, en el tramo central. */
    private fun frecuencia(x: FloatArray, hz: Int): Double {
        val a = x.size / 4
        val b = x.size * 3 / 4
        var cruces = 0
        for (i in a + 1 until b) if ((x[i - 1] < 0) != (x[i] < 0)) cruces++
        return cruces / 2.0 / ((b - a).toDouble() / hz)
    }

    private fun rms(x: FloatArray): Double {
        val a = x.size / 4
        val b = x.size * 3 / 4
        var s = 0.0
        for (i in a until b) s += x[i] * x[i]
        return sqrt(s / (b - a))
    }

    @Test
    fun remuestrearConservaTonoYDuracion() {
        val x = todo(Remuestreador(tono(44_100, 1000.0, 1.0), 44_100.0))
        assertEquals(48_000.0, x.size.toDouble(), 2.0)
        assertEquals(1000.0, frecuencia(x, 48_000), 2.0)
        // Sin perder volumen: 0.8 de pico son 0.566 de RMS
        assertEquals(0.566, rms(x), 0.01)
    }

    @Test
    fun remuestrearAgudosCercaDelLimite() {
        // 15 kHz de 44.1 a 48: con interpolacion lineal perderia mucho
        val x = todo(Remuestreador(tono(44_100, 15_000.0, 0.5), 44_100.0))
        assertEquals(0.566, rms(x), 0.03)
    }

    @Test
    fun estirarCambiaDuracionNoTono() {
        for (v in listOf(2f, 0.5f, 1.5f)) {
            val x = todo(Estirador(tono(48_000, 440.0, 2.0), v))
            val esperado = 96_000 / v
            assertEquals("duracion a $v", esperado.toDouble(), x.size.toDouble(), 1600.0)
            assertEquals("tono a $v", 440.0, frecuencia(x, 48_000), 6.0)
            // Las juntas no se cancelan: el volumen se mantiene
            assertEquals("volumen a $v", 0.566, rms(x), 0.05)
        }
    }

    @Test
    fun limitadorNoTocaLoQueCabe() {
        val l = Limitador()
        val n = 4000
        val x = FloatArray(n * 2) { (0.5 * sin(it / 2 * 0.05)).toFloat() }
        val y = x.copyOf()
        l.procesar(y, n)
        for (i in Limitador.LATENCIA until n) assertEquals(x[(i - Limitador.LATENCIA) * 2], y[i * 2], 1e-6f)
    }

    @Test
    fun limitadorFrenaLosPicosSinRecortar() {
        val l = Limitador()
        val n = 48_000
        val x = FloatArray(n * 2) { (1.6 * sin(it / 2 * 2 * PI * 200 / 48_000)).toFloat() }
        l.procesar(x, n)
        var maximo = 0f
        for (v in x) maximo = maxOf(maximo, abs(v))
        assertTrue("pico $maximo", maximo <= 0.9661f)
        // Y no se ha quedado en silencio
        assertTrue(maximo > 0.9f)
    }

    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 4_000_000, fps = 30f, conAudio = true)

    @Test
    fun elCruceDeUnaTransicionSuenaParejo() {
        val a = Clip(id = 1, origen = video, transicion = Transicion.FUNDIDO, duracionTransicionUs = 1_000_000)
        val b = Clip(id = 2, origen = video)
        val p = Proyecto(principal = listOf(a, b))
        val segs = PlanAudio.de(p)
        assertEquals(2, segs.size)
        val (sa, sb) = segs
        // Se solapan justo lo que dura la transicion, ni un frame de diferencia
        assertEquals(sa.fin - sb.inicio, sa.salida)
        assertEquals(sa.salida, sb.entrada)
        assertEquals(48_000L, sa.salida)
        // Igual potencia a lo largo de todo el cruce
        for (f in sb.inicio until sa.fin step 997) {
            val ga = sa.ganancia(f - sa.inicio)
            val gb = sb.ganancia(f - sb.inicio)
            assertEquals(1f, ga * ga + gb * gb, 0.001f)
        }
        // 7 s de video son 336000 frames de audio exactos
        assertEquals(336_000L, PlanAudio.total(p))
        assertEquals(336_000L, sb.fin)
    }

    @Test
    fun mezcladorEntregaLaDuracionExacta() {
        val a = Clip(id = 1, origen = video, volumen = 0.5f)
        val p = Proyecto(principal = listOf(a))
        val segs = PlanAudio.de(p)
        val total = PlanAudio.total(p)
        // Una fuente que se queda corta: lo que falte, silencio
        val m = Mezclador(segs, total) { _, _ -> tono(48_000, 300.0, 1.0, 0.5f) }
        val x = todo(m)
        assertEquals(total.toInt(), x.size)
        assertEquals(0f, x[x.size - 10], 0f)
        // Con el volumen al 50%
        var maximo = 0f
        for (i in 1000 until 40_000) maximo = maxOf(maximo, abs(x[i]))
        assertEquals(0.25f, maximo, 0.01f)
    }

    @Test
    fun mezcladorEmpiezaDondeSePide() {
        val a = Clip(id = 1, origen = video)
        val p = Proyecto(principal = listOf(a))
        var pedido = -1L
        val m = Mezclador(PlanAudio.de(p), PlanAudio.total(p), desde = 96_000) { _, dentro ->
            pedido = dentro
            Silencio
        }
        val x = todo(m)
        assertEquals(96_000L, pedido)
        assertEquals(192_000 - 96_000, x.size)
    }
}
