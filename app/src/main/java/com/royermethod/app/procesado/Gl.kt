package com.royermethod.app.procesado

import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Contexto de OpenGL atado a la superficie de entrada del codificador.
 *
 * Todo el reescalado ocurre aqui, en la GPU, sin que los pixeles bajen nunca
 * a memoria normal: el decodificador entrega su fotograma como textura, se
 * dibuja reducido sobre la superficie del codificador y este lo comprime. Si
 * se hiciera copiando bytes, cada fotograma serian varios megas de ida y
 * vuelta y el movil no daria abasto.
 */
class EntornoGl(superficie: Surface) {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "sin display EGL" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize" }

        // EGL_RECORDABLE_ANDROID: sin esto el driver puede elegir un formato
        // que el codificador de video no sabe leer.
        val atributos = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            0x3142, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val cuantas = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, atributos, 0, configs, 0, 1, cuantas, 0) &&
                cuantas[0] > 0
        ) { "sin configuracion EGL valida" }

        context = EGL14.eglCreateContext(
            display, configs[0], EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        check(context != EGL14.EGL_NO_CONTEXT) { "eglCreateContext" }

        eglSurface = EGL14.eglCreateWindowSurface(
            display, configs[0], superficie, intArrayOf(EGL14.EGL_NONE), 0
        )
        check(eglSurface != EGL14.EGL_NO_SURFACE) { "eglCreateWindowSurface" }
    }

    fun activar() {
        check(EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) { "eglMakeCurrent" }
    }

    /** El codificador toma la marca de tiempo de aqui, no del orden de llamada. */
    fun marcarTiempo(nanos: Long) {
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nanos)
    }

    fun publicar() {
        EGL14.eglSwapBuffers(display, eglSurface)
    }

    fun soltar() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            EGL14.eglDestroySurface(display, eglSurface)
            EGL14.eglDestroyContext(display, context)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }
}

/**
 * Reduce la resolucion con un filtro Lanczos en dos pasadas.
 *
 * El filtro por defecto de la GPU (bilineal) mira solo cuatro pixeles vecinos.
 * Al reducir a la mitad o menos, la mayoria de los pixeles del original no se
 * miran siquiera: el detalle fino no se promedia, se tira, y aparece ese
 * cosquilleo en las lineas finas y las tramas. Lanczos abre el radio segun
 * cuanto se reduzca, asi que todos los pixeles de origen cuentan.
 *
 * Va en dos pasadas, primero horizontal y luego vertical, porque el filtro es
 * separable: hacerlo de una sola vez cuesta el cuadrado de muestras para el
 * mismo resultado.
 */
class Reductor(
    private val anchoOrigen: Int,
    private val altoOrigen: Int,
    private val anchoDestino: Int,
    private val altoDestino: Int,
    /**
     * Cuanto realzar el filo despues de reducir, de 0 a 1.
     *
     * Reducir siempre ablanda el contorno: los pixeles del borde se promedian
     * con los del fondo y la linea pierde el canto. Devolverle ese canto es lo
     * que hace que un video reducido no parezca reducido, y es justo lo que
     * hacia el paso de realce del pipeline de escritorio. Va despues de
     * reducir y nunca antes: afilar primero y reducir despues solo genera el
     * cerco blanco alrededor de las lineas.
     */
    private val realce: Float = 0f
) {
    var texturaEntrada = 0
        private set
    lateinit var surfaceTexture: SurfaceTexture
        private set

    private var programaH = 0
    private var programaV = 0
    private var programaPlano = 0
    private var fbo = 0
    private var fboSalida = 0
    private var texturaIntermedia = 0
    private var texturaSalida = 0
    private var texturaSubida = 0
    private val matriz = FloatArray(16)

    private val vertices: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        .apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    fun preparar() {
        programaH = compilar(VS, fs(externa = true, conRealce = false))
        programaV = compilar(VS, fs(externa = false, conRealce = realce > 0f))

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texturaEntrada = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texturaEntrada)
        parametros(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        surfaceTexture = SurfaceTexture(texturaEntrada)

        // Destino de la pasada horizontal: ya con el ancho final pero el alto
        // todavia del original.
        GLES20.glGenTextures(1, ids, 0)
        texturaIntermedia = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaIntermedia)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            anchoDestino, altoOrigen, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        parametros(GLES20.GL_TEXTURE_2D)

        GLES20.glGenFramebuffers(1, ids, 0)
        fbo = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texturaIntermedia, 0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * Lo que hace falta solo cuando se van a generar fotogramas: un destino
     * del que se pueda leer, y un camino para devolver a pantalla una imagen
     * que viene de memoria.
     *
     * No se monta siempre porque sin interpolacion el fotograma no baja nunca
     * de la GPU, y esto seria memoria reservada para nada.
     */
    fun prepararIdaYVuelta() {
        if (fboSalida != 0) return
        programaPlano = compilar(VS, FS_PLANO)

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texturaSalida = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaSalida)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            anchoDestino, altoDestino, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        parametros(GLES20.GL_TEXTURE_2D)

        GLES20.glGenFramebuffers(1, ids, 0)
        fboSalida = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboSalida)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, texturaSalida, 0
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        GLES20.glGenTextures(1, ids, 0)
        texturaSubida = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaSubida)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            anchoDestino, altoDestino, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
        )
        parametros(GLES20.GL_TEXTURE_2D)
    }

    /** Reduce el ultimo fotograma y lo deja en el destino intermedio. */
    fun reducirAparte() {
        dibujarEn(fboSalida)
    }

    /** Baja a memoria lo que haya en el destino intermedio, en RGBA. */
    fun leer(destino: java.nio.ByteBuffer) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboSalida)
        destino.position(0)
        GLES20.glReadPixels(
            0, 0, anchoDestino, altoDestino,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, destino
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /**
     * Sube una imagen de memoria y la dibuja sobre la superficie activa.
     *
     * Aqui va el realce, no en la reduccion: cuando hay interpolacion, lo que
     * llega ya ha pasado por el modelo y por la mezcla, y afilar antes de
     * mezclar solo habria emborronado el filo que se acaba de recuperar.
     */
    fun dibujarDesde(fuente: java.nio.ByteBuffer) {
        fuente.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaSubida)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D, 0, 0, 0, anchoDestino, altoDestino,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, fuente
        )

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, anchoDestino, altoDestino)
        GLES20.glUseProgram(programaPlano)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texturaSubida)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programaPlano, "sTex"), 0)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programaPlano, "uRealce"), realce)
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(programaPlano, "uSalto"),
            1f / anchoDestino, 1f / altoDestino
        )
        val pos = GLES20.glGetAttribLocation(programaPlano, "aPos")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
    }

    /** Dibuja el ultimo fotograma recibido, ya reducido, sobre lo que haya activo. */
    fun dibujar() = dibujarEn(0)

    private fun dibujarEn(destinoFinal: Int) {
        surfaceTexture.getTransformMatrix(matriz)

        // Pasada 1: solo horizontal, hacia la textura intermedia
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, anchoDestino, altoOrigen)
        pasada(
            programa = programaH,
            objetivo = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textura = texturaEntrada,
            usarMatriz = true,
            pasoX = 1f / anchoOrigen, pasoY = 0f,
            escala = maxOf(1f, anchoOrigen.toFloat() / anchoDestino)
        )

        // Pasada 2: solo vertical, hacia donde toque
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, destinoFinal)
        GLES20.glViewport(0, 0, anchoDestino, altoDestino)
        pasada(
            programa = programaV,
            objetivo = GLES20.GL_TEXTURE_2D,
            textura = texturaIntermedia,
            usarMatriz = false,
            pasoX = 0f, pasoY = 1f / altoOrigen,
            escala = maxOf(1f, altoOrigen.toFloat() / altoDestino),
            // Con interpolacion el realce no va aqui: se aplica al final, ya
            // sobre el fotograma mezclado.
            realce = if (destinoFinal == 0) realce else 0f
        )
        if (destinoFinal != 0) GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun pasada(
        programa: Int,
        objetivo: Int,
        textura: Int,
        usarMatriz: Boolean,
        pasoX: Float,
        pasoY: Float,
        escala: Float,
        realce: Float = 0f
    ) {
        GLES20.glUseProgram(programa)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programa, "uRealce"), realce)
        // Un pixel del resultado, no del original: el realce se mide sobre lo
        // que va a salir.
        GLES20.glUniform2f(
            GLES20.glGetUniformLocation(programa, "uSalto"),
            1f / anchoDestino, 1f / altoDestino
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(objetivo, textura)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programa, "sTex"), 0)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(programa, "uPaso"), pasoX, pasoY)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(programa, "uEscala"), escala)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(programa, "uMatriz"), 1, false,
            if (usarMatriz) matriz else IDENTIDAD, 0
        )
        val pos = GLES20.glGetAttribLocation(programa, "aPos")
        GLES20.glEnableVertexAttribArray(pos)
        GLES20.glVertexAttribPointer(pos, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(pos)
    }

    fun soltar() {
        if (::surfaceTexture.isInitialized) surfaceTexture.release()
        GLES20.glDeleteProgram(programaH)
        GLES20.glDeleteProgram(programaV)
        if (programaPlano != 0) GLES20.glDeleteProgram(programaPlano)
        GLES20.glDeleteFramebuffers(2, intArrayOf(fbo, fboSalida), 0)
        GLES20.glDeleteTextures(
            4, intArrayOf(texturaEntrada, texturaIntermedia, texturaSalida, texturaSubida), 0
        )
    }

    private fun parametros(objetivo: Int) {
        GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(objetivo, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private companion object {
        val IDENTIDAD = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )

        const val VS = """
            attribute vec2 aPos;
            varying vec2 vTex;
            void main() {
                vTex = aPos * 0.5 + 0.5;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        /**
         * Dibuja una imagen ya del tamano final, con el realce opcional.
         *
         * Es el camino de vuelta cuando el fotograma ha pasado por el modelo
         * de interpolacion: ya no hay que reducir nada, solo devolver el
         * resultado al codificador dandole el canto que la reduccion le quito.
         */
        const val FS_PLANO = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vTex;
            uniform sampler2D sTex;
            uniform vec2 uSalto;
            uniform float uRealce;

            void main() {
                vec4 color = texture2D(sTex, vTex);
                if (uRealce > 0.001) {
                    vec4 a = texture2D(sTex, vTex - vec2(uSalto.x, 0.0));
                    vec4 b = texture2D(sTex, vTex + vec2(uSalto.x, 0.0));
                    vec4 c = texture2D(sTex, vTex - vec2(0.0, uSalto.y));
                    vec4 d = texture2D(sTex, vTex + vec2(0.0, uSalto.y));
                    vec4 media = (a + b + c + d) * 0.25;
                    vec4 techo = max(max(max(a, b), max(c, d)), color);
                    vec4 suelo = min(min(min(a, b), min(c, d)), color);
                    color = clamp(color + (color - media) * uRealce, suelo, techo);
                }
                // Alfa a uno siempre.
                //
                // Un video no tiene transparencia, pero la superficie del
                // codificador si tiene canal alfa, y lo que llega de memoria
                // puede traerlo a cero. Escribirlo tal cual dejaba fotogramas
                // completamente negros mezclados con otros buenos.
                gl_FragColor = vec4(color.rgb, 1.0);
            }
        """

        /**
         * El mismo filtro para las dos pasadas; lo unico que cambia es el tipo
         * de textura, porque la del decodificador es externa y la intermedia
         * es normal.
         *
         * El limite del bucle es fijo porque en GLSL ES 2 tiene que ser
         * constante. Se sale antes con break en cuanto se pasa del radio, asi
         * que en reducciones suaves no cuesta las 20 muestras.
         */
        fun fs(externa: Boolean, conRealce: Boolean) = """
            ${if (externa) "#extension GL_OES_EGL_image_external : require" else ""}
            // El filtro acumula muchas muestras con pesos pequenos y en media
            // precision los redondeos se notan como bandas. No todas las GPU
            // viejas tienen alta precision en el fragmento, asi que se
            // pregunta antes en vez de darlo por hecho.
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            varying vec2 vTex;
            uniform ${if (externa) "samplerExternalOES" else "sampler2D"} sTex;
            uniform mat4 uMatriz;
            uniform vec2 uPaso;
            uniform vec2 uSalto;
            uniform float uEscala;
            uniform float uRealce;

            float lanczos(float x) {
                x = abs(x);
                if (x < 0.0001) return 1.0;
                if (x >= 2.0) return 0.0;
                float p = 3.14159265 * x;
                return (sin(p) / p) * (sin(p * 0.5) / (p * 0.5));
            }

            vec4 reducir(vec2 base) {
                float radio = 2.0 * uEscala;
                float total = 1.0 / max(uPaso.x + uPaso.y, 0.0000001);
                float centro = dot(base, sign(uPaso)) * total;
                // Si no llegan 20 muestras para todo el radio, se separan
                // (cada una ya promedia dos pixeles con el filtro de la GPU)
                float separacion = max(1.0, 2.0 * radio / 19.0);
                float inicio = separacion > 1.0 ? centro - radio + 0.5 * separacion : floor(centro - radio) + 0.5;
                // Los pixeles que caen justo debajo: el resultado no sale de
                // su rango, asi Lanczos da filo sin el cerco claro alrededor
                // de las lineas oscuras.
                float pie = 0.5 * uEscala + 0.5;
                vec4 suma = vec4(0.0);
                float pesos = 0.0;
                vec4 suelo = vec4(1.0);
                vec4 techo = vec4(0.0);
                for (int i = 0; i < 20; i++) {
                    float p = inicio + float(i) * separacion;
                    if (p > centro + radio) break;
                    float w = lanczos((p - centro) / uEscala);
                    // Sin salir del borde: fuera esta el relleno del decodificador
                    vec2 c = base + uPaso * (clamp(p, 0.5, total - 0.5) - centro);
                    vec4 t = texture2D(sTex, (uMatriz * vec4(c, 0.0, 1.0)).xy);
                    suma += t * w;
                    pesos += w;
                    if (abs(p - centro) <= pie) { suelo = min(suelo, t); techo = max(techo, t); }
                }
                return clamp(suma / max(pesos, 0.0001), suelo, techo);
            }

            void main() {
                vec4 color = reducir(vTex);
            ${
            if (!conRealce) "" else """
                // Realce por contraste local: se compara el pixel con la cruz
                // de sus cuatro vecinos y se le devuelve la diferencia. Se
                // limita al rango de esos vecinos para que el filo no genere
                // el cerco blanco tipico de afilar de mas.
                vec4 a = reducir(vTex - vec2(uSalto.x, 0.0));
                vec4 b = reducir(vTex + vec2(uSalto.x, 0.0));
                vec4 c = reducir(vTex - vec2(0.0, uSalto.y));
                vec4 d = reducir(vTex + vec2(0.0, uSalto.y));
                vec4 media = (a + b + c + d) * 0.25;
                vec4 techo = max(max(max(a, b), max(c, d)), color);
                vec4 suelo = min(min(min(a, b), min(c, d)), color);
                color = clamp(color + (color - media) * uRealce, suelo, techo);
            """
        }
                gl_FragColor = vec4(color.rgb, 1.0);
            }
        """

        fun compilar(vs: String, fs: String): Int {
            val programa = GLES20.glCreateProgram()
            GLES20.glAttachShader(programa, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(programa, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(programa)
            val estado = IntArray(1)
            GLES20.glGetProgramiv(programa, GLES20.GL_LINK_STATUS, estado, 0)
            check(estado[0] == GLES20.GL_TRUE) {
                "enlace: " + GLES20.glGetProgramInfoLog(programa)
            }
            return programa
        }

        fun shader(tipo: Int, fuente: String): Int {
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
