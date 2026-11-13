package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PaqueteProyectoTest {
    @Test fun conservaMontajeYArchivosCompartidos() {
        val o = Origen("content://fotos/1", "foto.jpg", TipoOrigen.IMAGEN, 1080, 1920)
        val c = Clip(1, origen = o, efectos = listOf(EfectoVideo(TipoEfectoVideo.RGB, finUs = 500_000)))
        val p = Proyecto(formato = Formato.RETRATO_3_4, principal = listOf(c), capas = listOf(Capa(300_000, 0, c.copy(id = 2))), siguienteId = 3)
        val bytes = byteArrayOf(1, 2, 3, 4)
        val out = ByteArrayOutputStream()
        var lecturas = 0
        PaqueteProyecto.escribir(p, out) { lecturas++; ByteArrayInputStream(bytes) }
        assertEquals(1, lecturas)
        val dir = Files.createTempDirectory("royer-paquete-").toFile()
        try {
            val q = PaqueteProyecto.leer(ByteArrayInputStream(out.toByteArray()), dir)
            assertEquals(p.formato, q.formato)
            assertEquals(c.efectos, q.principal.single().efectos)
            assertEquals(q.principal.single().origen, q.capas.single().clip.origen)
            assertArrayEquals(bytes, java.io.File(java.net.URI(q.principal.single().origen!!.uri)).readBytes())
            assertEquals(3L, q.siguienteId)
        } finally { dir.deleteRecursively() }
    }

    @Test fun rechazaRutasFueraDelPaquete() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("../fuera.txt")); z.write(byteArrayOf(1)); z.closeEntry() }
        val dir = Files.createTempDirectory("royer-paquete-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { PaqueteProyecto.leer(ByteArrayInputStream(out.toByteArray()), dir) }
            assertFalse(java.io.File(dir, "../fuera.txt").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test fun rechazaArchivosFaltantes() {
        val p = Proyecto(principal = listOf(Clip(1, origen = Origen("medios/00000.jpg", "foto.jpg", TipoOrigen.IMAGEN))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("proyecto.json")); z.write(Persistencia.aTexto(p).toByteArray()); z.closeEntry() }
        val dir = Files.createTempDirectory("royer-paquete-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { PaqueteProyecto.leer(ByteArrayInputStream(out.toByteArray()), dir) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun rechazaMediosVaciosAunqueElZipYElManifiestoSeanValidos() {
        val p = Proyecto(principal = listOf(Clip(1, origen = Origen("medios/00000.jpg", "foto.jpg", TipoOrigen.IMAGEN))))
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("proyecto.json"))
            z.write(Persistencia.aTexto(p).toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("medios/00000.jpg"))
            z.closeEntry()
        }
        val dir = Files.createTempDirectory("royer-paquete-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) { PaqueteProyecto.leer(ByteArrayInputStream(out.toByteArray()), dir) }
        } finally { dir.deleteRecursively() }
    }

    @Test fun guardarUnaFuenteVaciaTambienFalla() {
        val p = Proyecto(principal = listOf(Clip(1, origen = Origen("content://fotos/1", "foto.jpg", TipoOrigen.IMAGEN))))
        assertThrows(IllegalArgumentException::class.java) {
            PaqueteProyecto.escribir(p, ByteArrayOutputStream()) { ByteArrayInputStream(byteArrayOf()) }
        }
    }
}
