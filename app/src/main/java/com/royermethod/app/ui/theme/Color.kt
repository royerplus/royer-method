package com.royermethod.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta estrictamente monocroma.
 * El negro es negro puro para que en pantallas OLED el pixel quede apagado
 * y el blanco parezca neon de verdad por contraste absoluto.
 */
object Ink {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)

    /** Blancos atenuados: jerarquia sin salirse del monocromo. */
    val White90 = Color(0xE6FFFFFF)
    val White70 = Color(0xB3FFFFFF)
    val White50 = Color(0x80FFFFFF)
    val White30 = Color(0x4DFFFFFF)
    val White15 = Color(0x26FFFFFF)
    val White08 = Color(0x14FFFFFF)
    val White04 = Color(0x0AFFFFFF)

    /** Capas del halo neon, de mas cerrado a mas abierto. */
    val Glow60 = Color(0x99FFFFFF)
    val Glow35 = Color(0x59FFFFFF)
    val Glow18 = Color(0x2EFFFFFF)
    val Glow08 = Color(0x14FFFFFF)
    val Glow04 = Color(0x0AFFFFFF)
}
