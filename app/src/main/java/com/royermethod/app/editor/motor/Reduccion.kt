package com.royermethod.app.editor.motor

import android.opengl.GLES11Ext
import android.opengl.GLES20

/**
 * Baja un fotograma del decodificador al tamano al que se va a ver.
 *
 * Es el filtro que mejor sale para anime en las pruebas, sin pasarse de lo
 * que un telefono hace a 30 fps: Lanczos de radio 2 abierto segun cuanto se
 * reduce, en la gama de colores tal cual (en luz lineal las lineas oscuras
 * adelgazan), y limitado al rango de los pixeles de debajo para que no salga
 * el cerco claro alrededor de las lineas. Ver [Sombreadores.REDUCIR_EXTERNA].
 *
 * Va en dos pasadas, primero a lo ancho y luego a lo alto, porque el filtro
 * es separable: de una vez costaria el cuadrado de muestras para lo mismo.
 */
class Reduccion {
    private val horizontal = Programa(Sombreadores.VERTICE, Sombreadores.REDUCIR_EXTERNA)
    private val vertical = Programa(Sombreadores.VERTICE, Sombreadores.REDUCIR_PLANA)

    /**
     * De la textura externa [oes] a [destino].
     *
     * @param mapa de la coordenada de la copia (derecha, origen arriba) a la
     *   de la textura del decodificador
     * @param anchoOrigen,altoOrigen el fotograma ya girado
     * @param intermedia [destino].ancho x [altoOrigen]: el resultado de la
     *   primera pasada
     */
    fun reducir(oes: Int, mapa: FloatArray, anchoOrigen: Int, altoOrigen: Int, intermedia: Lamina, destino: Lamina) {
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        intermedia.activar()
        horizontal.usar()
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oes)
        pasada(horizontal, mapa, 1f, 0f, anchoOrigen, intermedia.ancho)

        destino.activar()
        vertical.usar()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, intermedia.textura)
        pasada(vertical, IDENTIDAD, 0f, 1f, altoOrigen, destino.alto)
    }

    private fun pasada(p: Programa, mapa: FloatArray, dx: Float, dy: Float, origen: Int, destino: Int) {
        GLES20.glUniform1i(p.u("sTex"), 0)
        GLES20.glUniformMatrix3fv(p.u("uMapa"), 1, false, mapa, 0)
        GLES20.glUniform2f(p.u("uDir"), dx, dy)
        GLES20.glUniform1f(p.u("uOrigen"), origen.toFloat())
        // Al ampliar (no deberia pasar: la copia nunca es mayor que el
        // original) se queda en Lanczos normal, que solo interpola.
        GLES20.glUniform1f(p.u("uEscala"), maxOf(1f, origen.toFloat() / destino))
        p.dibujar(voltear = true)
    }

    fun soltar() {
        horizontal.soltar()
        vertical.soltar()
    }

    private companion object {
        val IDENTIDAD = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
    }
}
