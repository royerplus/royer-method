package com.royermethod.app.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.royermethod.app.editor.modelo.AjustesSalida
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Proyecto

/**
 * Preferencias del editor: con que empieza un proyecto nuevo, cuanto duran
 * por defecto las transiciones y animaciones, y las animaciones que el
 * usuario ha combinado y guardado. Se guardan con Preferencias.
 */
object AjustesEditor {
    var formato by mutableStateOf(Formato.VERTICAL)
    var fondo by mutableStateOf(Fondo.DESENFOCADO)
    var salida by mutableStateOf(AjustesSalida())
    /** Lo que dura una transicion recien puesta. */
    var duracionTransicionUs by mutableLongStateOf(400_000L)
    /** Lo que dura una animacion de entrada o salida recien puesta. */
    var duracionAnimacionUs by mutableLongStateOf(400_000L)
    /** Que al mover o recortar los bloques se peguen al cabezal y a los cortes. */
    var imanes by mutableStateOf(true)
    /** Animaciones combinadas por el usuario. */
    var mias by mutableStateOf<List<Combinaciones.Combinacion>>(emptyList())
    var continuasMias by mutableStateOf<List<Pair<String, List<Continuo>>>>(emptyList())

    var alCambiar: () -> Unit = {}

    /** "[base] 1", "[base] 2"... el primero que no este usado. */
    fun nombreNuevo(base: String, usados: List<String>): String =
        generateSequence(1) { it + 1 }.map { "$base $it" }.first { it !in usados }

    fun proyectoNuevo(): Proyecto = Proyecto(formato = formato, fondo = fondo, salida = salida)
}
