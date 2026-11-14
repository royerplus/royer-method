package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royermethod.app.ui.components.BotonAccion
import com.royermethod.app.ui.components.Glifos

/**
 * Lo del proyecto entero, junto en una hoja corta: abrir otro, guardar este,
 * agregar los ejemplos de movimiento y borrar todo. Antes eran cuatro
 * herramientas sueltas al final de la fila, donde casi nadie llegaba.
 */
@Composable
internal fun HojaProyecto(
    estado: EstadoEditor,
    onCerrar: () -> Unit,
    onAbrir: () -> Unit,
    onGuardar: () -> Unit,
    onEjemplos: () -> Unit
) {
    var preguntarBorrar by remember { mutableStateOf(false) }
    // Para empezar otro: pregunta antes, y se puede deshacer
    if (preguntarBorrar) DialogoBorrarTodo(
        onCancelar = { preguntarBorrar = false },
        onBorrar = { preguntarBorrar = false; borrarTodo(estado) }
    )
    val vacio = estado.proyecto == proyectoVacio(estado.proyecto)
    HojaPanel(Panel.PROYECTO.titulo, onCerrar, corta = true) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // La hoja se cierra antes de salir a elegir archivo: al volver se ve el proyecto
            BotonAccion(Glifos.carpeta, "Abrir", { estado.panel = null; onAbrir() }, Modifier.weight(1f))
            BotonAccion(Glifos.guardar, "Guardar", { estado.panel = null; onGuardar() }, Modifier.weight(1f))
            // Bloques hechos con las herramientas de movimiento, para verlas y cambiarlas
            BotonAccion(Glifos.animacion, "Ejemplos", { estado.panel = null; onEjemplos() }, Modifier.weight(1f))
            BotonAccion(Glifos.papelera, "Borrar todo", { preguntarBorrar = true }, Modifier.weight(1f), activo = !vacio, peligro = true)
        }
    }
}
