package com.royermethod.app.editor.motor

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Una textura en la que se puede dibujar.
 *
 * Convencion de toda la app: la fila 0 de la textura (v = 0) es la de arriba
 * de la imagen. OpenGL pone el origen abajo, asi que quien dibuja en una
 * lamina voltea la y (ver [Programa.dibujar]). Fijarlo una vez evita el
 * clasico video que sale boca abajo solo en uno de los caminos.
 */
class Lamina(ancho: Int, alto: Int) {
    var ancho = ancho
        private set
    var alto = alto
        private set
    val textura: Int
    val fbo: Int

    init {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textura = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, ancho, alto, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        filtros(GLES20.GL_TEXTURE_2D)
        GLES20.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textura, 0
        )
        val estado = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        check(estado == GLES20.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incompleto: $estado" }
    }

    fun activar() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, ancho, alto)
    }

    /** Baja los pixeles a memoria, RGBA, fila de arriba primero. */
    fun leer(destino: ByteBuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        destino.position(0)
        GLES20.glReadPixels(0, 0, ancho, alto, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, destino)
        destino.position(0)
    }

    /** Sube pixeles de memoria con la misma convencion que [leer]. */
    fun escribir(fuente: ByteBuffer) {
        fuente.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D, 0, 0, 0, ancho, alto,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, fuente
        )
        fuente.position(0)
    }

    fun soltar() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES20.glDeleteTextures(1, intArrayOf(textura), 0)
    }

    companion object {
        fun filtros(objetivo: Int) {
            GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
    }
}

/**
 * Un programa de sombreado con sus uniformes cacheados.
 *
 * glGetUniformLocation es una busqueda por nombre en el driver. Con ocho
 * muestras, una docena de ajustes y varias capas por fotograma, preguntarlo
 * cada vez son cientos de busquedas por fotograma para nada.
 */
class Programa(vertice: String, fragmento: String) {
    val id: Int = compilar(vertice, fragmento)
    private val posiciones = HashMap<String, Int>()

    fun u(nombre: String): Int = posiciones.getOrPut(nombre) {
        GLES20.glGetUniformLocation(id, nombre)
    }

    fun usar() = GLES20.glUseProgram(id)

    /**
     * Dibuja el rectangulo que cubre todo el destino.
     *
     * @param voltear true al dibujar en una [Lamina], para mantener la fila 0
     *   arriba; false al dibujar en la pantalla o el codificador.
     */
    fun dibujar(voltear: Boolean) {
        GLES20.glUniform1f(u("uVoltear"), if (voltear) 1f else 0f)
        val pos = GLES20.glGetAttribLocation(id, "aPos")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, CUADRADO)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
    }

    fun soltar() = GLES20.glDeleteProgram(id)

    companion object {
        private val CUADRADO: FloatBuffer = ByteBuffer
            .allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

        fun compilar(vs: String, fs: String): Int {
            val programa = GLES20.glCreateProgram()
            val v = shader(GLES20.GL_VERTEX_SHADER, vs)
            val f = shader(GLES20.GL_FRAGMENT_SHADER, fs)
            GLES20.glAttachShader(programa, v)
            GLES20.glAttachShader(programa, f)
            GLES20.glLinkProgram(programa)
            val estado = IntArray(1)
            GLES20.glGetProgramiv(programa, GLES20.GL_LINK_STATUS, estado, 0)
            check(estado[0] == GLES20.GL_TRUE) { "enlace: " + GLES20.glGetProgramInfoLog(programa) }
            GLES20.glDeleteShader(v)
            GLES20.glDeleteShader(f)
            return programa
        }

        private fun shader(tipo: Int, fuente: String): Int {
            val s = GLES20.glCreateShader(tipo)
            GLES20.glShaderSource(s, fuente)
            GLES20.glCompileShader(s)
            val estado = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, estado, 0)
            check(estado[0] == GLES20.GL_TRUE) { "shader: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
    }
}
