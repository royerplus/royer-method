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
    val claves: List<Clave> = emptyList(),
    /** Las claves de antes de simplificar la trayectoria, para poder volver. */
    val clavesOriginales: List<Clave>? = null
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
     * Las de antes de simplificar se recortan igual.
     */
    fun recortada(desdeUs: Long, hastaUs: Long): PiezaVideo {
        if (claves.isEmpty() && clavesOriginales == null) return this
        return copy(
            claves = recortar(claves, desdeUs, hastaUs),
            clavesOriginales = clavesOriginales?.let { recortar(it, desdeUs, hastaUs) }
        )
    }

    fun tiempoEscalado(factor: Double): PiezaVideo = copy(
        claves = escalar(claves, factor),
        clavesOriginales = clavesOriginales?.let { escalar(it, factor) }
    )

    /**
     * La clave mas cercana a [local] a [tolerancia] o menos, entre las que
     * caen dentro del bloque (0 a [duracionUs]): las que encierran un tramo
     * recortado no se ven ni se tocan desde ahi.
     */
    fun claveCercana(local: Long, tolerancia: Long, duracionUs: Long = Long.MAX_VALUE): Clave? =
        Edicion.claveCercana(claves.filter { it.tiempoUs in 0L..duracionUs }, local, tolerancia)

    /**
     * Cambia la pose en [local]: en la clave que haya a [tolerancia] o menos
     * (la mas cercana), o en una nueva. La primera clave lejos del 0 deja
     * tambien una en el 0 con la pose de antes, para que no salte.
     */
    fun conPose(local: Long, duracionUs: Long, tolerancia: Long = Edicion.TOLERANCIA_CLAVE, cambio: (Encuadre) -> Encuadre): PiezaVideo {
        val tol = tolerancia.coerceAtLeast(0)
        val previa = claveCercana(local, tol, duracionUs)
        val t = previa?.tiempoUs ?: local.coerceIn(0L, duracionUs.coerceAtLeast(0))
        val base = if (claves.isEmpty() && t > tol) listOf(Clave(0, encuadre)) else emptyList()
        val nueva = (previa ?: Clave(t, encuadreEn(t))).copy(encuadre = cambio(encuadreEn(t)))
        return copy(claves = (claves.filter { it.tiempoUs != t } + base + nueva).sortedBy { it.tiempoUs })
    }

    /** Sin la clave mas cercana a [local] (a [tolerancia] o menos). */
    fun sinClave(local: Long, tolerancia: Long, duracionUs: Long = Long.MAX_VALUE): PiezaVideo {
        val k = claveCercana(local, tolerancia, duracionUs) ?: return this
        return copy(claves = claves.filter { it.tiempoUs != k.tiempoUs })
    }

    /**
     * La clave que esta en [desdeUs] (la mas cercana dentro del bloque, a
     * [tolerancia] o menos) llevada a [hastaUs] con su pose y su curva,
     * dentro de 0 a [duracionUs]. Si en el destino ya hay otra a
     * [tolerancia] o menos, la parte queda igual.
     */
    fun conClaveMovida(desdeUs: Long, hastaUs: Long, duracionUs: Long, tolerancia: Long = Edicion.TOLERANCIA_CLAVE): PiezaVideo {
        val k = claveCercana(desdeUs, tolerancia, duracionUs) ?: return this
        val movidas = Edicion.movidas(claves, k, hastaUs, duracionUs, tolerancia) ?: return this
        return copy(claves = movidas)
    }

    private companion object {
        fun recortar(lista: List<Clave>, desdeUs: Long, hastaUs: Long): List<Clave> {
            if (lista.isEmpty()) return lista
            val dentro = lista.filter { it.tiempoUs in desdeUs..hastaUs }
            val anterior = lista.filter { it.tiempoUs < desdeUs }.maxByOrNull { it.tiempoUs }
            val posterior = lista.filter { it.tiempoUs > hastaUs }.minByOrNull { it.tiempoUs }
            return (listOfNotNull(anterior) + dentro + listOfNotNull(posterior))
                .sortedBy { it.tiempoUs }.map { it.copy(tiempoUs = it.tiempoUs - desdeUs) }
        }

        fun escalar(lista: List<Clave>, factor: Double): List<Clave> =
            lista.map { it.copy(tiempoUs = (it.tiempoUs * factor).toLong()) }
    }
}
