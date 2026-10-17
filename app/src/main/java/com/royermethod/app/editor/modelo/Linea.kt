package com.royermethod.app.editor.modelo

/**
 * Cuentas de la linea de tiempo.
 *
 * Todo lo que necesita saber donde empieza cada cosa sale de aqui: la vista,
 * el render y la exportacion. Si cada uno hiciera su propia cuenta, bastaria
 * un redondeo distinto para que la transicion que se ve en el editor no
 * cayera en el mismo fotograma que la exportada.
 */
object Linea {

    /** 30 fps fijos: es el objetivo de toda la app. */
    const val FPS = 30

    /** Instante exacto del fotograma [n], sin acumular error. */
    fun tiempoDe(n: Long): Long = n * 1_000_000L / FPS

    /** Primer fotograma cuyo instante es >= [us]. */
    fun fotogramaDe(us: Long): Long = (us * FPS + 999_999L) / 1_000_000L

    /**
     * Fotograma que se esta viendo en [us]: el ultimo cuyo instante ya llego.
     * Hecho a mano y no como us*30/1e6 porque [tiempoDe] redondea hacia
     * abajo, y el fotograma 1 empieza en 33333, no en 33333.33.
     */
    fun fotogramaEn(us: Long): Long = ((us + 1) * FPS - 1).floorDiv(1_000_000L)

    /**
     * Lo que dura de verdad la transicion entre [a] y [b].
     *
     * Una transicion solapa los dos bloques: el segundo empieza antes de que
     * acabe el primero. No puede comerse mas de la mitad de ninguno, o se
     * solaparia tambien con la transicion del otro lado y habria tres bloques
     * a la vez en pantalla.
     */
    fun solape(a: Clip, b: Clip): Long {
        if (a.transicion == Transicion.NINGUNA) return 0
        val maximo = minOf(a.duracionUs, b.duracionUs) / 2
        // Redondeado a fotogramas enteros: una transicion de 16.5 fotogramas
        // empezaria a mitad de uno y el primero saldria a medio camino.
        val us = a.duracionTransicionUs.coerceIn(0, maximo)
        return tiempoDe(fotogramaEn(us))
    }

    /** Donde empieza cada bloque de la pista principal. */
    fun inicios(principal: List<Clip>): LongArray {
        val r = LongArray(principal.size)
        var t = 0L
        for (i in principal.indices) {
            r[i] = t
            t += principal[i].duracionUs
            if (i + 1 < principal.size) t -= solape(principal[i], principal[i + 1])
        }
        return r
    }

    fun duracionPrincipal(principal: List<Clip>): Long {
        if (principal.isEmpty()) return 0
        val ini = inicios(principal)
        return ini.last() + principal.last().duracionUs
    }

    /**
     * Lo que dura el video exportado.
     *
     * Manda lo que se ve: la pista principal y las capas. El audio no alarga
     * el video; una cancion de tres minutos debajo de un edit de quince
     * segundos se corta con el.
     */
    fun duracion(p: Proyecto): Long {
        var fin = duracionPrincipal(p.principal)
        for (c in p.capas) fin = maxOf(fin, c.finUs)
        return fin
    }

    /** Cuantos fotogramas salen en total. */
    fun fotogramas(p: Proyecto): Long = fotogramaDe(duracion(p))

    /**
     * Que hay en la pista principal en [t].
     *
     * Dentro de una transicion devuelve los dos bloques y por donde va. Fuera,
     * solo uno y [segundo] a null.
     */
    fun principalEn(principal: List<Clip>, inicios: LongArray, t: Long): EnPrincipal? {
        if (principal.isEmpty()) return null
        // El ultimo cuyo inicio ya paso. Hay pocos bloques: buscar de uno en
        // uno es mas claro que una busqueda binaria y no cuesta nada.
        var i = -1
        for (n in principal.indices) if (inicios[n] <= t) i = n
        if (i < 0) return null
        val clip = principal[i]
        val local = t - inicios[i]
        if (local >= clip.duracionUs) return null
        // Si el anterior todavia no ha acabado, estamos en su transicion.
        if (i > 0) {
            val previo = principal[i - 1]
            val localPrevio = t - inicios[i - 1]
            if (localPrevio < previo.duracionUs) {
                val solape = solape(previo, clip)
                val p = if (solape > 0) (t - inicios[i]).toFloat() / solape else 1f
                return EnPrincipal(i - 1, previo, localPrevio, clip, local, p.coerceIn(0f, 1f))
            }
        }
        return EnPrincipal(i, clip, local, null, 0, 0f)
    }

    /**
     * Fotogramas que son un corte seco: el primero de un bloque al que se
     * llega sin transicion.
     *
     * En un corte el codificador no puede aprovechar nada del fotograma
     * anterior, asi que es el mejor sitio posible para un fotograma clave. Si
     * el fotograma clave periodico cayera justo despues de un corte, se
     * pagaria dos veces la misma imagen completa.
     */
    fun cortes(p: Proyecto): Set<Long> {
        val ini = inicios(p.principal)
        val r = HashSet<Long>()
        for (i in 1 until p.principal.size) {
            if (solape(p.principal[i - 1], p.principal[i]) == 0L) r.add(fotogramaDe(ini[i]))
        }
        return r
    }

    /**
     * Los archivos de todo lo que se ve en algun momento de [desde] a [hasta]
     * (incluidos), en la pista principal o en capas. Sirve para ir leyendolos
     * antes de que aparezcan.
     */
    fun origenesEntre(p: Proyecto, desde: Long, hasta: Long): Set<Origen> {
        val r = LinkedHashSet<Origen>()
        val ini = inicios(p.principal)
        for (i in p.principal.indices) {
            val c = p.principal[i]
            if (ini[i] <= hasta && ini[i] + c.duracionUs > desde) c.origen?.let(r::add)
        }
        for (c in p.capas) {
            if (c.inicioUs <= hasta && c.finUs > desde) c.clip.origen?.let(r::add)
        }
        return r
    }

    /** Capas visibles en [t], de abajo arriba. */
    fun capasEn(capas: List<Capa>, t: Long): List<Capa> =
        capas.filter { t >= it.inicioUs && t < it.finUs }.sortedBy { it.fila }

    /** La primera fila en la que cabe un bloque de [desde] a [hasta] sin pisar otro. */
    fun filaLibre(capas: List<Capa>, desde: Long, hasta: Long, ignorar: Long = -1): Int {
        var fila = 0
        while (true) {
            val ocupada = capas.any {
                it.id != ignorar && it.fila == fila && it.inicioUs < hasta && it.finUs > desde
            }
            if (!ocupada) return fila
            fila++
        }
    }

    /**
     * Tramos para repartir el bitrate.
     *
     * Cada tramo pide mas o menos bits segun lo que contiene: una foto quieta
     * se comprime casi sola, una transicion con desenfoque de movimiento es lo
     * mas caro que hay, y un clip de video cuesta lo que costaba en origen
     * comparado con los demas. Repartir el mismo bitrate a todo seria tirar
     * bits en lo facil y quedarse corto en lo dificil.
     *
     * Con [normalizar], la media ponderada por tiempo es 1: el peso total
     * sale el mismo que con bitrate fijo y solo cambia donde se gasta. Sin
     * normalizar, un proyecto de fotos quietas pesa menos que uno lleno de
     * transiciones, que es lo que se usa al exportar.
     */
    fun tramos(p: Proyecto, normalizar: Boolean = true): List<Tramo> {
        val total = duracion(p)
        if (total <= 0) return emptyList()
        val ini = inicios(p.principal)
        val cortesEn = ArrayList<Long>()
        cortesEn.add(0); cortesEn.add(total)
        for (i in p.principal.indices) {
            cortesEn.add(ini[i])
            cortesEn.add(ini[i] + p.principal[i].duracionUs)
        }
        for (c in p.capas) { cortesEn.add(c.inicioUs); cortesEn.add(c.finUs) }
        // Donde empieza y acaba lo que se mueve dentro de cada bloque: una foto
        // de cinco segundos con medio segundo de entrada solo necesita bits
        // en ese medio segundo.
        for (i in p.principal.indices) movimiento(p.principal[i]).forEach { cortesEn.add(ini[i] + it) }
        for (c in p.capas) movimiento(c.clip).forEach { cortesEn.add(c.inicioUs + it) }
        val puntos = cortesEn.filter { it in 0..total }.distinct().sorted()

        // Complejidad media de los videos, para comparar cada uno contra ella
        val complejidades = p.principal.mapNotNull { it.origen?.complejidad()?.takeIf { c -> c > 0f } }
        val media = if (complejidades.isNotEmpty()) complejidades.average().toFloat() else 0f

        val crudos = ArrayList<Tramo>()
        for (k in 0 until puntos.size - 1) {
            val a = puntos[k]
            val b = puntos[k + 1]
            if (b <= a) continue
            val medio = (a + b) / 2
            crudos.add(Tramo(a, b, peso(p, ini, medio, media)))
        }
        if (!normalizar) return crudos
        // Normalizar a media 1 ponderada por duracion
        val suma = crudos.sumOf { (it.hastaUs - it.desdeUs) * it.factor.toDouble() }
        val norma = if (suma > 0) total / suma else 1.0
        return crudos.map { it.copy(factor = (it.factor * norma).toFloat()) }
    }

    private fun peso(p: Proyecto, ini: LongArray, t: Long, media: Float): Float {
        val en = principalEn(p.principal, ini, t)
        var f = when {
            en == null -> 0.4f
            en.segundo != null -> 1.6f   // transicion: dos fuentes y movimiento
            en.primero.esVideo -> {
                val c = en.primero.origen?.complejidad() ?: 0f
                val base = if (c > 0f && media > 0f) (c / media).coerceIn(0.7f, 1.4f) else 1f
                // Un video que ademas se mueve o se amplia cambia mas entre fotogramas
                if (seMueve(en.primero, en.localPrimero)) base * 1.15f else base
            }
            // Imagen o texto: casi quieto, salvo mientras se mueve
            seMueve(en.primero, en.localPrimero) -> 0.8f
            else -> 0.45f
        }
        if (en != null && en.primero.velocidad < 0.9f && en.primero.fluido) f *= 1.15f
        // Un flash o un negativo cambia todos los pixeles de golpe: el
        // codificador no puede aprovechar el fotograma anterior
        if (en != null && (golpea(en.primero, en.localPrimero) ||
                en.segundo?.let { golpea(it, en.localSegundo) } == true)) f *= 1.25f
        // Lo que el usuario le pidio a ese bloque: en una transicion, la media de los dos
        if (en != null) f *= en.segundo?.let { (en.primero.calidad + it.calidad) / 2f } ?: en.primero.calidad
        // Lo que va encima tambien hay que pagarlo
        val encima = capasEn(p.capas, t)
        for (c in encima) f += c.clip.calidad * when {
            c.clip.esVideo -> 0.35f
            seMueve(c.clip, t - c.inicioUs) -> 0.25f
            else -> 0.1f
        }
        if (encima.any { golpea(it.clip, t - it.inicioUs) }) f *= 1.15f
        return f.coerceIn(0.2f, 3.3f)
    }

    /**
     * Instantes (dentro del bloque) donde empieza o deja de moverse: fin de la
     * entrada, principio de la salida (las de antes y las aplicadas), primer y
     * ultimo fotograma clave.
     */
    private fun movimiento(c: Clip): List<Long> {
        val r = ArrayList<Long>(4)
        if (c.entrada.isNotEmpty()) r.add(minOf(c.duracionEntradaUs, c.duracionUs))
        if (c.salida.isNotEmpty()) r.add((c.duracionUs - c.duracionSalidaUs).coerceAtLeast(0))
        for (a in c.animaciones) if (a.activa && a.piezas.isNotEmpty()) when (a.momento) {
            MomentoAnimacion.ENTRADA -> r.add(minOf(a.duracionUs, c.duracionUs))
            MomentoAnimacion.SALIDA -> r.add((c.duracionUs - a.duracionUs).coerceAtLeast(0))
            MomentoAnimacion.SIEMPRE -> Unit
        }
        if (c.claves.size >= 2) {
            r.add(c.claves.minOf { it.tiempoUs })
            r.add(c.claves.maxOf { it.tiempoUs })
        }
        for (pieza in c.piezas) if (pieza.claves.size >= 2) {
            r.add(pieza.claves.minOf { it.tiempoUs }.coerceAtLeast(0))
            r.add(pieza.claves.maxOf { it.tiempoUs }.coerceAtMost(c.duracionUs))
        }
        for (e in c.efectos) if (e.activo && e.intensidad > 0f) {
            r.add(e.inicioUs)
            r.add(e.finUs)
        }
        return r
    }

    private val PIEZAS_FUERTES = setOf(
        Animacion.FLASH, Animacion.FLASH_NEGRO, Animacion.ESTROBO, Animacion.NEGATIVO,
        Animacion.RGB, Animacion.CAMARA, Animacion.TWITCH
    )
    private val CONTINUAS_FUERTES = setOf(
        AnimacionContinua.SHAKE_FLASH, AnimacionContinua.ESTROBO, AnimacionContinua.RGB, AnimacionContinua.TWITCH
    )

    private val EFECTOS_FUERTES = setOf(
        TipoEfectoVideo.FLASH, TipoEfectoVideo.FLASH_NEGRO, TipoEfectoVideo.ESTROBO,
        TipoEfectoVideo.NEGATIVO, TipoEfectoVideo.RGB, TipoEfectoVideo.GLITCH, TipoEfectoVideo.TWITCH,
        TipoEfectoVideo.LATIGAZO
    )

    /** ¿Lo que hace una animacion (aplicada o dentro de un efecto) es de los golpes fuertes? */
    private fun fuerte(momento: MomentoAnimacion, piezas: List<Movimiento>, continuas: List<Continuo>): Boolean =
        if (momento == MomentoAnimacion.SIEMPRE) continuas.any { it.tipo in CONTINUAS_FUERTES }
        else piezas.any { it.tipo in PIEZAS_FUERTES }

    private fun efectoFuerte(e: EfectoVideo): Boolean =
        e.tipo in EFECTOS_FUERTES ||
            (e.tipo == TipoEfectoVideo.ANIMACION && e.animacion?.let { fuerte(it.momento, it.piezas, it.continuas) } == true)

    /** ¿En [local] hay flashes, RGB o sacudidas fuertes? */
    private fun golpea(c: Clip, local: Long): Boolean {
        if (c.efectos.any { efectoFuerte(it) && it.activoEn(local) }) return true
        if (c.continua.any { it.tipo in CONTINUAS_FUERTES }) return true
        if (local < c.duracionEntradaUs && c.entrada.any { it.tipo in PIEZAS_FUERTES }) return true
        if (local >= c.duracionUs - c.duracionSalidaUs && c.salida.any { it.tipo in PIEZAS_FUERTES }) return true
        for (a in c.animaciones) if (a.activa && fuerte(a.momento, a.piezas, a.continuas)) {
            val dentro = when (a.momento) {
                MomentoAnimacion.ENTRADA -> local < a.duracionUs
                MomentoAnimacion.SALIDA -> local >= c.duracionUs - a.duracionUs
                MomentoAnimacion.SIEMPRE -> true
            }
            if (dentro) return true
        }
        return false
    }

    /** ¿Algo de [c] cambia de un fotograma al siguiente en [local]? */
    private fun seMueve(c: Clip, local: Long): Boolean {
        if (c.efectos.any { it.activoEn(local) }) return true
        if (c.piezas.any { it.claves.size >= 2 && local >= it.claves.minOf { k -> k.tiempoUs } && local < it.claves.maxOf { k -> k.tiempoUs } }) return true
        if (c.continua.isNotEmpty()) return true
        if (c.entrada.isNotEmpty() && local < c.duracionEntradaUs) return true
        if (c.salida.isNotEmpty() && local >= c.duracionUs - c.duracionSalidaUs) return true
        for (a in c.animaciones) if (a.activa) when (a.momento) {
            MomentoAnimacion.ENTRADA -> if (a.piezas.isNotEmpty() && local < a.duracionUs) return true
            MomentoAnimacion.SALIDA -> if (a.piezas.isNotEmpty() && local >= c.duracionUs - a.duracionUs) return true
            MomentoAnimacion.SIEMPRE -> if (a.continuas.isNotEmpty()) return true
        }
        if (c.claves.size >= 2 && local >= c.claves.minOf { it.tiempoUs } && local < c.claves.maxOf { it.tiempoUs }) return true
        return false
    }
}

/** Lo que hay en la pista principal en un instante. */
data class EnPrincipal(
    val indice: Int,
    val primero: Clip,
    /** Tiempo dentro de [primero]. */
    val localPrimero: Long,
    /** El que entra, si estamos en una transicion. */
    val segundo: Clip?,
    val localSegundo: Long,
    /** Avance de la transicion, 0 a 1. */
    val progreso: Float
)

/** Un trozo del video con su parte del bitrate. */
data class Tramo(val desdeUs: Long, val hastaUs: Long, val factor: Float)
