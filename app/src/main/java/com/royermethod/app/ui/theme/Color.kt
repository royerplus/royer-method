package com.royermethod.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de la app: un solo color de neon sobre fondo oscuro.
 *
 * Por defecto es blanco sobre negro puro (en OLED el pixel negro va apagado
 * y el blanco parece neon de verdad). El usuario puede cambiar el color del
 * neon y el fondo en Personalizar: todo lo que se dibuja sale de aqui, asi
 * que el cambio llega a cada pantalla sin tocarlas.
 *
 * Son propiedades y no constantes porque leen [Estilo], que es estado de
 * Compose: al cambiarlo se repinta lo que lo usa.
 */
object Ink {
    /** El fondo. Tambien el texto que va sobre un relleno de neon. */
    val Black: Color get() = Estilo.fondo.color
    /** El neon. */
    val White: Color get() = Estilo.acento

    /** Neon atenuado: jerarquia sin salirse del monocromo. */
    val White90: Color get() = Estilo.acento.copy(alpha = 0.90f)
    val White70: Color get() = Estilo.acento.copy(alpha = 0.70f)
    val White50: Color get() = Estilo.acento.copy(alpha = 0.50f)
    val White30: Color get() = Estilo.acento.copy(alpha = 0.30f)
    val White15: Color get() = Estilo.acento.copy(alpha = 0.15f)
    val White08: Color get() = Estilo.acento.copy(alpha = 0.08f)
    val White04: Color get() = Estilo.acento.copy(alpha = 0.04f)

    /** Capas del halo neon, de mas cerrado a mas abierto. */
    val Glow60: Color get() = Estilo.acento.copy(alpha = (0.60f * Estilo.halo).coerceIn(0f, 1f))
    val Glow35: Color get() = Estilo.acento.copy(alpha = (0.35f * Estilo.halo).coerceIn(0f, 1f))
    val Glow18: Color get() = Estilo.acento.copy(alpha = (0.18f * Estilo.halo).coerceIn(0f, 1f))
    val Glow08: Color get() = Estilo.acento.copy(alpha = (0.08f * Estilo.halo).coerceIn(0f, 1f))
    val Glow04: Color get() = Estilo.acento.copy(alpha = (0.04f * Estilo.halo).coerceIn(0f, 1f))

    /** Para avisos que no pueden pasar desapercibidos, sea cual sea el neon. */
    val Peligro = Color(0xFFFF5A6A)
}
