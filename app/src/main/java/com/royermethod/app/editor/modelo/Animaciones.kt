package com.royermethod.app.editor.modelo

/*
 * Animaciones que se suman en un bloque: cada una con su nombre, sus piezas y
 * su duracion, y que se pueden quitar o cambiar sin tocar las demas.
 *
 * Los proyectos de antes guardan una sola entrada, una salida y unas
 * continuas en el propio bloque. No se tocan al abrirlos: pasan a la lista en
 * cuanto se edita una animacion ([conAnimacionesUnificadas]), y se ven igual
 * antes y despues, cuadro a cuadro.
 *
 * Todo es puro: cada operacion devuelve un bloque nuevo, asi una accion del
 * usuario es un solo paso de deshacer.
 */

/**
 * La entrada, la salida y las continuas de antes pasan a [Clip.animaciones],
 * delante de las que ya hubiera y en ese orden (entrada, salida, siempre),
 * que es el orden en que se evaluaban: el bloque se ve exactamente igual.
 * Llevan el nombre del catalogo si coinciden con una combinacion, o
 * "Entrada", "Salida" y "Siempre". Los ids son deterministas (el mayor + 1,
 * + 2...), asi la interfaz puede pedir la lista unificada para ensenarla y
 * operar despues sobre el bloque sin unificar. Una aplicada sin id o con el
 * id repetido recibe uno nuevo. Hacerlo dos veces no cambia nada.
 */
fun Clip.conAnimacionesUnificadas(): Clip {
    val heredadas = ArrayList<AnimacionAplicada>(3)
    if (entrada.isNotEmpty()) heredadas.add(
        AnimacionAplicada(0, nombrePiezas(entrada, MomentoAnimacion.ENTRADA), MomentoAnimacion.ENTRADA, piezas = entrada, duracionUs = duracionEntradaUs)
    )
    if (salida.isNotEmpty()) heredadas.add(
        AnimacionAplicada(0, nombrePiezas(salida, MomentoAnimacion.SALIDA), MomentoAnimacion.SALIDA, piezas = salida, duracionUs = duracionSalidaUs)
    )
    if (continua.isNotEmpty()) heredadas.add(
        AnimacionAplicada(0, nombreContinuas(continua), MomentoAnimacion.SIEMPRE, continuas = continua, duracionUs = duracionUs)
    )
    val vistos = HashSet<Long>()
    val buenas = animaciones.map { it.id > 0 && vistos.add(it.id) }
    if (heredadas.isEmpty() && buenas.all { it }) return this
    var siguiente = (animaciones.filterIndexed { i, _ -> buenas[i] }.maxOfOrNull { it.id } ?: 0L) + 1
    val nuevas = heredadas.map { it.copy(id = siguiente++) }
    val propias = animaciones.mapIndexed { i, a -> if (buenas[i]) a else a.copy(id = siguiente++) }
    return copy(entrada = emptyList(), salida = emptyList(), continua = emptyList(), animaciones = nuevas + propias)
}

/** El id que llevara la proxima animacion agregada (contando las de antes, que se unifican primero). */
fun Clip.siguienteIdAnimacion(): Long =
    (conAnimacionesUnificadas().animaciones.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(0L) + 1

/**
 * Duracion con la que empieza una animacion recien puesta, segun lo que hace
 * y lo que dura el bloque. Los golpes de luz son cortos, las sacudidas algo
 * mas, los desplazamientos medio segundo y lo que oscila un poco mas:
 *
 * | Piezas | Duracion |
 * |---|---|
 * | FLASH, FLASH_NEGRO, ESTROBO, NEGATIVO, RGB, PARPADEO | 0.3 s |
 * | SACUDIR, SACUDIR_VERTICAL, CAMARA, TWITCH, GLITCH | 0.4 s |
 * | APARECER, ZOOM_ENTRA, ZOOM_SALE, IZQUIERDA, DERECHA, ARRIBA, ABAJO, GIRO, REVELAR, DESENFOCAR | 0.5 s |
 * | REBOTE, PENDULO, ZOOM_VIBRA | 0.7 s |
 *
 * Con varias piezas, la de la mas larga. Nunca mas de la mitad del bloque ni
 * menos de un fotograma (ni mas que el bloque entero). SIEMPRE no tiene
 * duracion propia: dura todo el bloque. La interfaz la ajusta a fotogramas si
 * el iman esta encendido.
 */
fun duracionSugeridaUs(piezas: List<Movimiento>, momento: MomentoAnimacion, duracionBloqueUs: Long): Long {
    val dur = duracionBloqueUs.coerceAtLeast(1)
    if (momento == MomentoAnimacion.SIEMPRE) return dur
    val base = piezas.maxOfOrNull { duracionDe(it.tipo) } ?: 500_000L
    return minOf(base, dur / 2).coerceAtLeast(UN_FOTOGRAMA_US).coerceAtMost(dur)
}

private const val UN_FOTOGRAMA_US = 1_000_000L / Linea.FPS

private fun duracionDe(t: Animacion): Long = when (t) {
    Animacion.FLASH, Animacion.FLASH_NEGRO, Animacion.ESTROBO, Animacion.NEGATIVO, Animacion.RGB,
    Animacion.PARPADEO -> 300_000L
    Animacion.SACUDIR, Animacion.SACUDIR_VERTICAL, Animacion.CAMARA, Animacion.TWITCH, Animacion.GLITCH -> 400_000L
    Animacion.APARECER, Animacion.ZOOM_ENTRA, Animacion.ZOOM_SALE, Animacion.IZQUIERDA, Animacion.DERECHA,
    Animacion.ARRIBA, Animacion.ABAJO, Animacion.GIRO, Animacion.REVELAR, Animacion.DESENFOCAR -> 500_000L
    Animacion.REBOTE, Animacion.PENDULO, Animacion.ZOOM_VIBRA -> 700_000L
}

/**
 * Suma [a] al final, despues de unificar las de antes. Conserva su id si esta
 * libre; si no (o es 0) le da [siguienteIdAnimacion]. La agregada es siempre
 * la ultima de la lista. Las demas no cambian.
 */
fun Clip.conAnimacion(a: AnimacionAplicada): Clip {
    val u = conAnimacionesUnificadas()
    val id = if (a.id > 0 && u.animaciones.none { it.id == a.id }) a.id else u.siguienteIdAnimacion()
    return u.copy(animaciones = u.animaciones + a.copy(id = id, duracionUs = a.duracionUs.coerceAtLeast(0)))
}

/**
 * Cambia solo la animacion [id] (sobre la lista unificada), sin tocar las
 * demas ni su id. Si no existe o el cambio no cambia nada, devuelve el mismo
 * bloque: asi no se gasta un paso de deshacer en nada.
 */
fun Clip.cambiarAnimacion(id: Long, f: (AnimacionAplicada) -> AnimacionAplicada): Clip {
    val u = conAnimacionesUnificadas()
    val i = u.animaciones.indexOfFirst { it.id == id }
    if (i < 0) return this
    val vieja = u.animaciones[i]
    val nueva = f(vieja).let { it.copy(id = vieja.id, duracionUs = it.duracionUs.coerceAtLeast(0)) }
    if (nueva == vieja) return this
    return u.copy(animaciones = u.animaciones.toMutableList().also { it[i] = nueva })
}

/** Quita la animacion [id] y deja las demas como estaban. */
fun Clip.sinAnimacion(id: Long): Clip {
    val u = conAnimacionesUnificadas()
    if (u.animaciones.none { it.id == id }) return this
    return u.copy(animaciones = u.animaciones.filter { it.id != id })
}

/**
 * Lo que ocupan la entrada y la salida desde cada borde (la mas larga de
 * cada lado, de las que hacen algo), para dibujarlo en la linea de tiempo.
 */
fun Clip.tramosAnimacion(): Pair<Long, Long> {
    val dur = duracionUs.coerceAtLeast(0)
    var e = if (entrada.isNotEmpty()) duracionEntradaUs else 0L
    var s = if (salida.isNotEmpty()) duracionSalidaUs else 0L
    for (a in animaciones) if (a.activa && a.piezas.isNotEmpty()) when (a.momento) {
        MomentoAnimacion.ENTRADA -> e = maxOf(e, a.duracionUs)
        MomentoAnimacion.SALIDA -> s = maxOf(s, a.duracionUs)
        MomentoAnimacion.SIEMPRE -> Unit
    }
    return e.coerceIn(0, dur) to s.coerceIn(0, dur)
}

/** ¿Algo se mueve durante todo el bloque? (las continuas de antes o una SIEMPRE activa) */
fun Clip.mueveSiempre(): Boolean =
    continua.isNotEmpty() || animaciones.any { it.activa && it.momento == MomentoAnimacion.SIEMPRE && it.continuas.isNotEmpty() }

/**
 * ¿Hace algo que se pueda llevar a un efecto? ENTRADA y SALIDA con piezas y
 * duracion; SIEMPRE con continuas. Lo que se convierte se ve igual al
 * convertir (ver [limitacionesConversion] para lo que cambia despues).
 */
fun AnimacionAplicada.convertible(): Boolean = when (momento) {
    MomentoAnimacion.SIEMPRE -> continuas.isNotEmpty()
    MomentoAnimacion.ENTRADA, MomentoAnimacion.SALIDA -> piezas.isNotEmpty() && duracionUs > 0
}

/**
 * Lo que deja de ser igual despues de convertir, en frases cortas para
 * ensenarlas antes de hacerlo. Al convertir se ve igual; esto es lo que
 * cambia al editar el bloque despues.
 */
fun limitacionesConversion(a: AnimacionAplicada): List<String> {
    val r = ArrayList<String>()
    r.add(
        "La animacion seguia los bordes del bloque y el efecto sigue al contenido: si luego recortas, " +
            "cambias la velocidad o divides el bloque, ya no se recoloca sola."
    )
    if (a.momento == MomentoAnimacion.SIEMPRE) {
        val porBloque = setOf(AnimacionContinua.ACERCAMIENTO, AnimacionContinua.ALEJAMIENTO, AnimacionContinua.PANEO)
        if (a.continuas.any { it.tipo in porBloque }) {
            r.add("Acercamiento, alejamiento y paneo quedan medidos sobre lo que dura ahora el bloque.")
        }
        if (a.continuas.any { it.tipo != AnimacionContinua.ESTROBO && it.tipo != AnimacionContinua.RGB }) {
            r.add("El marco del bloque en el lienzo ya no sigue este movimiento.")
        }
    } else {
        // Una entrada esperaba fuera hasta su momento; un efecto, fuera de su tramo, no hace nada
        r.add(
            if (a.momento == MomentoAnimacion.ENTRADA) "Si lo mueves para que empiece mas tarde, antes de su tramo el bloque se ve entero."
            else "Si lo adelantas, despues de su tramo el bloque vuelve a verse entero."
        )
        if (a.piezas.any { it.tipo == Animacion.REVELAR }) r.add(
            "Con otra mascara o revelado a la vez manda la primera, y el orden cambia: " +
                "las animaciones se aplican antes que los efectos."
        )
    }
    return r
}

/** Lo que devuelve [convertirEnEfecto]: el bloque ya sin la animacion y el efecto que la sustituye. */
data class Conversion(val clip: Clip, val efecto: EfectoVideo)

/**
 * Quita la animacion [idAnimacion] y agrega en el MISMO paso un efecto
 * ANIMACION que hace lo mismo, con su propio tramo para moverlo y
 * recortarlo: nunca quedan las dos actuando a la vez.
 *
 * Tramos: ENTRADA de 0 a su duracion, SALIDA de (bloque - duracion) al final,
 * SIEMPRE todo el bloque. La base del efecto es la duracion de la animacion
 * (aunque el bloque sea mas corto), sin desfase salvo una salida mas larga
 * que el bloque, rapidez 1, intensidad 1 (las de las piezas van dentro) y
 * sin fundidos. Null si no existe o no es [convertible].
 */
fun Clip.convertirEnEfecto(idAnimacion: Long): Conversion? {
    val u = conAnimacionesUnificadas()
    val a = u.animaciones.firstOrNull { it.id == idAnimacion } ?: return null
    if (!a.convertible()) return null
    val dur = u.duracionUs
    if (dur <= 0) return null
    val inicio: Long
    val fin: Long
    val base: Long
    var desfase = 0L
    when (a.momento) {
        MomentoAnimacion.ENTRADA -> { inicio = 0; fin = minOf(a.duracionUs, dur); base = a.duracionUs }
        MomentoAnimacion.SALIDA -> {
            inicio = (dur - a.duracionUs).coerceAtLeast(0); fin = dur; base = a.duracionUs
            // Una salida mas larga que el bloque ya iba empezada en su primer instante
            desfase = (a.duracionUs - dur).coerceAtLeast(0)
        }
        MomentoAnimacion.SIEMPRE -> { inicio = 0; fin = dur; base = dur }
    }
    val efecto = EfectoVideo(
        tipo = TipoEfectoVideo.ANIMACION,
        inicioUs = inicio,
        finUs = fin,
        desfaseUs = desfase,
        duracionBaseUs = base,
        activo = a.activa,
        id = (u.efectos.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(0L) + 1,
        animacion = AnimacionEnEfecto(a.nombre, a.momento, a.piezas, a.continuas)
    )
    return Conversion(u.copy(animaciones = u.animaciones.filter { it.id != idAnimacion }, efectos = u.efectos + efecto), efecto)
}

/** Nombre del catalogo de unas piezas de entrada o salida (las dos usan el mismo). */
private fun nombrePiezas(piezas: List<Movimiento>, momento: MomentoAnimacion): String =
    Combinaciones.entrada.firstOrNull { it.piezas == piezas }?.nombre ?: momento.nombre

private fun nombreContinuas(continuas: List<Continuo>): String =
    Combinaciones.continuas.firstOrNull { it.second == continuas }?.first ?: MomentoAnimacion.SIEMPRE.nombre
