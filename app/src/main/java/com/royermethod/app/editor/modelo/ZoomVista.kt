package com.royermethod.app.editor.modelo

/**
 * Zoom de la vista previa (no del proyecto): [escala] 1 es sin zoom, y
 * [x], [y] el punto del lienzo (0 a 1, desde arriba a la izquierda) que queda
 * en el centro de la pantalla.
 */
data class ZoomVista(val escala: Float = 1f, val x: Float = 0.5f, val y: Float = 0.5f) {
    val puesto: Boolean get() = escala > 1.001f

    /** De un punto del lienzo (px de la vista sin zoom, de [w] x [h]) a la pantalla. */
    fun aPantalla(px: Float, py: Float, w: Float, h: Float): Pair<Float, Float> =
        (px - x * w) * escala + w / 2 to (py - y * h) * escala + h / 2

    /** Lo contrario de [aPantalla]. */
    fun aLienzo(sx: Float, sy: Float, w: Float, h: Float): Pair<Float, Float> =
        (sx - w / 2) / escala + x * w to (sy - h / 2) / escala + y * h

    /**
     * Pellizco de [factor] alrededor de [focoX], [focoY] (pantalla) y
     * desplazamiento [dx], [dy] (pantalla), acotado a [MAXIMO] y a que la
     * vista no salga del lienzo.
     */
    fun mover(factor: Float, focoX: Float, focoY: Float, dx: Float, dy: Float, w: Float, h: Float): ZoomVista {
        val (fx, fy) = aLienzo(focoX, focoY, w, h)
        val e = (escala * (if (factor.isFinite() && factor > 0f) factor else 1f)).coerceIn(1f, MAXIMO)
        // El punto bajo los dedos se queda bajo los dedos
        var cx = (fx - (focoX - w / 2) / e) / w
        var cy = (fy - (focoY - h / 2) / e) / h
        cx -= dx / (w * e)
        cy -= dy / (h * e)
        return acotado(ZoomVista(e, cx, cy))
    }

    companion object {
        val NINGUNO = ZoomVista()
        const val MAXIMO = 6f

        fun acotado(z: ZoomVista): ZoomVista {
            val e = z.escala.coerceIn(1f, MAXIMO)
            val m = 0.5f / e
            fun c(v: Float) = if (v.isFinite()) v.coerceIn(m, 1f - m) else 0.5f
            return ZoomVista(e, c(z.x), c(z.y))
        }
    }
}
