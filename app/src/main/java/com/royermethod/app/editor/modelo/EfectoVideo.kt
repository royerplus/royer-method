package com.royermethod.app.editor.modelo

/** Un efecto independiente, activo solo en este tramo del bloque. */
data class EfectoVideo(
    val tipo: TipoEfectoVideo,
    val inicioUs: Long = 0,
    val finUs: Long,
    val intensidad: Float = 1f,
    val rapidez: Float = 1f,
    /** Tiempo recorrido: puede ser negativo al ampliar el comienzo del tramo. */
    val desfaseUs: Long = 0,
    val fundidoEntradaUs: Long = 0,
    val fundidoSalidaUs: Long = 0,
    /** Duracion antes de recortar; 0 usa la duracion del tramo actual. */
    val duracionBaseUs: Long = 0,
    val activo: Boolean = true,
    /** Progreso medido en el reloj original del efecto, sin presets. */
    val progresos: List<ProgresoEfecto> = emptyList(),
    /** Identidad dentro del bloque para elegirlo en la interfaz; 0 en proyectos viejos. */
    val id: Long = 0,
    /** Nombre de la combinacion rapida que lo agrego junto a otros; vacio si va suelto. */
    val grupo: String = "",
    /** Forma del movimiento: direccion, recorrido, curva, centro... Cada tipo usa los suyos. */
    val ajustes: AjustesEfecto = AjustesEfecto(),
    /** Solo en [TipoEfectoVideo.ANIMACION]: la animacion del bloque convertida en efecto. */
    val animacion: AnimacionEnEfecto? = null
) {
    val duracionUs: Long get() = (finUs - inicioUs).coerceAtLeast(0)

    /** Cambiar los bordes conserva el reloj y extiende su base si hace falta. */
    fun conRango(inicio: Long, fin: Long): EfectoVideo {
        require(inicio >= 0 && fin > inicio)
        val desfase = desfaseUs + inicio - inicioUs
        val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
        return copy(inicioUs = inicio, finUs = fin, desfaseUs = desfase,
            duracionBaseUs = maxOf(base, desfase + fin - inicio))
    }

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

    /**
     * Mueve el tramo entero sin cambiar lo que dura ni su reloj: lo que pasaba
     * al principio del efecto sigue pasando al principio. Nunca sale del bloque.
     */
    fun desplazado(deltaUs: Long, duracionBloqueUs: Long): EfectoVideo {
        val d = deltaUs.coerceIn(-inicioUs, (duracionBloqueUs - finUs).coerceAtLeast(-inicioUs))
        return if (d == 0L) this else copy(inicioUs = inicioUs + d, finUs = finUs + d)
    }

    /** El progreso con la curva elegida aplicada: lo que recorren los tipos "de .. a ..". */
    fun avanceEn(localUs: Long): Float = ajustes.avance(progresoEn(localUs))

    /** El valor entre [AjustesEfecto.desde] y [AjustesEfecto.hasta] en [localUs]. */
    fun valorEn(localUs: Long): Float = ajustes.desde + (ajustes.hasta - ajustes.desde) * avanceEn(localUs)

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

/**
 * Lo que da forma a un efecto ademas de su tramo e intensidad. Pocos numeros
 * con sentido en vez de decenas de claves. Que significa cada uno depende del
 * tipo (ver [TipoEfectoVideo]); los que un tipo no usa se ignoran.
 */
data class AjustesEfecto(
    /** Direccion en grados: 0 derecha, 90 abajo, 180 izquierda, 270 arriba (como las transiciones). */
    val angulo: Float = 0f,
    /** Valor al empezar el efecto y al terminarlo. La unidad la pone el tipo. */
    val desde: Float = 0f,
    val hasta: Float = 1f,
    /** Como se reparte el recorrido de [desde] a [hasta]. Si hay [bezier], manda ella. */
    val curva: Curva = Curva.SUAVE,
    val bezier: Bezier? = null,
    /** Centro respecto al centro del lienzo, en fracciones de su ancho y su alto. */
    val centroX: Float = 0f,
    val centroY: Float = 0f,
    /** Grosor del anillo en fraccion del radio maximo; 0 es un circulo lleno. */
    val grosor: Float = 0f,
    /** Borde suave en fraccion del alto del lienzo; 0 usa el de siempre del tipo. */
    val suavidad: Float = 0f,
    /** Al reves: mostrar fuera en vez de dentro, ocultar en vez de descubrir. */
    val invertir: Boolean = false,
    /** Mitades: corte vertical (izquierda y derecha) en vez de horizontal (arriba y abajo). */
    val vertical: Boolean = false
) {
    /** La curva elegida en [x], de 0 a 1. */
    fun avance(x: Float): Float = bezier?.en(x) ?: Escena.curva(curva, x)
}

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
    REVELAR_HORIZONTAL("Revelar horizontal"),

    // Herramientas de movimiento: pocos parametros (ajustes) en vez de claves

    /** Desde/hasta: recorrido en lienzos a lo largo de [AjustesEfecto.angulo]. */
    MOVER("Mover"),
    /** Desde/hasta: escala (1 = tamano normal). */
    ZOOM("Zoom"),
    /** Desde/hasta: grados. */
    GIRAR("Girar"),
    /** Se va hacia [AjustesEfecto.angulo] y vuelve por el lado contrario; desde = recorrido en lienzos. */
    LATIGAZO("Latigazo"),
    /** Rastro en la direccion de [AjustesEfecto.angulo], a cualquier angulo. */
    DESENFOQUE_DIRECCIONAL("Desenfoque direccional"),
    /** Desde/hasta: radio en fraccion del radio que cubre todo; con grosor es un anillo. */
    CIRCULO("Circulo o anillo"),
    /** Desde/hasta: cuanto se ha descubierto (0 a 1), con el borde avanzando hacia el angulo. */
    BARRIDO("Barrido"),
    /** Dos mitades que se juntan; desde/hasta: recorrido de cada mitad en lienzos. */
    MITADES("Mitades"),
    /** Una animacion del bloque convertida en efecto: lleva su contenido en [EfectoVideo.animacion]. */
    ANIMACION("Animacion")
}
