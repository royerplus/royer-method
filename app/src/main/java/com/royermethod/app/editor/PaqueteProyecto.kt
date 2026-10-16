package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Un proyecto transportable: conserva el montaje y los archivos que utiliza. */
object PaqueteProyecto {
    const val MIME = "application/zip"
    private const val MANIFIESTO = "proyecto.json"
    private const val MAX_ENTRADAS = 4096
    private const val MAX_JSON = 16 * 1024 * 1024L
    private const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    private val MEDIO = Regex("medios/[0-9]{5}\\.[a-z0-9]{1,8}")

    fun escribir(proyecto: Proyecto, salida: OutputStream, abrir: (Origen) -> InputStream?) {
        val origenes = origenes(proyecto).distinctBy { it.uri }
        require(origenes.size < MAX_ENTRADAS) { "Demasiados archivos en el proyecto" }
        val rutas = origenes.mapIndexed { i, o ->
            val extension = o.nombre.substringAfterLast('.', "bin").lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) } ?: "bin"
            o.uri to String.format(Locale.ROOT, "medios/%05d.%s", i, extension)
        }.toMap()
        val portable = transformar(proyecto) { it.copy(uri = rutas.getValue(it.uri)) }
        ZipOutputStream(salida.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFIESTO))
            zip.write(Persistencia.aTexto(portable).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (origen in origenes) {
                zip.putNextEntry(ZipEntry(rutas.getValue(origen.uri)))
                val entrada = abrir(origen) ?: error("No se puede leer ${origen.nombre}")
                val bytes = entrada.use { it.copyTo(zip) }
                require(bytes > 0) { "El archivo ${origen.nombre} esta vacio" }
                zip.closeEntry()
            }
        }
    }

    /** [carpeta] debe ser una carpeta nueva, privada de la app. Nunca sobrescribe. */
    fun leer(entrada: InputStream, carpeta: File): Proyecto {
        require(!carpeta.exists() || carpeta.listFiles().isNullOrEmpty()) { "La carpeta de destino no esta vacia" }
        require(carpeta.mkdirs() || carpeta.isDirectory) { "No se puede crear la carpeta del proyecto" }
        var json: String? = null
        val nombres = HashSet<String>()
        var total = 0L
        ZipInputStream(entrada.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                require(nombres.size < MAX_ENTRADAS && nombres.add(e.name)) { "Paquete con archivos duplicados o demasiados archivos" }
                require(!e.isDirectory && (e.name == MANIFIESTO || MEDIO.matches(e.name))) { "Archivo inesperado en el proyecto: ${e.name}" }
                val limite = if (e.name == MANIFIESTO) MAX_JSON else MAX_BYTES
                val destino = File(carpeta, e.name)
                destino.parentFile?.mkdirs()
                var tamano = 0L
                destino.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        tamano += n
                        total += n
                        require(tamano <= limite && total <= MAX_BYTES) { "El proyecto supera el tamano admitido" }
                        out.write(buffer, 0, n)
                    }
                }
                if (e.name == MANIFIESTO) json = destino.readText(Charsets.UTF_8)
                else require(tamano > 0) { "El archivo ${e.name} esta vacio" }
                zip.closeEntry()
            }
        }
        val p = json?.let(Persistencia::deTexto) ?: error("No contiene un proyecto Royer valido")
        return transformar(p) { origen ->
            require(MEDIO.matches(origen.uri) && origen.uri in nombres) { "Falta el archivo ${origen.nombre}" }
            origen.copy(uri = File(carpeta, origen.uri).toURI().toString())
        }
    }

    private fun origenes(p: Proyecto): List<Origen> =
        p.principal.mapNotNull { it.origen } + p.capas.mapNotNull { it.clip.origen } + p.audios.map { it.origen }

    private fun transformar(p: Proyecto, f: (Origen) -> Origen): Proyecto = p.copy(
        principal = p.principal.map { c -> c.copy(origen = c.origen?.let(f)) },
        capas = p.capas.map { k -> k.copy(clip = k.clip.copy(origen = k.clip.origen?.let(f))) },
        audios = p.audios.map { a -> a.copy(origen = f(a.origen)) }
    )
}
