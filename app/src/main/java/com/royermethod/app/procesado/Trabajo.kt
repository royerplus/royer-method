package com.royermethod.app.procesado

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
private const val REALCE = 0.22f

/**
 * Imagen: reducir y comprimir.
 *
 * Se decodifica en dos tiempos. La primera pasada solo mide, y con la medida
 * se elige un inSampleSize para que el mapa de bits que se carga ya sea
 * pequeno. Cargar una foto de movil entera son unos cincuenta megas de
 * memoria para luego tirar el noventa por ciento.
 */
private fun procesarImagen(
    context: Context,
    trabajo: Trabajo,
    destino: File,
    onAvance: (Avance) -> Unit
): Resultado {
    fun onProgreso(f: Float) = onAvance(Avance(Tareas.PROCESAR, 0, 0, f))
    val uri: Uri = trabajo.medio.uri

    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, medidas)
    }
    require(medidas.outWidth > 0) { "no se pudo leer la imagen" }

    val corto = minOf(medidas.outWidth, medidas.outHeight)
    val objetivo = if (trabajo.ladoCorto <= 0) corto else minOf(trabajo.ladoCorto, corto)

    // Se decodifica entero, sin inSampleSize.
    //
    // El primer intento cargaba ya reducido a la potencia de dos mas cercana,
    // que es rapido pero descarta pixeles sin mirarlos: eso es exactamente el
    // problema que en el video resolvimos con Lanczos. Aqui se carga todo y se
    // reduce bien, aunque cueste memoria y tiempo. Una foto no son mil
    // fotogramas; se puede pagar.
    val crudo = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } ?: error("no se pudo decodificar la imagen")

    onProgreso(0.2f)

    // Reconstruccion antes de reducir.
    //
    // El orden importa y es al reves de lo que parece: se reconstruye al doble
    // primero y se baja despues. Asi cada pixel del resultado sale del
    // promedio de cuatro reconstruidos y hereda ese detalle. Reconstruir
    // despues de reducir seria pedirle al modelo que invente sobre lo que ya
    // se ha tirado.
    val mejorado = if (Etapas.REALZAR in trabajo.etapas) {
        // Copiar y cargar el modelo son unos segundos en los que no se puede
        // decir por donde va. Se avisa como etapa aparte en vez de dejar el
        // porcentaje quieto en cero sin explicacion.
        onAvance(Avance(Tareas.PREPARAR, 0, 0, 0f))
        if (Waifu2x.preparar(context, trabajo.limpieza)) {
            val franjas = ((crudo.height + 191) / 192).coerceAtLeast(1)
            reconstruir(crudo, trabajo.limpieza) { hechas, p ->
                onAvance(Avance(Tareas.GENERAR, hechas, franjas, 0.2f + 0.55f * p))
            }
        } else null
    } else null

    val fuente = mejorado ?: crudo
    if (mejorado != null && crudo !== mejorado) crudo.recycle()

    onProgreso(0.7f)

    val factor = objetivo.toFloat() / minOf(fuente.width, fuente.height)
    val anchoFinal = (fuente.width * factor).toInt().coerceAtLeast(1)
    val altoFinal = (fuente.height * factor).toInt().coerceAtLeast(1)

    val reducido = if (factor < 0.999f) {
        reducirPorMitades(fuente, anchoFinal, altoFinal)
    } else fuente

    onProgreso(0.85f)

    // Un realce muy suave al final. Con el modelo delante casi no hace falta;
    // solo devuelve el poquito de canto que quita el ultimo reescalado.
    val bmp = if (Etapas.REALZAR in trabajo.etapas && factor < 0.999f) {
        realzar(reducido).also { if (it !== reducido) reducido.recycle() }
    } else reducido

    // Sin comprimir marcado, se guarda a tope. Con el, la calidad sale del
    // mismo ajuste que el video para que el control signifique lo mismo.
    val calidad = if (Etapas.PESO !in trabajo.etapas) 100 else when {
        trabajo.bpp >= 0.20f -> 96
        trabajo.bpp >= 0.15f -> 92
        else -> 86
    }

    // PNG no tiene calidad: guarda todo tal cual. Es el unico sin perdida, y
    // por eso tambien el que mas pesa con diferencia.
    val formato = when (trabajo.formato.uppercase()) {
        "PNG" -> Bitmap.CompressFormat.PNG
        "WEBP" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
        }

        else -> Bitmap.CompressFormat.JPEG
    }
    destino.outputStream().use { bmp.compress(formato, calidad, it) }

    val salida = Resultado(destino, bmp.width, bmp.height, 0, destino.length())
    bmp.recycle()
    onProgreso(1f)
    return salida
}

/**
 * Pasa la imagen por el modelo, al doble de tamano.
 *
 * Se hace en franjas horizontales y no de golpe: una foto de movil al doble
 * son unos ciento cincuenta megas de pixeles, y reservar eso dos veces (antes
 * y despues) tumba la app en cualquier telefono normal. En franjas, la memoria
 * que hace falta a la vez es una fraccion.
 *
 * Las franjas llevan solape porque el modelo necesita ver alrededor de cada
 * pixel para decidir; sin el, se notarian las costuras entre franjas.
 */
private fun reconstruir(
    origen: Bitmap,
    ruido: Int,
    onProgreso: (hechas: Int, fraccion: Float) -> Unit
): Bitmap? {
    val w = origen.width
    val h = origen.height
    if (w < 8 || h < 8) return null

    val salida = runCatching {
        Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
    }.getOrNull() ?: return null

    val solape = 16
    // Franjas de 192 filas, no las mas gordas que quepan en memoria.
    //
    // Antes se calculaba el maximo que cabia, y en una foto normal salia una
    // sola franja: el porcentaje se quedaba clavado en cero durante todo el
    // trabajo y luego saltaba de golpe. Troceando mas, el avance que se ensena
    // es real y continuo. El coste es el solape repetido, que es poco.
    val altoFranja = minOf(h, 192)
    val lienzo = android.graphics.Canvas(salida)

    var y = 0
    var hechas = 0
    while (y < h) {
        val desde = maxOf(0, y - solape)
        val hasta = minOf(h, y + altoFranja + solape)
        val alturaFranja = hasta - desde

        val entrada = Waifu2x.buffer(w, alturaFranja)
        val trozo = Bitmap.createBitmap(origen, 0, desde, w, alturaFranja)
        trozo.copyPixelsToBuffer(entrada)
        trozo.recycle()

        val resultado = Waifu2x.buffer(w * 2, alturaFranja * 2)
        val ok = Waifu2x.procesar(entrada, resultado, w, alturaFranja)
        if (!ok) {
            salida.recycle()
            return null
        }

        val grande = Bitmap.createBitmap(w * 2, alturaFranja * 2, Bitmap.Config.ARGB_8888)
        resultado.position(0)
        grande.copyPixelsFromBuffer(resultado)

        // Se pega solo la parte util, sin el solape, que estaba solo para que
        // el modelo tuviera contexto.
        val recorteArriba = (y - desde) * 2
        val alto = (minOf(h, y + altoFranja) - y) * 2
        val util = Bitmap.createBitmap(grande, 0, recorteArriba, w * 2, alto)
        lienzo.drawBitmap(util, 0f, (y * 2).toFloat(), null)
        util.recycle()
        grande.recycle()

        y += altoFranja
        hechas++
        onProgreso(hechas, (y.toFloat() / h).coerceIn(0f, 1f))
    }

    return salida
}

/**
 * Reduce a la mitad tantas veces como haga falta, y el resto de una vez.
 *
 * Encoger de golpe a un tercio con el filtro bilineal de Android mira cuatro
 * pixeles vecinos por cada uno de salida: de cada nueve pixeles del original,
 * cinco no se miran siquiera. El detalle fino no se promedia, se tira, y por
 * eso las tramas y las lineas finas quedan sucias.
 *
 * Bajando por mitades, cada paso promedia exactamente cuatro pixeles en uno y
 * todos cuentan. Es la version en CPU de lo que hace el Lanczos del video.
 */
private fun reducirPorMitades(origen: Bitmap, ancho: Int, alto: Int): Bitmap {
    var actual = origen
    while (actual.width / 2 >= ancho && actual.height / 2 >= alto &&
        actual.width / 2 > 0 && actual.height / 2 > 0
    ) {
        val paso = Bitmap.createScaledBitmap(actual, actual.width / 2, actual.height / 2, true)
        if (actual !== origen) actual.recycle()
        actual = paso
    }
    if (actual.width == ancho && actual.height == alto) return actual
    val fin = Bitmap.createScaledBitmap(actual, ancho, alto, true)
    if (actual !== origen) actual.recycle()
    return fin
}

/**
 * Devuelve el canto que la reduccion quita.
 *
 * Mismo criterio que en el video: se compara cada pixel con la cruz de sus
 * cuatro vecinos y se le suma la diferencia, limitada al rango de esos
 * vecinos para que el filo no genere el cerco blanco de afilar de mas.
 */
private fun realzar(origen: Bitmap): Bitmap {
    val w = origen.width
    val h = origen.height
    if (w < 3 || h < 3) return origen

    val px = IntArray(w * h)
    origen.getPixels(px, 0, w, 0, 0, w, h)
    val salida = IntArray(w * h)
    System.arraycopy(px, 0, salida, 0, px.size)

    val fuerza = 0.35f
    for (y in 1 until h - 1) {
        for (x in 1 until w - 1) {
            val i = y * w + x
            var res = px[i].toLong() and 0xFF000000L
            for (desp in intArrayOf(16, 8, 0)) {
                val c = (px[i] shr desp) and 0xFF
                val a = (px[i - 1] shr desp) and 0xFF
                val b = (px[i + 1] shr desp) and 0xFF
                val u = (px[i - w] shr desp) and 0xFF
                val d = (px[i + w] shr desp) and 0xFF
                val media = (a + b + u + d) / 4
                val techo = maxOf(maxOf(a, b), maxOf(u, d), c)
                val suelo = minOf(minOf(a, b), minOf(u, d), c)
                val v = (c + (c - media) * fuerza).toInt().coerceIn(suelo, techo)
                res = res or (v.toLong() shl desp)
            }
            salida[i] = res.toInt()
        }
    }

    val nueva = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    nueva.setPixels(salida, 0, w, 0, 0, w, h)
    return nueva
}

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
