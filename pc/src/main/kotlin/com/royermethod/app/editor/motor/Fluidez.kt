package com.royermethod.app.editor.motor

import android.content.Context
import com.royermethod.app.editor.modelo.PasoClip

/**
 * El modo fluido (fotogramas generados con RIFE). En el PC todavia no esta:
 * [fotograma] devuelve null y el compositor sigue con los fotogramas del
 * archivo, como en un telefono sin el modelo.
 */
class Fluidez(context: Context) {
    fun fotograma(fuente: FuenteVideo, paso: PasoClip, ancho: Int, alto: Int, copia: Reduccion): Lamina? = null
    fun olvidar(fuente: FuenteVideo) {}
    fun soltar() {}
}
