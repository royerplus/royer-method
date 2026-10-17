package com.royermethod.app.editor.motor

import android.content.Context
import com.royermethod.app.editor.modelo.PasoClip
import com.royermethod.app.procesado.Rife
import java.nio.ByteBuffer

/**
 * Fotogramas generados con el modelo de interpolacion, por bloque.
 *
 * Para cada fotograma de salida se toman varios instantes repartidos por su
 * exposicion, se genera con el modelo como se veria el video en cada uno, y
 * se promedian. Eso da dos cosas que el archivo original no tiene:
 *
 *  - Instantes exactos. Un video a 24 fps pasado a 30 no repite fotogramas:
 *    cada uno de salida se genera en su sitio. Sin esto, uno de cada cuatro
 *    se repite y el movimiento va a tirones.
 *  - El rastro del movimiento dentro de cada fotograma, que es lo que hace
 *    que 30 fps se vean fluidos y no a saltos.
 *
 * Es la misma idea que el pipeline de mejora (generar y mezclar), aplicada a
 * cada bloque por separado y en su tiempo, no al video ya montado: asi una
 * camara lenta dentro del edit tambien sale con fotogramas reales.
 */
class Fluidez(context: Context) {

    private val disponible = Rife.preparar(context)

    private class Estado(val ancho: Int, val alto: Int) {
        var a: ByteBuffer = Rife.buffer(ancho, alto)
        var b: ByteBuffer = Rife.buffer(ancho, alto)
        var ptsA = -1L
        var ptsB = -1L
        val generado: ByteBuffer = Rife.buffer(ancho, alto)
        val mezcla: ByteBuffer = Rife.buffer(ancho, alto)
        val acumulador: ByteBuffer = Rife.acumuladorPara(ancho, alto)
        val salida = Lamina(ancho, alto)
        var claveSalida: Pair<Long, Long>? = null

        fun soltar() = salida.soltar()
    }

    private val estados = HashMap<FuenteVideo, Estado>()

    /**
     * El fotograma de [paso] con los intermedios generados y mezclados.
     *
     * @return null si el modelo no esta disponible o algo fallo; quien llama
     *   sigue por el camino normal.
     */
    fun fotograma(fuente: FuenteVideo, paso: PasoClip, ancho: Int, alto: Int, copia: Reduccion): Lamina? {
        if (!disponible || !Rife.listo()) return null
        var e = estados[fuente]
        if (e == null || e.ancho != ancho || e.alto != alto) {
            e?.soltar()
            e = Estado(ancho, alto)
            estados[fuente] = e
        }
        val clave = paso.tiempoArchivoUs to paso.exposicionUs
        if (e.claveSalida == clave) return e.salida

        val centro = paso.tiempoArchivoUs
        val exp = paso.exposicionUs
        val n = if (exp > 0) MUESTRAS else 1
        val instantes = LongArray(n) { i ->
            if (n == 1) centro else centro + ((i + 0.5) / n - 0.5).times(exp).toLong()
        }
        val muestras = ancho * alto * 4

        if (!ventana(fuente, e, instantes.first(), copia)) return null
        var sumados = 0
        for (t in instantes) {
            // Avanzar la ventana hasta que [a, b) contenga t
            while (e.ptsB in 0..t && !fuente.agotada) {
                if (!avanzar(fuente, e, copia)) break
            }
            val f = if (e.ptsB > e.ptsA && e.ptsA >= 0) {
                ((t - e.ptsA).toDouble() / (e.ptsB - e.ptsA)).toFloat().coerceIn(0f, 1f)
            } else 0f
            val fuenteMuestra = when {
                f < 0.02f -> e.a
                f > 0.98f -> e.b
                Rife.interpolar(e.a, e.b, e.generado, ancho, alto, f) -> e.generado
                // Si el modelo falla en este instante, mejor el fotograma real
                // mas cercano que una imagen rota.
                f < 0.5f -> e.a
                else -> e.b
            }
            Rife.acumular(fuenteMuestra, e.acumulador, muestras)
            sumados++
        }
        Rife.promediar(e.acumulador, e.mezcla, muestras, sumados)
        e.salida.escribir(e.mezcla)
        e.claveSalida = clave
        return e.salida
    }

    /**
     * Deja en [e] los dos fotogramas que rodean [t]. Si la ventana ya esta
     * cerca, no hace nada: se avanza fotograma a fotograma, que es barato.
     */
    private fun ventana(fuente: FuenteVideo, e: Estado, t: Long, copia: Reduccion): Boolean {
        val valida = e.ptsA in 0..t && (e.ptsB > t || fuente.agotada || t - e.ptsB < 1_000_000)
        if (valida) return true
        // Salto: el fotograma que ya ha empezado en t, y el siguiente
        if (!fuente.irA(t)) return false
        if (fuente.ptsActual < 0) return false
        fuente.copiar(e.ancho, e.alto, copia).leer(e.a)
        e.ptsA = fuente.ptsActual
        if (fuente.siguiente()) {
            fuente.copiar(e.ancho, e.alto, copia).leer(e.b)
            e.ptsB = fuente.ptsActual
        } else {
            e.b.position(0); e.a.position(0)
            e.b.put(e.a); e.b.position(0); e.a.position(0)
            e.ptsB = e.ptsA
        }
        return true
    }

    private fun avanzar(fuente: FuenteVideo, e: Estado, copia: Reduccion): Boolean {
        val viejo = e.a
        e.a = e.b
        e.ptsA = e.ptsB
        e.b = viejo
        if (!fuente.siguiente()) {
            e.ptsB = e.ptsA
            return false
        }
        fuente.copiar(e.ancho, e.alto, copia).leer(e.b)
        e.ptsB = fuente.ptsActual
        return true
    }

    /** La fuente se cerro: lo suyo sobra. */
    fun olvidar(fuente: FuenteVideo) {
        estados.remove(fuente)?.soltar()
    }

    fun soltar() {
        estados.values.forEach { it.soltar() }
        estados.clear()
    }

    private companion object {
        /**
         * Instantes generados por fotograma de salida. Con el obturador a la
         * mitad, cinco por fotograma equivalen a muestrear a 300 fps durante
         * la exposicion: el rastro sale continuo, sin escalones.
         */
        const val MUESTRAS = 5
    }
}
