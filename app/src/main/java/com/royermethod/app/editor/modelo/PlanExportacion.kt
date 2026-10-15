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
    /** Bitrate para el fotograma [n]. */
    fun bitrateEn(n: Long): Int {
        val t = Linea.tiempoDe(n)
        val f = tramos.firstOrNull { t >= it.desdeUs && t < it.hastaUs }?.factor ?: 1f
        return (bitrateBase * f).toInt().coerceAtLeast(MINIMO)
    }

    /** Peso aproximado del archivo, para ensenarlo antes de exportar. */
    val bytesEstimados: Long
        get() {
            var bits = 0.0
            for (tr in tramos) {
                val s = (tr.hastaUs - tr.desdeUs) / 1_000_000.0
                bits += s * (bitrateBase * tr.factor).coerceAtLeast(MINIMO.toFloat())
            }
            if (conAudio) bits += duracionUs / 1_000_000.0 * audioKbps * 1000
            // Lo que ocupa el contenedor (cabeceras, indices): poco, pero algo
            return (bits / 8 * 1.01).toLong()
        }

    companion object {
        /** Por debajo de esto cualquier codificador se desmorona. */
        const val MINIMO = 120_000

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
