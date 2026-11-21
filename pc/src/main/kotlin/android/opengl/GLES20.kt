package android.opengl

import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30
import org.lwjgl.system.MemoryUtil
import java.nio.Buffer

/**
 * El OpenGL ES 2 del telefono sobre el OpenGL del PC (LWJGL, perfil de
 * compatibilidad). Con esto el motor del telefono (Gles, Compositor, las luces)
 * compila y dibuja aqui sin cambiar una linea.
 *
 * Lo unico que no es igual son los sombreadores: se escribieron en GLSL ES
 * 1.00 y el PC habla GLSL 1.20, asi que [glShaderSource] los traduce (ver
 * [Traductor]). Los videos llegan como textura normal, no externa: ver
 * [GLES11Ext].
 */
object GLES20 {
    const val GL_TRUE = 1
    const val GL_FALSE = 0
    const val GL_ONE = 1
    const val GL_ZERO = 0
    const val GL_SRC_ALPHA = 0x0302
    const val GL_ONE_MINUS_SRC_ALPHA = 0x0303
    const val GL_SRC_COLOR = 0x0300
    const val GL_ONE_MINUS_SRC_COLOR = 0x0301
    const val GL_DST_COLOR = 0x0306
    const val GL_ONE_MINUS_DST_COLOR = 0x0307
    const val GL_DST_ALPHA = 0x0304
    const val GL_ONE_MINUS_DST_ALPHA = 0x0305
    const val GL_CONSTANT_COLOR = 0x8001
    const val GL_CONSTANT_ALPHA = 0x8003
    const val GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004
    const val GL_BLEND = 0x0BE2
    const val GL_SCISSOR_TEST = 0x0C11
    const val GL_DEPTH_TEST = 0x0B71
    const val GL_TEXTURE_2D = 0x0DE1
    const val GL_TEXTURE0 = 0x84C0
    const val GL_TEXTURE1 = 0x84C1
    const val GL_TEXTURE2 = 0x84C2
    const val GL_TEXTURE3 = 0x84C3
    const val GL_TEXTURE4 = 0x84C4
    const val GL_TEXTURE5 = 0x84C5
    const val GL_TEXTURE_MIN_FILTER = 0x2801
    const val GL_TEXTURE_MAG_FILTER = 0x2800
    const val GL_TEXTURE_WRAP_S = 0x2802
    const val GL_TEXTURE_WRAP_T = 0x2803
    const val GL_LINEAR = 0x2601
    const val GL_NEAREST = 0x2600
    const val GL_CLAMP_TO_EDGE = 0x812F
    const val GL_REPEAT = 0x2901
    const val GL_RGBA = 0x1908
    const val GL_RGB = 0x1907
    const val GL_UNSIGNED_BYTE = 0x1401
    const val GL_FLOAT = 0x1406
    const val GL_FRAMEBUFFER = 0x8D40
    const val GL_FRAMEBUFFER_BINDING = 0x8CA6
    const val GL_FRAMEBUFFER_COMPLETE = 0x8CD5
    const val GL_COLOR_ATTACHMENT0 = 0x8CE0
    const val GL_COLOR_BUFFER_BIT = 0x4000
    const val GL_VIEWPORT = 0x0BA2
    const val GL_MAX_VIEWPORT_DIMS = 0x0D3A
    const val GL_MAX_TEXTURE_SIZE = 0x0D33
    const val GL_VERTEX_SHADER = 0x8B31
    const val GL_FRAGMENT_SHADER = 0x8B30
    const val GL_COMPILE_STATUS = 0x8B81
    const val GL_LINK_STATUS = 0x8B82
    const val GL_TRIANGLES = 0x0004
    const val GL_TRIANGLE_STRIP = 0x0005
    const val GL_TRIANGLE_FAN = 0x0006
    const val GL_NO_ERROR = 0

    fun glActiveTexture(t: Int) = GL13.glActiveTexture(t)
    fun glAttachShader(p: Int, s: Int) = GL20.glAttachShader(p, s)
    fun glBindFramebuffer(target: Int, fb: Int) = GL30.glBindFramebuffer(target, fb)
    fun glBindTexture(target: Int, t: Int) = GL11.glBindTexture(target, t)
    fun glBlendFunc(s: Int, d: Int) = GL11.glBlendFunc(s, d)
    fun glBlendColor(r: Float, g: Float, b: Float, a: Float) = GL14.glBlendColor(r, g, b, a)
    fun glCheckFramebufferStatus(target: Int): Int = GL30.glCheckFramebufferStatus(target)
    fun glClear(mask: Int) = GL11.glClear(mask)
    fun glClearColor(r: Float, g: Float, b: Float, a: Float) = GL11.glClearColor(r, g, b, a)
    fun glCompileShader(s: Int) = GL20.glCompileShader(s)
    fun glCreateProgram(): Int = GL20.glCreateProgram()
    fun glCreateShader(tipo: Int): Int = GL20.glCreateShader(tipo).also { tipos[it] = tipo }
    fun glDeleteFramebuffers(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) GL30.glDeleteFramebuffers(ids[offset + i])
    }
    fun glDeleteProgram(p: Int) = GL20.glDeleteProgram(p)
    fun glDeleteShader(s: Int) { tipos.remove(s); GL20.glDeleteShader(s) }
    fun glDeleteTextures(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) GL11.glDeleteTextures(ids[offset + i])
    }
    fun glDisable(cap: Int) = GL11.glDisable(cap)
    fun glEnable(cap: Int) = GL11.glEnable(cap)
    fun glIsEnabled(cap: Int): Boolean = GL11.glIsEnabled(cap)
    fun glDisableVertexAttribArray(i: Int) = GL20.glDisableVertexAttribArray(i)
    fun glEnableVertexAttribArray(i: Int) = GL20.glEnableVertexAttribArray(i)
    fun glFramebufferTexture2D(target: Int, attachment: Int, textarget: Int, texture: Int, level: Int) =
        GL30.glFramebufferTexture2D(target, attachment, textarget, texture, level)
    fun glGenFramebuffers(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) ids[offset + i] = GL30.glGenFramebuffers()
    }
    fun glGenTextures(n: Int, ids: IntArray, offset: Int) {
        for (i in 0 until n) ids[offset + i] = GL11.glGenTextures()
    }
    fun glGetAttribLocation(p: Int, nombre: String): Int = GL20.glGetAttribLocation(p, nombre)
    fun glGetUniformLocation(p: Int, nombre: String): Int = GL20.glGetUniformLocation(p, nombre)
    fun glGetIntegerv(pname: Int, v: IntArray, offset: Int) {
        val tmp = IntArray(16)
        GL11.glGetIntegerv(pname, tmp)
        val n = minOf(v.size - offset, tmp.size)
        System.arraycopy(tmp, 0, v, offset, n)
    }
    fun glGetError(): Int = GL11.glGetError()
    fun glGetProgramInfoLog(p: Int): String = GL20.glGetProgramInfoLog(p)
    fun glGetShaderInfoLog(s: Int): String = GL20.glGetShaderInfoLog(s)
    fun glGetProgramiv(p: Int, pname: Int, v: IntArray, offset: Int) { v[offset] = GL20.glGetProgrami(p, pname) }
    fun glGetShaderiv(s: Int, pname: Int, v: IntArray, offset: Int) { v[offset] = GL20.glGetShaderi(s, pname) }
    fun glLinkProgram(p: Int) = GL20.glLinkProgram(p)
    fun glFinish() = GL11.glFinish()
    fun glFlush() = GL11.glFlush()
    fun glScissor(x: Int, y: Int, w: Int, h: Int) = GL11.glScissor(x, y, w, h)
    fun glPixelStorei(pname: Int, v: Int) = GL11.glPixelStorei(pname, v)

    fun glReadPixels(x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, pixeles: Buffer) =
        GL11.nglReadPixels(x, y, w, h, format, type, MemoryUtil.memAddress(pixeles))

    fun glShaderSource(s: Int, fuente: String) =
        GL20.glShaderSource(s, Traductor.glsl(fuente, tipos[s] == GL_FRAGMENT_SHADER))

    fun glTexImage2D(target: Int, level: Int, interno: Int, w: Int, h: Int, borde: Int, format: Int, type: Int, pixeles: Buffer?) =
        GL11.nglTexImage2D(target, level, interno, w, h, borde, format, type, pixeles?.let { MemoryUtil.memAddress(it) } ?: 0L)

    fun glTexSubImage2D(target: Int, level: Int, x: Int, y: Int, w: Int, h: Int, format: Int, type: Int, pixeles: Buffer) =
        GL11.nglTexSubImage2D(target, level, x, y, w, h, format, type, MemoryUtil.memAddress(pixeles))

    fun glTexParameteri(target: Int, pname: Int, v: Int) = GL11.glTexParameteri(target, pname, v)
    fun glUniform1f(l: Int, x: Float) = GL20.glUniform1f(l, x)
    fun glUniform1i(l: Int, x: Int) = GL20.glUniform1i(l, x)
    fun glUniform2f(l: Int, x: Float, y: Float) = GL20.glUniform2f(l, x, y)
    fun glUniform3f(l: Int, x: Float, y: Float, z: Float) = GL20.glUniform3f(l, x, y, z)
    fun glUniform4f(l: Int, x: Float, y: Float, z: Float, w: Float) = GL20.glUniform4f(l, x, y, z, w)
    fun glUniform1fv(l: Int, n: Int, v: FloatArray, offset: Int) = GL20.glUniform1fv(l, v.copyOfRange(offset, offset + n))
    fun glUniform2fv(l: Int, n: Int, v: FloatArray, offset: Int) = GL20.glUniform2fv(l, v.copyOfRange(offset, offset + 2 * n))
    fun glUniform3fv(l: Int, n: Int, v: FloatArray, offset: Int) = GL20.glUniform3fv(l, v.copyOfRange(offset, offset + 3 * n))
    fun glUniform4fv(l: Int, n: Int, v: FloatArray, offset: Int) = GL20.glUniform4fv(l, v.copyOfRange(offset, offset + 4 * n))
    fun glUniformMatrix3fv(l: Int, n: Int, transponer: Boolean, v: FloatArray, offset: Int) =
        GL20.glUniformMatrix3fv(l, transponer, v.copyOfRange(offset, offset + 9 * n))
    fun glUniformMatrix4fv(l: Int, n: Int, transponer: Boolean, v: FloatArray, offset: Int) =
        GL20.glUniformMatrix4fv(l, transponer, v.copyOfRange(offset, offset + 16 * n))
    fun glUseProgram(p: Int) = GL20.glUseProgram(p)

    /** Como en Android: la posicion del bufer es el principio de los datos. */
    fun glVertexAttribPointer(i: Int, tam: Int, tipo: Int, normalizar: Boolean, paso: Int, datos: Buffer) =
        GL20.nglVertexAttribPointer(i, tam, tipo, normalizar, paso, MemoryUtil.memAddress(datos))

    fun glDrawArrays(modo: Int, primero: Int, cuantos: Int) = GL11.glDrawArrays(modo, primero, cuantos)
    fun glViewport(x: Int, y: Int, w: Int, h: Int) = GL11.glViewport(x, y, w, h)

    /** De que tipo es cada sombreador, para traducirlo al recibir su fuente. */
    private val tipos = java.util.concurrent.ConcurrentHashMap<Int, Int>()
}

/** Los videos del PC llegan en una textura normal: la "externa" del telefono es aqui una 2D. */
object GLES11Ext {
    const val GL_TEXTURE_EXTERNAL_OES = GLES20.GL_TEXTURE_2D
}

/**
 * De GLSL ES 1.00 (telefono) a GLSL 1.20 (PC).
 *
 * - Sin "precision": en el PC todo va en precision completa. Se definen
 *   lowp/mediump/highp vacios por si algun sombreador los pone en una
 *   variable.
 * - El sampler externo del video es un sampler2D (ver [GLES11Ext]).
 * - Los #ifdef GL_FRAGMENT_PRECISION_HIGH se quedan: sin "precision" las dos
 *   ramas quedan vacias.
 */
internal object Traductor {
    private val precision = Regex("""(?m)^\s*precision\s+\w+\s+\w+\s*;""")
    private val extension = Regex("""(?m)^\s*#extension\s+GL_OES_EGL_image_external\w*\s*:\s*\w+""")

    fun glsl(fuente: String, fragmento: Boolean): String {
        val cuerpo = fuente
            .replace(extension, "")
            .replace(precision, "")
            .replace("samplerExternalOES", "sampler2D")
        return buildString {
            append("#version 120\n")
            append("#define lowp\n#define mediump\n#define highp\n")
            append(cuerpo)
        }
    }
}
