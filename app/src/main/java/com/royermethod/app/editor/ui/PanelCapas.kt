package com.royermethod.app.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip("Todas", c.afectaA == null, { estado.cambiarClip { it.copy(afectaA = null) } })
        Chip("Elegir", c.afectaA != null, {
            estado.cambiarClip { it.copy(afectaA = it.afectaA ?: emptySet()) }
        })
    }
    if (c.afectaA == null) {
        Nota("Incluye fotos, videos y textos que coincidan con este tramo, también los que agregues después.")
    } else {
        val p = estado.proyecto
        val candidatos = p.capas.filterNot { it.clip.esAjuste }.sortedByDescending { it.fila }.map { it.clip } + p.principal
        if (candidatos.isEmpty()) Nota("Agrega una foto, un video o un texto para elegir su capa.")
        candidatos.forEach { destino ->
            val capa = p.capas.firstOrNull { it.id == destino.id }
            val zona = if (capa == null) "Principal" else "Capa ${capa.fila + 1}"
            Interruptor("$zona · ${destino.nombre()}", destino.id in c.afectaA, { activo ->
                estado.cambiarClip { actual ->
                    val ids = actual.afectaA.orEmpty()
                    actual.copy(afectaA = if (activo) ids + destino.id else ids - destino.id)
                }
            }, "${TiempoPreciso.texto(Edicion.inicio(p, destino.id) ?: 0)} · ${TiempoPreciso.texto(destino.duracionUs)}")
        }
        if (c.afectaA.isEmpty()) Nota("Este tramo no afecta a nada hasta que selecciones un elemento.")
    }
    Spacer(Modifier.height(8.dp))
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
