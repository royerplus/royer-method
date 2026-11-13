package com.royermethod.app.editor.modelo

/**
 * Cuanto pesa cada trozo del video exportado, decidido antes de empezar.
 *
 * El bitrate no es uno para todo el video. Cada tramo pide segun lo que
 * contiene: una foto quieta con un texto se ve perfecta con muy pocos bits,
 * una transicion con desenfoque de movimiento necesita muchos para no
 * deshacerse en bloques. Repartirlo asi da el mismo aspecto con bastante
 * menos peso que un bitrate fijo, que gasta de mas en lo facil y se queda
 * corto en lo dificil.
 */
data class PlanExportacion(
    val ancho: Int,
    val alto: Int,
    val fotogramas: Long,
    val hevc: Boolean,
    /** Bits por segundo de un tramo normal (factor 1). */
    val bitrateBase: Int,
    /** Factores sin normalizar: lo facil por debajo de 1, lo dificil por encima. */
    val tramos: List<Tramo>,
    val audioKbps: Int,
    val conAudio: Boolean,
    val duracionUs: Long
) {
    /**
     * Bitrate para el fotograma [n].
     *
     * Mira un poco por delante: el control de bitrate del codificador tarda
     * unos fotogramas en hacer caso a un cambio, y una transicion de un tercio
     * de segundo se habria acabado antes de recibir lo que pidio. Al subir se
     * adelanta; al bajar se espera a que el tramo caro haya pasado.
     */
    fun bitrateEn(n: Long): Int {
        val t = Linea.tiempoDe(n)
        val f = maxOf(factorEn(t) ?: 1f, factorEn(t + ANTICIPO_US) ?: 0f)
        return (bitrateBase * f).toInt().coerceAtLeast(MINIMO)
    }

    private fun factorEn(t: Long): Float? = tramos.firstOrNull { t >= it.desdeUs && t < it.hastaUs }?.factor

    /** Peso aproximado del archivo, para ensenarlo antes de exportar. */
    val bytesEstimados: Long by lazy {
        var bits = 0.0
        for (n in 0 until fotogramas) bits += bitrateEn(n).toDouble() / Linea.FPS
        if (conAudio) bits += duracionUs / 1_000_000.0 * audioKbps * 1000
        // Lo que ocupa el contenedor (cabeceras, indices): poco, pero algo
        (bits / 8 * 1.01).toLong()
    }

    companion object {
        /** Por debajo de esto cualquier codificador se desmorona. */
        const val MINIMO = 120_000

        /** Cuanto antes se pide el bitrate de un tramo caro. */
        const val ANTICIPO_US = 200_000L

        fun de(p: Proyecto, hevc: Boolean = p.salida.hevc, conAudio: Boolean = true): PlanExportacion {
            val (w, h) = p.formato.medidas(p.salida.ladoCorto)
            val bpp = if (hevc) p.salida.calidad.bppHevc else p.salida.calidad.bppAvc
            val base = (bpp.toDouble() * w * h * Linea.FPS).toInt()
            return PlanExportacion(
                ancho = w,
                alto = h,
                fotogramas = Linea.fotogramas(p),
                hevc = hevc,
                bitrateBase = base,
                tramos = Linea.tramos(p, normalizar = false),
                audioKbps = p.salida.calidad.audioKbps,
                conAudio = conAudio,
                duracionUs = Linea.duracion(p)
            )
        }
    }
}
