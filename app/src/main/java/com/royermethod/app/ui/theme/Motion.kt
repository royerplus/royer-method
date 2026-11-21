package com.royermethod.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Todo el movimiento de la app sale de aqui.
 *
 * Criterio: animaciones lentas y con mucha desaceleracion al final.
 * Nada arranca de golpe ni frena de golpe; la idea es que se sienta
 * pesado y caro, no rapido. Las duraciones se escalan con la velocidad
 * que el usuario elija en Personalizar (o se anulan del todo).
 */
object Motion {

    // ---- Duraciones (ms) ----

    /** Micro-reaccion de un boton al tocarlo. */
    val TAP: Int get() = ms(220)

    /** Cambio de estado dentro de un mismo componente. */
    val QUICK: Int get() = ms(420)

    /** Aparicion o desaparicion de un elemento. */
    val NORMAL: Int get() = ms(700)

    /** Transicion entre secciones. Los 2 segundos que pediste. */
    val SECTION: Int get() = ms(2000)

    /** Tramos del splash. */
    val SPLASH_LOGO_IN: Int get() = ms(1400)
    val SPLASH_HOLD: Int get() = ms(500)
    val SPLASH_SLIDE: Int get() = ms(2000)
    val SPLASH_SETTLE: Int get() = ms(900)
    val SPLASH_OUT: Int get() = ms(900)

    /** Ciclo del latido del halo neon. No se acelera: un latido rapido cansa. */
    const val BREATH = 3800

    /** Duracion ajustada a la velocidad elegida en Personalizar. */
    private fun ms(base: Int): Int = (base * Estilo.velocidad.factor).toInt()

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
