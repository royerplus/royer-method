package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Origen

/** Lo que se le dice al usuario despues de cambiar el archivo [viejo] por otro. */
internal fun avisoReemplazo(r: Edicion.Reemplazo, viejo: Origen): String {
    val video = !viejo.esImagen
    return when (r) {
        is Edicion.Reemplazo.Hecho -> {
            val que = if (video) "Video cambiado" else "Foto cambiada"
            val donde = if (r.usos > 1) " en ${r.usos} bloques" else ""
            val final = if (r.recortados > 0) "; el nuevo dura un poco menos y se acorto el final" else ""
            "$que$donde$final. Puedes deshacer"
        }
        is Edicion.Reemplazo.Corto ->
            "El video nuevo dura %.2f s y la edicion usa hasta %.2f s del anterior: no se cambio nada"
                .format(r.duracionUs / 1e6, r.usadoUs / 1e6)
        Edicion.Reemplazo.OtroTipo -> if (video) "Elige un video: este bloque es un video" else "Elige una foto: este bloque es una foto"
        Edicion.Reemplazo.Mismo -> "Es el mismo archivo"
        Edicion.Reemplazo.SinUso -> "No se pudo cambiar el archivo"
    }
}
