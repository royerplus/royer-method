package com.royermethod.app.procesado

import android.content.Context
import com.royermethod.app.media.Medio
import java.io.File

/** Etapas que se pueden encender o apagar. */
object Etapas {
    const val FRAMES = "frames"
    const val ESTABILIZAR = "estabilizar"
    const val REALZAR = "imagen"
    const val PESO = "peso"
}

/** Extension que le toca al archivo de salida. */
fun extensionDe(formato: String): String = when (formato.uppercase()) {
    "PNG" -> "png"
    "WEBP" -> "webp"
    else -> "jpg"
}

/**
 * Un encargo completo: que archivo, con que ajustes y que etapas.
 *
 * Se arma en la pantalla de ajustes y viaja entero hasta el procesado, para
 * que el orden de las etapas lo decida un solo sitio y no la interfaz.
 */
data class Trabajo(
    val medio: Medio,
    val ladoCorto: Int,
    val fpsSalida: Int,
    val bpp: Float,
    val etapas: Set<String>,
    /** A cuantos fotogramas por segundo generar antes de mezclar. */
    val fpsGenerados: Int = 0,
    /**
     * Cuanto limpiar al reconstruir la imagen: -1 nada, 1 a 3 cada vez mas.
     *
     * Mas no siempre es mejor. Un nivel alto sobre una imagen que ya estaba
     * limpia se lleva por delante la textura fina junto con el ruido.
     */
    val limpieza: Int = 1,
    /** JPG, WEBP, PNG. Vacio deja el del archivo de partida. */
    val formato: String = "JPG",
    /** Trozos que van a camara lenta. */
    val tramos: List<Tramo> = emptyList()
)

/** Lo que sale del procesado. */
data class Salida(
    val archivo: File,
    val descripcion: String
)

/**
 * Por donde va el trabajo.
 *
 * Se informa por etapas y no con un solo porcentaje porque las etapas cuestan
 * cosas muy distintas: generar fotogramas puede ser diez veces mas caro que
 * todo lo demas junto, y una barra unica mentiria sobre lo que queda.
 *
 * @param etapa cual de las tareas se esta haciendo ahora
 * @param hechos y [total] fotogramas de la etapa actual, para el contador
 * @param fraccion avance global, de 0 a 1
 */
data class Avance(
    val etapa: Int,
    val hechos: Int,
    val total: Int,
    val fraccion: Float
)

/** Las etapas, en el orden en que ocurren. */
object Tareas {
    const val PREPARAR = 0
    const val GENERAR = 1
    const val ESTABILIZAR = 2
    const val PROCESAR = 3
    const val CERRAR = 4
}

/**
 * Ejecuta el encargo en el orden establecido.
 *
 * El orden no es negociable desde la interfaz porque importa:
 *
 *   generar frames -> estabilizar -> realzar -> reducir -> comprimir
 *
 * Comprimir va siempre al final: comprimir antes obliga a todo lo que venga
 * detras a trabajar sobre material ya danado. Realzar va antes de reducir
 * seria un error, porque reducir vuelve a ablandar el filo; por eso el realce
 * ocurre dentro del mismo paso de reduccion, ya sobre el resultado.
 *
 * Generar frames y estabilizar todavia no estan: necesitan el modelo de
 * interpolacion, que es codigo nativo aparte. Si vienen encendidas se ignoran
 * en vez de fingir que se aplicaron.
 */
fun ejecutar(
    context: Context,
    trabajo: Trabajo,
    onAvance: (Avance) -> Unit
): Salida {
    val destino = File(
        context.cacheDir,
        if (trabajo.medio.esVideo) "salida.mp4" else "salida.${extensionDe(trabajo.formato)}"
    )
    if (destino.exists()) destino.delete()

    onAvance(Avance(Tareas.PREPARAR, 0, 0, 0f))

    // Cargar el modelo tarda unos segundos la primera vez, asi que se hace
    // aqui y no al abrir la app: solo lo paga quien va a usarlo.
    val quiereFrames = Etapas.FRAMES in trabajo.etapas && trabajo.fpsGenerados > 0
    val quiereMejora = Etapas.REALZAR in trabajo.etapas
    if (quiereFrames) Rife.preparar(context)
    if (quiereMejora && trabajo.medio.esVideo) Waifu2x.preparar(context, trabajo.limpieza)

    val r = if (trabajo.medio.esVideo) {
        transcodificar(
            context = context,
            entrada = trabajo.medio.uri,
            salida = destino,
            ajustes = Ajustes(
                ladoCorto = trabajo.ladoCorto,
                fpsSalida = trabajo.fpsSalida,
                bpp = trabajo.bpp,
                // Con el modelo delante no hace falta afilar: el realce solo
                // subrayaria lo que el modelo acaba de reconstruir bien.
                realce = if (quiereMejora) 0f else REALCE,
                reconstruir = quiereMejora,
                fpsGenerados = if (quiereFrames) trabajo.fpsGenerados else 0,
                estabilizar = Etapas.ESTABILIZAR in trabajo.etapas,
                // Sin fotogramas generados no hay nada que repartir, asi que
                // ralentizar solo repetiria: se ignora.
                tramos = if (quiereFrames) trabajo.tramos else emptyList()
            ),
            onProgreso = { hechos, total, fraccion ->
                onAvance(
                    Avance(
                        if (quiereFrames) Tareas.GENERAR else Tareas.PROCESAR,
                        hechos, total, fraccion
                    )
                )
            }
        )
    } else {
        procesarImagen(context, trabajo, destino, onAvance)
    }

    onAvance(Avance(Tareas.CERRAR, 0, 0, 1f))

    // El peso solo dice algo comparado con el de partida. "4 MB" no significa
    // nada; "de 11.3 a 4.0 MB" es el resultado.
    val antes = trabajo.medio.bytes
    val peso = if (antes > 0) "${pesoCorto(antes)} → ${pesoCorto(r.bytes)}" else pesoCorto(r.bytes)
    val medidas = "${r.ancho}x${r.alto}"
    val fps = if (r.fps > 0) "  ·  ${r.fps} fps" else ""

    return Salida(r.archivo, "$medidas$fps  ·  $peso")
}

/**
 * En escritorio el realce iba a 0.4, pero ahi detras habia un codificador que
 * sabia guardar ese filo. El del movil no: afilar de mas le mete detalle que
 * no puede pagar y lo devuelve como ruido alrededor de las lineas. A la mitad
 * se recupera el canto que quita la reduccion sin cargarle trabajo de mas.
 */
internal const val REALCE = 0.22f

fun pesoCorto(bytes: Long): String = when {
    bytes >= 1048576L -> "%.1f MB".format(bytes / 1048576.0)
    bytes > 0L -> "%.0f KB".format(bytes / 1024.0)
    else -> "?"
}

/**
 * Bits por pixel de cada nivel.
 *
 * Ojo con estos numeros: los de las pruebas de escritorio (0.14 / 0.095 /
 * 0.065) salieron de x264 en preset lento y afinado para dibujo. El
 * codificador del movil es el del propio chip, esta hecho para ir rapido y
 * gastando poca bateria, y con los mismos bits saca bastante menos calidad:
 * aparece ruido alrededor de las lineas y las zonas planas se ensucian.
 *
 * Por eso aqui van mas altos. Sigue pesando mucho menos que el original,
 * pero sin el destrozo.
 */
fun bppDe(nivel: String): Float = when (nivel) {
    "Ligera" -> 0.24f
    "Maxima" -> 0.12f
    else -> 0.17f
}

/** "576p" -> 576. "Original" y cualquier otra cosa -> 0, que no toca nada. */
fun ladoDe(texto: String): Int = texto.trimEnd('p').toIntOrNull() ?: 0
