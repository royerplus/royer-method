package com.royermethod.app.editor.modelo

/** Un efecto independiente, activo solo en este tramo del bloque. */
data class EfectoVideo(
    val tipo: TipoEfectoVideo,
    val inicioUs: Long = 0,
    val finUs: Long,
    val intensidad: Float = 1f,
    val rapidez: Float = 1f,
    /** Tiempo ya recorrido antes de recortar o dividir: el patron no reinicia. */
    val desfaseUs: Long = 0,
    val fundidoEntradaUs: Long = 0,
    val fundidoSalidaUs: Long = 0,
    /** Duracion antes de recortar; 0 usa la duracion del tramo actual. */
    val duracionBaseUs: Long = 0,
    val activo: Boolean = true,
    /** Progreso medido en el reloj original del efecto, sin presets. */
    val progresos: List<ProgresoEfecto> = emptyList()
) {
    val duracionUs: Long get() = (finUs - inicioUs).coerceAtLeast(0)

    fun activoEn(localUs: Long): Boolean =
        activo && intensidad.isFinite() && intensidad > 0f && localUs >= inicioUs && localUs < finUs

    /** Intensidad con ambos bordes suaves, conservados al cortar el bloque. */
    fun fuerzaEn(localUs: Long): Float {
        if (!activoEn(localUs)) return 0f
        val transcurrido = (localUs - inicioUs + desfaseUs).coerceAtLeast(0)
        val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
        val entrada = if (fundidoEntradaUs > 0) (transcurrido.toDouble() / fundidoEntradaUs).coerceIn(0.0, 1.0) else 1.0
        val salida = if (fundidoSalidaUs > 0) ((base - transcurrido).toDouble() / fundidoSalidaUs).coerceIn(0.0, 1.0) else 1.0
        return (intensidad.coerceIn(0f, 3f) * entrada * salida).toFloat()
    }

    fun segundosEn(localUs: Long): Float {
        val ritmo = rapidez.takeIf { it.isFinite() }?.coerceIn(0.1f, 10f) ?: 1f
        return (localUs - inicioUs + desfaseUs) / 1_000_000f * ritmo
    }

    fun progresoEn(localUs: Long): Float {
        val ritmo = rapidez.takeIf { it.isFinite() }?.coerceIn(0.1f, 10f) ?: 1f
        val transcurrido = (localUs - inicioUs + desfaseUs).toDouble() * ritmo
        val puntos = progresos.filter { it.valor.isFinite() }.sortedBy { it.tiempoUs }.distinctBy { it.tiempoUs }
        if (puntos.isNotEmpty()) {
            if (transcurrido <= puntos.first().tiempoUs) return puntos.first().valor.coerceIn(0f, 1f)
            if (transcurrido >= puntos.last().tiempoUs) return puntos.last().valor.coerceIn(0f, 1f)
            val i = puntos.indexOfLast { it.tiempoUs <= transcurrido }
            val a = puntos[i]
            val b = puntos[i + 1]
            val f = ((transcurrido - a.tiempoUs) / (b.tiempoUs - a.tiempoUs)).toFloat()
            return (a.valor + (b.valor - a.valor) * f).coerceIn(0f, 1f)
        }
        val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
        if (base <= 0) return 1f
        return (transcurrido / base).toFloat().coerceIn(0f, 1f)
    }

    /** Conserva solo lo que cae entre [desdeUs] y [hastaUs], en el nuevo reloj. */
    fun recortado(desdeUs: Long, hastaUs: Long): EfectoVideo? {
        val desde = maxOf(inicioUs, desdeUs)
        val hasta = minOf(finUs, hastaUs)
        if (hasta <= desde) return null
        return copy(
            inicioUs = desde - desdeUs,
            finUs = hasta - desdeUs,
            desfaseUs = desfaseUs + desde - inicioUs,
            duracionBaseUs = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
        )
    }

    /** Los intervalos siguen al contenido al cambiar su velocidad. */
    fun tiempoEscalado(factor: Double): EfectoVideo = copy(
        inicioUs = (inicioUs * factor).toLong(),
        finUs = (finUs * factor).toLong(),
        desfaseUs = (desfaseUs * factor).toLong(),
        fundidoEntradaUs = (fundidoEntradaUs * factor).toLong(),
        fundidoSalidaUs = (fundidoSalidaUs * factor).toLong(),
        duracionBaseUs = (duracionBaseUs * factor).toLong(),
        progresos = progresos.map { it.copy(tiempoUs = (it.tiempoUs * factor).toLong()) }
    )
}

data class ProgresoEfecto(val tiempoUs: Long, val valor: Float)

enum class TipoEfectoVideo(val nombre: String) {
    SACUDIDA("Sacudida"),
    SACUDIDA_VERTICAL("Sacudida vertical"),
    ZOOM_RITMO("Zoom al ritmo"),
    FLASH("Flash"),
    FLASH_NEGRO("Flash negro"),
    ESTROBO("Estrobo"),
    RGB("Separar RGB"),
    NEGATIVO("Negativo"),
    DESENFOQUE("Desenfoque"),
    GLITCH("Glitch"),
    TWITCH("Twitch"),
    CIRCULO_CERRAR("Cerrar en circulo"),
    CIRCULO_ABRIR("Abrir en circulo"),
    BORDES_ESPEJO("Bordes espejo"),
    DESENFOQUE_HORIZONTAL("Desenfoque horizontal"),
    DESENFOQUE_VERTICAL("Desenfoque vertical"),
    CERRAR_VERTICAL("Cerrar vertical"),
    CERRAR_HORIZONTAL("Cerrar horizontal"),
    REVELAR_HORIZONTAL("Revelar horizontal")
}
