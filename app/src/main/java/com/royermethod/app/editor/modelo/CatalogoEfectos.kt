package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.modelo.TipoEfectoVideo.*

/** Lo que se puede ajustar de un efecto. El panel ensena solo lo que usa su tipo. */
enum class Parametro {
    /** Valor al empezar y al acabar; su nombre y su unidad los da [CatalogoEfectos.rango]. */
    DESDE, HASTA,
    ANGULO, CURVA, CENTRO, GROSOR, SUAVIDAD, INVERTIR,
    /** Mitades: izquierda y derecha en vez de arriba y abajo. */
    CORTE_VERTICAL,
    INTENSIDAD,
    /** Entrada y salida suaves. */
    FUNDIDOS,
    RAPIDEZ,
    /** Puntos de progreso medidos a mano. */
    PROGRESO
}

/** Que son Desde y Hasta en un tipo: nombre, limites del control y unidad. */
data class RangoValor(val nombre: String, val minimo: Float, val maximo: Float, val unidad: String)

/** Varios efectos que se ponen de una vez y despues se editan cada uno por su lado. */
data class CombinacionEfectos(val nombre: String, val descripcion: String)

/**
 * Lo que sabe cada tipo de efecto de si mismo, para el panel: en que grupo va,
 * que se ajusta, con que valores empieza y cuanto dura recien puesto. Tambien
 * las combinaciones rapidas: varios efectos independientes de un toque.
 */
object CatalogoEfectos {

    /** Para el boton de agregar, por grupos. ANIMACION no aparece: sale de convertir una animacion. */
    val grupos: List<Pair<String, List<TipoEfectoVideo>>> = listOf(
        "Movimiento" to listOf(MOVER, ZOOM, GIRAR, LATIGAZO, MITADES, SACUDIDA, SACUDIDA_VERTICAL, ZOOM_RITMO, TWITCH),
        "Mascaras y cortes" to listOf(CIRCULO, BARRIDO, CIRCULO_CERRAR, CIRCULO_ABRIR, REVELAR_HORIZONTAL,
            CERRAR_VERTICAL, CERRAR_HORIZONTAL),
        "Desenfoque y bordes" to listOf(DESENFOQUE_DIRECCIONAL, DESENFOQUE, DESENFOQUE_HORIZONTAL, DESENFOQUE_VERTICAL, BORDES_ESPEJO),
        "Luz" to listOf(FLASH, FLASH_NEGRO, ESTROBO),
        "Imagen" to listOf(RGB, NEGATIVO, GLITCH)
    )

    /** Que controles ensena el panel para [tipo], en este orden. */
    fun parametros(tipo: TipoEfectoVideo): List<Parametro> = when (tipo) {
        MOVER -> listOf(Parametro.DESDE, Parametro.HASTA, Parametro.ANGULO, Parametro.CURVA,
            Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        ZOOM, GIRAR -> listOf(Parametro.DESDE, Parametro.HASTA, Parametro.CURVA,
            Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        LATIGAZO -> listOf(Parametro.DESDE, Parametro.ANGULO, Parametro.CURVA,
            Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        MITADES -> listOf(Parametro.DESDE, Parametro.HASTA, Parametro.ANGULO, Parametro.CURVA, Parametro.CORTE_VERTICAL,
            Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        // Las mascaras nuevas no se funden: la intensidad solo las enciende
        CIRCULO -> listOf(Parametro.DESDE, Parametro.HASTA, Parametro.CURVA, Parametro.CENTRO, Parametro.GROSOR,
            Parametro.SUAVIDAD, Parametro.INVERTIR, Parametro.RAPIDEZ, Parametro.PROGRESO)
        BARRIDO -> listOf(Parametro.DESDE, Parametro.HASTA, Parametro.ANGULO, Parametro.CURVA, Parametro.CENTRO,
            Parametro.SUAVIDAD, Parametro.INVERTIR, Parametro.RAPIDEZ, Parametro.PROGRESO)
        DESENFOQUE_DIRECCIONAL -> listOf(Parametro.ANGULO, Parametro.INTENSIDAD, Parametro.FUNDIDOS)
        CIRCULO_CERRAR, CIRCULO_ABRIR, REVELAR_HORIZONTAL -> listOf(Parametro.CENTRO,
            Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        CERRAR_VERTICAL, CERRAR_HORIZONTAL -> listOf(Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
        // Quietos: la rapidez no les cambia nada
        DESENFOQUE, DESENFOQUE_HORIZONTAL, DESENFOQUE_VERTICAL, BORDES_ESPEJO -> listOf(Parametro.INTENSIDAD, Parametro.FUNDIDOS)
        // Al ritmo: la rapidez cambia el periodo
        SACUDIDA, SACUDIDA_VERTICAL, ZOOM_RITMO, FLASH, FLASH_NEGRO, ESTROBO, RGB, NEGATIVO, GLITCH, TWITCH ->
            listOf(Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ)
        ANIMACION -> listOf(Parametro.INTENSIDAD, Parametro.FUNDIDOS, Parametro.RAPIDEZ, Parametro.PROGRESO)
    }

    /** Que miden Desde y Hasta en [tipo]; null si el tipo no los usa. */
    fun rango(tipo: TipoEfectoVideo): RangoValor? = when (tipo) {
        MOVER -> RangoValor("Recorrido", -2f, 2f, "lienzos")
        ZOOM -> RangoValor("Escala", 0.1f, 4f, "x")
        GIRAR -> RangoValor("Giro", -720f, 720f, "grados")
        LATIGAZO -> RangoValor("Recorrido", 0f, 2f, "lienzos")
        MITADES -> RangoValor("Separacion", -1f, 1.5f, "lienzos")
        CIRCULO -> RangoValor("Radio", 0f, 1.5f, "fraccion")
        BARRIDO -> RangoValor("Descubierto", 0f, 1f, "fraccion")
        else -> null
    }

    fun descripcion(tipo: TipoEfectoVideo): String = when (tipo) {
        SACUDIDA -> "Vibracion de camara con desplazamiento y giro."
        SACUDIDA_VERTICAL -> "Golpes de camara de arriba abajo."
        ZOOM_RITMO -> "Pulsos de acercamiento. Ajusta la rapidez para acompasarlos con la musica."
        FLASH -> "Destello blanco que se desvanece durante el tramo."
        FLASH_NEGRO -> "Destello oscuro que se desvanece durante el tramo."
        ESTROBO -> "Destellos blancos repetidos. La rapidez controla su frecuencia."
        RGB -> "Separa los canales de color en los bordes de la imagen."
        NEGATIVO -> "Invierte los colores con intensidad regulable."
        DESENFOQUE -> "Suaviza la imagen. Combinalo con entrada y salida suaves."
        DESENFOQUE_HORIZONTAL -> "Estela lateral para barridos rapidos. Conserva el desenfoque de movimiento."
        DESENFOQUE_VERTICAL -> "Estela vertical para entradas y salidas rapidas."
        BORDES_ESPEJO -> "Repite los bordes en espejo durante el movimiento para cubrir el lienzo."
        CIRCULO_CERRAR -> "Cierra la imagen en un circulo y descubre la capa que ya se mueve debajo."
        CIRCULO_ABRIR -> "Un circulo central se encoge para descubrir la imagen. Usa otra capa o fondo debajo."
        REVELAR_HORIZONTAL -> "Descubre la imagen de izquierda a derecha. Registra puntos de progreso para controlar la velocidad del borde."
        CERRAR_VERTICAL -> "Aplasta la imagen en una banda horizontal y revela la capa inferior."
        CERRAR_HORIZONTAL -> "Aplasta la imagen en una banda vertical y revela la capa inferior."
        GLITCH -> "Distorsion digital con cortes y separacion de color."
        TWITCH -> "Tirones cortos y rapidos para remarcar un golpe."
        MOVER -> "Lleva la imagen de un sitio a otro con la curva elegida."
        ZOOM -> "Acerca o aleja la imagen de un tamano a otro."
        GIRAR -> "Gira la imagen de un angulo a otro."
        LATIGAZO -> "Sale disparada hacia un lado y vuelve por el contrario."
        DESENFOQUE_DIRECCIONAL -> "Rastro de movimiento en cualquier direccion."
        CIRCULO -> "Circulo o anillo que se abre o se cierra, con su centro."
        BARRIDO -> "Descubre u oculta la imagen con un borde que avanza en cualquier direccion."
        MITADES -> "Las dos mitades de la imagen llegan desde lados opuestos y se juntan."
        ANIMACION -> "Una animacion convertida en efecto: muevela y recortala como cualquier otro."
    }

    /** Lo que dura recien puesto (si cabe en el bloque). */
    fun duracionSugeridaUs(tipo: TipoEfectoVideo): Long = when (tipo) {
        LATIGAZO -> 300_000
        else -> 500_000
    }

    /** Los ajustes con los que empieza cada tipo: el movimiento tipico, listo para cambiarlo. */
    fun ajustesDe(tipo: TipoEfectoVideo): AjustesEfecto = when (tipo) {
        // Entra desde la izquierda hasta su sitio
        MOVER -> AjustesEfecto(angulo = 180f, desde = 1f, hasta = 0f, curva = Curva.SALIDA)
        ZOOM -> AjustesEfecto(desde = 1.3f, hasta = 1f, curva = Curva.SALIDA)
        GIRAR -> AjustesEfecto(desde = -90f, hasta = 0f, curva = Curva.SALIDA)
        LATIGAZO -> AjustesEfecto(angulo = 180f, desde = 1f, curva = Curva.ENTRADA)
        MITADES -> AjustesEfecto(angulo = 90f, desde = 0.5f, hasta = 0f, curva = Curva.SALIDA)
        // Circulo y barrido de 0 a 1 con la curva suave: son los ajustes por defecto
        else -> AjustesEfecto()
    }

    /**
     * Un efecto recien puesto con los ajustes de su tipo, dentro del bloque.
     * Empieza en [inicioUs]; si no cabe entero hasta el final del bloque, se
     * adelanta lo justo para conservar su duracion.
     */
    fun nuevo(tipo: TipoEfectoVideo, inicioUs: Long, duracionBloqueUs: Long, id: Long): EfectoVideo {
        val bloque = duracionBloqueUs.coerceAtLeast(1)
        val d = duracionSugeridaUs(tipo).coerceIn(1, bloque)
        val ini = inicioUs.coerceIn(0, bloque - d)
        return EfectoVideo(tipo, ini, ini + d, id = id, ajustes = ajustesDe(tipo))
    }

    /** Una pieza de una combinacion: un efecto con sus fundidos en fraccion de lo que dura. */
    private class Pieza(
        val tipo: TipoEfectoVideo,
        val duracionUs: Long,
        val ajustes: AjustesEfecto,
        val intensidad: Float = 1f,
        val entrada: Double = 0.0,
        val salida: Double = 0.0
    )

    private val piezas: Map<String, List<Pieza>> = linkedMapOf(
        "Mitades que se juntan" to listOf(
            Pieza(MITADES, 500_000, ajustesDe(MITADES)),
            // El rastro va con las mitades y se apaga a la vez que frenan
            Pieza(DESENFOQUE_DIRECCIONAL, 500_000, AjustesEfecto(angulo = 90f), salida = 1.0)
        ),
        "Circulo que se abre" to listOf(
            Pieza(CIRCULO, 500_000, AjustesEfecto(desde = 0f, hasta = 1f, curva = Curva.SUAVE))
        ),
        "Anillo que se abre" to listOf(
            Pieza(CIRCULO, 600_000, AjustesEfecto(desde = 0f, hasta = 1.15f, grosor = 0.15f, curva = Curva.SALIDA))
        ),
        "Latigazo con desenfoque" to listOf(
            Pieza(LATIGAZO, 300_000, ajustesDe(LATIGAZO)),
            // Mas fuerte en el centro, que es cuando va mas deprisa
            Pieza(DESENFOQUE_DIRECCIONAL, 300_000, AjustesEfecto(angulo = 180f), intensidad = 1.5f, entrada = 0.5, salida = 0.5),
            Pieza(BORDES_ESPEJO, 300_000, AjustesEfecto())
        ),
        "Sacudida con desenfoque" to listOf(
            Pieza(SACUDIDA, 500_000, AjustesEfecto()),
            Pieza(DESENFOQUE, 500_000, AjustesEfecto(), intensidad = 0.6f, entrada = 1.0 / 3, salida = 1.0 / 3)
        )
    )

    val combinaciones: List<CombinacionEfectos> = listOf(
        CombinacionEfectos("Mitades que se juntan",
            "Las dos mitades llegan desde arriba y abajo con un rastro que se apaga al juntarse."),
        CombinacionEfectos("Circulo que se abre", "Un circulo desde el centro descubre la imagen."),
        CombinacionEfectos("Anillo que se abre", "Un anillo crece desde el centro y se pierde por los bordes."),
        CombinacionEfectos("Latigazo con desenfoque",
            "Sale disparada hacia un lado y vuelve por el otro, con rastro y bordes espejo."),
        CombinacionEfectos("Sacudida con desenfoque", "La sacudida de siempre con un desenfoque suave que entra y sale.")
    )

    /**
     * Agrega los efectos de [c] desde [inicioUs], con ids nuevos y un mismo
     * grupo "Nombre N". Son efectos normales: cada uno se mueve, se recorta y
     * se quita por separado, y el grupo se mueve entero con [moverGrupo].
     */
    fun aplicar(c: CombinacionEfectos, clip: Clip, inicioUs: Long): Clip {
        val lista = piezas[c.nombre] ?: return clip
        val dur = clip.duracionUs
        if (dur <= 0) return clip
        val base = clip.conIdsEfectos()
        var id = base.siguienteIdEfecto()
        val usados = base.efectos.map { it.grupo }.toSet()
        var n = 1
        while ("${c.nombre} $n" in usados) n++
        val grupo = "${c.nombre} $n"
        val largo = lista.maxOf { it.duracionUs }.coerceIn(1, dur)
        val ini = inicioUs.coerceIn(0, dur - largo)
        val nuevos = lista.map { p ->
            val d = p.duracionUs.coerceIn(1, dur)
            EfectoVideo(
                p.tipo, ini, ini + d, intensidad = p.intensidad,
                fundidoEntradaUs = (d * p.entrada).toLong(), fundidoSalidaUs = (d * p.salida).toLong(),
                id = id++, grupo = grupo, ajustes = p.ajustes
            )
        }
        return base.copy(efectos = base.efectos + nuevos)
    }
}

// ---- Operaciones de bloque sobre sus efectos, por id ----

/** El id para el siguiente efecto del bloque: uno mas que el mayor, desde 1. */
fun Clip.siguienteIdEfecto(): Long = (efectos.maxOfOrNull { it.id } ?: 0L).coerceAtLeast(0L) + 1

/**
 * Da id a los efectos que no tienen (los de proyectos viejos, con 0) y a los
 * que repiten el de otro anterior (un duplicado). No cambia nada mas.
 */
fun Clip.conIdsEfectos(): Clip {
    val vistos = HashSet<Long>()
    if (efectos.all { it.id != 0L && vistos.add(it.id) }) return this
    var siguiente = siguienteIdEfecto()
    vistos.clear()
    return copy(efectos = efectos.map { e ->
        if (e.id != 0L && vistos.add(e.id)) e else e.copy(id = siguiente++).also { vistos.add(it.id) }
    })
}

fun Clip.cambiarEfecto(id: Long, f: (EfectoVideo) -> EfectoVideo): Clip =
    copy(efectos = efectos.map { if (it.id == id) f(it) else it })

fun Clip.sinEfecto(id: Long): Clip = copy(efectos = efectos.filter { it.id != id })

/**
 * Mueve todos los efectos de [grupo] el mismo tiempo, conservando sus
 * relojes. Se acota para que ninguno salga del bloque: el grupo se mueve
 * entero o se queda donde esta, nunca se descoloca.
 */
fun Clip.moverGrupo(grupo: String, deltaUs: Long): Clip {
    if (grupo.isEmpty()) return this
    val delGrupo = efectos.filter { it.grupo == grupo }
    if (delGrupo.isEmpty()) return this
    val dur = duracionUs
    val atras = delGrupo.maxOf { -it.inicioUs }
    val adelante = delGrupo.minOf { dur - it.finUs }
    if (adelante < atras) return this
    val d = deltaUs.coerceIn(atras, adelante)
    if (d == 0L) return this
    return copy(efectos = efectos.map { if (it.grupo == grupo) it.desplazado(d, dur) else it })
}
