package com.royermethod.app.editor.modelo

/** Limites reales en la textura completa, despues de orientar la fuente. */
data class RegionUv(
    val izquierda: Float = 0f,
    val arriba: Float = 0f,
    val derecha: Float = 1f,
    val abajo: Float = 1f
) {
    val ancho: Float get() = derecha - izquierda
    val alto: Float get() = abajo - arriba
    val centroX: Float get() = (izquierda + derecha) / 2f
    val centroY: Float get() = (arriba + abajo) / 2f

    fun acotada(): RegionUv {
        val l = izquierda.finito(0f).coerceIn(0f, 0.99999f)
        val t = arriba.finito(0f).coerceIn(0f, 0.99999f)
        return RegionUv(l, t, derecha.finito(1f).coerceIn(l + 0.00001f, 1f), abajo.finito(1f).coerceIn(t + 0.00001f, 1f))
    }

    fun interseccion(otra: RegionUv): RegionUv? {
        val l = maxOf(izquierda, otra.izquierda)
        val t = maxOf(arriba, otra.arriba)
        val r = minOf(derecha, otra.derecha)
        val b = minOf(abajo, otra.abajo)
        return if (r > l && b > t) RegionUv(l, t, r, b) else null
    }

    private fun Float.finito(defecto: Float) = takeIf { it.isFinite() } ?: defecto
}

/**
 * Parte de una fuente que conserva el encuadre de la imagen completa.
 * Sus posiciones, escala y giro son relativos al clip; el pivote es el centro
 * de su region visible. No vuelve a ajustar media imagen al lienzo entero.
 */
data class PiezaVideo(
    val nombre: String,
    val region: RegionUv,
    val encuadre: Encuadre = Encuadre(),
    val claves: List<Clave> = emptyList()
) {
    fun encuadreEn(local: Long): Encuadre {
        if (claves.isEmpty()) return encuadre
        val ordenadas = claves.sortedBy { it.tiempoUs }.distinctBy { it.tiempoUs }
        return when {
            local <= ordenadas.first().tiempoUs -> ordenadas.first().encuadre
            local >= ordenadas.last().tiempoUs -> ordenadas.last().encuadre
            else -> {
                val i = ordenadas.indexOfLast { it.tiempoUs <= local }
                val a = ordenadas[i]
                val b = ordenadas[i + 1]
                val f = (local - a.tiempoUs).toFloat() / (b.tiempoUs - a.tiempoUs)
                a.encuadre.hacia(b.encuadre, a.avance(f))
            }
        }
    }

    /** Editar una clave no deja tiempos fuera del bloque. */
    fun conClave(tiempoUs: Long, duracionUs: Long, valor: Encuadre): PiezaVideo {
        val t = tiempoUs.coerceIn(0, duracionUs.coerceAtLeast(0))
        val previa = claves.firstOrNull { it.tiempoUs == t }
        return copy(claves = (claves.filter { it.tiempoUs != t } +
            (previa?.copy(encuadre = valor) ?: Clave(t, valor))).sortedBy { it.tiempoUs })
    }

    /**
     * Conserva las claves que encierran el tramo, aunque queden fuera de sus
     * bordes. Reanclar una Bezier en el corte cambiaria su recorrido interior.
     */
    fun recortada(desdeUs: Long, hastaUs: Long): PiezaVideo {
        if (claves.isEmpty()) return this
        val dentro = claves.filter { it.tiempoUs in desdeUs..hastaUs }
        val anterior = claves.filter { it.tiempoUs < desdeUs }.maxByOrNull { it.tiempoUs }
        val posterior = claves.filter { it.tiempoUs > hastaUs }.minByOrNull { it.tiempoUs }
        return copy(claves = (listOfNotNull(anterior) + dentro + listOfNotNull(posterior))
            .sortedBy { it.tiempoUs }.map { it.copy(tiempoUs = it.tiempoUs - desdeUs) })
    }

    fun tiempoEscalado(factor: Double): PiezaVideo =
        copy(claves = claves.map { it.copy(tiempoUs = (it.tiempoUs * factor).toLong()) })
}
