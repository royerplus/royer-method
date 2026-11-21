package com.royermethod.app.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonLleno
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink

/** El proyecto vacio para empezar otro: se queda con el formato, el fondo y la calidad. */
fun proyectoVacio(p: Proyecto): Proyecto =
    Proyecto(formato = p.formato, fondo = p.fondo, salida = p.salida, siguienteId = p.siguienteId)

/** Quita todo para empezar otro proyecto. Es un paso de deshacer. */
fun borrarTodo(estado: EstadoEditor) {
    estado.pausarEn(0L)
    estado.panel = null
    estado.elegir(null)
    estado.hacer { proyectoVacio(it) }
}

@Composable
fun DialogoBorrarTodo(onCancelar: () -> Unit, onBorrar: () -> Unit) {
    Dialog(onDismissRequest = onCancelar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(esquinas(20.dp))
                .background(Ink.Black)
                .background(Ink.White08)
                .border(1.dp, Ink.White15, esquinas(20.dp))
                .padding(20.dp)
        ) {
            Text("Borrar todo", style = MaterialTheme.typography.titleMedium, color = Ink.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Se quitan todos los videos, fotos, textos y sonidos para empezar otro proyecto. " +
                    "Si quieres conservar este, primero toca Guardar proyecto. " +
                    "Si te equivocas, usa deshacer.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BotonBorde("Cancelar", onCancelar)
                BotonLleno("Borrar todo", onBorrar)
            }
        }
    }
}
