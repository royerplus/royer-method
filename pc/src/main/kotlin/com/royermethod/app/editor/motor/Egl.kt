package com.royermethod.app.editor.motor

import org.lwjgl.glfw.GLFW
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GLCapabilities
import org.lwjgl.system.MemoryUtil
import java.util.concurrent.Executors

/**
 * Un contexto de OpenGL propio (PC), en una ventana de GLFW que nunca se ve.
 *
 * Se dibuja siempre en laminas (framebuffers) y se lee el resultado: la vista
 * previa lo ensena en la ventana de Compose y la exportacion se lo da a FFmpeg.
 * Asi los dos caminos son el mismo, como en el telefono.
 *
 * [activar] lo deja activo en el hilo que llama; cada hilo de dibujo tiene el
 * suyo (vista previa, exportacion).
 */
class Egl {
    private val ventana: Long
    private var capacidades: GLCapabilities? = null

    init {
        ventana = enHiloGlfw {
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_FOCUSED, GLFW.GLFW_FALSE)
            // Sin version pedida: el driver da su perfil de compatibilidad mas
            // alto, que es el que deja usar vertices desde memoria como en GLES 2
            val v = GLFW.glfwCreateWindow(16, 16, "Royer Method GL", MemoryUtil.NULL, MemoryUtil.NULL)
            check(v != MemoryUtil.NULL) { "No se pudo abrir OpenGL en este PC" }
            v
        }
    }

    fun activar() {
        GLFW.glfwMakeContextCurrent(ventana)
        val c = capacidades ?: GL.createCapabilities().also { capacidades = it }
        GL.setCapabilities(c)
    }

    fun soltar() {
        GLFW.glfwMakeContextCurrent(MemoryUtil.NULL)
        GL.setCapabilities(null)
        enHiloGlfw { GLFW.glfwDestroyWindow(ventana) }
    }

    companion object {
        /** GLFW se inicia y crea sus ventanas en un solo hilo. */
        private val hilo = Executors.newSingleThreadExecutor { r -> Thread(r, "glfw").apply { isDaemon = true } }
        @Volatile
        private var iniciado = false

        private fun <T> enHiloGlfw(f: () -> T): T = hilo.submit<T> {
            if (!iniciado) {
                check(GLFW.glfwInit()) { "No se pudo iniciar GLFW" }
                iniciado = true
            }
            f()
        }.get()

        /** Nombre de la tarjeta y version de OpenGL, para el informe. */
        fun descripcion(): String = runCatching {
            "${org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)} · OpenGL ${org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION)}"
        }.getOrDefault("?")
    }
}
