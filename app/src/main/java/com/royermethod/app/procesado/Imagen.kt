package com.royermethod.app.procesado

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import java.io.File

/**
 * Imagen: reducir y comprimir.
 *
 * Se decodifica en dos tiempos. La primera pasada solo mide, y con la medida
 * se elige un inSampleSize para que el mapa de bits que se carga ya sea
 * pequeno. Cargar una foto de movil entera son unos cincuenta megas de
 * memoria para luego tirar el noventa por ciento.
 */
internal fun procesarImagen(
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
