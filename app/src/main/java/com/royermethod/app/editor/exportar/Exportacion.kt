package com.royermethod.app.editor.exportar

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.procesado.Procesador

/**
 * La exportacion en curso, fuera de la pantalla.
 *
 * Como el procesado de la app de mejora: la hace un servicio en primer plano
 * y la pantalla solo mira. Salir de la app a mitad no la tira.
 */
object Exportacion {

    var corriendo by mutableStateOf(false)
        internal set
    var hechos by mutableLongStateOf(0L)
        internal set
    var total by mutableLongStateOf(0L)
        internal set
    var arranqueMs by mutableLongStateOf(0L)
        internal set
    var informe by mutableStateOf<Informe?>(null)
        internal set
    /** Donde quedo en la galeria. */
    var guardado by mutableStateOf<Uri?>(null)
        internal set
    var fallo by mutableStateOf<String?>(null)
        internal set

    @Volatile
    var cancelado = false
        private set

    @Volatile
    internal var proyecto: Proyecto? = null

    val fraccion: Float get() = if (total > 0) hechos.toFloat() / total else 0f

    /**
     * Arranca. No hace nada si ya hay una en marcha o si la app de mejora esta
     * trabajando: las dos usan la GPU y el modelo a la vez y saldrian las dos
     * a medias.
     *
     * @return false si no se pudo empezar
     */
    fun empezar(context: Context, p: Proyecto): Boolean {
        if (corriendo || Procesador.corriendo) return false
        proyecto = p
        cancelado = false
        hechos = 0
        total = 0
        informe = null
        guardado = null
        fallo = null
        arranqueMs = System.currentTimeMillis()
        corriendo = true
        ExportacionService.arrancar(context)
        return true
    }

    /** Lo cumple el servicio en el siguiente fotograma. */
    fun cancelar() {
        cancelado = true
    }

    /** Olvida el resultado anterior. */
    fun limpiar() {
        if (corriendo) return
        informe = null
        guardado = null
        fallo = null
        hechos = 0
        total = 0
        proyecto = null
    }
}
