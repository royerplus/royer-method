package com.royermethod.pc

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.royermethod.app.Preferencias
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.app.editor.ui.EstadoEditor
import com.royermethod.app.ui.theme.RoyerMethodTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Royer Method para PC: abre directamente el editor.
 *
 * Atajos: Espacio reproduce o pausa, Ctrl+Z deshace, Ctrl+Y o Ctrl+Shift+Z
 * rehace, Esc es el "atras" del telefono.
 */
fun main() {
    Preferencias.cargar(Context.app)
    application {
        val ventana = rememberWindowState(size = DpSize(520.dp, 940.dp))
        val guardado = remember { Proyectos.cargar() }
        val estado = remember {
            val p = guardado?.takeIf { it.principal.isNotEmpty() || it.capas.isNotEmpty() || it.audios.isNotEmpty() }
            EstadoEditor(p ?: AjustesEditor.proyectoNuevo())
        }
        var frame: java.awt.Frame? = null
        val dialogos = remember { Dialogos { frame } }
        Window(
            onCloseRequest = ::exitApplication,
            state = ventana,
            title = "Royer Method",
            onPreviewKeyEvent = { e ->
                if (e.type != KeyEventType.KeyDown) false
                else when {
                    e.isCtrlPressed && e.key == Key.Z && !e.isShiftPressed -> { estado.deshacer(); true }
                    e.isCtrlPressed && (e.key == Key.Y || e.key == Key.Z) -> { estado.rehacer(); true }
                    e.key == Key.Escape -> { atrasEditor(estado) {}; true }
                    e.key == Key.Spacebar && !escribiendo() -> { estado.reproduciendo = !estado.reproduciendo; true }
                    else -> false
                }
            }
        ) {
            frame = window
            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) { Proyectos.limpiezaImportados(guardado).limpiar() }
            }
            RoyerMethodTheme {
                Box(Modifier.fillMaxSize()) {
                    EditorPc(estado, dialogos, onVolver = {})
                }
            }
        }
    }
}

/** Con el foco en un campo de texto, el espacio es un espacio. */
private fun escribiendo(): Boolean =
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner is javax.swing.text.JTextComponent
