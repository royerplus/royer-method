package com.royermethod.app.editor.motor

import android.content.Context
import com.royermethod.app.editor.modelo.Origen
import java.io.File

/**
 * En el telefono, copias pequenas de los videos para que la vista previa
 * vaya fluida. El PC decodifica el original sobrado, asi que aqui no se hace
 * ninguna: la vista previa lee siempre el archivo de verdad.
 */
object Ligeras {
    class Copia(val archivo: File, val corrimientoUs: Long)

    var ocupado: () -> Boolean = { false }
    var alListo: ((String) -> Unit)? = null

    fun de(uri: String): Copia? = null
    fun descartar(uri: String) {}
    fun pedir(context: Context, origenes: Collection<Origen>) {}
}
