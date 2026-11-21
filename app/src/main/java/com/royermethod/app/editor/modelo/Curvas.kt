package com.royermethod.app.editor.modelo

import kotlin.math.abs
import kotlin.math.max

/**
 * Curvas de velocidad para los fotogramas clave: plantillas listas, las que
 * guarda el usuario y la grafica de velocidad que se ensena al editarlas.
 *
 * Todas las plantillas son [Bezier]: elegir una y despues tocar sus puntos la
 * deja retocada a mano sin saltos. Los valores son los de las curvas de
 * siempre de la animacion por ordenador (easings.net), con nombres de lo que
 * se ve: una "Seda" arranca y frena despacio, un "Frenazo" sale disparado y
 * se para suave. Todas pasan por (0,0) y (1,1): la pose de cada clave se
 * respeta exacta, la curva solo reparte el camino en el tiempo.
 */
object PlantillasCurva {

    data class Plantilla(val nombre: String, val bezier: Bezier)

    val TODAS: List<Plantilla> = listOf(
        // Arranca y frena
        Plantilla("Seda", Bezier(0.37f, 0f, 0.63f, 1f)),
        Plantilla("Suave fuerte", Bezier(0.83f, 0f, 0.17f, 1f)),
        Plantilla("Extremo", Bezier(0.87f, 0f, 0.13f, 1f)),
        // Solo arranca o solo frena
        Plantilla("Despega", Bezier(0.64f, 0f, 0.78f, 0f)),
        Plantilla("Aterriza", Bezier(0.22f, 1f, 0.36f, 1f)),
        Plantilla("Frenazo", Bezier(0.16f, 1f, 0.3f, 1f)),
        Plantilla("Impacto", Bezier(0.05f, 0.7f, 0.1f, 1f)),
        // Se pasan y vuelven
        Plantilla("Anticipa", Bezier(0.36f, 0f, 0.66f, -0.56f)),
        Plantilla("Se pasa", Bezier(0.34f, 1.56f, 0.64f, 1f)),
        Plantilla("Ida y vuelta", Bezier(0.68f, -0.6f, 0.32f, 1.6f)),
        // Rapido, lento, rapido: la de los edits de velocidad
        Plantilla("Velocity", Bezier(0.1f, 0.9f, 0.9f, 0.1f)),
        Plantilla("Velocity suave", Bezier(0.2f, 0.7f, 0.8f, 0.3f))
    )

    /**
     * Una [Bezier] que se parece a la curva con nombre [c]: el punto de partida
     * al pasar a editarla a mano, asi tocar "A mano" no cambia el movimiento
     * de golpe. El elastico no cabe en una Bezier: se queda con la que frena.
     */
    fun aproximar(c: Curva): Bezier = when (c) {
        Curva.LINEAL -> Bezier(0.25f, 0.25f, 0.75f, 0.75f)
        Curva.SUAVE -> Bezier(0.65f, 0f, 0.35f, 1f)
        Curva.ENTRADA -> Bezier(0.32f, 0f, 0.67f, 0f)
        Curva.SALIDA -> Bezier(0.33f, 1f, 0.68f, 1f)
        Curva.REBOTE -> Bezier(0.34f, 1.56f, 0.64f, 1f)
        Curva.ELASTICO -> Bezier(0.22f, 1f, 0.36f, 1f)
        Curva.RAPIDA -> Bezier(0.16f, 1f, 0.3f, 1f)
    }

    /** El nombre de la plantilla que es exactamente [b], o null. */
    fun nombreDe(b: Bezier?): String? = b?.let { x -> TODAS.firstOrNull { it.bezier == x }?.nombre }

    /**
     * Velocidad de [f] en [x]: cuanto avanza por unidad de tiempo, 1 es la de
     * una recta. Diferencia centrada, con el paso recortado en los bordes.
     */
    fun velocidad(f: (Float) -> Float, x: Float): Float {
        val h = 1f / 512f
        val a = (x - h).coerceIn(0f, 1f)
        val b = (x + h).coerceIn(0f, 1f)
        if (b <= a) return 0f
        return (f(b) - f(a)) / (b - a)
    }

    /**
     * La velocidad de [f] en [n] + 1 puntos de 0 a 1, y la mayor en valor
     * absoluto (al menos 1, para que una curva lenta no parezca rapida).
     */
    fun grafica(f: (Float) -> Float, n: Int = 96): Pair<FloatArray, Float> {
        val v = FloatArray(n + 1) { i -> velocidad(f, i / n.toFloat()) }
        var pico = 1f
        for (x in v) if (x.isFinite()) pico = max(pico, abs(x))
        for (i in v.indices) if (!v[i].isFinite()) v[i] = 0f
        return v to pico
    }
}

/**
 * Una curva que el usuario guardo para usarla en otras claves: con puntos de
 * velocidad ([velocidad]) o, las de antes, una [Bezier].
 */
data class CurvaMia(val nombre: String, val bezier: Bezier? = null, val velocidad: CurvaVelocidad? = null)

/** Un punto de una [CurvaVelocidad]: en [x] (0 a 1 del tramo) va a [v] (1 es la velocidad pareja). */
data class PuntoVelocidad(val x: Float, val v: Float)

/**
 * Curva de velocidad hecha con puntos, como las de los editores de movil: se
 * dice lo rapido que va en cada parte del tramo y la app saca de ahi cuanto
 * ha avanzado. Entre los puntos la velocidad sigue una curva suave (Hermite
 * monotona: no se pasa por debajo de cero ni hace bultos entre dos puntos),
 * asi que el movimiento nunca da tirones. El camino se reparte para llegar
 * justo a la clave siguiente: si todos los puntos estan en 3, es igual que
 * todos en 1 (una lineal); lo que cuenta es la forma.
 *
 * El primer punto va siempre en x = 0 y el ultimo en x = 1.
 */
class CurvaVelocidad(puntos: List<PuntoVelocidad>) {

    val puntos: List<PuntoVelocidad> = limpiar(puntos)

    override fun equals(other: Any?): Boolean = other is CurvaVelocidad && other.puntos == puntos
    override fun hashCode(): Int = puntos.hashCode()
    override fun toString(): String = "CurvaVelocidad($puntos)"

    /** Pendientes de Fritsch-Carlson en cada punto. */
    private val pendientes: FloatArray by lazy { pendientesMonotonas(this.puntos) }

    /**
     * Avance acumulado en N + 1 instantes (ya dividido por el total) y la
     * velocidad repartida en esos mismos instantes.
     */
    private val tablas: Pair<FloatArray, FloatArray> by lazy { integrar() }

    /** Lo rapido que va en [x] segun los puntos (sin repartir). */
    fun velocidadCruda(x: Float): Float {
        val p = puntos
        val xx = x.coerceIn(0f, 1f)
        var i = 0
        while (i < p.size - 2 && xx > p[i + 1].x) i++
        val a = p[i]
        val b = p[i + 1]
        val h = b.x - a.x
        if (h <= 0f) return a.v
        val t = (xx - a.x) / h
        val t2 = t * t
        val t3 = t2 * t
        val v = (2 * t3 - 3 * t2 + 1) * a.v + (t3 - 2 * t2 + t) * h * pendientes[i] +
            (-2 * t3 + 3 * t2) * b.v + (t3 - t2) * h * pendientes[i + 1]
        return v.coerceAtLeast(0f)
    }

    /** Cuanto ha avanzado (0 a 1) en la fraccion de tiempo [x]. */
    fun en(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val (y, v) = tablas
        val f = x * N
        val i = f.toInt().coerceIn(0, N - 1)
        val t = f - i
        val h = 1f / N
        // Hermite con la velocidad de cada punto de la tabla: el avance y su
        // derivada siguen siendo continuos dentro de cada paso
        val t2 = t * t
        val t3 = t2 * t
        return ((2 * t3 - 3 * t2 + 1) * y[i] + (t3 - 2 * t2 + t) * h * v[i] +
            (-2 * t3 + 3 * t2) * y[i + 1] + (t3 - t2) * h * v[i + 1]).coerceIn(0f, 1f)
    }

    private fun integrar(): Pair<FloatArray, FloatArray> {
        // Simpson en cada paso: con la velocidad suave, el error queda muy por
        // debajo de una milesima de pixel
        val acum = DoubleArray(N + 1)
        for (i in 0 until N) {
            val a = i / N.toFloat()
            val b = (i + 1) / N.toFloat()
            val m = (a + b) / 2f
            acum[i + 1] = acum[i] + (b - a) / 6.0 * (velocidadCruda(a) + 4 * velocidadCruda(m) + velocidadCruda(b))
        }
        val tot = acum[N]
        // Todo a cero: no hay forma que repartir, va pareja
        if (tot <= 1e-9) return FloatArray(N + 1) { it / N.toFloat() } to FloatArray(N + 1) { 1f }
        return FloatArray(N + 1) { (acum[it] / tot).toFloat() } to
            FloatArray(N + 1) { (velocidadCruda(it / N.toFloat()) / tot).toFloat() }
    }

    companion object {
        private const val N = 256
        /** Lo mas que deja subir un punto: 6 veces la velocidad pareja. */
        const val MAXIMA = 6f
        /** Lo mas juntos que pueden quedar dos puntos. */
        const val SEPARACION = 0.03f

        /** Una lineal: dos puntos a la velocidad pareja. */
        val PAREJA = CurvaVelocidad(listOf(PuntoVelocidad(0f, 1f), PuntoVelocidad(1f, 1f)))

        /** Ordena, acota, junta los que se pisan y fija los extremos en 0 y 1. */
        internal fun limpiar(p: List<PuntoVelocidad>): List<PuntoVelocidad> {
            val ok = p.filter { it.x.isFinite() && it.v.isFinite() }
                .map { PuntoVelocidad(it.x.coerceIn(0f, 1f), it.v.coerceIn(0f, MAXIMA)) }
                .sortedBy { it.x }
            if (ok.isEmpty()) return listOf(PuntoVelocidad(0f, 1f), PuntoVelocidad(1f, 1f))
            val r = ArrayList<PuntoVelocidad>()
            for (q in ok) if (r.isEmpty() || q.x - r.last().x >= SEPARACION * 0.5f) r.add(q)
            if (r.size == 1) r.add(PuntoVelocidad(1f, r[0].v))
            r[0] = r[0].copy(x = 0f)
            r[r.lastIndex] = r.last().copy(x = 1f)
            return r
        }

        private fun pendientesMonotonas(p: List<PuntoVelocidad>): FloatArray {
            val n = p.size
            val m = FloatArray(n)
            if (n < 2) return m
            val d = FloatArray(n - 1) { i ->
                val h = p[i + 1].x - p[i].x
                if (h <= 0f) 0f else (p[i + 1].v - p[i].v) / h
            }
            m[0] = d[0]
            m[n - 1] = d[n - 2]
            for (i in 1 until n - 1) {
                m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
            }
            // Fritsch-Carlson: que ningun tramo se pase de sus dos puntos
            for (i in 0 until n - 1) {
                if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
                val a = m[i] / d[i]
                val b = m[i + 1] / d[i]
                val s = a * a + b * b
                if (s > 9f) {
                    val t = 3f / kotlin.math.sqrt(s)
                    m[i] = t * a * d[i]
                    m[i + 1] = t * b * d[i]
                }
            }
            return m
        }

        /** Puntos que siguen la velocidad de [f] (una curva cualquiera), para empezar a retocarla. */
        fun desde(f: (Float) -> Float, cuantos: Int = 5): CurvaVelocidad {
            val n = cuantos.coerceAtLeast(2)
            val (_, pico) = PlantillasCurva.grafica(f)
            val escala = if (pico > MAXIMA) MAXIMA / pico else 1f
            return CurvaVelocidad(List(n) { i ->
                val x = i / (n - 1f)
                PuntoVelocidad(x, (PlantillasCurva.velocidad(f, x) * escala).coerceIn(0f, MAXIMA))
            })
        }
    }
}

/** Curvas de velocidad listas, hechas con puntos. */
object PlantillasVelocidad {
    data class Plantilla(val nombre: String, val curva: CurvaVelocidad)

    private fun c(vararg p: Float) = CurvaVelocidad(p.toList().chunked(2).map { PuntoVelocidad(it[0], it[1]) })

    val TODAS: List<Plantilla> = listOf(
        // Lento, rapidisimo en medio y lento: el golpe de los edits
        Plantilla("Golpe", c(0f, 0.15f, 0.5f, 4.5f, 1f, 0.15f)),
        // Rapido, casi parado en medio y rapido otra vez
        Plantilla("Rampa", c(0f, 3f, 0.5f, 0.2f, 1f, 3f)),
        Plantilla("Doble golpe", c(0f, 0.2f, 0.25f, 3f, 0.5f, 0.2f, 0.75f, 3f, 1f, 0.2f)),
        Plantilla("Arranca", c(0f, 0.05f, 0.6f, 0.8f, 1f, 3.5f)),
        Plantilla("Frena", c(0f, 3.5f, 0.4f, 0.8f, 1f, 0.05f)),
        Plantilla("Pausa", c(0f, 2.2f, 0.4f, 0.05f, 0.6f, 0.05f, 1f, 2.2f)),
        Plantilla("Latigazo", c(0f, 0.2f, 0.65f, 0.35f, 0.85f, 5f, 1f, 0.3f)),
        Plantilla("Disparo", c(0f, 5f, 0.15f, 1.2f, 1f, 0.1f))
    )
}
