package com.royermethod.app.editor.modelo

/**
 * Rampa de velocidad de un video: lo rapido que va en cada parte del bloque,
 * con puntos, como la "curva" de velocidad de los editores de movil. Sirve
 * para ralentizar un momento (normal, 0.2x en el golpe, normal) sin partir el
 * bloque.
 *
 * Los puntos son los de [CurvaVelocidad] (la misma curva suave entre ellos),
 * pero aqui la v de cada punto es la velocidad de verdad: 1 es normal, 0.2 es
 * cinco veces mas lento. La x es la posicion en el trozo del archivo que usa
 * el bloque (0 = [Clip.desdeUs], 1 = [Clip.hastaUs]); asi el punto lento cae
 * siempre sobre la misma imagen aunque cambie lo que dura el resto.
 *
 * Se multiplica por [Clip.velocidad]: la rampa da la forma y la velocidad del
 * bloque la sube o la baja entera.
 */
class Rampa(val curva: CurvaVelocidad) {

    override fun equals(other: Any?): Boolean = other is Rampa && other.curva == curva
    override fun hashCode(): Int = curva.hashCode()
    override fun toString(): String = "Rampa(${curva.puntos})"

    /** La velocidad en la posicion [u] del archivo (0 a 1). Nunca cero: se pararia para siempre. */
    fun velocidad(u: Float): Float = curva.velocidadCruda(u).coerceAtLeast(MINIMA)

    /**
     * Tiempo acumulado para llegar a cada posicion (N + 1 valores), en
     * unidades de "lo que dura el trozo a velocidad 1": la integral de 1/v.
     */
    private val tabla: DoubleArray by lazy {
        val t = DoubleArray(N + 1)
        for (i in 0 until N) {
            val a = i / N.toFloat()
            val b = (i + 1) / N.toFloat()
            val m = (a + b) / 2f
            t[i + 1] = t[i] + (b - a) / 6.0 * (1.0 / velocidad(a) + 4.0 / velocidad(m) + 1.0 / velocidad(b))
        }
        t
    }

    /** Lo que dura el trozo con la rampa, en veces lo que dura a velocidad 1. */
    val total: Double get() = tabla[N]

    /** Tiempo (en esas unidades) hasta la posicion [u]. */
    fun tiempoHasta(u: Float): Double {
        val f = u.coerceIn(0f, 1f) * N
        val i = f.toInt().coerceIn(0, N - 1)
        return tabla[i] + (tabla[i + 1] - tabla[i]) * (f - i)
    }

    /** La posicion del archivo (0 a 1) a la que se llega en el tiempo [t] (en esas unidades). */
    fun posicionEn(t: Double): Float {
        if (t <= 0.0) return 0f
        if (t >= total) return 1f
        var lo = 0
        var hi = N
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (tabla[mid] <= t) lo = mid else hi = mid
        }
        val d = tabla[hi] - tabla[lo]
        val f = if (d > 1e-12) (t - tabla[lo]) / d else 0.0
        return ((lo + f) / N).toFloat()
    }

    /** La rampa del trozo de [u0] a [u1], reescalada a 0..1 (al partir el bloque). */
    fun parte(u0: Float, u1: Float): Rampa? {
        if (u1 - u0 <= 1e-4f) return null
        val dentro = curva.puntos.filter { it.x > u0 && it.x < u1 }
        val pts = listOf(PuntoVelocidad(u0, curva.velocidadCruda(u0))) + dentro + PuntoVelocidad(u1, curva.velocidadCruda(u1))
        val r = Rampa(CurvaVelocidad(pts.map { PuntoVelocidad((it.x - u0) / (u1 - u0), it.v) }))
        return if (r.esPareja) null else r
    }

    /** Todos los puntos a 1: no cambia nada. */
    val esPareja: Boolean get() = curva.puntos.all { kotlin.math.abs(it.v - 1f) < 1e-3f }

    /** La mas lenta, para avisos y para el bitrate. */
    val minima: Float get() = (0..64).minOf { velocidad(it / 64f) }

    companion object {
        private const val N = 512
        /** Lo mas lento: 0.05x, veinte veces. */
        const val MINIMA = 0.05f

        /**
         * Las de siempre. v de cada punto = velocidad real, x = posicion en
         * el trozo.
         */
        val PLANTILLAS: List<Pair<String, Rampa>> = listOf(
            "Golpe lento" to r(0f, 1f, 0.35f, 1f, 0.5f, 0.2f, 0.65f, 1f, 1f, 1f),
            "Frenar" to r(0f, 1f, 0.6f, 1f, 1f, 0.2f),
            "Arrancar" to r(0f, 0.2f, 0.4f, 1f, 1f, 1f),
            "Héroe" to r(0f, 1.5f, 0.4f, 1.5f, 0.5f, 0.15f, 0.6f, 1.5f, 1f, 1.5f),
            "Bala" to r(0f, 2f, 0.3f, 2f, 0.4f, 0.1f, 0.7f, 0.1f, 0.8f, 2f, 1f, 2f),
            "Montaje" to r(0f, 1f, 0.25f, 3f, 0.5f, 0.3f, 0.75f, 3f, 1f, 1f)
        )

        private fun r(vararg xv: Float) = Rampa(CurvaVelocidad((xv.indices step 2).map { PuntoVelocidad(xv[it], xv[it + 1]) }))
    }
}
