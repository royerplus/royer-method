package com.royermethod.app.editor.modelo

import kotlin.math.*

data class Croma(
    val activo: Boolean = false,
    val color: Int = 0xFF00FF00.toInt(),
    val intensidad: Float = 1f,
    val tolerancia: Float = .14f,
    val suavidad: Float = .05f,
    val limpiar: Float = .5f,
    val invertir: Boolean = false
) {
    val efectivo: Boolean get() = activo && intensidad > 0f
}

enum class FormaRecorte(val titulo: String) {
    NINGUNA("Sin máscara"), CIRCULO("Círculo"), CUADRADO("Cuadrado"), RECTANGULO("Rectángulo"),
    REDONDEADO("Redondeado"), OVALO("Óvalo"), ROMBO("Rombo"), ESTRELLA("Estrella"), CORAZON("Corazón"), LINEAL("Dividir")
}

/** Centro en UV del recorte visible; tamaños y pluma independientes del tamaño de exportación. */
data class MascaraForma(
    val tipo: FormaRecorte = FormaRecorte.NINGUNA,
    val x: Float = .5f, val y: Float = .5f,
    val ancho: Float = .8f, val alto: Float = .8f,
    val giro: Float = 0f, val pluma: Float = .015f,
    val radio: Float = .12f, val invertir: Boolean = false
) {
    fun semiejes(aspecto: Pair<Float, Float>): Pair<Float, Float> =
        if (tipo == FormaRecorte.CIRCULO || tipo == FormaRecorte.CUADRADO) ancho / 2 to ancho / 2
        else ancho * aspecto.first / 2 to alto * aspecto.second / 2

    /** Polígonos en una caja -1..1. Compartidos por el contorno de edición y el shader. */
    fun poligono(): List<Pair<Float, Float>> = when (tipo) {
        FormaRecorte.ESTRELLA -> List(10) { i ->
            val a = -PI / 2 + i * PI / 5
            val r = if (i % 2 == 0) 1f else .43f
            cos(a).toFloat() * r to sin(a).toFloat() * r
        }
        FormaRecorte.CORAZON -> List(24) { i ->
            val a = i * 2 * PI / 24
            val xx = 16 * sin(a).pow(3)
            val yy = -(13 * cos(a) - 5 * cos(2*a) - 2 * cos(3*a) - cos(4*a))
            (xx / 16).toFloat() to ((yy - 2) / 15).toFloat()
        }
        else -> emptyList()
    }

    fun aUv(p: Pair<Float, Float>, aspecto: Pair<Float, Float>): Pair<Float, Float> {
        val r = Math.toRadians(giro.toDouble()); val co = cos(r).toFloat(); val si = sin(r).toFloat()
        return x + (p.first * co - p.second * si) / aspecto.first to
            y + (p.first * si + p.second * co) / aspecto.second
    }

    fun asas(aspecto: Pair<Float, Float>): List<Pair<Float, Float>> {
        val (w,h) = semiejes(aspecto)
        return listOf(-w to -h, w to -h, w to h, -w to h).map { aUv(it, aspecto) }
    }

    fun contorno(aspecto: Pair<Float, Float>): List<Pair<Float, Float>> {
        val (w,h) = semiejes(aspecto)
        val puntos = when (tipo) {
            FormaRecorte.CIRCULO, FormaRecorte.OVALO -> List(64) { i -> val a = i * PI / 32; cos(a).toFloat()*w to sin(a).toFloat()*h }
            FormaRecorte.ROMBO -> listOf(0f to -h, w to 0f, 0f to h, -w to 0f)
            FormaRecorte.ESTRELLA, FormaRecorte.CORAZON -> poligono().map { it.first*w to it.second*h }
            FormaRecorte.REDONDEADO -> {
                val rr = radio.coerceIn(0f, min(w,h))
                (0..3).flatMap { j -> List(12) { i ->
                    val a = -PI + j*PI/2 + i*PI/22
                    val cx = if (j == 0 || j == 3) -w+rr else w-rr
                    val cy = if (j < 2) -h+rr else h-rr
                    cx + cos(a).toFloat()*rr to cy + sin(a).toFloat()*rr
                } }
            }
            FormaRecorte.LINEAL -> { val l = max(aspecto.first,aspecto.second)*4f; listOf(-l to 0f,l to 0f,l to l,-l to l) }
            else -> listOf(-w to -h, w to -h, w to h, -w to h)
        }
        return puntos.map { aUv(it, aspecto) }
    }
}

data class RecorteVisual(val croma: Croma = Croma(), val mascara: MascaraForma = MascaraForma()) {
    val activo: Boolean get() = croma.efectivo || mascara.tipo != FormaRecorte.NINGUNA
}
