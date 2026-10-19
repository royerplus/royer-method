@file:Suppress("DEPRECATION")

package com.royermethod.app.editor

import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import android.test.InstrumentationTestCase
import android.util.Log
import com.royermethod.app.editor.modelo.AjustesSalida
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PiezaVideo
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.RegionUv
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.motor.Compositor
import com.royermethod.app.editor.motor.Egl
import com.royermethod.app.editor.motor.Lamina
import com.royermethod.app.editor.motor.Programa
import com.royermethod.app.editor.motor.Sombreadores
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs

/** Regresiones de pixeles en el GL real de .prueba, sin archivos del usuario. */
class SombreadoresIntegrationTest : InstrumentationTestCase() {
    fun testCopiaExactaNoAblandaElDetalleDeUnPixelA1080x1920() = conGl(1080, 1920) {
        val rango = IntArray(2)
        val precision = IntArray(1)
        GLES20.glGetShaderPrecisionFormat(GLES20.GL_FRAGMENT_SHADER, GLES20.GL_HIGH_FLOAT,
            rango, 0, precision, 0)
        if (precision[0] == 0) {
            Log.i(TAG, "GL_HIGH_FLOAT no disponible: se conserva el fallback mediump")
            return@conGl
        }
        val w = 1080
        val h = 1920
        val fuente = Lamina(w, h)
        val destino = Lamina(w, h)
        val programa = Programa(Sombreadores.VERTICE, Sombreadores.COPIA_EXACTA)
        try {
            val pixeles = ByteBuffer.allocateDirect(w * h * 4)
            for (y in 0 until h) for (x in 0 until w) {
                val valor = ((x * 73 + y * 151) xor (x * y * 17)) and 255
                pixeles.put(valor.toByte()).put((255 - valor).toByte())
                    .put(((valor * 19) and 255).toByte()).put(255.toByte())
            }
            fuente.escribir(pixeles)
            destino.activar()
            GLES20.glDisable(GLES20.GL_BLEND)
            programa.usar()
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fuente.textura)
            GLES20.glUniform1i(programa.u("sTex"), 0)
            programa.dibujar(voltear = true)
            val leidos = ByteBuffer.allocateDirect(w * h * 4)
            destino.leer(leidos)
            var diferencia = 0L
            for (p in 0 until w * h) for (c in 0..2) {
                diferencia += abs((pixeles.get(p * 4 + c).toInt() and 255) -
                    (leidos.get(p * 4 + c).toInt() and 255))
            }
            val media = diferencia.toDouble() / (w * h * 3)
            Log.i(TAG, "Copia 1080x1920: error medio=$media/255, precision=${precision[0]}")
            assertTrue("La copia pierde detalle de un pixel: $media/255", media < 1.0)
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            programa.soltar()
            destino.soltar()
            fuente.soltar()
        }
    }

    fun testCorteInteriorGiradoTieneUnBordeSuave() = conFuenteBlanca { compositor, destino, clip ->
        val c = clip.copy(piezas = listOf(PiezaVideo("Interior", RegionUv(.2f, .2f, .8f, .8f))))
        val imagen = dibujar(compositor, destino, c)
        val grises = (0 until LADO * LADO).count { (imagen[it * 4].toInt() and 255) in 1..254 }
        Log.i(TAG, "Corte girado: $grises pixeles de cobertura parcial")
        assertTrue("El borde de corte sigue siendo un escalon binario", grises > 100)
    }

    fun testDosMitadesDeMismaMatrizNoDejanCosturaAlGirarlas() = conFuenteBlanca { compositor, destino, clip ->
        val entero = dibujar(compositor, destino, clip)
        // Distinta opacidad evita que Escena fusione la particion antes de GL.
        val c = clip.copy(piezas = listOf(PiezaVideo("Superior", RegionUv(abajo = .5f)),
            PiezaVideo("Inferior", RegionUv(arriba = .5f), Encuadre(opacidad = .999f))))
        val partes = dibujar(compositor, destino, c)
        val diferencia = diferenciaMaxima(entero, partes)
        Log.i(TAG, "Mitades giradas: diferencia maxima=$diferencia/255")
        assertTrue("Una junta suave doble deja una raya oscura: $diferencia/255", diferencia <= 2)
    }

    fun testVecinoParcialConservaBordeExpuestoSuaveYJuntaSinCostura() = conFuenteBlanca { compositor, destino, clip ->
        // La union forma un bloque con un vecino solo en el tercio central.
        val izquierda = RegionUv(.15f, .15f, .5f, .85f)
        val vecino = RegionUv(.5f, .4f, .85f, .6f)
        val c = clip.copy(piezas = listOf(PiezaVideo("Izquierda", izquierda), PiezaVideo("Vecino", vecino)))
        val imagen = dibujar(compositor, destino, c)
        val escena = Escena(proyecto(c), LADO, LADO, compositor.medidor)
        val matriz = escena.cuadroEn(0).clips.first().matrices.first()
        var expuestosSuaves = 0
        var juntaMinima = 255
        var muestrasJunta = 0
        for (y in 0 until LADO) for (x in 0 until LADO) {
            val u = matriz[0] * (x + .5f) + matriz[3] * (y + .5f) + matriz[6]
            val v = matriz[1] * (x + .5f) + matriz[4] * (y + .5f) + matriz[7]
            val valor = imagen[(y * LADO + x) * 4].toInt() and 255
            // Lejos de las esquinas: se mide cada tramo del mismo borde UV.
            if (abs(u - .5f) < .003f && v in 0.43f..0.57f) {
                juntaMinima = minOf(juntaMinima, valor)
                muestrasJunta++
            }
            if (abs(u - .5f) < .004f && (v in 0.2f..0.35f || v in 0.65f..0.8f) && valor in 1..254) {
                expuestosSuaves++
            }
        }
        Log.i(TAG, "Junta parcial: minimo=$juntaMinima/255 ($muestrasJunta muestras), expuestos suaves=$expuestosSuaves")
        assertTrue("No se midio la junta", muestrasJunta > 10)
        assertTrue("La junta parcial tiene costura", juntaMinima >= 253)
        assertTrue("Se endurecio tambien el tramo expuesto", expuestosSuaves > 10)
    }

    private fun conGl(ancho: Int, alto: Int, accion: () -> Unit) {
        assertTrue("Solo .prueba", instrumentation.targetContext.packageName.endsWith(".prueba"))
        val egl = Egl(null, ancho, alto)
        try {
            egl.activar()
            GLES20.glDisable(GLES20.GL_DITHER)
            accion()
        } finally { egl.soltar() }
    }

    private fun conFuenteBlanca(accion: (Compositor, Lamina, Clip) -> Unit) = conGl(LADO, LADO) {
        val context = instrumentation.targetContext
        val archivo = File.createTempFile("shader-blanco-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        try { archivo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
        val compositor = Compositor(context, LADO, LADO)
        val destino = Lamina(LADO, LADO)
        val origen = Origen(Uri.fromFile(archivo).toString(), "Prueba blanca", TipoOrigen.IMAGEN, LADO, LADO)
        val clip = Clip(1, origen = origen, hastaUs = 1_000_000, encuadre = Encuadre(rotacion = 17f, escala = .9f))
        try {
            accion(compositor, destino, clip)
            assertEquals(GLES20.GL_NO_ERROR, GLES20.glGetError())
        } finally {
            destino.soltar()
            compositor.soltar()
            archivo.delete()
        }
    }

    private fun proyecto(c: Clip) = Proyecto(formato = Formato.CUADRADO, principal = listOf(c),
        fondo = Fondo.NEGRO, salida = AjustesSalida(desenfoqueMovimiento = false))

    private fun dibujar(compositor: Compositor, destino: Lamina, c: Clip): ByteArray {
        compositor.dibujar(Escena(proyecto(c), LADO, LADO, compositor.medidor).cuadroEn(0), destino)
        val datos = ByteBuffer.allocateDirect(LADO * LADO * 4)
        destino.leer(datos)
        return ByteArray(datos.capacity()).also { datos.get(it) }
    }

    private fun diferenciaMaxima(a: ByteArray, b: ByteArray): Int = a.indices.filter { it % 4 != 3 }
        .maxOf { abs((a[it].toInt() and 255) - (b[it].toInt() and 255)) }

    private companion object {
        const val TAG = "ShaderRegresion"
        const val LADO = 256
    }
}
