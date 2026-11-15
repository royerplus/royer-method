package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Bezier
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.PiezaVideo
import com.royermethod.app.editor.modelo.RegionUv
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.SeccionAjustes
import com.royermethod.app.ui.components.Tira

/** Que cada cambio en "Movimiento" pone un keyframe se dice una vez por sesion. */
private var consejoParteVisto = false

/** Las maneras de partir la imagen. La ultima ya trae las dos mitades entrando hasta juntarse. */
private enum class Division(val titulo: String) {
    ENTERA("Entera"), VERTICAL("Arriba y abajo"), HORIZONTAL("A los lados"), UNIR("Unir mitades")
}

/**
 * Partes: la imagen del bloque en trozos que se mueven por separado; ninguno
 * vuelve a llenar el lienzo. Sin partir, la hoja son las maneras de hacerlo.
 * Con partes, arriba van ellas y debajo la elegida en tres pestanas: que
 * trozo es, como se mueve y con que curva.
 */
@Composable
internal fun HojaPiezas(estado: EstadoEditor, cerrar: () -> Unit) {
    val clip = estado.clipElegido ?: return
    var elegida by rememberSaveable(clip.id) { mutableIntStateOf(0) }
    var pestana by rememberSaveable(clip.id) { mutableIntStateOf(0) }
    var dividir by rememberSaveable(clip.id) { mutableStateOf(false) }
    val indice = elegida.coerceIn(0, clip.piezas.lastIndex.coerceAtLeast(0))
    val pieza = clip.piezas.getOrNull(indice)
    val catalogo = dividir || pieza == null
    val inicio = estado.inicioElegido
    val local = if (inicio == null) 0L else (estado.tiempo - inicio).coerceIn(0L, clip.duracionUs)
    // La mas cercana dentro del bloque; sin el ajuste a fotogramas, a 0,0002 s o menos
    val tolerancia = toleranciaClaves()

    fun partir(d: Division) {
        estado.cambiarClip { c ->
            c.copy(piezas = when (d) {
                Division.ENTERA -> emptyList()
                Division.VERTICAL -> listOf(PiezaVideo("Superior", RegionUv(abajo = 0.5f)), PiezaVideo("Inferior", RegionUv(arriba = 0.5f)))
                Division.HORIZONTAL -> listOf(PiezaVideo("Izquierda", RegionUv(derecha = 0.5f)), PiezaVideo("Derecha", RegionUv(izquierda = 0.5f)))
                Division.UNIR -> {
                    val fin = minOf(300_000L, c.duracionUs.coerceAtLeast(1L))
                    listOf(
                        PiezaVideo("Superior", RegionUv(abajo = 0.5f), claves = listOf(Clave(0, Encuadre(y = -0.5f), Curva.SALIDA), Clave(fin, Encuadre()))),
                        PiezaVideo("Inferior", RegionUv(arriba = 0.5f), claves = listOf(Clave(0, Encuadre(y = 0.5f), Curva.SALIDA), Clave(fin, Encuadre())))
                    )
                }
            })
        }
        elegida = 0
        dividir = false
        if (d == Division.UNIR) {
            pestana = 1
            estado.inicioElegido?.let { estado.pausarEn(it) }
        }
    }
    fun cambiar(arrastre: Boolean = false, f: (PiezaVideo) -> PiezaVideo) {
        estado.cambiarClip(arrastre) { actual ->
            actual.copy(piezas = actual.piezas.mapIndexed { i, p -> if (i == indice) f(p) else p })
        }
    }
    LaunchedEffect(pestana, catalogo) {
        if (pestana == 1 && !catalogo && !consejoParteVisto) {
            consejoParteVisto = true
            estado.avisar("Cada cambio pone un keyframe en el cabezal")
        }
    }

    HojaPanel(Panel.PIEZAS.titulo, cerrar, claveDesplazamiento = listOf(clip.id, indice, pestana, catalogo),
        acciones = if (clip.piezas.isNotEmpty()) ({
            BotonTexto(if (dividir) "Volver" else "División", { dividir = !dividir }, glifo = if (dividir) Glifos.atras else Glifos.partes)
        }) else null,
        fijo = {
            if (clip.piezas.isNotEmpty()) Tira(Modifier.padding(bottom = 2.dp)) {
                clip.piezas.forEachIndexed { i, p ->
                    PastillaElemento(p.nombre.ifBlank { "Parte ${i + 1}" }, i == indice && !catalogo, { elegida = i; dividir = false },
                        conClaves = p.claves.isNotEmpty()) { color -> dibujoRegion(p.region, color) }
                }
            }
            if (pieza != null && !catalogo) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pestanas(listOf("Región", "Movimiento", "Curva"), pestana, { pestana = it }, Modifier.weight(1f))
                    BotonIcono(Glifos.duplicar, {
                        estado.cambiarClip { c ->
                            val original = c.piezas.getOrNull(indice) ?: return@cambiarClip c
                            c.copy(piezas = c.piezas + original.copy(nombre = "${original.nombre} copia"))
                        }
                        elegida = clip.piezas.size
                    }, tam = 38.dp, descripcion = "Duplicar esta parte")
                    BotonIcono(Glifos.papelera, {
                        estado.cambiarClip { c -> c.copy(piezas = c.piezas.filterIndexed { i, _ -> i != indice }) }
                        elegida = (indice - 1).coerceAtLeast(0)
                    }, tam = 38.dp, descripcion = "Quitar esta parte")
                }
                if (pestana != 0 && inicio != null) {
                    val tiempos = remember(pieza.claves, clip.duracionUs) {
                        pieza.claves.map { it.tiempoUs }.filter { it in 0L..clip.duracionUs }.distinct().sorted()
                    }
                    FranjaClaves(clip.id to indice, clip.duracionUs, local, tiempos, tolerancia,
                        onIr = { estado.pausarEn(inicio + it) },
                        onPoner = {
                            estado.pausarEn(inicio + local)
                            cambiar { p -> p.conPose(local, clip.duracionUs, tolerancia) { it } }
                        },
                        onQuitar = { cambiar { p -> p.sinClave(local, tolerancia, clip.duracionUs) } },
                        de = " de esta parte")
                }
            }
        },
        // Elegir como partirla son cuatro casillas: la hoja se queda en eso
        corta = catalogo
    ) {
        val parte = pieza
        if (catalogo || parte == null) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in Division.values()) {
                    Mosaico(d.titulo, d == Division.ENTERA && clip.piezas.isEmpty(), { partir(d) }, Modifier.weight(1f)) { color -> dibujoDivision(d, color) }
                }
            }
            return@HojaPanel
        }
        if (pestana == 0) {
            RegionDePieza(parte.region) { region -> cambiar(true) { it.copy(region = region.acotada()) } }
            return@HojaPanel
        }
        val ini = inicio ?: return@HojaPanel
        val clave = parte.claveCercana(local, tolerancia, clip.duracionUs)
        if (pestana == 1) {
            val pose = parte.encuadreEn(clave?.tiempoUs ?: local)
            fun transformar(f: (Encuadre) -> Encuadre) {
                estado.pausarEn(ini + local)
                cambiar(true) { it.conPose(local, clip.duracionUs, tolerancia, f) }
            }
            Deslizador("Posición X", pose.x, -2f, 2f, { v -> transformar { it.copy(x = v) } }, neutro = 0f, texto = { "%.3f".format(it) })
            Deslizador("Posición Y", pose.y, -2f, 2f, { v -> transformar { it.copy(y = v) } }, neutro = 0f, texto = { "%.3f".format(it) })
            Deslizador("Escala", pose.escala, 0f, 5f, { v -> transformar { it.copy(escala = v) } }, neutro = 1f, texto = { "%.3fx".format(it) })
            Deslizador("Giro", pose.rotacion, -360f, 360f, { v -> transformar { it.copy(rotacion = v) } }, neutro = 0f, texto = { "%.1f°".format(it) })
            Deslizador("Opacidad", pose.opacidad, 0f, 1f, { v -> transformar { it.copy(opacidad = v) } }, neutro = 1f, texto = { "%.1f%%".format(it * 100) })
            Tira(Modifier.padding(vertical = 4.dp)) {
                BotonTexto("Restablecer", { cambiar { it.conPose(local, clip.duracionUs, tolerancia) { Encuadre() } } }, glifo = Glifos.reiniciar)
                InterruptorFotogramas()
            }
            // El instante con precision: sin el ajuste a fotogramas se puede ir a 0,001 s
            SeccionAjustes("Tiempo exacto") {
                CampoTiempo("Cabezal", local, 0L, clip.duracionUs, { us, _ -> estado.pausarEn(ini + us) }, origenUs = ini)
                if (clave != null) MomentoClavePieza(estado, clip.id, indice, ini, clave.tiempoUs,
                    ClavesLinea.limites(parte.claves, clave.tiempoUs, clip.duracionUs, tolerancia), clip.duracionUs, tolerancia)
            }
            return@HojaPanel
        }
        // Curva: la del keyframe en que esta el cabezal y, con muchos, simplificarlos
        if (clave == null) {
            Nota("Lleva el cabezal a un keyframe para elegir cómo se mueve hasta el siguiente.", Modifier.padding(top = 6.dp))
        } else {
            fun cambiarClave(arrastre: Boolean = false, f: (Clave) -> Clave) = cambiar(arrastre) { p ->
                p.copy(claves = p.claves.map { if (it.tiempoUs == clave.tiempoUs) f(it) else it })
            }
            SelectorCurva(clave.curva, clave.bezier, clave.velocidad) { cu, b, v, arrastre ->
                cambiarClave(arrastre) { it.copy(curva = cu, bezier = b, velocidad = v) }
            }
            SeccionAjustes("Bezier a mano") {
                val bezier = clave.bezier?.takeIf { clave.velocidad == null }
                if (bezier == null) {
                    BotonTexto("Pasar esta curva a Bezier", { cambiarClave { it.copy(bezier = it.bezier ?: Bezier.SUAVE, velocidad = null) } }, glifo = Glifos.curva)
                } else {
                    EditorBezier(bezier) { b -> cambiarClave(true) { it.copy(bezier = b) } }
                    Deslizador("P1 tiempo", bezier.x1, 0f, 1f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(x1 = v)) } }, texto = { "%.3f".format(it) })
                    Deslizador("P1 avance", bezier.y1, -0.3f, 1.3f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(y1 = v)) } }, neutro = 0f, texto = { "%.3f".format(it) })
                    Deslizador("P2 tiempo", bezier.x2, 0f, 1f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(x2 = v)) } }, neutro = 1f, texto = { "%.3f".format(it) })
                    Deslizador("P2 avance", bezier.y2, -0.3f, 1.3f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(y2 = v)) } }, neutro = 1f, texto = { "%.3f".format(it) })
                }
            }
        }
        if (parte.claves.size >= 3 || parte.clavesOriginales != null) SimplificadorClaves(estado, DuenoClaves.Parte(clip.id, indice))
    }
}

/** El trozo de la imagen que ocupa una parte, en pequeno. */
private fun DrawScope.dibujoRegion(region: RegionUv, color: Color) {
    val r = region.acotada()
    drawRect(color.copy(alpha = 0.25f), Offset.Zero, size)
    drawRect(color, Offset(r.izquierda * size.width, r.arriba * size.height), Size(r.ancho * size.width, r.alto * size.height))
}

/** Como queda la imagen con cada manera de partirla. */
private fun DrawScope.dibujoDivision(d: Division, color: Color) {
    val w = size.width
    val h = size.height * 0.8f
    val y = (size.height - h) / 2f
    val radio = CornerRadius(3.dp.toPx())
    val hueco = 3.dp.toPx()
    val trazo = Stroke(1.6.dp.toPx())
    when (d) {
        Division.ENTERA -> drawRoundRect(color, Offset(0f, y), Size(w, h), radio, style = trazo)
        Division.VERTICAL -> {
            drawRoundRect(color, Offset(0f, y), Size(w, (h - hueco) / 2f), radio, style = trazo)
            drawRoundRect(color, Offset(0f, y + (h + hueco) / 2f), Size(w, (h - hueco) / 2f), radio, style = trazo)
        }
        Division.HORIZONTAL -> {
            drawRoundRect(color, Offset(0f, y), Size((w - hueco) / 2f, h), radio, style = trazo)
            drawRoundRect(color, Offset((w + hueco) / 2f, y), Size((w - hueco) / 2f, h), radio, style = trazo)
        }
        Division.UNIR -> {
            // Las dos mitades separadas y, entre ellas, las puntas que dicen que se juntan
            val alto = h * 0.3f
            drawRoundRect(color, Offset(0f, y), Size(w, alto), radio, style = trazo)
            drawRoundRect(color, Offset(0f, y + h - alto), Size(w, alto), radio, style = trazo)
            val cx = w / 2f
            val cy = size.height / 2f
            val p = 3.2.dp.toPx()
            drawPath(Path().apply { moveTo(cx - p, cy - p * 1.5f); lineTo(cx + p, cy - p * 1.5f); lineTo(cx, cy - p * 0.3f); close() }, color)
            drawPath(Path().apply { moveTo(cx - p, cy + p * 1.5f); lineTo(cx + p, cy + p * 1.5f); lineTo(cx, cy + p * 0.3f); close() }, color)
        }
    }
}

/**
 * El tiempo exacto de un keyframe de una parte, entre sus vecinos ([limites]),
 * con el ajuste a fotogramas si esta puesto. Como en el bloque, el cabezal lo
 * acompana y se mueve por su tiempo real aunque lleguen varios cambios de un
 * arrastre antes de recomponer.
 */
@Composable
private fun MomentoClavePieza(
    estado: EstadoEditor, id: Long, indice: Int, inicio: Long, tUs: Long,
    limites: LongRange?, duracionUs: Long, tolerancia: Long
) {
    if (limites == null || tUs !in limites) {
        Nota(
            if (AjustesEditor.ajustarFotogramas) "Hay otro keyframe a menos de medio fotograma: sin «Ajustar a fotogramas» se puede mover."
            else "No hay sitio para mover este keyframe entre los de al lado."
        )
        return
    }
    val donde = remember(id, indice) { longArrayOf(tUs) }
    SideEffect { donde[0] = tUs }
    CampoTiempo("Keyframe", tUs, limites.first, limites.last, { us, arrastre ->
        val desde = donde[0]
        estado.cambiarClip(arrastre) { c ->
            c.copy(piezas = c.piezas.mapIndexed { i, p -> if (i == indice) p.conClaveMovida(desde, us, duracionUs, tolerancia) else p })
        }
        val ahora = Edicion.clip(estado.proyecto, id)?.piezas?.getOrNull(indice)?.claves
        if (ahora?.any { it.tiempoUs == us } == true) donde[0] = us
        estado.pausarEn(inicio + donde[0])
    }, origenUs = inicio)
}

/** Los cuatro bordes del trozo dentro de la imagen: moverlos la corta y conserva su encuadre. */
@Composable
private fun RegionDePieza(region: RegionUv, cambiar: (RegionUv) -> Unit) {
    val r = region.acotada()
    // Deja recorrido util incluso si una region importada mide una fraccion de pixel.
    val margenX = minOf(0.00001f, r.ancho / 2f)
    val margenY = minOf(0.00001f, r.alto / 2f)
    Deslizador("Izquierda", r.izquierda, 0f, r.derecha - margenX, { cambiar(r.copy(izquierda = it)) }, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Derecha", r.derecha, r.izquierda + margenX, 1f, { cambiar(r.copy(derecha = it)) }, neutro = 1f, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Arriba", r.arriba, 0f, r.abajo - margenY, { cambiar(r.copy(arriba = it)) }, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Abajo", r.abajo, r.arriba + margenY, 1f, { cambiar(r.copy(abajo = it)) }, neutro = 1f, texto = { "%.2f%%".format(it * 100) })
}
