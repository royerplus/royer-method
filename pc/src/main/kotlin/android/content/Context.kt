package android.content

import org.json.JSONObject
import java.io.File

/**
 * Lo poco del Context del telefono que usa el codigo compartido: la carpeta
 * de la app y las preferencias. En el PC hay uno solo, [Context.app].
 */
open class Context(val filesDir: File) {
    val applicationContext: Context get() = this
    val cacheDir: File get() = File(filesDir, "cache").apply { mkdirs() }

    fun getSharedPreferences(nombre: String, modo: Int): SharedPreferences =
        synchronized(preferencias) { preferencias.getOrPut(nombre) { Preferencias(File(filesDir, "$nombre.json")) } }

    private val preferencias = HashMap<String, SharedPreferences>()

    companion object {
        const val MODE_PRIVATE = 0

        /** %APPDATA%\RoyerMethod en Windows, ~/.royermethod en otros. */
        val app: Context by lazy {
            val base = System.getenv("APPDATA")?.let { File(it, "RoyerMethod") }
                ?: File(System.getProperty("user.home"), ".royermethod")
            base.mkdirs()
            Context(base)
        }
    }
}

interface SharedPreferences {
    fun getString(clave: String, porDefecto: String?): String?
    fun getInt(clave: String, porDefecto: Int): Int
    fun getBoolean(clave: String, porDefecto: Boolean): Boolean
    fun edit(): Editor

    interface Editor {
        fun putString(clave: String, valor: String?): Editor
        fun putInt(clave: String, valor: Int): Editor
        fun putBoolean(clave: String, valor: Boolean): Editor
        fun remove(clave: String): Editor
        fun apply()
        fun commit(): Boolean
    }
}

/** Preferencias en un archivo JSON; se escribe entero en cada apply. */
private class Preferencias(private val archivo: File) : SharedPreferences {
    private val datos: JSONObject = runCatching { JSONObject(archivo.readText()) }.getOrElse { JSONObject() }

    override fun getString(clave: String, porDefecto: String?): String? = synchronized(this) {
        if (datos.has(clave)) datos.optString(clave) else porDefecto
    }
    override fun getInt(clave: String, porDefecto: Int): Int = synchronized(this) { datos.optInt(clave, porDefecto) }
    override fun getBoolean(clave: String, porDefecto: Boolean): Boolean = synchronized(this) { datos.optBoolean(clave, porDefecto) }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val cambios = LinkedHashMap<String, Any?>()
        override fun putString(clave: String, valor: String?) = apply { cambios[clave] = valor }
        override fun putInt(clave: String, valor: Int) = apply { cambios[clave] = valor }
        override fun putBoolean(clave: String, valor: Boolean) = apply { cambios[clave] = valor }
        override fun remove(clave: String) = apply { cambios[clave] = null }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(this@Preferencias) {
            cambios.forEach { (k, v) -> if (v == null) datos.remove(k) else datos.put(k, v) }
            runCatching {
                archivo.parentFile?.mkdirs()
                val tmp = File(archivo.parentFile, archivo.name + ".tmp")
                tmp.writeText(datos.toString())
                if (!tmp.renameTo(archivo)) { archivo.delete(); tmp.renameTo(archivo) }
            }.isSuccess
        }
    }
}
