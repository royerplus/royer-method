package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.Capa
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PistaAudio
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.ui.EstadoEditor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ImportadosProyectoTest {
    private fun medio(raiz: File, nombre: String): File = File(raiz, "$nombre/medios/foto con espacios.jpg").apply {
        parentFile!!.mkdirs()
        writeBytes(byteArrayOf(1, 2, 3))
    }

    private fun origen(f: File) = Origen(f.toURI().toString(), f.name, TipoOrigen.IMAGEN)

    @Test fun conservaMediosDeTodasLasPistasYBorraSoloCarpetasHuerfanas() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val principal = medio(raiz, "principal")
            val capa = medio(raiz, "capa")
            val audio = medio(raiz, "audio")
            val huerfano = medio(raiz, "huerfano")
            val suelto = File(raiz, "conservar.txt").apply { writeText("fuera de una carpeta importada") }
            val p = Proyecto(
                principal = listOf(Clip(1, origen = origen(principal))),
                capas = listOf(Capa(0, 0, Clip(2, origen = origen(capa)))),
                audios = listOf(PistaAudio(3, origen(audio)))
            )
            ImportadosProyecto.alIniciar(raiz, p).limpiar()
            assertTrue(principal.exists())
            assertTrue(capa.exists())
            assertTrue(audio.exists())
            assertFalse(huerfano.parentFile!!.parentFile!!.exists())
            assertTrue(suelto.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun sinProyectoGuardadoLegibleNoBorraMedios() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val f = medio(raiz, "recuperable")
            ImportadosProyecto.alIniciar(raiz, null).limpiar()
            assertTrue(f.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun proyectoGuardadoVacioPermiteLimpiarPeroUriLocalIlegibleNo() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val f = medio(raiz, "recuperable")
            val invalido = Proyecto(principal = listOf(Clip(1, origen =
                Origen("file:///ruta sin codificar/foto.jpg", "foto.jpg", TipoOrigen.IMAGEN))))
            ImportadosProyecto.alIniciar(raiz, invalido).limpiar()
            assertTrue(f.exists())
            ImportadosProyecto.alIniciar(raiz, Proyecto()).limpiar()
            assertFalse(f.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun noBorraUnaImportacionIniciadaDespuesDelSnapshot() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val anterior = medio(raiz, "huerfano")
            val limpieza = ImportadosProyecto.alIniciar(raiz, Proyecto())
            val importando = medio(raiz, "nuevo-uuid")
            limpieza.limpiar()
            assertFalse(anterior.exists())
            assertTrue(importando.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun conservaElProyectoInicialAunqueYaEsteEnDeshacer() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val inicial = medio(raiz, "inicial")
            val p = Proyecto(principal = listOf(Clip(1, origen = origen(inicial))))
            val limpieza = ImportadosProyecto.alIniciar(raiz, p)
            val estado = EstadoEditor(p)
            val nuevo = medio(raiz, "importado-despues")
            estado.hacer { Proyecto(principal = listOf(Clip(2, origen = origen(nuevo)))) }
            limpieza.limpiar()
            estado.deshacer()
            assertEquals(p, estado.proyecto)
            assertTrue(inicial.exists())
            assertTrue(nuevo.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun unaRutaConPrefijoIgualNoConservaLaCarpetaEquivocada() {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        try {
            val usado = medio(raiz, "uno-mas")
            val huerfano = medio(raiz, "uno")
            val p = Proyecto(principal = listOf(Clip(1, origen = origen(usado))))
            ImportadosProyecto.alIniciar(raiz, p).limpiar()
            assertTrue(usado.exists())
            assertFalse(huerfano.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun unaOperacionCompletadaConservaElArchivo() = runBlocking {
        var borrado = false
        val resultado = limpiarSiFallaProyecto(limpiar = { borrado = true }) { 42 }
        assertEquals(42, resultado)
        assertFalse(borrado)
    }

    @Test fun conservaElErrorOriginalAunqueFalleElBorrado() = runBlocking {
        val original = IOException("fuente no disponible")
        var intentos = 0
        try {
            limpiarSiFallaProyecto(limpiar = { intentos++; error("proveedor no permite borrar") }) {
                throw original
            }
            fail("Debia relanzar el fallo")
        } catch (fallo: IOException) {
            assertSame(original, fallo)
        }
        assertEquals(1, intentos)
    }

    @Test fun cancelarAlVolverDeIoBorraLaImportacionYPropagaCancelacion() = runBlocking {
        val raiz = Files.createTempDirectory("royer-importados-").toFile()
        val carpeta = File(raiz, "cancelado")
        var cancelacionPropagada = false
        try {
            val tarea = launch {
                try {
                    limpiarSiFallaProyecto(limpiar = { carpeta.deleteRecursively() }) {
                        withContext(Dispatchers.IO) {
                            medio(raiz, carpeta.name)
                            // El ZIP termino, pero el llamador ya no puede adoptar el proyecto.
                            currentCoroutineContext().cancel(CancellationException("pantalla cerrada"))
                            Proyecto()
                        }
                    }
                    fail("No debe devolver una importacion cancelada")
                } catch (cancelacion: CancellationException) {
                    cancelacionPropagada = true
                    throw cancelacion
                }
            }
            tarea.join()
            assertTrue(tarea.isCancelled)
            assertTrue(cancelacionPropagada)
            assertFalse(carpeta.exists())
        } finally { raiz.deleteRecursively() }
    }

    @Test fun guardarConLaUltimaFuenteFallidaBorraElDocumentoParcial() = runBlocking {
        val raiz = Files.createTempDirectory("royer-guardado-").toFile()
        val documento = File(raiz, "parcial.royer")
        val fuente = Origen("content://fotos/1", "foto.jpg", TipoOrigen.IMAGEN)
        val p = Proyecto(principal = listOf(Clip(1, origen = fuente), Clip(2, origen = fuente.copy(uri = "content://fotos/2"))))
        var borrados = 0
        try {
            try {
                limpiarSiFallaProyecto(limpiar = { borrados++; documento.delete() }) {
                    withContext(Dispatchers.IO) {
                        documento.outputStream().use { salida ->
                            PaqueteProyecto.escribir(p, salida) { o ->
                                if (o.uri == fuente.uri) ByteArrayInputStream(byteArrayOf(1, 2, 3))
                                else throw IOException("ultima fuente inaccesible")
                            }
                        }
                    }
                }
                fail("Debia rechazar la fuente ilegible")
            } catch (_: IOException) { }
            assertEquals(1, borrados)
            assertFalse(documento.exists())
        } finally { raiz.deleteRecursively() }
    }
}
