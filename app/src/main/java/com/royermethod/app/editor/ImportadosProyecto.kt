package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.Proyecto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI

/** Carpetas descartables al abrir el editor, antes de que haya historial de deshacer. */
internal class ImportadosProyecto private constructor(
    private val raiz: File,
    private val candidatas: List<File>
) {
    fun limpiar() {
        candidatas.forEach { carpeta ->
            runCatching {
                // No seguir enlaces ni salir de la raiz privada, tampoco si cambio desde el snapshot.
                if (carpeta.canonicalFile == carpeta && carpeta.parentFile == raiz) {
                    carpeta.deleteRecursively()
                }
            }
        }
    }

    companion object {
        /**
         * Captura solo las carpetas que YA existen. Una importacion posterior y los
         * proyectos que pasan a deshacer no se vuelven a considerar para limpieza.
         * Sin un proyecto guardado legible se conserva todo para poder recuperarlo.
         */
        fun alIniciar(raiz: File, guardado: Proyecto?): ImportadosProyecto {
            val base = runCatching { raiz.canonicalFile }.getOrElse {
                return ImportadosProyecto(raiz.absoluteFile, emptyList())
            }
            val candidatas = if (guardado == null) emptyList() else runCatching {
                val origenes = guardado.principal.mapNotNull { it.origen } +
                    guardado.capas.mapNotNull { it.clip.origen } + guardado.audios.map { it.origen }
                val usados = origenes.mapNotNull { o ->
                    // Una URI local ilegible cancela la limpieza, por prudencia con el guardado.
                    if (!o.uri.startsWith("file:", ignoreCase = true)) null
                    else File(URI(o.uri)).canonicalFile.toPath()
                }
                base.listFiles().orEmpty().filter { f ->
                    val normal = File(base, f.name)
                    f.isDirectory && f.canonicalFile == normal &&
                        usados.none { it.startsWith(normal.toPath()) }
                }.map { File(base, it.name) }
            }.getOrDefault(emptyList())
            return ImportadosProyecto(base, candidatas)
        }
    }
}

/**
 * Incluye la vuelta de IO al llamador: withContext puede cancelar despues de
 * terminar la copia. La limpieza debe poder ejecutarse incluso en ese caso.
 */
internal suspend fun <T> limpiarSiFallaProyecto(limpiar: () -> Unit, operacion: suspend () -> T): T =
    try {
        operacion()
    } catch (fallo: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) { runCatching(limpiar) }
        throw fallo
    }
