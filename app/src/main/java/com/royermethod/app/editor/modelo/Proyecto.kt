package com.royermethod.app.editor.modelo

/**
 * El proyecto de edicion entero.
 *
 * Todo es inmutable a proposito. Deshacer guarda copias del proyecto, y con
 * objetos inmutables una copia es solo una referencia: nada se puede cambiar
 * por debajo de una version guardada. Tambien es lo que permite que el motor
 * de render lea el proyecto desde otro hilo sin cerrojos: lo que tiene en la
 * mano no va a cambiar mientras dibuja.
 *
 * Los tiempos van en microsegundos, que es la unidad de MediaCodec. Usar
 * milisegundos obligaria a redondear cada fotograma (33.333 ms) y el error se
 * iria sumando a lo largo del video.
 */
data class Proyecto(
    val formato: Formato = Formato.VERTICAL,
    val fondo: Fondo = Fondo.DESENFOCADO,
    /** Calidad de salida. El proyecto la recuerda para no preguntarla cada vez. */
    val salida: AjustesSalida = AjustesSalida(),
    /** La pista principal: los bloques van uno detras de otro, sin huecos. */
    val principal: List<Clip> = emptyList(),
    /** Lo que va encima: videos, imagenes y textos superpuestos. */
    val capas: List<Capa> = emptyList(),
    /** Musica y sonidos sueltos. */
    val audios: List<PistaAudio> = emptyList(),
    /** Contador para dar identificadores unicos sin depender del reloj. */
    val siguienteId: Long = 1
) {
    fun conId(): Pair<Long, Proyecto> = siguienteId to copy(siguienteId = siguienteId + 1)
}

/** Proporcion del lienzo. */
enum class Formato(val nombre: String, val ancho: Int, val alto: Int) {
    VERTICAL("9:16", 9, 16),
    HORIZONTAL("16:9", 16, 9),
    CUADRADO("1:1", 1, 1),
    RETRATO_3_4("3:4", 3, 4),
    RETRATO("4:5", 4, 5);

    /**
     * Medidas en pixeles para un lado corto dado. Siempre pares: es lo unico
     * que H.264 y H.265 exigen de verdad.
     */
    fun medidas(ladoCorto: Int): Pair<Int, Int> {
        val corto = minOf(ancho, alto)
        val w = ladoCorto.toLong() * ancho / corto
        val h = ladoCorto.toLong() * alto / corto
        return par(w.toInt()) to par(h.toInt())
    }
}

internal fun par(v: Int): Int = (v / 2) * 2

/** Lo que se ve donde el video no llega a cubrir el lienzo. */
enum class Fondo(val nombre: String) {
    NEGRO("Negro"),
    /** El mismo clip, ampliado y desenfocado detras. Lo que hacen todas las apps. */
    DESENFOCADO("Desenfocado"),
    BLANCO("Blanco")
}

/** Que se ha elegido para exportar. */
data class AjustesSalida(
    /**
     * Lado corto en pixeles. 576 por defecto: en las pruebas de la app de
     * mejora fue donde el detalle se mantiene y el peso se desploma, y es
     * ademas a lo que las redes recomprimen un video vertical.
     */
    val ladoCorto: Int = 576,
    val calidad: Calidad = Calidad.EQUILIBRADA,
    /** H.265 si el telefono lo tiene: misma calidad en bastante menos peso. */
    val hevc: Boolean = true,
    /**
     * Desenfoque de movimiento en lo que se mueve (transiciones, animaciones,
     * fotogramas clave). Es lo que hace que 30 fps se vean fluidos y no a
     * saltos: cada fotograma lleva dentro el rastro del movimiento.
     */
    val desenfoqueMovimiento: Boolean = true,
    /** Muestras por fotograma para ese desenfoque. Mas = mas suave y mas lento. */
    val muestras: Int = 8
)

/**
 * Bits por pixel por fotograma.
 *
 * Es la medida que de verdad manda: el mismo bitrate se ve muy distinto segun
 * cuantos pixeles tenga que repartir. Los valores salen de las pruebas de la
 * app de mejora (ver bppDe), corregidos para H.265, que con los mismos bits
 * saca bastante mas.
 */
enum class Calidad(val nombre: String, val bppAvc: Float, val bppHevc: Float, val audioKbps: Int) {
    LIGERA("Menos peso", 0.10f, 0.065f, 96),
    EQUILIBRADA("Equilibrada", 0.14f, 0.09f, 128),
    ALTA("Mas calidad", 0.20f, 0.13f, 160)
}

enum class TipoOrigen { VIDEO, IMAGEN, AUDIO }

/** De donde viene un clip: el archivo y lo que se sabe de el. */
data class Origen(
    /** Uri como texto: el modelo no depende de Android y se puede probar fuera. */
    val uri: String,
    val nombre: String,
    val tipo: TipoOrigen,
    val ancho: Int = 0,
    val alto: Int = 0,
    /** 0 en imagenes. */
    val duracionUs: Long = 0,
    /** 0 si no se pudo averiguar. */
    val fps: Float = 0f,
    val conAudio: Boolean = false,
    /** Grados que hay que girar al mostrarlo; los moviles graban tumbado. */
    val giro: Int = 0,
    /** Bytes del archivo. Sirve para estimar lo complejo que es el contenido. */
    val bytes: Long = 0
) {
    val esVideo: Boolean get() = tipo == TipoOrigen.VIDEO
    val esImagen: Boolean get() = tipo == TipoOrigen.IMAGEN

    /**
     * Grabado a menos de 30 fps (24, 25...). Pasado tal cual a 30, uno de
     * cada pocos fotogramas se repite y el movimiento va a tirones: estos
     * entran ya con los fotogramas generados.
     */
    val pideFluidez: Boolean get() = esVideo && fps > 0f && fps < 29f

    /** Medidas tal y como se ven, con el giro ya aplicado. */
    val anchoVisible: Int get() = if (giro == 90 || giro == 270) alto else ancho
    val altoVisible: Int get() = if (giro == 90 || giro == 270) ancho else alto

    /**
     * Bits por pixel por fotograma del archivo de partida.
     *
     * Es una medida barata de lo dificil de comprimir que es el contenido: un
     * clip con mucho movimiento o mucho grano llega pesando mas por segundo
     * que uno quieto. Se usa para repartir el bitrate de salida por tramos.
     */
    fun complejidad(): Float {
        if (!esVideo || duracionUs <= 0 || ancho <= 0 || alto <= 0) return 0f
        val fpsReal = if (fps > 0f) fps else 30f
        val bitsPorSegundo = bytes * 8.0 / (duracionUs / 1_000_000.0)
        return (bitsPorSegundo / (ancho.toDouble() * alto * fpsReal)).toFloat()
    }
}
