package com.royermethod.pc

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import com.royermethod.app.editor.ImportadosProyecto
import com.royermethod.app.editor.PaqueteProyecto
import com.royermethod.app.editor.Persistencia
import com.royermethod.app.editor.limpiarSiFallaProyecto
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.EjemplosMovimiento
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.motor.MaquetaTexto
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.app.editor.ui.EstadoEditor
import com.royermethod.app.editor.ui.EstadoExportar
import com.royermethod.app.editor.ui.PantallaEditor
import com.royermethod.app.editor.ui.ResultadoExportar
import com.royermethod.app.editor.ui.Seleccion
import com.royermethod.app.editor.ui.avisoReemplazo
import com.royermethod.pc.exportar.Exportacion
import com.royermethod.pc.medios.Importar
import com.royermethod.pc.medios.RecursosPc
import com.royermethod.pc.vista.Previsualizador
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BasicStroke
import java.awt.GradientPaint
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.UUID
import javax.imageio.ImageIO

/**
 * El editor en el PC: la misma pantalla que en el telefono (PantallaEditor),
 * unida a la vista previa, los dialogos de archivos de Windows, el guardado y
 * la exportacion del PC.
 */
@Composable
fun EditorPc(estado: EstadoEditor, dialogos: Dialogos, onVolver: () -> Unit) {
    val context = Context.app
    val alcance = rememberCoroutineScope()
    val prev = remember { Previsualizador(context) }
    val recursos = remember { RecursosPc(context) }
    var imagen by remember { mutableStateOf<ImageBitmap?>(null) }
    var operandoProyecto by remember { mutableStateOf(false) }
    var haciendoEjemplos by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        snapshotFlow { estado.zoomVista }.collect { prev.zoom(it) }
    }

    DisposableEffect(Unit) {
        estado.alCambiar = { prev.proyecto(it) }
        estado.alBuscar = { prev.irA(it) }
        estado.alPausar = { prev.pausar() }
        prev.alImagen = { imagen = it }
        prev.alTiempo = { t -> javax.swing.SwingUtilities.invokeLater {
            if (!prev.reproduciendo || (estado.reproduciendo && !estado.siguiendoReloj)) estado.tiempo = t
        } }
        estado.instanteSonando = { prev.instanteEn(it) }
        prev.alReproducir = { r -> javax.swing.SwingUtilities.invokeLater { estado.reproduciendo = r } }
        prev.proyecto(estado.proyecto)
        prev.irA(estado.tiempo)
        onDispose {
            prev.pausar()
            Proyectos.guardar(estado.proyecto)
            prev.cerrar()
            recursos.cerrar()
        }
    }

    // Guardado automatico un momento despues de cada cambio
    LaunchedEffect(estado.proyecto) {
        delay(600)
        val p = estado.proyecto
        withContext(Dispatchers.IO) { Proyectos.guardar(p) }
    }

    LaunchedEffect(estado.reproduciendo) {
        if (estado.reproduciendo != prev.reproduciendo) {
            if (estado.reproduciendo) prev.reproducir() else prev.pausar()
        }
    }

    fun importar(archivos: List<File>, meter: (Proyecto, List<Origen>) -> Proyecto) {
        if (archivos.isEmpty()) return
        alcance.launch {
            val origenes = withContext(Dispatchers.IO) { archivos.mapNotNull { Importar.origen(it) } }
            if (origenes.size < archivos.size) {
                estado.avisar(if (origenes.isEmpty()) "No se pudo abrir el archivo" else "Algunos archivos no se pudieron abrir")
            }
            if (origenes.isNotEmpty()) estado.hacer { meter(it, origenes) }
        }
    }

    PantallaEditor(
        estado = estado,
        recursos = recursos,
        medidor = MaquetaTexto.medidor,
        exportar = estadoExportar(),
        onVolver = onVolver,
        onAgregarPrincipal = {
            val t = if (estado.proyecto.principal.isEmpty()) Long.MAX_VALUE else estado.tiempo
            importar(dialogos.abrir("Agregar videos o fotos", Dialogos.VISUALES, varios = true)) { p, o -> Edicion.agregarPrincipal(p, o, t).first }
        },
        onAgregarCapa = {
            val t = estado.tiempo
            importar(dialogos.abrir("Agregar una capa", Dialogos.VISUALES)) { p, o ->
                val (q, id) = Edicion.agregarSuperpuesto(p, o.first(), t)
                estado.elegir(Seleccion.Bloque(id))
                q
            }
        },
        onAgregarSonido = {
            val t = estado.tiempo
            importar(dialogos.abrir("Agregar un sonido", Dialogos.SONIDOS)) { p, o -> Edicion.agregarAudio(p, o.first(), t).first }
        },
        onExportar = {
            if (estado.reproduciendo) prev.pausar()
            if (!Exportacion.empezar(context, estado.proyecto)) estado.avisar("Espera a que termine lo que se esta exportando")
        },
        onCancelarExportar = { Exportacion.cancelar() },
        onCompartir = { Exportacion.informe?.archivo?.let { mostrarEnCarpeta(it) } },
        onCerrarExportar = { Exportacion.limpiar() },
        onAbrirProyecto = {
            val f = dialogos.abrir("Abrir proyecto", Dialogos.PROYECTOS).firstOrNull()
            if (f != null && !operandoProyecto) alcance.launch {
                operandoProyecto = true
                prev.pausar()
                estado.avisar("Abriendo proyecto...")
                val carpeta = File(context.filesDir, "editor/importados/${UUID.randomUUID()}")
                try {
                    val p = limpiarSiFallaProyecto(limpiar = { carpeta.deleteRecursively() }) {
                        withContext(Dispatchers.IO) { f.inputStream().use { PaqueteProyecto.leer(it, carpeta) } }
                    }
                    estado.elegir(null)
                    estado.panel = null
                    estado.hacer { p }
                    estado.irA(0)
                    estado.avisar("Proyecto abierto. Puedes deshacer para volver al anterior")
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    estado.avisar(e.message ?: "No se pudo abrir el proyecto")
                } finally {
                    operandoProyecto = false
                }
            }
        },
        onGuardarProyecto = {
            val f = dialogos.guardar("Guardar proyecto", "Proyecto-Royer.royer")
            val proyecto = estado.proyecto
            if (f != null && !operandoProyecto) alcance.launch {
                operandoProyecto = true
                estado.avisar("Guardando proyecto con sus archivos...")
                try {
                    limpiarSiFallaProyecto(limpiar = { f.delete() }) {
                        withContext(Dispatchers.IO) {
                            f.outputStream().use { s ->
                                PaqueteProyecto.escribir(proyecto, s) { o -> runCatching { android.net.Uri.parse(o.uri).archivo().inputStream() }.getOrNull() }
                            }
                        }
                    }
                    estado.avisar("Proyecto guardado con sus fotos, videos y sonidos")
                } catch (c: CancellationException) {
                    throw c
                } catch (e: Exception) {
                    estado.avisar(e.message ?: "No se pudo guardar el proyecto")
                } finally {
                    operandoProyecto = false
                }
            }
        },
        onReemplazar = { viejo ->
            val f = dialogos.abrir("Reemplazar archivo", if (viejo.esImagen) Dialogos.VISUALES else Dialogos.VISUALES).firstOrNull()
            if (f != null) alcance.launch {
                val nuevo = withContext(Dispatchers.IO) { Importar.origen(f) }
                if (nuevo == null) estado.avisar("No se pudo abrir el archivo")
                else {
                    val r = Edicion.reemplazar(estado.proyecto, viejo.uri, nuevo)
                    if (r is Edicion.Reemplazo.Hecho) estado.hacer { r.proyecto }
                    estado.avisar(avisoReemplazo(r, viejo))
                }
            }
        },
        onEjemplos = {
            if (!haciendoEjemplos) alcance.launch {
                haciendoEjemplos = true
                try {
                    val (a, b) = withContext(Dispatchers.IO) {
                        imagenEjemplo(context, "A", 0xFFE0435A.toInt(), 0xFFF2A33A.toInt()) to
                            imagenEjemplo(context, "B", 0xFF2D6CDF.toInt(), 0xFF19B3A6.toInt())
                    }
                    if (a == null) estado.avisar("No se pudieron crear los ejemplos") else cargarEjemplos(estado, a, b)
                } finally {
                    haciendoEjemplos = false
                }
            }
        }
    ) { modificador ->
        Box(modificador.background(Color.Black).onSizeChanged { prev.cambiarTamano(it.width, it.height) }) {
            imagen?.let {
                Image(it, null, contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None, modifier = Modifier.matchParentSize())
            }
        }
    }
}

@Composable
private fun estadoExportar(): EstadoExportar {
    val f = Exportacion.fraccion
    val restante = if (Exportacion.corriendo && f > 0.02f) {
        val pasado = System.currentTimeMillis() - Exportacion.arranqueMs
        (pasado * (1f - f) / f).toLong()
    } else null
    return EstadoExportar(
        corriendo = Exportacion.corriendo,
        fraccion = f,
        restanteMs = restante,
        resultado = Exportacion.informe?.let {
            ResultadoExportar(it.bytes, it.ancho, it.alto, it.codec + "  ·  " + it.archivo.name, it.verificacion.resumen(), it.verificacion.perfecta, it.avisos)
        },
        fallo = Exportacion.fallo
    )
}

/** Abre el Explorador con el video elegido. */
private fun mostrarEnCarpeta(f: File) {
    runCatching {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            ProcessBuilder("explorer.exe", "/select,", f.absolutePath).start()
        } else java.awt.Desktop.getDesktop().open(f.parentFile)
    }
}

/** Esc en el editor, de dentro afuera, como el boton atras del telefono. */
fun atrasEditor(estado: EstadoEditor, onVolver: () -> Unit) {
    when {
        estado.panel != null && estado.atras() -> Unit
        estado.panel != null -> estado.panel = null
        estado.seleccion != null -> estado.elegir(null)
        else -> onVolver()
    }
}

internal fun cargarEjemplos(estado: EstadoEditor, a: Origen, b: Origen?) {
    var inicio = 0L
    estado.hacer { p -> EjemplosMovimiento.agregar(p, a, b).also { inicio = it.inicioUs }.proyecto }
    estado.elegir(null)
    estado.pausarEn(inicio)
    estado.avisar(
        if (AjustesEditor.ajustarFotogramas) "Ejemplos agregados (Deshacer los quita). Las claves a 5 ms del zoom se mueven con Ajustar a fotogramas apagado"
        else "Ejemplos agregados. Deshacer para quitarlos"
    )
}

/** La misma foto de ejemplo que pinta el telefono, aqui con AWT. */
private fun imagenEjemplo(context: Context, letra: String, arriba: Int, abajo: Int): Origen? = runCatching {
    val w = 1080
    val h = 1350
    val carpeta = File(context.filesDir, "editor/ejemplos").apply { mkdirs() }
    val f = File(carpeta, "ejemplo-${letra.lowercase()}-1.png")
    if (!f.isFile || f.length() == 0L) {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.paint = GradientPaint(0f, 0f, java.awt.Color(arriba, true), 0f, h.toFloat(), java.awt.Color(abajo, true))
        g.fillRect(0, 0, w, h)
        g.color = java.awt.Color(255, 255, 255, 0x59)
        g.stroke = BasicStroke(3f)
        for (x in 0..w step 90) g.drawLine(x, 0, x, h)
        for (y in 0..h step 90) g.drawLine(0, y, w, y)
        g.color = java.awt.Color(255, 255, 255, 0xB3)
        g.stroke = BasicStroke(8f)
        g.drawLine(w / 2, 0, w / 2, h)
        g.drawLine(0, h / 2, w, h / 2)
        val r = (w * 0.12f).toInt()
        g.drawOval(w / 2 - r, h / 2 - r, r * 2, r * 2)
        g.font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, (h * 0.5f).toInt())
        val fm = g.fontMetrics
        val x = (w - fm.stringWidth(letra)) / 2
        val y = h / 2 + (fm.ascent - fm.descent) / 2
        g.color = java.awt.Color(0, 0, 0, 0x60)
        g.drawString(letra, x, y + 6)
        g.color = java.awt.Color.WHITE
        g.drawString(letra, x, y)
        g.color = java.awt.Color(0xFFFFD84A.toInt(), true)
        g.fillRect(0, 0, (w * 0.15f).toInt(), (h * 0.08f).toInt())
        g.dispose()
        val tmp = File(carpeta, f.name + ".tmp")
        check(ImageIO.write(img, "png", tmp)) { "no se pudo escribir" }
        if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) { "no se pudo guardar" } }
    }
    Origen(f.toURI().toString(), "Ejemplo $letra.png", TipoOrigen.IMAGEN, w, h, bytes = f.length())
}.getOrNull()

/** El proyecto que se guarda solo, en %APPDATA%\RoyerMethod\editor. */
object Proyectos {
    private val archivo get() = File(Context.app.filesDir, "editor/proyecto.json")
    private val cerrojo = Any()

    fun cargar(): Proyecto? {
        val f = archivo
        if (!f.exists()) return null
        val p = runCatching { f.readText() }.getOrNull()?.let { Persistencia.deTexto(it) }
        if (p == null) runCatching { f.renameTo(File(f.parentFile, "proyecto-ilegible.json")) }
        return p
    }

    internal fun limpiezaImportados(guardado: Proyecto?) =
        ImportadosProyecto.alIniciar(File(Context.app.filesDir, "editor/importados"), guardado)

    fun guardar(p: Proyecto) = synchronized(cerrojo) {
        runCatching {
            val destino = archivo
            destino.parentFile?.mkdirs()
            val tmp = File(destino.parentFile, "proyecto.json.tmp")
            tmp.writeText(Persistencia.aTexto(p))
            if (!tmp.renameTo(destino)) { destino.delete(); tmp.renameTo(destino) }
        }
    }
}
