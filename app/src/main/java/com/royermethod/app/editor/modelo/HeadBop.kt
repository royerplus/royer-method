package com.royermethod.app.editor.modelo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Roles de capas PNG alineadas: el cabello hereda la cabeza y añade su arrastre. */
enum class RolHeadBop(val nombre: String) {
    CABEZA("Cabeza"), CABELLO("Cabello"), CUERPO("Cuerpo"),
    MANO_IZQUIERDA("Mano izquierda"), MANO_DERECHA("Mano derecha")
}

/**
 * Un movimiento musical continuo, evaluado por muestra, sin generar claves.
 * Los pivotes usan UV del PNG original (0..1 dentro; -1..2 permite un cuello
 * fuera de una pieza recortada). Los cuellos deben coincidir en el lienzo, aunque cada PNG
 * tenga otro recorte o encuadre; manos usan su propio pivote.
 */
data class HeadBop(
    val rol: RolHeadBop = RolHeadBop.CABEZA,
    val inicioUs: Long = 0,
    val finUs: Long,
    val bpm: Float = 120f,
    val intensidad: Float = 1f,
    val desfaseUs: Long = 0,
    val rapidez: Float = 1f,
    val retardoUs: Long = 80_000,
    val cuelloX: Float = 0.5f,
    val cuelloY: Float = 0.58f,
    val pivoteX: Float = 0.5f,
    val pivoteY: Float = 0.3f,
    val fundidoUs: Long = 120_000,
    val activo: Boolean = true,
    /** Conserva la envolvente al recortar, independientemente del desfase musical. */
    val recorteUs: Long = 0,
    val duracionBaseUs: Long = 0,
    /** Grados de giro principal antes de intensidad. */
    val giro: Float = 7f,
    /** Recorrido vertical máximo, en fracción del alto del lienzo. */
    val rebote: Float = 0.025f,
    /** Sólo balanceo secundario del pelo: no cambia el movimiento heredado. */
    val arrastre: Float = 1f,
    /** 0 mantiene el perfil original; 1 añade arco, cabeceo y recuperación suaves. */
    val naturalidad: Float = 0f
) {
    val duracionUs: Long get() = (finUs - inicioUs).coerceAtLeast(0)

    /** La envolvente original sigue siendo la misma tras dividir o recortar. */
    fun fuerzaEn(localUs: Long): Float {
        if (!activo || localUs < inicioUs || localUs >= finUs || !intensidad.isFinite()) return 0f
        val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
        val t = localUs - inicioUs + recorteUs
        fun suave(v: Double): Double = v.coerceIn(0.0, 1.0).let { it * it * (3.0 - 2.0 * it) }
        val borde = if (fundidoUs > 0)
            suave(t.toDouble() / fundidoUs) * suave((base - t).toDouble() / fundidoUs)
        else 1.0
        return (intensidad.coerceIn(0f, 3f) * borde).toFloat()
    }

    fun recortado(desdeUs: Long, hastaUs: Long): HeadBop? {
        val d = maxOf(inicioUs, desdeUs)
        val h = minOf(finUs, hastaUs)
        if (h <= d) return null
        return copy(inicioUs = d - desdeUs, finUs = h - desdeUs,
            desfaseUs = desfaseUs + d - inicioUs, recorteUs = recorteUs + d - inicioUs,
            duracionBaseUs = duracionBaseUs.takeIf { it > 0 } ?: duracionUs)
    }

    /** El mismo punto musical sigue al mismo contenido al cambiar velocidad. */
    fun tiempoEscalado(factor: Double): HeadBop = copy(
        inicioUs = (inicioUs * factor).toLong(), finUs = (finUs * factor).toLong(),
        desfaseUs = (desfaseUs * factor).toLong(), recorteUs = (recorteUs * factor).toLong(),
        fundidoUs = (fundidoUs * factor).toLong(), duracionBaseUs = (duracionBaseUs * factor).toLong(),
        rapidez = (rapidez / factor).toFloat())

    /** Ajusta bordes conservando el reloj; mover el intervalo completo usa [movido]. */
    fun conRango(inicio: Long, fin: Long): HeadBop {
        require(inicio >= 0 && fin > inicio)
        val delta = inicio - inicioUs
        val recorte = recorteUs + delta
        return copy(inicioUs = inicio, finUs = fin, desfaseUs = desfaseUs + delta,
            recorteUs = recorte, duracionBaseUs = maxOf(
                duracionBaseUs.takeIf { it > 0 } ?: duracionUs, recorte + fin - inicio))
    }

    fun movido(inicio: Long): HeadBop = copy(inicioUs = inicio, finUs = inicio + duracionUs)

    /** Matriz pantalla->UV con la jerarquía local compuesta sobre el encuadre. */
    fun matrizEn(base: FloatArray, localUs: Long, anchoFuente: Float, altoFuente: Float,
        anchoLienzo: Float = anchoFuente, altoLienzo: Float = altoFuente): FloatArray {
        val fuerza = fuerzaEn(localUs)
        if (fuerza <= 0f || anchoFuente <= 0 || altoFuente <= 0) return base
        val tempo = bpm.takeIf { it.isFinite() }?.coerceIn(1f, 2400f) ?: 120f
        val rate = rapidez.takeIf { it.isFinite() }?.coerceIn(0.001f, 1000f) ?: 1f
        val segundos = (localUs - inicioUs + desfaseUs).toDouble() * rate / 1_000_000.0
        val golpe = segundos * tempo / 60.0
        val lado = sin(PI * golpe).toFloat()
        val salto = (1.0 - cos(2.0 * PI * golpe)).toFloat()
        val mezcla = naturalidad.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val natural = if (mezcla > 0f) perfilNatural(golpe) else null
        fun mezclar(clasico: Float, nuevo: Float): Float = clasico + (nuevo - clasico) * mezcla
        val balanceo = natural?.let { mezclar(lado, it.giro) } ?: lado
        val grados = giro.takeIf { it.isFinite() }?.coerceIn(0f, 45f) ?: 7f
        val recorrido = rebote.takeIf { it.isFinite() }?.coerceIn(0f, 0.25f) ?: 0.025f
        val det = base[0] * base[4] - base[3] * base[1]
        if (kotlin.math.abs(det) < 1e-20f) return base
        fun pantalla(u: Float, v: Float): Pair<Float, Float> {
            val x = (u.takeIf { it.isFinite() } ?: 0.5f).coerceIn(-1f, 2f) - base[6]
            val y = (v.takeIf { it.isFinite() } ?: 0.5f).coerceIn(-1f, 2f) - base[7]
            return (x * base[4] - y * base[3]) / det to (base[0] * y - base[1] * x) / det
        }
        fun mover(m: FloatArray, px: Float, py: Float, giro: Float, dx: Float, dy: Float,
            escalaY: Float = 1f): FloatArray {
            val r = Math.toRadians((giro * fuerza).toDouble())
            val c = cos(r).toFloat(); val s = sin(r).toFloat()
            // Mbase * inversa(Tpantalla). El PNG puede tener otra escala,
            // recorte o giro: importa que sus pivotes coincidan en el lienzo.
            // La pequeña proyección de cabeceo ocurre sobre el cuello, en el
            // eje local de la cabeza. Su inversa se compone con el giro.
            val proyeccion = 1f - (1f - escalaY) * fuerza.coerceAtMost(1f)
            val a = c; val b = s
            val d = if (proyeccion == 1f) -s else -s / proyeccion
            val e = if (proyeccion == 1f) c else c / proyeccion
            val (x, y) = pantalla(px, py)
            val tx = x - a * (x + dx * anchoLienzo * fuerza) - b * (y + dy * altoLienzo * fuerza)
            val ty = y - d * (x + dx * anchoLienzo * fuerza) - e * (y + dy * altoLienzo * fuerza)
            return floatArrayOf(
                m[0] * a + m[3] * d, m[1] * a + m[4] * d, 0f,
                m[0] * b + m[3] * e, m[1] * b + m[4] * e, 0f,
                m[0] * tx + m[3] * ty + m[6], m[1] * tx + m[4] * ty + m[7], 1f)
        }
        fun cabeza(m: FloatArray = base) = mover(m, cuelloX, cuelloY, grados * balanceo,
            natural?.let { mezclar(recorrido * 0.24f * lado, recorrido * it.x) }
                ?: (recorrido * 0.24f * lado),
            natural?.let { mezclar(-0.5f * recorrido * salto, recorrido * it.y) }
                ?: (-0.5f * recorrido * salto),
            natural?.let { 1f - 0.012f * mezcla * it.cabeceo } ?: 1f)
        return when (rol) {
            RolHeadBop.CABEZA -> cabeza()
            RolHeadBop.CABELLO -> {
                val atras = golpe - retardoUs.coerceAtLeast(0) / 1_000_000.0 * tempo / 60.0
                val clasico = sin(PI * atras).toFloat() - lado
                val arrastre = natural?.let { mezclar(clasico, perfilNatural(atras).giro - it.giro) }
                    ?: clasico
                val secundaria = this.arrastre.takeIf { it.isFinite() }?.coerceIn(0f, 3f) ?: 1f
                // Forward = Tpadre * Tpelo * Tbase: la raíz del pelo viaja
                // exactamente con la cabeza, incluso con PNG recortados.
                cabeza(mover(base, pivoteX, pivoteY, grados * arrastre * secundaria, 0f, 0f))
            }
            RolHeadBop.CUERPO -> mover(base, pivoteX, pivoteY, -grados * 0.14f * balanceo, 0f,
                natural?.let { mezclar(-recorrido * 0.12f * salto, recorrido * 0.24f * it.y) }
                    ?: (-recorrido * 0.12f * salto))
            RolHeadBop.MANO_IZQUIERDA -> mover(base, pivoteX, pivoteY,
                natural?.let { mezclar(grados * 0.9f * sin(PI * golpe - 0.35).toFloat(),
                    grados * 0.72f * perfilNatural(golpe - 0.35 / PI).giro) }
                    ?: (grados * 0.9f * sin(PI * golpe - 0.35).toFloat()),
                0f, natural?.let { mezclar(-recorrido * 0.2f * salto, recorrido * 0.4f * it.y) }
                    ?: (-recorrido * 0.2f * salto))
            RolHeadBop.MANO_DERECHA -> mover(base, pivoteX, pivoteY,
                natural?.let { mezclar(-grados * 0.9f * sin(PI * golpe + 0.35).toFloat(),
                    -grados * 0.72f * perfilNatural(golpe + 0.35 / PI).giro) }
                    ?: (-grados * 0.9f * sin(PI * golpe + 0.35).toFloat()),
                0f, natural?.let { mezclar(-recorrido * 0.2f * salto, recorrido * 0.4f * it.y) }
                    ?: (-recorrido * 0.2f * salto))
        }
    }
}

private data class PerfilNatural(val giro: Float, val x: Float, val y: Float, val cabeceo: Float)

/** Un compás de cuatro golpes, sin ruido, saltos de fase ni deriva acumulada. */
private fun perfilNatural(golpe: Double): PerfilNatural {
    val frase = PI * golpe / 2.0
    val acento = 0.93 + 0.07 * cos(frase)
    val angulo = PI * golpe + 0.10 * sin(frase)
    // El tercer armónico aplana suavemente la reversa, sin congelar la cabeza.
    val giro = (sin(angulo) + 0.10 * sin(3.0 * angulo)) / 0.90
    val retrasado = golpe - 0.15
    // Fase monotónica: llegar al gesto y recuperarse duran tiempos diferentes.
    val fase = 2.0 * PI * (retrasado + 0.055 * (1.0 - cos(2.0 * PI * retrasado)))
    val cabeceo = (0.5 - 0.5 * cos(fase)) * acento
    return PerfilNatural(
        ((0.76 * giro + 0.06 * sin(fase)) * acento).toFloat(),
        (0.085 * giro + 0.015 * sin(fase)).toFloat(),
        (-0.72 * cabeceo - 0.10 * giro * giro * acento).toFloat(),
        cabeceo.toFloat())
}
