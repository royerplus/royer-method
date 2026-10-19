package com.royermethod.app.editor.modelo

/**
 * Un bloque: la unidad de todo el editor.
 *
 * Video, imagen o texto, en la pista principal o encima, todo es un Clip. Asi
 * cada bloque se edita con las mismas herramientas (encuadre, color,
 * animaciones, fotogramas clave) y lo que se aprende en uno sirve en todos.
 *
 * Exactamente uno de [origen] y [texto] viene relleno.
 */
data class Clip(
    val id: Long,
    val origen: Origen? = null,
    val texto: Texto? = null,
    /**
     * Trozo del archivo que se usa. Recortar no toca el archivo: solo mueve
     * estos dos limites, y el original sigue entero por si se quiere volver.
     * En imagenes y textos [desdeUs] es 0 y [hastaUs] es lo que dura.
     */
    val desdeUs: Long = 0,
    val hastaUs: Long = origen?.duracionUs?.takeIf { it > 0 } ?: IMAGEN_POR_DEFECTO_US,
    /** 0.1 a 10. Solo en video. Para cambiarla, [conVelocidad]. */
    val velocidad: Float = 1f,
    /** 0 a 2: se puede subir por encima del original. */
    val volumen: Float = 1f,
    /**
     * Al cambiar la velocidad, que la voz y la musica suenen igual de agudas.
     * Sin esto, a doble velocidad todo sale como una ardilla.
     */
    val mantenerTono: Boolean = true,
    val fundidoEntradaUs: Long = 0,
    val fundidoSalidaUs: Long = 0,
    val color: AjustesColor = AjustesColor(),
    val filtro: Filtro = Filtro.NINGUNO,
    val intensidadFiltro: Float = 1f,
    val encuadre: Encuadre = Encuadre(),
    /** Fotogramas clave del encuadre, en tiempo del clip (0 = su primer fotograma). */
    val claves: List<Clave> = emptyList(),
    /**
     * Animacion de entrada. Una lista: varias piezas se combinan en una sola
     * animacion (acercar + girar + desenfocar, por ejemplo), cada una con su
     * intensidad, su curva y su tramo dentro de la duracion.
     */
    val entrada: List<Movimiento> = emptyList(),
    /** La de salida, igual que la de entrada pero al reves. */
    val salida: List<Movimiento> = emptyList(),
    /** Desde una decima de segundo: las animaciones de los edits son cortas. */
    val duracionEntradaUs: Long = 500_000,
    val duracionSalidaUs: Long = 500_000,
    /** Lo que se mueve durante todo el bloque. Tambien se combinan. */
    val continua: List<Continuo> = emptyList(),
    /** Efectos de video colocados libremente dentro del bloque. */
    val efectos: List<EfectoVideo> = emptyList(),
    /** Partes de la misma fuente con regiones y movimientos independientes. */
    val piezas: List<PiezaVideo> = emptyList(),
    /**
     * Animaciones que se suman, cada una con su nombre y su duracion. Se
     * evaluan despues de [entrada], [salida] y [continua], que quedan para los
     * proyectos de antes y pasan a esta lista al editarlas.
     */
    val animaciones: List<AnimacionAplicada> = emptyList(),
    /** Las claves de antes de simplificar la trayectoria, para poder volver. */
    val clavesOriginales: List<Clave>? = null,
    /** Paso al bloque siguiente de la pista principal. En capas no se usa. */
    val transicion: Transicion = Transicion.NINGUNA,
    val duracionTransicionUs: Long = 500_000,
    val ajusteTransicion: AjusteTransicion = AjusteTransicion(),
    /**
     * Generar los fotogramas que faltan con el modelo de interpolacion.
     *
     * Hace dos cosas a la vez: convierte cualquier video a 30 fps exactos sin
     * repetir fotogramas (un 24 fps pasado a 30 repite uno de cada cuatro y se
     * nota el tiron), y da camara lenta de verdad al bajar la velocidad.
     */
    val fluido: Boolean = false,
    /**
     * Cuantos bits se lleva este bloque al exportar, sobre lo que le tocaria:
     * 0.5 la mitad (pesa menos, para un fondo o algo de paso), 1.5 la mitad
     * mas (para el plano que tiene que verse perfecto). Se suma a lo que ya
     * decide el reparto por tramos, no lo sustituye.
     */
    val calidad: Float = 1f
) {
    /** Se calcula una vez por version del clip; permite fusionar partes reunidas. */
    internal val piezasCubrenFuente: Boolean by lazy {
        if (piezas.isEmpty()) false else {
            val regiones = piezas.map { it.region.acotada() }
            val area = regiones.sumOf { it.ancho.toDouble() * it.alto }
            kotlin.math.abs(area - 1.0) < 1e-6 && regiones.indices.none { a ->
                (a + 1 until regiones.size).any { b -> regiones[a].interseccion(regiones[b]) != null }
            }
        }
    }

    init {
        require((origen == null) != (texto == null)) { "un clip es archivo o texto" }
    }

    val esTexto: Boolean get() = texto != null
    val esVideo: Boolean get() = origen?.esVideo == true
    val esImagen: Boolean get() = origen?.esImagen == true

    /** Lo que ocupa en la linea de tiempo. */
    val duracionUs: Long
        get() = if (esVideo) ((hastaUs - desdeUs) / velocidad.toDouble()).toLong()
        else hastaUs - desdeUs

    /**
     * Instante del archivo que toca mostrar a [localUs] del inicio del clip.
     *
     * Nunca devuelve el final exacto: el ultimo fotograma de un video empieza
     * un poco antes de su duracion, y pedir justo la duracion devolveria
     * "nada" en vez de ese ultimo fotograma.
     */
    fun tiempoArchivo(localUs: Long): Long {
        if (!esVideo) return 0
        val t = desdeUs + (localUs.coerceAtLeast(0) * velocidad.toDouble()).toLong()
        return t.coerceIn(desdeUs, (hastaUs - 1).coerceAtLeast(desdeUs))
    }

    /**
     * El encuadre a [local] del inicio con los fotogramas clave aplicados.
     * Lo que no se anima (ajuste, recorte, espejo) sale siempre del clip:
     * cambiarlo no obliga a rehacer cada fotograma clave.
     */
    fun encuadreEn(local: Long): Encuadre {
        if (claves.isEmpty()) return encuadre
        val ordenadas = claves.sortedBy { it.tiempoUs }
        val e = when {
            local <= ordenadas.first().tiempoUs -> ordenadas.first().encuadre
            local >= ordenadas.last().tiempoUs -> ordenadas.last().encuadre
            else -> {
                val i = ordenadas.indexOfLast { it.tiempoUs <= local }
                val a = ordenadas[i]
                val b = ordenadas[i + 1]
                val f = (local - a.tiempoUs).toFloat() / (b.tiempoUs - a.tiempoUs)
                a.encuadre.hacia(b.encuadre, a.avance(f))
            }
        }
        return e.copy(ajuste = encuadre.ajuste, recorte = encuadre.recorte, espejo = encuadre.espejo)
    }

    /**
     * El mismo bloque a otra velocidad. Los fotogramas clave van en tiempo del
     * bloque, asi que se estiran o encogen con el: a doble velocidad, la clave
     * que estaba en el segundo 2 pasa al 1 y cae sobre la misma imagen. Las
     * animaciones no: siguen durando lo mismo, solo que nunca mas que el bloque.
     */
    fun conVelocidad(v: Float): Clip {
        if (!esVideo || v == velocidad) return copy(velocidad = v)
        val f = velocidad.toDouble() / v
        val nuevo = copy(
            velocidad = v,
            claves = claves.map { it.copy(tiempoUs = (it.tiempoUs * f).toLong()) },
            clavesOriginales = clavesOriginales?.map { it.copy(tiempoUs = (it.tiempoUs * f).toLong()) },
            efectos = efectos.map { it.tiempoEscalado(f) },
            piezas = piezas.map { it.tiempoEscalado(f) }
        )
        val dur = nuevo.duracionUs
        return nuevo.copy(
            duracionEntradaUs = duracionEntradaUs.coerceAtMost(dur),
            duracionSalidaUs = duracionSalidaUs.coerceAtMost(dur),
            animaciones = animaciones.map { if (it.duracionUs > dur) it.copy(duracionUs = dur) else it }
        )
    }

    /** Ajustes de color con el filtro ya sumado. Es lo que dibuja el motor. */
    fun colorFinal(): AjustesColor = color.mas(filtro.ajustes, intensidadFiltro)

    fun nombre(): String = texto?.contenido?.lineSequence()?.firstOrNull()?.take(24)
        ?: origen?.nombre ?: ""

    companion object {
        /** Lo que dura una imagen o un texto recien puesto. */
        const val IMAGEN_POR_DEFECTO_US = 3_000_000L

        /** Lo minimo que puede quedar un bloque al recortar o dividir. */
        const val MINIMO_US = 33_333L
    }
}

/**
 * Donde y como se coloca el bloque sobre el lienzo.
 *
 * Las posiciones van en fracciones del lienzo y no en pixeles: el mismo
 * proyecto se exporta a 576 o a 1080 y tiene que verse igual.
 */
data class Encuadre(
    val ajuste: Ajuste = Ajuste.CUBRIR,
    /** 1 = el tamano que da [ajuste]. */
    val escala: Float = 1f,
    /** Desplazamiento del centro, en anchos y altos de lienzo. */
    val x: Float = 0f,
    val y: Float = 0f,
    /** Grados, en el sentido de las agujas del reloj. */
    val rotacion: Float = 0f,
    val opacidad: Float = 1f,
    val espejo: Boolean = false,
    val recorte: Recorte = Recorte()
) {
    /** Lo animable, mezclado con [otro] en [t] de 0 a 1. */
    fun hacia(otro: Encuadre, t: Float): Encuadre = copy(
        escala = escala + (otro.escala - escala) * t,
        x = x + (otro.x - x) * t,
        y = y + (otro.y - y) * t,
        rotacion = rotacion + (otro.rotacion - rotacion) * t,
        opacidad = opacidad + (otro.opacidad - opacidad) * t
    )
}

enum class Ajuste(val nombre: String) {
    /** Llena el lienzo y recorta lo que sobra. */
    CUBRIR("Llenar"),
    /** Se ve entero; lo que falta lo pone el fondo. */
    ENCAJAR("Encajar")
}

/** Cuanto quitar de cada lado, en fraccion del clip. */
data class Recorte(
    val izquierda: Float = 0f,
    val arriba: Float = 0f,
    val derecha: Float = 0f,
    val abajo: Float = 0f
) {
    val vacio: Boolean get() = izquierda == 0f && arriba == 0f && derecha == 0f && abajo == 0f
    val ancho: Float get() = (1f - izquierda - derecha).coerceAtLeast(0.05f)
    val alto: Float get() = (1f - arriba - abajo).coerceAtLeast(0.05f)
}

/**
 * Un fotograma clave: como esta el encuadre en ese instante del clip.
 *
 * [curva] y [bezier] dicen como se llega desde aqui a la clave siguiente. Si
 * hay [bezier], manda ella: es la curva dibujada a mano en el editor.
 */
data class Clave(
    val tiempoUs: Long,
    val encuadre: Encuadre,
    val curva: Curva = Curva.SUAVE,
    val bezier: Bezier? = null
) {
    fun avance(x: Float): Float = bezier?.en(x) ?: Escena.curva(curva, x)
}

/**
 * Curva de movimiento libre, como las de los editores de escritorio: va de
 * (0,0) a (1,1) y los dos puntos de control dan la forma. La y de los puntos
 * puede salirse de 0..1 para que el movimiento se pase y vuelva.
 */
data class Bezier(val x1: Float, val y1: Float, val x2: Float, val y2: Float) {

    /** Avance (y) para la fraccion de tiempo [x]. */
    fun en(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        // La x de una bezier con x1, x2 en 0..1 siempre crece: se busca por biseccion
        var lo = 0f
        var hi = 1f
        var t = x
        repeat(24) {
            if (coordenada(t, x1.coerceIn(0f, 1f), x2.coerceIn(0f, 1f)) < x) lo = t else hi = t
            t = (lo + hi) / 2f
        }
        return coordenada(t, y1, y2)
    }

    private fun coordenada(t: Float, a: Float, b: Float): Float {
        val u = 1f - t
        return 3f * u * u * t * a + 3f * u * t * t * b + t * t * t
    }

    companion object {
        /** Punto de partida al pasar a curva libre: la suave de siempre. */
        val SUAVE = Bezier(0.42f, 0f, 0.58f, 1f)
    }
}

/** Como se reparte el movimiento en el tiempo. */
enum class Curva(val nombre: String) {
    LINEAL("Lineal"),
    SUAVE("Suave"),
    ENTRADA("Acelera"),
    SALIDA("Frena"),
    /** Se pasa un poco y vuelve. */
    REBOTE("Rebote"),
    /** Oscila un par de veces antes de quedarse. */
    ELASTICO("Elastico"),
    /** Sale disparada y frena en seco: la de los latigazos. */
    RAPIDA("Rapida")
}

/** Piezas de animacion de entrada y salida. Se combinan entre ellas. */
enum class Animacion(val nombre: String) {
    APARECER("Aparecer"),
    ZOOM_ENTRA("Acercar"),
    ZOOM_SALE("Alejar"),
    IZQUIERDA("Izquierda"),
    DERECHA("Derecha"),
    ARRIBA("Arriba"),
    ABAJO("Abajo"),
    GIRO("Girar"),
    REBOTE("Rebote"),
    /** Se descubre de izquierda a derecha, como escrito. Pensado para textos. */
    REVELAR("Revelar"),
    DESENFOCAR("Enfocar"),
    SACUDIR("Sacudir"),
    PARPADEO("Parpadeo"),
    /** Sacudida de arriba a abajo. */
    SACUDIR_VERTICAL("Sacudir vertical"),
    /** Se balancea de lado a lado y se va calmando, como un pendulo. */
    PENDULO("Pendulo"),
    /** Late de tamano rapido mientras se calma. */
    ZOOM_VIBRA("Zoom vibrante"),
    /** Saltos de posicion y parpadeos, como una imagen que falla. */
    GLITCH("Glitch"),
    /** Se pone blanco y vuelve. Con una sacudida es el shake con flash. */
    FLASH("Flash"),
    FLASH_NEGRO("Flash negro"),
    /** Varios flashes seguidos que se van apagando. */
    ESTROBO("Estrobo"),
    /** Rojo y azul se separan a los lados y vuelven a juntarse. */
    RGB("Separar RGB"),
    /** Colores invertidos un instante, a golpes. */
    NEGATIVO("Negativo"),
    /** Sacudida en todas direcciones con giro y algo de zoom, como una camara golpeada. */
    CAMARA("Sacudida de camara"),
    /** Saltos secos de tamano y sitio con los colores separados. */
    TWITCH("Twitch")
}

/**
 * Una pieza de una animacion.
 *
 * @param intensidad cuanto se mueve: 1 lo normal, 0.2 apenas, 2 el doble
 * @param desde,hasta en que parte de la duracion actua (0 a 1). Sirve para
 *   encadenar: primero entra deslizando y al final da un giro.
 */
data class Movimiento(
    val tipo: Animacion,
    val intensidad: Float = 1f,
    val curva: Curva = Curva.SALIDA,
    val desde: Float = 0f,
    val hasta: Float = 1f
)

/** Cuando actua una animacion aplicada. */
enum class MomentoAnimacion(val nombre: String) {
    ENTRADA("Entrada"),
    SALIDA("Salida"),
    /** Durante todo el bloque: las continuas. */
    SIEMPRE("Siempre")
}

/**
 * Una animacion puesta en un bloque. Se pueden sumar varias: cada una conserva
 * su nombre del catalogo, sus piezas y su propia duracion.
 *
 * @param piezas lo que hace en ENTRADA y SALIDA
 * @param continuas lo que hace en SIEMPRE
 * @param duracionUs lo que dura desde el borde del bloque (ENTRADA y SALIDA)
 */
data class AnimacionAplicada(
    val id: Long,
    val nombre: String,
    val momento: MomentoAnimacion,
    val piezas: List<Movimiento> = emptyList(),
    val continuas: List<Continuo> = emptyList(),
    val duracionUs: Long = 500_000,
    val activa: Boolean = true
)

/** Lo que lleva un efecto de tipo ANIMACION: la animacion tal cual estaba. */
data class AnimacionEnEfecto(
    val nombre: String,
    val momento: MomentoAnimacion,
    val piezas: List<Movimiento> = emptyList(),
    val continuas: List<Continuo> = emptyList()
)

/** Lo que se mueve durante todo el bloque, no solo al entrar o salir. */
enum class AnimacionContinua(val nombre: String) {
    /** Acercamiento lento, el efecto Ken Burns de las fotos. */
    ACERCAMIENTO("Acercamiento"),
    ALEJAMIENTO("Alejamiento"),
    PULSO("Pulso"),
    TEMBLOR("Temblor"),
    BALANCEO("Balanceo"),
    FLOTAR("Flotar"),
    GIRAR("Girar sin parar"),
    /** Golpes de glitch cada poco, y entre medias quieto. */
    GLITCH("Glitch"),
    /** Temblor fino y rapido, como de bajo fuerte. */
    VIBRAR("Vibrar"),
    /** Se agranda y se encoge muy despacio. */
    RESPIRAR("Respirar"),
    /** Se desplaza de lado a lado a lo largo del bloque. */
    PANEO("Paneo"),
    /**
     * En cada golpe (medio segundo; con Rapidez se ajusta al tema) una
     * sacudida y un flash que se apagan antes del siguiente.
     */
    SHAKE_FLASH("Shake con flash"),
    /** Un zoom seco en cada golpe que vuelve enseguida. */
    ZOOM_RITMO("Zoom al ritmo"),
    /** Un flash corto en cada golpe. */
    ESTROBO("Estrobo"),
    /** Los colores separados, temblando. */
    RGB("RGB"),
    /** Se mueve suave y sin patron, como grabado a mano. */
    CAMARA_EN_MANO("Camara en mano"),
    /** Dos latidos seguidos y una pausa, como un corazon. */
    LATIDO("Latido"),
    /** Tirones de tamano y sitio con RGB, de vez en cuando. */
    TWITCH("Twitch")
}

data class Continuo(
    val tipo: AnimacionContinua,
    val intensidad: Float = 1f,
    /** 1 lo normal; 2 el doble de rapido. */
    val velocidad: Float = 1f
)

enum class Transicion(val nombre: String) {
    NINGUNA("Corte"),
    FUNDIDO("Fundido"),
    NEGRO("A negro"),
    DESTELLO("Destello"),
    /** Barrido con una linea brillante en el borde. */
    LINEA("Linea de luz"),
    BARRIDO("Barrido"),
    EMPUJAR("Empujar"),
    DESLIZAR("Deslizar"),
    ZOOM("Zoom"),
    DESENFOQUE("Desenfoque"),
    GIRO("Giro"),
    /** Se abre un circulo desde el centro. */
    CIRCULO("Circulo"),
    ZOOM_GIRO("Zoom y giro"),
    /** Zoom de golpe con destello en el cambio. */
    IMPACTO("Impacto"),
    /** La escena saliente se aplasta para descubrir la que esta debajo. */
    APLASTAR("Aplastar")
}

/** Lo que se puede cambiar de una transicion ademas de su duracion. */
data class AjusteTransicion(
    val curva: Curva = Curva.SUAVE,
    /**
     * Para empujar, deslizar y los barridos: hacia donde se va lo que sale,
     * en grados y a cualquier angulo. 0 derecha, 90 abajo, 180 izquierda,
     * 270 arriba.
     */
    val angulo: Float = 180f,
    /** Color de la linea o del destello. */
    val color: Int = 0xFFFFFFFF.toInt(),
    /** Cuanto zoom, giro o desenfoque: 1 lo normal. */
    val intensidad: Float = 1f
) {
    /** Hacia donde va, como vector de largo 1 (y hacia abajo). */
    val dx: Float get() = kotlin.math.cos(Math.toRadians(angulo.toDouble())).toFloat()
    val dy: Float get() = kotlin.math.sin(Math.toRadians(angulo.toDouble())).toFloat()

    companion object {
        /**
         * Los barridos quedan mejor un poco inclinados: en recto parecen un
         * fallo de imagen. Es el angulo con el que empiezan.
         */
        const val ANGULO_BARRIDO = 166f
    }
}

/**
 * Correccion de color. Todo en -1..1 con 0 neutro, salvo nitidez, vineta,
 * desvanecer y grano, que van de 0 a 1.
 *
 * Los campos nuevos van siempre al final: el proyecto los guarda por
 * posicion (Persistencia.color) y uno en medio correria los de despues.
 */
data class AjustesColor(
    val brillo: Float = 0f,
    val contraste: Float = 0f,
    val saturacion: Float = 0f,
    val exposicion: Float = 0f,
    val calidez: Float = 0f,
    val tinte: Float = 0f,
    val sombras: Float = 0f,
    val luces: Float = 0f,
    val nitidez: Float = 0f,
    val vineta: Float = 0f,
    /** Contraste local de los medios tonos: positivo marca volumen, negativo suaviza. */
    val claridad: Float = 0f,
    /** Saturacion que sube sobre todo los colores apagados. */
    val vivacidad: Float = 0f,
    /** Negros levantados, aspecto mate. */
    val desvanecer: Float = 0f,
    /** Grano de pelicula, distinto en cada fotograma. */
    val grano: Float = 0f
) {
    val neutro: Boolean get() = this == NEUTRO

    fun mas(o: AjustesColor, f: Float): AjustesColor = AjustesColor(
        brillo = brillo + o.brillo * f,
        contraste = contraste + o.contraste * f,
        saturacion = saturacion + o.saturacion * f,
        exposicion = exposicion + o.exposicion * f,
        calidez = calidez + o.calidez * f,
        tinte = tinte + o.tinte * f,
        sombras = sombras + o.sombras * f,
        luces = luces + o.luces * f,
        nitidez = (nitidez + o.nitidez * f).coerceIn(0f, 1f),
        vineta = (vineta + o.vineta * f).coerceIn(0f, 1f),
        claridad = claridad + o.claridad * f,
        vivacidad = vivacidad + o.vivacidad * f,
        desvanecer = (desvanecer + o.desvanecer * f).coerceIn(0f, 1f),
        grano = (grano + o.grano * f).coerceIn(0f, 1f)
    )

    companion object {
        val NEUTRO = AjustesColor()
    }
}

/** Combinaciones de ajustes con nombre. Se suman a lo que el usuario toque. */
enum class Filtro(val nombre: String, val ajustes: AjustesColor) {
    NINGUNO("Original", AjustesColor()),
    VIVO("Vivo", AjustesColor(contraste = 0.15f, saturacion = 0.35f, nitidez = 0.15f)),
    ANIME("Anime", AjustesColor(contraste = 0.12f, saturacion = 0.25f, luces = -0.1f, sombras = 0.1f, nitidez = 0.3f)),
    FRIO("Frio", AjustesColor(calidez = -0.35f, tinte = -0.05f, saturacion = 0.05f)),
    CALIDO("Calido", AjustesColor(calidez = 0.35f, saturacion = 0.1f, brillo = 0.03f)),
    CINE("Cine", AjustesColor(contraste = 0.2f, saturacion = -0.15f, calidez = 0.1f, sombras = -0.1f, vineta = 0.35f)),
    NOCHE("Noche", AjustesColor(exposicion = -0.25f, calidez = -0.4f, contraste = 0.1f, vineta = 0.3f)),
    DESVAIDO("Desvaido", AjustesColor(contraste = -0.3f, brillo = 0.06f, saturacion = -0.25f, sombras = 0.25f)),
    BYN("Blanco y negro", AjustesColor(saturacion = -1f, contraste = 0.25f)),
    DRAMA("Drama", AjustesColor(contraste = 0.4f, saturacion = -0.1f, luces = -0.25f, sombras = -0.15f, vineta = 0.45f, nitidez = 0.2f))
}

/** Un texto puesto encima. */
data class Texto(
    val contenido: String,
    /** Altura de la letra en fraccion del alto del lienzo. */
    val tamano: Float = 0.055f,
    /** ARGB. */
    val color: Int = 0xFFFFFFFF.toInt(),
    val fuente: Fuente = Fuente.SANS,
    val negrita: Boolean = true,
    /** 0 sin contorno. Se mide en fraccion del tamano de letra. */
    val contorno: Float = 0f,
    val colorContorno: Int = 0xFF000000.toInt(),
    val sombra: Boolean = true,
    /** 0 sin caja de fondo. */
    val fondo: Int = 0,
    val alineacion: Alineacion = Alineacion.CENTRO,
    val espaciado: Float = 0f
)

enum class Fuente(val nombre: String) {
    SANS("Clasica"),
    CONDENSADA("Estrecha"),
    SERIF("Serif"),
    MONO("Maquina"),
    REDONDA("Casual")
}

enum class Alineacion { IZQUIERDA, CENTRO, DERECHA }

/** Como se mezcla una capa con lo que tiene debajo. */
enum class Mezcla(val nombre: String) {
    NORMAL("Normal"),
    PANTALLA("Pantalla"),
    SUMAR("Sumar"),
    MULTIPLICAR("Multiplicar")
}

/**
 * Un bloque encima de la pista principal.
 *
 * [fila] decide el orden: la fila 0 va justo encima del video principal y
 * cada fila mas arriba tapa a las de debajo. En la linea de tiempo cada fila
 * se ve como su propia pista, debajo de la principal.
 */
data class Capa(
    val inicioUs: Long,
    val fila: Int,
    val clip: Clip,
    val mezcla: Mezcla = Mezcla.NORMAL
) {
    val id: Long get() = clip.id
    val finUs: Long get() = inicioUs + clip.duracionUs
}

/** Musica o un sonido suelto. */
data class PistaAudio(
    val id: Long,
    val origen: Origen,
    val inicioUs: Long = 0,
    val desdeUs: Long = 0,
    val hastaUs: Long = origen.duracionUs,
    val volumen: Float = 1f,
    val fundidoEntradaUs: Long = 0,
    val fundidoSalidaUs: Long = 0
) {
    val duracionUs: Long get() = hastaUs - desdeUs
    val finUs: Long get() = inicioUs + duracionUs
}
