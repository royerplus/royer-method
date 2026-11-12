package com.royermethod.app.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.*
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink

internal fun resumenDestino(c: Clip): String = when {
    !c.esAjuste -> "Este elemento · ${c.nombre()}"
    c.afectaA == null -> "Todas las capas"
    c.afectaA.isEmpty() -> "Ninguna capa seleccionada"
    else -> "${c.afectaA.size} elemento(s) seleccionados"
}

/** El destino se guarda por identidad, de modo que reordenar no lo cambia. */
@Composable
internal fun DestinosCapa(estado: EstadoEditor, efectoId: Long? = null) {
    val c = estado.clipElegido ?: return
    var abierto by rememberSaveable(c.id) { mutableStateOf(false) }
    Etiqueta("Afecta a")
    if (!c.esAjuste) {
        Nota(resumenDestino(c))
        BotonBorde(if (efectoId == null) "+ Tramo para todas las capas" else "Aplicar también a otras capas", {
            if (efectoId == null) crearCapaAjuste(estado, efectos = estado.panel == Panel.EFECTOS, desdeElegido = true)
            else {
                val r = compartirEfecto(estado.proyecto, c.id, efectoId) ?: return@BotonBorde
                estado.hacer { r.first }
                estado.elegir(Seleccion.Bloque(r.second))
                estado.efectoAElegir = efectoId
                estado.panel = Panel.EFECTOS
            }
        })
        return
    }
    BotonBorde("${resumenDestino(c)}  ▾", { abierto = !abierto })
    if (!abierto) return
    Spacer(Modifier.height(8.dp))
    val p = estado.proyecto
    val candidatos = p.capas.filterNot { it.clip.esAjuste }.sortedByDescending { it.fila }.map { it.clip } + p.principal
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Todas", c.afectaA == null, { estado.cambiarClip { it.copy(afectaA = null) } })
        Chip("Ninguna", c.afectaA?.isEmpty() == true, { estado.cambiarClip { it.copy(afectaA = emptySet()) } })
    }
    Spacer(Modifier.height(8.dp))
    if (candidatos.isEmpty()) Nota("Agrega una foto, un video o un texto.")
    else {
        Nota("Toca para activar · desliza para ver más")
        Spacer(Modifier.height(6.dp))
        val ids = candidatos.map { it.id }.toSet()
        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)) {
            items(candidatos, key = { it.id }) { destino ->
                MiniaturaDestino(destino, AlcanceCapas.acepta(c, destino.id), Modifier.width(76.dp)) {
                    alternarDestino(estado, destino.id, ids)
                }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

/** Desde Todas, quitar una conserva activas las demás; cada toque es reversible. */
internal fun alternarDestino(estado: EstadoEditor, id: Long, disponibles: Set<Long>) {
    estado.cambiarClip { actual ->
        val ids = actual.afectaA ?: disponibles
        actual.copy(afectaA = if (id in ids) ids - id else ids + id)
    }
}

@Composable
private fun MiniaturaDestino(c: Clip, activa: Boolean, modifier: Modifier, cambiar: () -> Unit) {
    val borde by animateColorAsState(if (activa) Ink.White else Ink.White30, tween(150), label = "destino")
    val origen = c.origen
    // El centro del segmento diferencia recortes del mismo video; se reutiliza la caché de la línea.
    val instante = if (c.esVideo) c.tiempoArchivo(c.duracionUs / 2) / 250_000L * 250_000L else 0L
    val miniatura = origen?.let { LocalRecursosEditor.current.miniatura(it, instante) }
    val forma = esquinas(10.dp)
    Box(modifier.aspectRatio(1f).clip(forma).background(Ink.White08)
        .toggleable(value = activa, role = Role.Checkbox, onValueChange = { cambiar() })
        .semantics { contentDescription = "${if (c.esTexto) "Texto" else if (c.esVideo) "Video" else "Imagen"}: ${c.nombre()}" }
        .border(if (activa) 2.5.dp else 1.dp, borde, forma)) {
        val opacidad = if (activa) 1f else .5f
        when {
            miniatura != null -> Image(miniatura, null, Modifier.fillMaxSize().padding(3.dp).clip(esquinas(7.dp)).alpha(opacidad), contentScale = ContentScale.Crop)
            c.texto != null -> {
                val t = c.texto
                val fuente = when (t.fuente) { Fuente.SERIF -> FontFamily.Serif; Fuente.MONO -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                Box(Modifier.fillMaxSize().padding(3.dp).clip(esquinas(7.dp)).background(Color(0xFF727780)).alpha(opacidad), contentAlignment = Alignment.Center) {
                    Text(t.contenido.ifBlank { "Aa" }, color = Color(t.color), fontFamily = fuente,
                        fontWeight = if (t.negrita) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (t.cursiva) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 15.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
                }
            }
            else -> Icono(if (c.esVideo) Glifos.play else Glifos.superponer,
                Modifier.align(Alignment.Center).alpha(opacidad), tam = 28.dp)
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).clip(CircleShape)
            .background(if (activa) Ink.White else Color.Black.copy(alpha = .65f))
            .border(1.dp, borde, CircleShape), contentAlignment = Alignment.Center) {
            if (activa) Icono(Glifos.listo, color = Ink.Black, tam = 12.dp)
        }
        Box(Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp).clip(esquinas(4.dp))
            .background(Color.Black.copy(alpha = .65f)), contentAlignment = Alignment.Center) {
            if (c.esTexto) Text("T", color = Ink.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            else Icono(if (c.esVideo) Glifos.play else Glifos.superponer, tam = 11.dp)
        }
        if (c.esVideo) Text(segundos(c.duracionUs), color = Ink.White, fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(esquinas(4.dp))
                .background(Color.Black.copy(alpha = .65f)).padding(horizontal = 3.dp))
    }
}

/** Las filas superiores de esta lista son las que se ven delante en el lienzo. */
@Composable
internal fun PanelCapas(estado: EstadoEditor) {
    Interruptor("Marcos de edición", AjustesEditor.mostrarMarcos, { visible ->
        AjustesEditor.mostrarMarcos = visible
        AjustesEditor.alCambiar()
    }, "Contornos blancos y esquinas de selección. Puedes seguir editando aunque los ocultes.")
    Spacer(Modifier.height(10.dp))
    Nota("Delante arriba · detrás abajo. Cambiar el orden no mueve los clips en el tiempo.")
    val todas = estado.proyecto.capas.groupBy { it.fila }.toSortedMap(compareByDescending { it })
    val visualesOrdenadas = todas.filterValues { capas -> capas.any { !it.clip.esAjuste } }
    val filas = visualesOrdenadas + todas.filterKeys { it !in visualesOrdenadas }

    if (filas.isEmpty()) Nota("Aún no hay superposiciones. Agrega una capa, una foto o un texto.")
    filas.entries.forEachIndexed { indice, (fila, capas) ->
        val visuales = capas.filterNot { it.clip.esAjuste }
        val elegida = capas.any { it.id == estado.idElegido }
        fun elegir() {
            val t = estado.tiempo
            val c = capas.firstOrNull { t >= it.inicioUs && t < it.finUs } ?: capas.first()
            estado.elegir(Seleccion.Bloque(c.id)); estado.panel = Panel.CAPAS
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(esquinas(12.dp))
            .background(if (elegida) Ink.White15 else Ink.White04).clickable { elegir() }.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (visuales.isEmpty()) "Tramo de ajustes / efectos" else "Capa ${fila + 1}",
                        style = MaterialTheme.typography.titleSmall, color = Ink.White)
                    Text(capas.joinToString(" · ") { it.clip.nombre() }, maxLines = 2,
                        style = MaterialTheme.typography.bodySmall, color = Ink.White70)
                }
                if (visuales.isNotEmpty()) {
                    BotonIcono(Glifos.atras, { estado.hacer { AlcanceCapas.cambiarOrden(it, fila, true) } },
                        activo = indice > 0, modifier = Modifier.rotate(90f).semantics { contentDescription = "Traer delante" })
                    BotonIcono(Glifos.siguiente, { estado.hacer { AlcanceCapas.cambiarOrden(it, fila, false) } },
                        activo = indice < visualesOrdenadas.size - 1, modifier = Modifier.rotate(90f).semantics { contentDescription = "Enviar detrás" })
                }
                val elegidaCapa = capas.firstOrNull { it.id == estado.idElegido }
                if (elegidaCapa != null) BotonIcono(Glifos.editar, {
                    estado.panel = if (elegidaCapa.clip.esAjuste) {
                        if (elegidaCapa.clip.efectos.isNotEmpty()) Panel.EFECTOS else Panel.COLOR
                    } else if (elegidaCapa.clip.esTexto) Panel.TEXTO else Panel.ENCUADRE
                }, modifier = Modifier.semantics { contentDescription = "Editar capa" })
            }
            if (visuales.isEmpty()) Nota("Su destino lo decides en «Afecta a»; su fila no limita el alcance.")
        }
    }
    if (estado.proyecto.principal.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Etiqueta("Pista principal · detrás de las superposiciones")
        Nota("Para poner un bloque principal delante de otro elemento, conviértelo en capa.")
        if (estado.enPrincipal) BotonBorde("Convertir el elegido en capa", {
            val id = estado.idElegido ?: return@BotonBorde
            estado.hacer { Edicion.aCapa(it, id) }
        })
    }
}
