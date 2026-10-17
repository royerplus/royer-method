package com.royermethod.app.editor.modelo

/** Una junta compartida usa lados complementarios; un borde expuesto se suaviza. */
data class BordesPieza(
    val izquierda: Boolean = false,
    val arriba: Boolean = false,
    val derecha: Boolean = false,
    val abajo: Boolean = false
) {
    companion object { val NINGUNO = BordesPieza() }
}

/** Un dibujo de una parte, conservando su matriz y la textura completa. */
data class CortePieza(val parte: Int, val region: RegionUv?, val compartidos: BordesPieza)

/**
 * Cortes que se dibujan en UNA muestra del obturador. Dos partes solo
 * comparten una junta si sus matrices finales lienzo->UV son identicas en
 * ese instante. Comparar el encuadre no basta: el pivote depende de la parte.
 *
 * Si el vecino ocupa solo un tramo de un borde, se subdivide este dibujo
 * por los extremos de ese tramo. Asi cada lado del rectangulo es entero
 * compartido o entero expuesto, y un vec4 por dibujo basta en el sombreador.
 * La subdivision no cambia el proyecto, el encuadre ni el tamano de textura.
 */
fun PasoPartes.cortesEn(muestra: Int): List<CortePieza> {
    val salida = ArrayList<CortePieza>(partes.size)
    for ((indice, p) in partes.withIndex()) {
        val r = p.recorteFuente
        if (r == null) {
            salida += CortePieza(indice, null, BordesPieza.NINGUNO)
            continue
        }
        val i = muestra.coerceIn(0, p.matrices.lastIndex)
        val vecinos = partes.asSequence().filter { q ->
            q !== p && q.clip.id == p.clip.id && q.recorteFuente != null && q.visible &&
                q.opacidades[muestra.coerceIn(0, q.opacidades.lastIndex)] > 0.002f &&
                q.matrices[muestra.coerceIn(0, q.matrices.lastIndex)].contentEquals(p.matrices[i])
        }.map { it.recorteFuente!! }.filter { q ->
            ((q.derecha == r.izquierda || q.izquierda == r.derecha) &&
                minOf(q.abajo, r.abajo) > maxOf(q.arriba, r.arriba)) ||
                ((q.abajo == r.arriba || q.arriba == r.abajo) &&
                    minOf(q.derecha, r.derecha) > maxOf(q.izquierda, r.izquierda))
        }.toList()
        if (vecinos.isEmpty()) {
            salida += CortePieza(indice, r, BordesPieza.NINGUNO)
            continue
        }

        val xs = sortedSetOf(r.izquierda, r.derecha)
        val ys = sortedSetOf(r.arriba, r.abajo)
        for (v in vecinos) {
            if (v.derecha == r.izquierda || v.izquierda == r.derecha) {
                ys += v.arriba.coerceIn(r.arriba, r.abajo)
                ys += v.abajo.coerceIn(r.arriba, r.abajo)
            }
            if (v.abajo == r.arriba || v.arriba == r.abajo) {
                xs += v.izquierda.coerceIn(r.izquierda, r.derecha)
                xs += v.derecha.coerceIn(r.izquierda, r.derecha)
            }
        }
        val columnas = xs.toList()
        val filas = ys.toList()
        for (y in 0 until filas.lastIndex) for (x in 0 until columnas.lastIndex) {
            val c = RegionUv(columnas[x], filas[y], columnas[x + 1], filas[y + 1])
            salida += CortePieza(indice, c, BordesPieza(
                izquierda = c.izquierda > r.izquierda || vecinos.any { v ->
                    v.derecha == c.izquierda && v.arriba <= c.arriba && v.abajo >= c.abajo
                },
                arriba = c.arriba > r.arriba || vecinos.any { v ->
                    v.abajo == c.arriba && v.izquierda <= c.izquierda && v.derecha >= c.derecha
                },
                derecha = c.derecha < r.derecha || vecinos.any { v ->
                    v.izquierda == c.derecha && v.arriba <= c.arriba && v.abajo >= c.abajo
                },
                abajo = c.abajo < r.abajo || vecinos.any { v ->
                    v.arriba == c.abajo && v.izquierda <= c.izquierda && v.derecha >= c.derecha
                }
            ))
        }
    }
    return salida
}
