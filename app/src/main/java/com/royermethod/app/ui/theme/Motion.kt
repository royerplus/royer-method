package com.royermethod.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Todo el movimiento de la app sale de aqui.
 *
 * Criterio: animaciones lentas y con mucha desaceleracion al final.
 * Nada arranca de golpe ni frena de golpe; la idea es que se sienta
 * pesado y caro, no rapido.
 */
object Motion {

    // ---- Duraciones (ms) ----

    /** Micro-reaccion de un boton al tocarlo. */
    const val TAP = 220

    /** Cambio de estado dentro de un mismo componente. */
    const val QUICK = 420

    /** Aparicion o desaparicion de un elemento. */
    const val NORMAL = 700

    /** Transicion entre secciones. Los 2 segundos que pediste. */
    const val SECTION = 2000

    /** Tramos del splash. */
    const val SPLASH_LOGO_IN = 1400
    const val SPLASH_HOLD = 500
    const val SPLASH_SLIDE = 2000
    const val SPLASH_SETTLE = 900
    const val SPLASH_OUT = 900

    /** Ciclo del latido del halo neon. */
    const val BREATH = 3800

    // ---- Curvas ----

    /** Curva principal. Salida muy larga: el 80% del recorrido pasa al inicio. */
    val Smooth: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    /** Para cosas que entran a cuadro. Arranca quieto y se desliza. */
    val EnterSoft: Easing = CubicBezierEasing(0.16f, 1f, 0.30f, 1f)

    /** Para cosas que salen. Acelera al irse. */
    val ExitSoft: Easing = CubicBezierEasing(0.60f, 0f, 0.90f, 0.30f)

    /** Simetrica, para latidos y bucles. */
    val Breathe: Easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

    /** Con un rebote minimo, casi imperceptible. Solo para el asentado del logo. */
    val Settle: Easing = CubicBezierEasing(0.34f, 1.20f, 0.64f, 1f)
}
