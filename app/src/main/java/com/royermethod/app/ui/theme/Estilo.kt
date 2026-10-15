package com.royermethod.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Lo que el usuario puede personalizar del aspecto de la app.
 *
 * Es estado de Compose a nivel global: cambiar algo aqui repinta al momento
 * toda la app. Quien lo guarda en disco es Preferencias; esto no sabe nada
 * de Android para poder dibujar las pantallas tambien fuera del telefono.
 */
object Estilo {
    /** Color del neon. */
    var acento by mutableStateOf(Color.White)
    var fondo by mutableStateOf(FondoApp.NEGRO)
    /** Fuerza del halo: 0 sin halo, 1 lo normal, 1.6 muy brillante. */
    var halo by mutableFloatStateOf(1f)
    /** Si el neon "respira" (late despacio) o se queda fijo. */
    var latido by mutableStateOf(true)
    var velocidad by mutableStateOf(Velocidad.NORMAL)
    /** Tamano de la letra: 0.85 a 1.25. */
    var letra by mutableFloatStateOf(1f)
    /** Esquinas: 0 rectas, 1 lo normal, 1.5 muy redondas. */
    var redondez by mutableFloatStateOf(1f)

    /** Se llama al cambiar algo, para guardarlo. */
    var alCambiar: () -> Unit = {}

    fun restablecer() {
        acento = Color.White
        fondo = FondoApp.NEGRO
        halo = 1f
        latido = true
        velocidad = Velocidad.NORMAL
        letra = 1f
        redondez = 1f
        alCambiar()
    }
}

enum class FondoApp(val nombre: String, val color: Color) {
    NEGRO("Negro puro", Color(0xFF000000)),
    GRAFITO("Grafito", Color(0xFF0E0F12)),
    NOCHE("Azul noche", Color(0xFF070B16)),
    CIRUELA("Ciruela", Color(0xFF110713))
}

/** Cuanto duran las animaciones de la interfaz. */
enum class Velocidad(val nombre: String, val factor: Float) {
    LENTA("Lenta", 1.35f),
    NORMAL("Normal", 1f),
    RAPIDA("Rapida", 0.55f),
    INSTANTANEA("Sin animaciones", 0f)
}

/** Colores de neon propuestos. Ademas se puede elegir cualquiera a mano. */
val ACENTOS: List<Pair<String, Color>> = listOf(
    "Blanco" to Color(0xFFFFFFFF),
    "Cian" to Color(0xFF7DF9FF),
    "Menta" to Color(0xFF7DFFC4),
    "Lima" to Color(0xFFC6FF6B),
    "Ambar" to Color(0xFFFFC46B),
    "Coral" to Color(0xFFFF8A7A),
    "Rosa" to Color(0xFFFF7AD9),
    "Violeta" to Color(0xFFB69CFF),
    "Azul" to Color(0xFF7FA8FF)
)
