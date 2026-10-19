package com.royermethod.app.editor.motor

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface

/**
 * Un contexto de OpenGL ES 2 con su superficie.
 *
 * El mismo sirve para la vista previa (la superficie es la de la pantalla) y
 * para exportar (la superficie es la entrada del codificador). Que el motor
 * dibuje igual en los dos casos es lo que garantiza que lo exportado sea lo
 * que se vio: no hay dos caminos de render que puedan separarse.
 */
class Egl(ventana: Any?, ancho: Int = 1, alto: Int = 1, paraCodificador: Boolean = false) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var contexto: EGLContext = EGL14.EGL_NO_CONTEXT
    private var superficie: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "sin display EGL" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }

        val atributos = mutableListOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE,
            if (ventana == null) EGL14.EGL_PBUFFER_BIT else EGL14.EGL_WINDOW_BIT
        )
        // EGL_RECORDABLE_ANDROID: sin esto el driver puede elegir un formato
        // que el codificador de video no sabe leer.
        if (paraCodificador) { atributos += RECORDABLE; atributos += 1 }
        atributos += EGL14.EGL_NONE

        val configs = arrayOfNulls<EGLConfig>(1)
        val cuantas = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, atributos.toIntArray(), 0, configs, 0, 1, cuantas, 0) &&
                cuantas[0] > 0
        ) { "eglChooseConfig" }
        val config = configs[0]

        contexto = EGL14.eglCreateContext(
            display, config, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(contexto != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }

        superficie = if (ventana == null) {
            EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, ancho, EGL14.EGL_HEIGHT, alto, EGL14.EGL_NONE), 0
            )
        } else {
            EGL14.eglCreateWindowSurface(display, config, ventana, intArrayOf(EGL14.EGL_NONE), 0)
        }
        check(superficie != EGL14.EGL_NO_SURFACE) { "eglCreateSurface" }
    }

    fun activar() {
        check(EGL14.eglMakeCurrent(display, superficie, superficie, contexto)) { "eglMakeCurrent" }
    }

    /** El codificador toma la marca de tiempo de aqui, no del orden de llamada. */
    fun marcarTiempo(nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, superficie, nanos)
    }

    fun publicar(): Boolean = EGL14.eglSwapBuffers(display, superficie)

    fun soltar() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (superficie != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, superficie)
            if (contexto != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, contexto)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        contexto = EGL14.EGL_NO_CONTEXT
        superficie = EGL14.EGL_NO_SURFACE
    }

    private companion object {
        const val RECORDABLE = 0x3142
    }
}
