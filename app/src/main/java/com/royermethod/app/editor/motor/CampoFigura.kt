package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.Clip
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * El campo de la figura de los elementos que llevan luces de contorno.
 *
 * Un campo son siete copias de la figura (lo que se ve del elemento: alfa,
 * croma y forma) cada vez mas desenfocadas, cada una el doble que la anterior,
 * repartidas en tres texturas pequenas. Con ellas el sombreador de la luz sabe
 * en cada pixel a que distancia cae el borde sin tener que buscarlo: una
 * linea gruesa, la luz que entra o el resplandor de fuera cuestan una o dos
 * lecturas, sean del ancho que sean y haya las luces que haya.
 *
 * Cada copia vive a la resolucion que le basta (las mas anchas, a un octavo):
 * hacer el campo entero cuesta poco mas que desenfocar una vez su nivel fino,
 * y un video recortado puede rehacerlo en cada fotograma.
 *
 * Se hace una vez por elemento y fotograma, antes de tocar el destino (cambiar
 * de destino a medio dibujar obliga a los moviles a guardar y recargar lo que
 * ya habia). Una foto o un texto no cambian: su campo se queda hecho.
 */
internal class CampoFigura {

    class Campo internal constructor(val ancho: Int, val alto: Int) {
        private val a = Lamina(ancho, alto)
        private val b = Lamina(ancho, alto)
        private val c = Lamina(ancho / 2, alto / 2)
        private val d = Lamina(ancho / 2, alto / 2)
        private val e = Lamina(ancho / 8, alto / 8)
        private val f = Lamina(ancho / 8, alto / 8)

        /** Nivel 0, en el canal rojo. */
        val fino: Int get() = a.textura
        /** Niveles 1 y 2, en rojo y verde. */
        val medio: Int get() = d.textura
        /** Niveles 3 a 6, en los cuatro canales, a un octavo de resolucion. */
        val ancha: Int get() = f.textura

        /** Lo que cubre el campo, en unidades del lado corto del elemento: su figura mas [MARGEN] por cada lado. */
        var ladoX = 1f
            internal set
        var ladoY = 1f
            internal set
        /** Anchura del desenfoque del nivel 0, en esas unidades. */
        val sigma0: Float get() = SIGMA * ladoX / ancho

        internal var textura = 0
        internal var clave: Any? = null
        internal var usadoEn = 0L

        internal fun hacer(silueta: Programa, desenfoque: Programa, cargar: (Programa) -> Unit, uvX: Float, uvY: Float, izquierda: Float, arriba: Float) {
            // Cada pasada pinta su lamina entera: borrarla antes le dice al movil
            // que no hace falta recargar lo que tenia
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            // 1. la figura
            a.activar()
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            silueta.usar()
            cargar(silueta)
            GLES20.glUniform4f(silueta.u("uRegion"), izquierda - MARGEN * uvX, arriba - MARGEN * uvY, ladoX * uvX, ladoY * uvY)
            GLES20.glUniform2f(silueta.u("uCuarto"), ladoX * uvX / ancho / 4f, ladoY * uvY / alto / 4f)
            silueta.dibujar(voltear = true)
            // 2. los niveles: cada pareja de pasadas (a lo ancho y a lo alto) deja los suyos
            desenfoque.usar()
            GLES20.glUniform1i(desenfoque.u("sTex"), 0)
            fun pasada(fuente: Lamina, destino: Lamina, x: Float, y: Float, s: FloatArray, canal: Int) {
                destino.activar()
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fuente.textura)
                GLES20.glUniform2f(desenfoque.u("uPaso"), x, y)
                GLES20.glUniform4f(desenfoque.u("uPeso"), peso(s[0]), peso(s[1]), peso(s[2]), peso(s[3]))
                GLES20.glUniform1f(desenfoque.u("uRadio"), min(60f, ceil(2.5f * s.max())))
                GLES20.glUniform4f(desenfoque.u("uCanal"), if (canal == 0) 1f else 0f, if (canal == 1) 1f else 0f, 0f, 0f)
                desenfoque.dibujar(voltear = true)
            }
            pasada(a, b, 1f / ancho, 0f, NIVEL_0, 0)
            pasada(b, a, 0f, 1f / alto, NIVEL_0, -1)
            pasada(a, c, 2f / ancho, 0f, NIVELES_12, 0)
            pasada(c, d, 0f, 2f / alto, NIVELES_12, -1)
            pasada(d, e, 8f / ancho, 0f, NIVELES_36, 1)
            pasada(e, f, 0f, 8f / alto, NIVELES_36, -1)
        }

        internal fun soltar() = listOf(a, b, c, d, e, f).forEach { it.soltar() }
    }

    private data class Clave(val textura: Int, val ancho: Int, val alto: Int, val fuente: Any?, val recorte: Any, val visual: Any, val fotograma: Long)

    private val campos = HashMap<Long, Campo>()
    private var silueta: Programa? = null
    private var desenfoque: Programa? = null

    /** Un sombreador no compilo en este telefono: las luces de contorno no se dibujan, lo demas sigue igual. */
    var fallo = false
        private set

    /**
     * Deja hecho el campo de [clip] para este fotograma. [textura] es la imagen
     * del elemento tal como se va a dibujar. Deja el destino y las texturas
     * activas cambiados y la mezcla quitada: quien llama los pone despues.
     *
     * @param estatica la imagen no cambia de un fotograma a otro (foto o texto)
     */
    fun preparar(numero: Long, clip: Clip, textura: Int, texAncho: Int, texAlto: Int, estatica: Boolean) {
        if (fallo) return
        try {
            val s = silueta ?: Programa(Sombreadores.VERTICE, FiguraShader.SILUETA).also { silueta = it }
            val d = desenfoque ?: Programa(Sombreadores.VERTICE, FiguraShader.DESENFOQUE).also { desenfoque = it }
            val (ax, ay) = FiguraShader.unidad(clip, texAncho, texAlto)
            val ladoX = ax + 2f * MARGEN
            val ladoY = ay + 2f * MARGEN
            val mayor = max(ladoX, ladoY)
            val lado = if (estatica) LADO else LADO_VIDEO
            val ancho = multiplo(lado * ladoX / mayor)
            val alto = multiplo(lado * ladoY / mayor)
            var campo = campos[clip.id]
            if (campo != null && (campo.ancho != ancho || campo.alto != alto)) {
                campo.soltar()
                campos.remove(clip.id)
                campo = null
            }
            if (campo == null) campo = Campo(ancho, alto).also { campos[clip.id] = it }
            val rc = clip.encuadre.recorte
            val clave = Clave(textura, texAncho, texAlto, clip.texto ?: clip.origen?.uri, rc, clip.recorteVisual, if (estatica) -1L else numero)
            campo.usadoEn = numero
            campo.textura = textura
            if (campo.clave == clave) return
            campo.ladoX = ladoX
            campo.ladoY = ladoY
            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textura)
            campo.hacer(s, d, { FiguraShader.cargar(it, clip, texAncho, texAlto) }, rc.ancho / ax, rc.alto / ay, rc.izquierda, rc.arriba)
            campo.clave = clave
        } catch (e: RuntimeException) {
            fallo = true
            runCatching { android.util.Log.w("RoyerLuces", "sin luces de contorno: ${e.message}") }
        }
    }

    /** El campo de [clip] si se preparo en este fotograma para esa misma [textura]. */
    fun de(clip: Long, textura: Int, numero: Long): Campo? =
        campos[clip]?.takeIf { it.usadoEn == numero && it.textura == textura && it.clave != null }

    /** Suelta los campos de los elementos que hace un rato que no se dibujan. */
    fun limpiar(numero: Long) {
        if (campos.isEmpty()) return
        val viejos = campos.filterValues { numero - it.usadoEn > RETENER }
        for ((k, v) in viejos) { v.soltar(); campos.remove(k) }
    }

    fun soltar() {
        campos.values.forEach { it.soltar() }
        campos.clear()
        silueta?.soltar(); silueta = null
        desenfoque?.soltar(); desenfoque = null
    }

    companion object {
        /** Lo que deja el campo alrededor del elemento, en unidades de su lado corto: hasta ahi llega el resplandor. */
        const val MARGEN = 0.25f

        /** Texeles del lado largo del campo. El de una foto o un texto se hace una sola vez. */
        private const val LADO = 768f

        /** El de un video se rehace en cada fotograma: mas pequeno. La linea fina no sale de el, sale de la imagen. */
        private const val LADO_VIDEO = 512f

        /** Anchura del nivel 0 en sus texeles; cada nivel siguiente, el doble. */
        private const val SIGMA = 1.5f

        private const val RETENER = 45L

        private fun multiplo(v: Float) = ((v / 8f).roundToInt() * 8).coerceAtLeast(8)

        /**
         * Lo que hay que desenfocar (en texeles del destino, que mide [div] del
         * nivel 0) para pasar de una anchura a otra. Las varianzas se suman;
         * [caja] es lo que ya anade promediar 2x2 al reducir a la mitad.
         */
        private fun falta(hasta: Float, desde: Float, div: Float, caja: Float) =
            sqrt(hasta * hasta - desde * desde - caja) / div

        private val NIVEL_0 = floatArrayOf(SIGMA, SIGMA, SIGMA, SIGMA)
        private val NIVELES_12 = falta(4f * SIGMA, SIGMA, 2f, .25f).let { floatArrayOf(falta(2f * SIGMA, SIGMA, 2f, .25f), it, it, it) }
        private val NIVELES_36 = floatArrayOf(8f, 16f, 32f, 64f).map { falta(it * SIGMA, 4f * SIGMA, 8f, 1f) }.toFloatArray()

        /** El peso de la muestra de al lado en una campana de esa anchura: de el salen los demas (ver DESENFOQUE). */
        private fun peso(sigma: Float) = exp(-.5f / (sigma * sigma))
    }
}
