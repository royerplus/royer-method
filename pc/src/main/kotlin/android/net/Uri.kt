package android.net

import java.io.File
import java.net.URI

/**
 * Una direccion de archivo como las del telefono. En el PC los origenes del
 * proyecto son "file:/C:/..." (ver [fromFile]); [archivo] da el archivo.
 */
class Uri private constructor(private val texto: String) {
    private val uri: URI? = runCatching { URI(texto) }.getOrNull()

    val scheme: String? get() = uri?.scheme
    val path: String? get() = uri?.path ?: texto
    val lastPathSegment: String? get() = (path ?: texto).trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\').ifEmpty { null }

    /** El archivo al que apunta: file:, o una ruta tal cual. */
    fun archivo(): File = when {
        uri != null && uri.scheme.equals("file", true) -> File(uri)
        else -> File(texto)
    }

    override fun toString() = texto
    override fun equals(other: Any?) = other is Uri && other.texto == texto
    override fun hashCode() = texto.hashCode()

    companion object {
        fun parse(s: String): Uri = Uri(s)
        fun fromFile(f: File): Uri = Uri(f.absoluteFile.toURI().toString())
    }
}
