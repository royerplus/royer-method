package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.royermethod.app.editor.modelo.*
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs

internal fun EstadoEditor.cambiarAspecto(arrastre: Boolean = false, f: (Clip) -> Clip) {
    val c = clipElegido ?: return
    val local = ((tiempo - (inicioElegido ?: 0L))).coerceIn(0, c.duracionUs)
    cambiarClip(arrastre) { it.editarPropiedades(local, f) }
}

internal fun crearCapaAjuste(estado: EstadoEditor, efectos: Boolean = false, desdeElegido: Boolean = false) {
    val referencia = if (desdeElegido) estado.clipElegido?.takeUnless { it.esAjuste } else null
    val inicio = if (referencia != null) estado.inicioElegido ?: 0L else 0L
    var id = 0L
    estado.hacer { p ->
        val (nuevo, q) = p.conId(); id = nuevo
        val dur = referencia?.duracionUs ?: (Linea.duracion(p).takeIf { it > 0 } ?: Clip.IMAGEN_POR_DEFECTO_US)
        q.copy(capas = q.capas + Capa(inicio, (p.capas.maxOfOrNull { it.fila } ?: -1) + 1,
            Clip(nuevo, esAjuste = true, hastaUs = dur)))
    }
    estado.elegir(Seleccion.Bloque(id)); estado.panel = if (efectos) Panel.EFECTOS else Panel.COLOR
    val fin = inicio + (estado.clipElegido?.duracionUs ?: 1L) - 1
    if (estado.tiempo !in inicio..fin) estado.pausarEn(estado.tiempo.coerceIn(inicio, fin))
}

internal fun cambiarClavePropiedades(estado: EstadoEditor, grupo: GrupoPropiedad, movimiento: Boolean, quitar: Boolean) {
    val c = estado.clipElegido ?: return
    val ini = estado.inicioElegido ?: return
    val local = (estado.tiempo - ini).coerceIn(0, c.duracionUs)
    val canal = estado.canalPropiedad?.takeIf { it.grupo == grupo }
    val canales = canal?.let { listOf(it) } ?: Propiedad.values().filter { it.grupo == grupo }
    val conEstilo = !movimiento || estado.modoClaveTexto != 1
    val conMovimiento = movimiento && estado.modoClaveTexto != 2 && canal == null
    val puntos = (if (conEstilo) canales.flatMap { c.propiedades[it].orEmpty().map { k -> k.tiempoUs - c.desfasePropiedadesUs } } else emptyList()) +
        (if (conMovimiento) c.claves.map { it.tiempoUs } else emptyList())
    val cerca = puntos.filter { it in 0..c.duracionUs }.minByOrNull { abs(it - local) }?.takeIf { abs(it - local) <= toleranciaClaves() }
    if (quitar && cerca == null) return
    val t = cerca ?: local
    estado.pausarEn(ini + t)
    estado.hacer { p ->
        var q = p
        if (conEstilo) q = Edicion.cambiar(q, c.id) { clip ->
            if (!quitar) clip.ponerPropiedades(t, canales)
            else clip.copy(propiedades = clip.propiedades.mapValues { (prop, ks) ->
                if (prop in canales) ks.filter { it.tiempoUs != t + clip.desfasePropiedadesUs } else ks
            }.filterValues { it.isNotEmpty() })
        }
        if (conMovimiento) q = if (!quitar) Edicion.ponerClave(q, c.id, ini + t, c.encuadreEn(t), 0)
            else Edicion.quitarClave(q, c.id, ini + t, 0)
        q
    }
}

/** Siempre visible sobre los controles: tiempo y rombos del grupo o canal elegido. */
@Composable
internal fun TiempoPropiedades(estado: EstadoEditor, grupo: GrupoPropiedad, movimientoPorDefecto: Boolean = false) {
    val c = estado.clipElegido ?: return
    val inicio = estado.inicioElegido ?: return
    val local = (estado.tiempo - inicio).coerceIn(0, c.duracionUs)
    val modo = estado.modoClaveTexto
    val disponibles = Propiedad.values().filter { it.grupo == grupo }
    val canal = estado.canalPropiedad?.takeIf { it.grupo == grupo }
    val canales = if (canal != null) listOf(canal) else disponibles
    val conMovimiento = movimientoPorDefecto && modo != 2 && canal == null
    val conEstilo = !movimientoPorDefecto || modo != 1
    val puntos = (if (conEstilo) canales.flatMap { c.propiedades[it].orEmpty().map { k -> k.tiempoUs - c.desfasePropiedadesUs } } else emptyList()) +
        (if (conMovimiento) c.claves.map { it.tiempoUs } else emptyList())
    val tiempos = puntos.filter { it in 0..c.duracionUs }.distinct().sorted()
    val tolerancia = toleranciaClaves()
    val cercano = tiempos.minByOrNull { abs(it - local) }?.takeIf { abs(it - local) <= tolerancia }
    Column(Modifier.fillMaxWidth().clip(esquinas(12.dp)).background(Ink.White04).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${TiempoPreciso.texto(local)} / ${TiempoPreciso.texto(c.duracionUs)}", style = MaterialTheme.typography.labelSmall, color = Ink.White70)
        val buscar by rememberUpdatedState<(Float, Float) -> Unit>({ x, w ->
            estado.pausarEn(inicio + ((x / w.coerceAtLeast(1f)).coerceIn(0f, 1f) * c.duracionUs).toLong())
        })
        Canvas(Modifier.fillMaxWidth().height(36.dp)
            .pointerInput(c.id) { detectTapGestures { buscar(it.x, size.width.toFloat()) } }
            .pointerInput(c.id) { detectDragGestures(onDragStart = { buscar(it.x, size.width.toFloat()) }) { ch, _ -> ch.consume(); buscar(ch.position.x, size.width.toFloat()) } }) {
            val cy = size.height * .75f
            val yClave = size.height * .22f
            drawLine(Ink.White15, Offset(0f, cy), Offset(size.width, cy), 4.dp.toPx())
            for (t in tiempos) {
                val x = t.toFloat() / c.duracionUs * size.width
                val r = 4.dp.toPx()
                val path = Path().apply { moveTo(x, yClave-r); lineTo(x+r, yClave); lineTo(x, yClave+r); lineTo(x-r, yClave); close() }
                drawPath(path, Ink.White70)
            }
            val x = local.toFloat() / c.duracionUs * size.width
            drawLine(Ink.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
            drawCircle(Ink.White, 4.dp.toPx(), Offset(x, cy))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotonIcono(Glifos.atras, { tiempos.lastOrNull { it < local - tolerancia }?.let { estado.pausarEn(inicio + it) } }, activo = tiempos.any { it < local - tolerancia })
            BotonIcono(Glifos.rombo, { cambiarClavePropiedades(estado, grupo, movimientoPorDefecto, false) },
                modifier = Modifier.semantics { contentDescription = "Poner keyframe" })
            BotonIcono(Glifos.romboQuitar, { cambiarClavePropiedades(estado, grupo, movimientoPorDefecto, true) }, activo = cercano != null,
                modifier = Modifier.semantics { contentDescription = "Quitar keyframe" })
            BotonIcono(Glifos.siguiente, { tiempos.firstOrNull { it > local + tolerancia }?.let { estado.pausarEn(inicio + it) } }, activo = tiempos.any { it > local + tolerancia })
            BotonBorde("Curva", { estado.verCurvasPropiedades = !estado.verCurvasPropiedades })
        }
    }
}

/** Controles avanzados en el cuerpo desplazable, sin tapar la barra de tiempo. */
@Composable
internal fun OpcionesPropiedades(estado: EstadoEditor, grupo: GrupoPropiedad, movimiento: Boolean = false) {
    val c = estado.clipElegido ?: return
    val local = estado.localElegido ?: 0L
    val disponibles = Propiedad.values().filter { it.grupo == grupo }
    val canal = estado.canalPropiedad?.takeIf { it.grupo == grupo }
    val canales = if (canal != null) listOf(canal) else disponibles
    val conEstilo = !movimiento || estado.modoClaveTexto != 1
    val conMovimiento = movimiento && estado.modoClaveTexto != 2 && canal == null
    Spacer(Modifier.height(8.dp))
    if (movimiento) Opciones(listOf(0, 1, 2), estado.modoClaveTexto, { listOf("Todo", "Movimiento", "Estilo")[it] }, { estado.modoClaveTexto = it })
    if (conEstilo) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("En conjunto", canal == null, { estado.canalPropiedad = null })
            Chip("Por propiedad", canal != null, { estado.canalPropiedad = disponibles.first() })
        }
        if (canal != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                disponibles.forEach { p -> Chip(p.titulo, canal == p, { estado.canalPropiedad = p }, marca = c.propiedades[p].orEmpty().isNotEmpty()) }
            }
        }
    }
    if (estado.verCurvasPropiedades) {
        val segmento = canales.firstNotNullOfOrNull { prop ->
            val ks = c.propiedades[prop].orEmpty().sortedBy { it.tiempoUs }
            if (ks.size < 2) null else ks[ks.indexOfLast { it.tiempoUs <= local + c.desfasePropiedadesUs }.coerceIn(0, ks.size - 2)]
        }
        if (conEstilo && segmento != null) {
            Etiqueta(if (conMovimiento) "Curva conjunta" else if (canal == null) "Curva del estilo" else "Curva · ${canal.titulo}")
            SelectorCurva(segmento.curva, segmento.bezier, segmento.velocidad) { cu, b, v, arrastre ->
                estado.cambiarClip(arrastre) { clip -> clip.copy(propiedades = clip.propiedades.mapValues { (p, ks) ->
                    if (p !in canales || ks.size < 2) ks else {
                        val orden = ks.sortedBy { it.tiempoUs }
                        val k = orden[orden.indexOfLast { it.tiempoUs <= local + clip.desfasePropiedadesUs }.coerceIn(0, orden.size - 2)]
                        ks.map { if (it == k) it.copy(curva = cu, bezier = b, velocidad = v) else it }
                    }
                }, claves = if (!conMovimiento || clip.claves.size < 2) clip.claves else {
                    val orden = clip.claves.sortedBy { it.tiempoUs }
                    val k = orden[orden.indexOfLast { it.tiempoUs <= local }.coerceIn(0, orden.size - 2)]
                    clip.claves.map { if (it == k) it.copy(curva = cu, bezier = b, velocidad = v) else it }
                }) }
            }
        } else if (conEstilo) Nota("Añade dos keyframes para elegir la curva del estilo.")
        if (conMovimiento && (!conEstilo || segmento == null)) {
            Etiqueta("Curva del movimiento")
            PanelCurva(estado)
        }
        if (canal?.tipo == TipoValor.PASO) Nota("Esta propiedad cambia al llegar a la clave; no tiene transición gradual.")
    }
}

@Composable
internal fun HojaMarco(estado: EstadoEditor, cerrar: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    HojaPanel("Bordes y perspectiva", cerrar, fijo = {
        TiempoPropiedades(estado, GrupoPropiedad.MARCO, tab == 2)
        Spacer(Modifier.height(8.dp))
        Pestanas(listOf("Borde", "Sombra", "Perspectiva"), tab, { tab = it })
    }) {
        OpcionesPropiedades(estado, GrupoPropiedad.MARCO, tab == 2)
        if (tab == 2) {
            TextoPerspectiva(estado, estado.clipElegido?.id ?: return@HojaPanel)
        } else {
            val original = estado.clipElegido ?: return@HojaPanel
            val c = original.propiedadesEn(estado.localElegido ?: 0L)
            val m = c.marco
            Spacer(Modifier.height(8.dp))
            if (tab == 0) {
                Etiqueta("Estilos rápidos")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((nombre, marco) in listOf(
                        "Limpio" to MarcoVisual(), "Fino" to MarcoVisual(ancho = .006f, color = 0xFF161616.toInt()),
                        "Blanco" to MarcoVisual(ancho = .012f), "Suave" to MarcoVisual(ancho = .008f, radio = .04f, sombra = .5f),
                        "Neón" to MarcoVisual(ancho = .008f, color = 0xFF53E4FF.toInt(), neon = .8f))) {
                        Chip(nombre, m == marco, { estado.cambiarAspecto { it.copy(marco = marco) } })
                    }
                }
                Deslizador("Anchura", m.ancho, 0f, .15f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(ancho = v)) } }, texto = { "%.1f %%".format(it * 100) })
                Deslizador("Esquinas redondeadas", m.radio, 0f, .5f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(radio = v)) } })
                Etiqueta("Color del borde")
                ColorMarco(m.color) { v -> estado.cambiarAspecto { it.copy(marco = it.marco.copy(color = v, ancho = it.marco.ancho.takeIf { a -> a > 0f } ?: .008f)) } }
                Deslizador("Resplandor neón", m.neon, 0f, 1f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(neon = v)) } })
            } else {
                Etiqueta("Sombra y resplandor")
                Nota("El color y la difusión de la sombra son independientes del borde. Sin distancia funciona como un halo.")
                Deslizador("Opacidad", m.sombra, 0f, 1f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(sombra = v)) } })
                Etiqueta("Color de la sombra")
                ColorMarco(m.colorSombra) { v -> estado.cambiarAspecto { it.copy(marco = it.marco.copy(colorSombra = v, sombra = it.marco.sombra.takeIf { a -> a > 0f } ?: .6f)) } }
                Deslizador("Difusión", m.suavidad, 0f, .2f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(suavidad = v)) } })
                Deslizador("Distancia", m.distancia, 0f, .3f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(distancia = v)) } })
                Deslizador("Dirección", m.angulo, 0f, 360f, { v -> estado.cambiarAspecto(true) { it.copy(marco = it.marco.copy(angulo = v)) } }, texto = { "${it.toInt()}°" })
            }
        }
    }
}

@Composable
private fun ColorMarco(color: Int, cambiar: (Int) -> Unit) {
    Muestras(COLORES_TEXTO, color, cambiar)
    // Tonos libres, además de las muestras rápidas.
    for ((nombre, shift) in listOf("Rojo" to 16, "Verde" to 8, "Azul" to 0)) {
        Deslizador(nombre, ((color ushr shift) and 255).toFloat(), 0f, 255f,
            { v -> cambiar((color and (255 shl shift).inv()) or (v.toInt() shl shift)) }, texto = { it.toInt().toString() })
    }
}

@Composable
internal fun HojaColor(estado: EstadoEditor, cerrar: () -> Unit) {
    HojaPanel(if (estado.clipElegido?.esAjuste == true) "Capa de ajuste" else "Ajustes del clip", cerrar, fijo = {
        TiempoPropiedades(estado, GrupoPropiedad.AJUSTES)
    }) {
        OpcionesPropiedades(estado, GrupoPropiedad.AJUSTES)
        DestinosCapa(estado)
        Nota("El valor base se aplica en todo el tramo. Los keyframes animan ese valor dentro de su duración.")
        PanelColor(estado)
    }
}
