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

/** Una curva que el usuario guardo para usarla en otras claves. */
data class CurvaMia(val nombre: String, val bezier: Bezier)

/** Que mueve un arrastre sobre la imagen cuando el bloque tiene fotogramas clave. */
enum class ModoMover(val nombre: String, val corto: String) {
    /** El de siempre: cambia (o crea) la clave que hay bajo el cabezal. */
    CLAVE("Solo la clave de aqui", "esta clave"),
    /** Las claves elegidas, todas lo mismo. */
    ELEGIDAS("Las claves elegidas", "las elegidas"),
    /** El bloque entero con todo su movimiento. */
    TODO("Todo el clip con sus claves", "todo el clip")
}
