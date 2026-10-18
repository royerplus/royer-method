package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.CombinacionEfectos
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.conIdsEfectos
import com.royermethod.app.editor.modelo.moverGrupo
import com.royermethod.app.editor.modelo.siguienteIdEfecto
import com.royermethod.app.editor.modelo.sinEfecto
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * Las cuentas del panel de efectos, sin Compose (se prueban en el ordenador):
 * la franja (mini linea de tiempo del bloque con un carril por solape), el
 * arrastre de un tramo, los campos de tiempo y las acciones sobre los
 * efectos del bloque.
 *
 * Tiempos en microsegundos del reloj del bloque. Mover un tramo
 * ([EfectoVideo.desplazado]) lleva su reloj con el: lo que pasaba al
 * principio sigue pasando al principio. Recortar ([EfectoVideo.conRango])
 * deja el reloj quieto y quita o devuelve un trozo por un borde.
 *
 * Los efectos se eligen por id; los de proyectos viejos no tienen, asi que
 * el panel trabaja con [Clip.conIdsEfectos], que da siempre los mismos ids a
 * la misma lista: lo elegido sigue siendo lo mismo antes y despues del primer
 * cambio (que es cuando se guardan) y tras deshacer. Los ids no cambian la
 * imagen.
 */
internal object TramosEfecto {

    /** Carriles que se dibujan como mucho; con mas solapes se comparten. */
    const val MAX_CARRILES = 4

    /**
     * Reparto voraz: por orden de inicio (a igual inicio, el mas largo antes)
     * cada efecto va al primer carril que ya este libre cuando empieza. Dos
     * efectos solo comparten carril si no se solapan, y salen tan pocos
     * carriles como el mayor numero de efectos a la vez. El carril de cada
     * efecto, en el orden de la lista.
     */
    fun carriles(efectos: List<EfectoVideo>): IntArray {
        val orden = efectos.indices.sortedWith(compareBy<Int>({ efectos[it].inicioUs }, { -efectos[it].finUs }, { it }))
        val finDeCarril = ArrayList<Long>()
        val r = IntArray(efectos.size)
        for (i in orden) {
            val e = efectos[i]
            val fin = maxOf(e.finUs, e.inicioUs)
            val libre = finDeCarril.indexOfFirst { it <= e.inicioUs }
            r[i] = if (libre >= 0) libre.also { finDeCarril[it] = fin }
            else finDeCarril.size.also { finDeCarril.add(fin) }
        }
        return r
    }

    /**
     * Los carriles con los de [fijos] (por id) quietos: mientras se arrastra
     * una barra nada cambia de carril bajo el dedo; al soltar se reparten de
     * nuevo. Sin [fijos], [carriles].
     */
    fun carriles(efectos: List<EfectoVideo>, fijos: Map<Long, Int>?): IntArray {
        val r = carriles(efectos)
        if (fijos == null) return r
        return IntArray(efectos.size) { i -> fijos[efectos[i].id] ?: r[i] }
    }

    /** Cuantos carriles se dibujan: los que hagan falta, de 1 a [MAX_CARRILES]. */
    fun visibles(carriles: IntArray): Int = ((carriles.maxOrNull() ?: 0) + 1).coerceIn(1, MAX_CARRILES)

    /** Con muchos solapes se compacta: los carriles de mas se reparten entre los que se ven. */
    fun carrilVisible(carril: Int): Int = carril % MAX_CARRILES

    // ---- Del tiempo a la franja y vuelta ----

    private fun util(anchoPx: Float, margenPx: Float): Float = (anchoPx - 2 * margenPx).coerceAtLeast(1f)

    /** Del tiempo del bloque a x en la franja, con [margenPx] libres a cada lado para las asas. */
    fun x(tUs: Long, duracionUs: Long, anchoPx: Float, margenPx: Float): Float {
        if (duracionUs <= 0) return margenPx
        return margenPx + util(anchoPx, margenPx) * (tUs.coerceIn(0, duracionUs).toDouble() / duracionUs).toFloat()
    }

    /** De x en la franja al tiempo del bloque, dentro de 0..[duracionUs]. */
    fun tiempo(xPx: Float, duracionUs: Long, anchoPx: Float, margenPx: Float): Long {
        if (duracionUs <= 0 || xPx.isNaN()) return 0
        val f = ((xPx - margenPx) / util(anchoPx, margenPx)).toDouble().coerceIn(0.0, 1.0)
        return (f * duracionUs).roundToLong().coerceIn(0, duracionUs)
    }

    /** Lo que se ha corrido el dedo, en tiempo (con signo). */
    fun delta(dxPx: Float, duracionUs: Long, anchoPx: Float, margenPx: Float): Long {
        if (duracionUs <= 0 || dxPx.isNaN()) return 0
        return (dxPx.toDouble() / util(anchoPx, margenPx) * duracionUs).roundToLong()
    }

    // ---- Tocar y agarrar ----

    enum class Agarre { MOVER, INICIO, FIN }

    /** Una barra de la franja, ya en pixeles. */
    data class Barra(val id: Long, val x0: Float, val x1: Float, val carril: Int)

    /**
     * Que parte de la barra [x0]..[x1] se agarra en [x]: los extremos
     * recortan y el centro mueve. Cada asa llega [asaPx] hacia fuera y hasta
     * un tercio de la barra hacia dentro; en una barra estrecha el centro
     * sigue midiendo al menos media asa, asi siempre se puede mover. Null si
     * [x] cae fuera de la barra y de sus asas.
     */
    fun agarre(x: Float, x0: Float, x1: Float, asaPx: Float): Agarre? {
        if (x < x0 - asaPx || x > x1 + asaPx) return null
        val dentro = minOf(asaPx, (x1 - x0) / 3f)
        var c0 = x0 + dentro
        var c1 = x1 - dentro
        if (c1 - c0 < asaPx / 2f) {
            val medio = (x0 + x1) / 2f
            c0 = medio - asaPx / 4f
            c1 = medio + asaPx / 4f
        }
        return when {
            x < c0 -> Agarre.INICIO
            x > c1 -> Agarre.FIN
            else -> Agarre.MOVER
        }
    }

    private fun carrilEn(y: Float, altoCarrilPx: Float): Int = floor(y / altoCarrilPx).toInt()

    /**
     * La barra que se toca en (x, y): de las que cubren x (con [holguraPx] a
     * cada lado, para que las cortas se puedan tocar), la del carril mas
     * cercano al dedo; a igual distancia, la elegida y despues la mas corta.
     * Null si en esa x no hay ninguna: es tocar vacio.
     */
    fun tocada(barras: List<Barra>, x: Float, y: Float, altoCarrilPx: Float, holguraPx: Float, elegida: Long?): Barra? {
        if (barras.isEmpty() || altoCarrilPx <= 0f) return null
        val carril = carrilEn(y, altoCarrilPx)
        return barras
            .filter { x >= it.x0 - holguraPx && x <= it.x1 + holguraPx }
            .minWithOrNull(compareBy<Barra>({ abs(it.carril - carril) }, { if (it.id == elegida) 0 else 1 }, { it.x1 - it.x0 }))
    }

    /**
     * A que barra va el dedo en (x, y), para tocar y para arrastrar:
     *  1. la que esta justo debajo, en su carril (si hay varias, la elegida
     *     y despues la mas corta);
     *  2. si ahi no hay ninguna, la elegida, aunque el dedo caiga en el carril
     *     de al lado o en su asa (los carriles son bajos y el pulgar ancho);
     *  3. si no, la mas cercana ([tocada]). Null en vacio.
     */
    fun objetivo(barras: List<Barra>, x: Float, y: Float, altoCarrilPx: Float, asaPx: Float, elegida: Long?): Barra? {
        if (barras.isEmpty() || altoCarrilPx <= 0f) return null
        val carril = carrilEn(y, altoCarrilPx)
        barras.filter { it.carril == carril && x >= it.x0 && x <= it.x1 }
            .minWithOrNull(compareBy<Barra>({ if (it.id == elegida) 0 else 1 }, { it.x1 - it.x0 }))
            ?.let { return it }
        barras.firstOrNull { it.id == elegida }?.let { b ->
            if (abs(b.carril - carril) <= 1 && agarre(x, b.x0, b.x1, asaPx) != null) return b
        }
        return tocada(barras, x, y, altoCarrilPx, asaPx, elegida)
    }

    /** Lo que se arrastra si el dedo empieza en (x, y): la barra de [objetivo] y que parte. Null en vacio. */
    fun agarrada(barras: List<Barra>, x: Float, y: Float, altoCarrilPx: Float, asaPx: Float, elegida: Long?): Pair<Barra, Agarre>? {
        val b = objetivo(barras, x, y, altoCarrilPx, asaPx, elegida) ?: return null
        val a = agarre(x, b.x0, b.x1, asaPx) ?: return null
        return b to a
    }

    // ---- Nombres en las barras ----

    enum class Rotulo { DENTRO, FUERA, CORTADO, NADA }

    /**
     * Donde va el nombre de una barra: dentro si cabe entero; si no, a su
     * derecha si ahi hay hueco libre en el carril ([libreDerechaPx]); si no,
     * dentro y cortado con "..." si queda sitio para algo ([minimoCortadoPx]).
     */
    fun rotulo(anchoTextoPx: Float, anchoBarraPx: Float, rellenoPx: Float, libreDerechaPx: Float, minimoCortadoPx: Float): Rotulo = when {
        anchoTextoPx + 2 * rellenoPx <= anchoBarraPx -> Rotulo.DENTRO
        anchoTextoPx + rellenoPx <= libreDerechaPx -> Rotulo.FUERA
        anchoBarraPx - 2 * rellenoPx >= minimoCortadoPx -> Rotulo.CORTADO
        else -> Rotulo.NADA
    }

    /** Hueco libre a la derecha de la barra [i] en su carril, hasta la siguiente o hasta [finPx]. */
    fun libreDerecha(barras: List<Barra>, i: Int, finPx: Float): Float {
        val b = barras[i]
        val siguiente = barras.filterIndexed { j, o -> j != i && o.carril == b.carril && o.x0 >= b.x1 }.minOfOrNull { it.x0 } ?: finPx
        return (siguiente - b.x1).coerceAtLeast(0f)
    }

    // ---- Iman y limites ----

    /** Lo menos que dura un efecto: un fotograma con el iman, si no 1 ms (nunca mas que el bloque). */
    fun duracionMinima(fotogramas: Boolean, duracionBloqueUs: Long): Long =
        (if (fotogramas) Linea.tiempoDe(1) else 1_000L).coerceIn(1, duracionBloqueUs.coerceAtLeast(1))

    /**
     * Lo menos que puede quedar [e] al recortarlo: [duracionMinima], salvo que
     * ya dure menos (un tiempo escrito sin iman): entonces no se le obliga a
     * crecer solo por tocarlo.
     */
    fun minimo(e: EfectoVideo, fotogramas: Boolean, duracionBloqueUs: Long): Long =
        minOf(duracionMinima(fotogramas, duracionBloqueUs), e.duracionUs).coerceAtLeast(1)

    /**
     * Un tiempo arrastrado, pegado al fotograma que se exporta (contando desde
     * [origenUs], el inicio del bloque en la linea) o, sin iman, al
     * milisegundo: un pixel de la franja son varios ms y asi no salen
     * microsegundos al azar. Lo exacto se escribe en los campos.
     */
    fun pegar(us: Long, fotogramas: Boolean, origenUs: Long): Long {
        val v = us.coerceAtLeast(0)
        return if (fotogramas) TiempoCampo.alFotograma(v, origenUs) else (v + 500) / 1_000 * 1_000
    }

    /** El cabezal como tiempo para poner algo: al fotograma con el iman; sin el, tal cual. */
    fun pegarCabezal(us: Long, fotogramas: Boolean, origenUs: Long): Long =
        if (fotogramas) TiempoCampo.alFotograma(us, origenUs) else us.coerceAtLeast(0)

    /**
     * [EfectoVideo.conRango] sin lanzar nunca: si los bordes no valen (un
     * efecto roto de un archivo viejo) se queda como estaba.
     */
    fun conRango(e: EfectoVideo, inicioUs: Long, finUs: Long): EfectoVideo =
        if (inicioUs < 0 || finUs <= inicioUs || (inicioUs == e.inicioUs && finUs == e.finUs)) e else e.conRango(inicioUs, finUs)

    // ---- Los campos de tiempo del panel ----

    /** "Inicio": lleva el tramo entero a [us], con lo que dura y su reloj, sin salir del bloque. */
    fun conInicio(e: EfectoVideo, us: Long, duracionBloqueUs: Long): EfectoVideo =
        e.desplazado(us.coerceAtLeast(0) - e.inicioUs, duracionBloqueUs)

    /** "Recortar inicio": el principio va a [us] y el final se queda (reloj quieto). */
    fun conInicioRecortado(e: EfectoVideo, us: Long, minimoUs: Long): EfectoVideo =
        conRango(e, acotarBordeEfecto(us, 0, (e.finUs - minimoUs).coerceAtLeast(0)), e.finUs)

    /** "Fin": el final va a [us] y el principio se queda (reloj quieto; la base crece si hace falta). */
    fun conFin(e: EfectoVideo, us: Long, duracionBloqueUs: Long, minimoUs: Long): EfectoVideo =
        conRango(e, e.inicioUs, acotarBordeEfecto(us, minimoFinEfecto(e.inicioUs, minimoUs, duracionBloqueUs), duracionBloqueUs))

    /** "Duracion": el final queda a [us] del principio (como [conFin]). */
    fun conDuracion(e: EfectoVideo, us: Long, duracionBloqueUs: Long, minimoUs: Long): EfectoVideo =
        conFin(e, e.inicioUs + us.coerceAtLeast(0), duracionBloqueUs, minimoUs)

    /** "Todo el bloque": de 0 al final, recortando o devolviendo por los dos bordes. */
    fun todoElBloque(e: EfectoVideo, duracionBloqueUs: Long): EfectoVideo = conRango(e, 0, duracionBloqueUs)

    // ---- Arrastre en la franja ----

    /**
     * El efecto tras correr el dedo [deltaUs] desde que se agarro [base]:
     * siempre se calcula desde la base, asi un arrastre de ida y vuelta deja
     * el efecto como estaba.
     *  - MOVER: el tramo entero, con lo que dura y su reloj (el inicio pegado
     *    al iman y el tramo dentro del bloque).
     *  - INICIO/FIN: recorta ese borde conservando el reloj ([EfectoVideo.conRango]),
     *    dejando al menos [minimo] y sin salir del bloque.
     */
    fun arrastrar(
        base: EfectoVideo, agarre: Agarre, deltaUs: Long, duracionBloqueUs: Long,
        fotogramas: Boolean, origenUs: Long
    ): EfectoVideo {
        val dur = duracionBloqueUs
        if (deltaUs == 0L || dur <= 0 || base.finUs <= base.inicioUs) return base
        val minimo = minimo(base, fotogramas, dur)
        return when (agarre) {
            Agarre.MOVER -> conInicio(base, pegar(base.inicioUs + deltaUs, fotogramas, origenUs), dur)
            Agarre.INICIO -> conInicioRecortado(base, pegar(base.inicioUs + deltaUs, fotogramas, origenUs), minimo)
            Agarre.FIN -> conFin(base, pegar(base.finUs + deltaUs, fotogramas, origenUs), dur, minimo)
        }
    }

    // ---- Nombres y marcas ----

    /** El nombre de un efecto aplicado: el de su animacion si es una animacion convertida. */
    fun nombre(e: EfectoVideo): String =
        e.animacion?.nombre?.takeIf { e.tipo == TipoEfectoVideo.ANIMACION && it.isNotBlank() } ?: e.tipo.nombre

    /** Para las barras de la franja, que son estrechas. */
    fun nombreCorto(e: EfectoVideo): String = when (e.tipo) {
        TipoEfectoVideo.SACUDIDA_VERTICAL -> "Sacudida V"
        TipoEfectoVideo.ZOOM_RITMO -> "Zoom ritmo"
        TipoEfectoVideo.RGB -> "RGB"
        TipoEfectoVideo.CIRCULO_CERRAR -> "Cerrar circulo"
        TipoEfectoVideo.CIRCULO_ABRIR -> "Abrir circulo"
        TipoEfectoVideo.BORDES_ESPEJO -> "Espejo"
        TipoEfectoVideo.DESENFOQUE_HORIZONTAL -> "Desenfoque H"
        TipoEfectoVideo.DESENFOQUE_VERTICAL -> "Desenfoque V"
        TipoEfectoVideo.CERRAR_VERTICAL -> "Cerrar V"
        TipoEfectoVideo.CERRAR_HORIZONTAL -> "Cerrar H"
        TipoEfectoVideo.REVELAR_HORIZONTAL -> "Revelar"
        TipoEfectoVideo.DESENFOQUE_DIRECCIONAL -> "Rastro"
        TipoEfectoVideo.CIRCULO -> "Circulo"
        else -> nombre(e)
    }

    /** Cuantos efectos de cada tipo hay ya en el bloque (para marcarlos en el catalogo). */
    fun cuantosPorTipo(efectos: List<EfectoVideo>): Map<TipoEfectoVideo, Int> = efectos.groupingBy { it.tipo }.eachCount()

    /** Cuantas veces esta puesta la combinacion [c]: sus grupos se llaman "Nombre N". */
    fun vecesCombinacion(efectos: List<EfectoVideo>, c: CombinacionEfectos): Int {
        val prefijo = c.nombre + " "
        return efectos.map { it.grupo }.filter { it.startsWith(prefijo) && it.removePrefix(prefijo).toIntOrNull() != null }.toSet().size
    }

    /**
     * El efecto que sale elegido al abrir el panel: el que pidio otro panel
     * ([pedido], si esta en el bloque); si no, el ultimo de la lista que
     * actua bajo el cabezal (el de encima), si no el primero que empieza
     * despues, y si no el primero. Null si no hay efectos.
     */
    fun elegidoAlAbrir(efectos: List<EfectoVideo>, localUs: Long?, pedido: Long? = null): Long? {
        if (efectos.isEmpty()) return null
        if (pedido != null && efectos.any { it.id == pedido }) return pedido
        if (localUs != null) {
            efectos.lastOrNull { localUs >= it.inicioUs && localUs < it.finUs }?.let { return it.id }
            efectos.filter { it.inicioUs >= localUs }.minByOrNull { it.inicioUs }?.let { return it.id }
        }
        return efectos.first().id
    }

    /** El que queda elegido al quitar [id]: el anterior en la lista, o el siguiente; null si no queda ninguno. */
    fun vecinoAlQuitar(efectos: List<EfectoVideo>, id: Long): Long? {
        val i = efectos.indexOfFirst { it.id == id }
        if (i < 0) return efectos.firstOrNull()?.id
        return (efectos.getOrNull(i - 1) ?: efectos.getOrNull(i + 1))?.id
    }
}

/**
 * Un arrastre de un tramo en la franja: todo el gesto es un paso de deshacer
 * (entre [EstadoEditor.empezarGesto] y [EstadoEditor.terminarGesto]) y cada
 * paso se calcula desde el efecto tal como estaba al agarrarlo, no desde el
 * del paso anterior: ida y vuelta lo deja igual y no se acumulan redondeos.
 */
internal class ArrastreTramo(
    private val estado: EstadoEditor,
    val base: EfectoVideo,
    val agarre: TramosEfecto.Agarre,
    private val duracionBloqueUs: Long,
    private val origenUs: Long
) {
    private var abierto = false

    fun empezar() {
        if (abierto) return
        estado.empezarGesto()
        abierto = true
    }

    /** El dedo esta [deltaUs] por delante (o por detras) de donde agarro. */
    fun seguir(deltaUs: Long, fotogramas: Boolean) {
        val nuevo = TramosEfecto.arrastrar(base, agarre, deltaUs, duracionBloqueUs, fotogramas, origenUs)
        estado.cambiarClip(arrastre = true) { it.conEfecto(base.id) { nuevo } }
    }

    fun terminar() {
        if (!abierto) return
        estado.terminarGesto()
        abierto = false
    }
}

// ---- Acciones sobre los efectos del bloque, por id (los de conIdsEfectos) ----

/**
 * Cambia el efecto [id]. Si [f] no cambia nada devuelve el bloque tal cual:
 * no se gasta un paso de deshacer ni se escriben los ids de un proyecto viejo.
 */
internal fun Clip.conEfecto(id: Long, f: (EfectoVideo) -> EfectoVideo): Clip {
    val v = conIdsEfectos()
    val e = v.efectos.firstOrNull { it.id == id } ?: return this
    val n = f(e).copy(id = id)
    if (n == e) return this
    return v.copy(efectos = v.efectos.map { if (it.id == id) n else it })
}

/** Agrega un efecto de [tipo] con los ajustes de su tipo desde [inicioUs]; da el bloque y el id nuevo. */
internal fun Clip.conEfectoNuevo(tipo: TipoEfectoVideo, inicioUs: Long): Pair<Clip, Long> {
    val v = conIdsEfectos()
    val id = v.siguienteIdEfecto()
    return v.copy(efectos = v.efectos + CatalogoEfectos.nuevo(tipo, inicioUs, duracionUs, id)) to id
}

/** Agrega los efectos de una combinacion (independientes, en un grupo); da el bloque y el id del primero. */
internal fun Clip.conCombinacion(c: CombinacionEfectos, inicioUs: Long): Pair<Clip, Long?> {
    val v = conIdsEfectos()
    val id = v.siguienteIdEfecto()
    val n = CatalogoEfectos.aplicar(c, v, inicioUs)
    return if (n.efectos.any { it.id == id }) n to id else this to null
}

/**
 * Una copia suelta de [id] (sin grupo) justo despues del original, o donde
 * quepa dentro del bloque; va al final de la lista. Da el bloque y su id.
 */
internal fun Clip.conDuplicado(id: Long): Pair<Clip, Long>? {
    val v = conIdsEfectos()
    val e = v.efectos.firstOrNull { it.id == id } ?: return null
    val nuevo = v.siguienteIdEfecto()
    val copia = e.copy(id = nuevo, grupo = "").desplazado(e.duracionUs, duracionUs)
    return v.copy(efectos = v.efectos + copia) to nuevo
}

internal fun Clip.sinElEfecto(id: Long): Clip {
    val v = conIdsEfectos()
    return if (v.efectos.none { it.id == id }) this else v.sinEfecto(id)
}

/** Lo saca de su combinacion: sigue igual, pero ya no se mueve con el grupo. */
internal fun Clip.separadoDelGrupo(id: Long): Clip = conEfecto(id) { it.copy(grupo = "") }

/** Mueve el grupo de [id] entero (ver [moverGrupo]); igual si no tiene grupo o no cabe. */
internal fun Clip.conGrupoMovido(id: Long, deltaUs: Long): Clip {
    val v = conIdsEfectos()
    val grupo = v.efectos.firstOrNull { it.id == id }?.grupo ?: return this
    val n = v.moverGrupo(grupo, deltaUs)
    return if (n === v || n == v) this else n
}

/** Donde empieza el grupo de [id] (su efecto mas temprano), o el propio efecto si va suelto. */
internal fun Clip.inicioDelGrupo(id: Long): Long? {
    val v = conIdsEfectos()
    val e = v.efectos.firstOrNull { it.id == id } ?: return null
    if (e.grupo.isEmpty()) return e.inicioUs
    return v.efectos.filter { it.grupo == e.grupo }.minOf { it.inicioUs }
}
