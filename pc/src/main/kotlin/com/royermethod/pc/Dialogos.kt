package com.royermethod.pc

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Los dialogos de abrir y guardar de Windows. */
class Dialogos(private val ventana: () -> Frame?) {
    private var ultimaCarpeta: String? = null

    fun abrir(titulo: String, extensiones: Set<String>, varios: Boolean = false): List<File> {
        val d = FileDialog(ventana(), titulo, FileDialog.LOAD)
        d.isMultipleMode = varios
        ultimaCarpeta?.let { d.directory = it }
        // En Windows el filtro va por patron; el de nombre lo usan los demas sistemas
        d.file = extensiones.joinToString(";") { "*.$it" }
        d.setFilenameFilter { _, n -> n.substringAfterLast('.', "").lowercase() in extensiones }
        d.isVisible = true
        val r = d.files?.toList().orEmpty()
        if (r.isNotEmpty()) ultimaCarpeta = r.first().parent
        return r
    }

    fun guardar(titulo: String, nombre: String): File? {
        val d = FileDialog(ventana(), titulo, FileDialog.SAVE)
        ultimaCarpeta?.let { d.directory = it }
        d.file = nombre
        d.isVisible = true
        val f = d.file ?: return null
        val dir = d.directory ?: return null
        ultimaCarpeta = dir
        val archivo = File(dir, f)
        return if (archivo.extension.isEmpty()) File(dir, "$f.royer") else archivo
    }

    companion object {
        val VISUALES = setOf("mp4", "mov", "mkv", "webm", "avi", "m4v", "3gp", "gif", "jpg", "jpeg", "png", "webp", "bmp")
        val SONIDOS = setOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "wma", "mp4", "mov", "mkv", "webm")
        val PROYECTOS = setOf("royer", "zip")
    }
}
