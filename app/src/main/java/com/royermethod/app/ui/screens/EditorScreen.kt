package com.royermethod.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.royermethod.app.editor.Persistencia
import com.royermethod.app.editor.PaqueteProyecto
import com.royermethod.app.editor.ImportadosProyecto
import com.royermethod.app.editor.limpiarSiFallaProyecto
import com.royermethod.app.editor.exportar.Exportacion
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
import com.royermethod.app.editor.vista.Importar
import com.royermethod.app.editor.vista.Previsualizador
import com.royermethod.app.editor.vista.RecursosAndroid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * El editor en el telefono: une la pantalla (que no sabe nada de Android)
 * con la vista previa, los selectores de archivos, el guardado y la
 * exportacion.
 *
 * El proyecto se guarda solo, un momento despues de cada cambio y al salir,
 * asi que cerrar la app a mitad no pierde nada.
 */
@Composable
fun EditorScreen(onVolver: () -> Unit) {
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()

    // Un proyecto guardado vacio no tiene nada que conservar: se empieza con
    // los ajustes de ahora (formato, fondo, salida), por si cambiaron.
    val proyectoGuardado = remember { cargar(context) }
    val limpiezaImportados = remember {
        ImportadosProyecto.alIniciar(File(context.filesDir, "editor/importados"), proyectoGuardado)
    }
    val estado = remember {
        val guardado = proyectoGuardado?.takeIf { it.principal.isNotEmpty() || it.capas.isNotEmpty() || it.audios.isNotEmpty() }
        EstadoEditor(guardado ?: AjustesEditor.proyectoNuevo())
    }
    val prev = remember { Previsualizador(context) }
    val recursos = remember { RecursosAndroid(context) }
    var operandoProyecto by remember { mutableStateOf(false) }
    var haciendoEjemplos by remember { mutableStateOf(false) }
    var porGuardar by remember { mutableStateOf<Proyecto?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { limpiezaImportados.limpiar() }
    }

    DisposableEffect(Unit) {
        estado.alCambiar = { prev.proyecto(it) }
        estado.alBuscar = { prev.irA(it) }
        // Pausa en el acto: pausarEn la pide antes de mover el cabezal, asi la
        // busqueda llega con la vista previa ya parada y el cabezal queda exacto
        estado.alPausar = { prev.pausar() }
        prev.alTiempo = { estado.tiempo = it }
        prev.alReproducir = { estado.reproduciendo = it }
        prev.proyecto(estado.proyecto)
        prev.irA(estado.tiempo)
        onDispose {
            prev.pausar()
            guardar(context, estado.proyecto)
            prev.cerrar()
            recursos.cerrar()
        }
    }

    // Guardado: cada cambio reinicia la espera, asi que se escribe una vez
    // cuando se deja de tocar, no en cada paso de un arrastre.
    LaunchedEffect(estado.proyecto) {
        delay(600)
        val p = estado.proyecto
        withContext(Dispatchers.IO) { guardar(context, p) }
    }

    LaunchedEffect(estado.reproduciendo) {
        if (estado.reproduciendo != prev.reproduciendo) {
            if (estado.reproduciendo) prev.reproducir() else prev.pausar()
        }
    }

    /** Lee los archivos fuera del hilo principal y los mete con [meter]. */
    fun importar(uris: List<Uri>, meter: (Proyecto, List<Origen>) -> Proyecto) {
        if (uris.isEmpty()) return
        alcance.launch {
            val origenes = withContext(Dispatchers.IO) { uris.mapNotNull { Importar.origen(context, it) } }
            if (origenes.size < uris.size) {
                estado.avisar(if (origenes.isEmpty()) "No se pudo abrir el archivo" else "Algunos archivos no se pudieron abrir")
            }
            if (origenes.isNotEmpty()) estado.hacer { meter(it, origenes) }
        }
    }

    val elegirPrincipal = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val t = if (estado.proyecto.principal.isEmpty()) Long.MAX_VALUE else estado.tiempo
        importar(uris) { p, o -> Edicion.agregarPrincipal(p, o, t).first }
    }
    val elegirCapa = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = estado.tiempo
        importar(listOfNotNull(uri)) { p, o ->
            val (q, id) = Edicion.agregarSuperpuesto(p, o.first(), t)
            estado.elegir(Seleccion.Bloque(id))
            q
        }
    }
    val elegirSonido = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val t = estado.tiempo
        importar(listOfNotNull(uri)) { p, o -> Edicion.agregarAudio(p, o.first(), t).first }
    }

    val abrirProyecto = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !operandoProyecto) alcance.launch {
            operandoProyecto = true
            prev.pausar()
            estado.avisar("Abriendo proyecto...")
            val carpeta = File(context.filesDir, "editor/importados/${UUID.randomUUID()}")
            try {
                val p = limpiarSiFallaProyecto(limpiar = { carpeta.deleteRecursively() }) {
                    withContext(Dispatchers.IO) {
                        val entrada = context.contentResolver.openInputStream(uri) ?: error("No se puede abrir el archivo")
                        entrada.use { PaqueteProyecto.leer(it, carpeta) }
                    }
                }
                estado.elegir(null)
                estado.panel = null
                estado.hacer { p }
                estado.irA(0)
                estado.avisar("Proyecto abierto. Puedes deshacer para volver al anterior")
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (fallo: Exception) {
                estado.avisar(fallo.message ?: "No se pudo abrir el proyecto")
            } finally {
                operandoProyecto = false
            }
        }
    }
    val guardarProyecto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PaqueteProyecto.MIME)) { uri ->
        val proyecto = porGuardar
        porGuardar = null
        if (uri != null && proyecto != null && !operandoProyecto) alcance.launch {
            operandoProyecto = true
            estado.avisar("Guardando proyecto con sus archivos...")
            try {
                limpiarSiFallaProyecto(limpiar = { DocumentsContract.deleteDocument(context.contentResolver, uri) }) {
                    withContext(Dispatchers.IO) {
                        val salida = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se puede guardar el archivo")
                        salida.use { stream ->
                            PaqueteProyecto.escribir(proyecto, stream) { o -> context.contentResolver.openInputStream(Uri.parse(o.uri)) }
                        }
                    }
                }
                estado.avisar("Proyecto guardado con sus fotos, videos y sonidos")
            } catch (cancelacion: CancellationException) {
                throw cancelacion
            } catch (fallo: Exception) {
                estado.avisar(fallo.message ?: "No se pudo guardar el proyecto")
            } finally {
                operandoProyecto = false
            }
        }
    }

    BackHandler {
        when {
            estado.panel != null -> estado.panel = null
            estado.seleccion != null -> estado.elegir(null)
            else -> onVolver()
        }
    }

    PantallaEditor(
        estado = estado,
        recursos = recursos,
        medidor = MaquetaTexto.medidor,
        exportar = estadoExportar(),
        onVolver = onVolver,
        onAgregarPrincipal = { elegirPrincipal.launch(arrayOf("video/*", "image/*")) },
        onAgregarCapa = { elegirCapa.launch(arrayOf("video/*", "image/*")) },
        onAgregarSonido = { elegirSonido.launch(arrayOf("audio/*", "video/*")) },
        onExportar = {
            if (estado.reproduciendo) prev.pausar()
            if (!Exportacion.empezar(context, estado.proyecto)) {
                estado.avisar("Espera a que termine lo que se esta procesando")
            }
        },
        onCancelarExportar = { Exportacion.cancelar() },
        onCompartir = { compartir(context, Exportacion.guardado) },
        onCerrarExportar = { Exportacion.limpiar() },
        onAbrirProyecto = {
            if (operandoProyecto) estado.avisar("Espera a que termine el archivo actual")
            else abrirProyecto.launch(arrayOf(PaqueteProyecto.MIME, "application/octet-stream", "application/x-zip-compressed"))
        },
        onGuardarProyecto = {
            if (operandoProyecto) estado.avisar("Espera a que termine el archivo actual")
            else {
                porGuardar = estado.proyecto
                guardarProyecto.launch("Proyecto-Royer.royer")
            }
        },
        onEjemplos = {
            // Las fotos se pintan fuera del hilo principal; dos toques seguidos no agregan dos veces
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
        AndroidView(
            factory = { ctx -> TextureView(ctx).apply { surfaceTextureListener = Superficie(prev) } },
            modifier = modificador
        )
    }
}

/** Lo que la pantalla de exportar necesita saber de la exportacion en curso. */
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
            ResultadoExportar(it.bytes, it.ancho, it.alto, it.codec, it.verificacion.resumen(), it.verificacion.perfecta, it.avisos)
        },
        fallo = Exportacion.fallo
    )
}

/** Pasa la superficie de la pantalla a la vista previa y se la quita antes de que se destruya. */
private class Superficie(private val prev: Previsualizador) : TextureView.SurfaceTextureListener {
    private var superficie: Surface? = null

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
        val s = Surface(st)
        superficie = s
        prev.adjuntar(s, w, h)
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = prev.cambiarTamano(w, h)

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        prev.soltarSuperficie()
        superficie?.release()
        superficie = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
}

private fun archivo(context: Context) = File(context.filesDir, "editor/proyecto.json")

/**
 * Mete los ejemplos de movimiento al final de la linea principal (si el
 * proyecto esta vacio, son el proyecto) en un solo paso de deshacer, y deja el
 * cabezal parado al comienzo del primero.
 */
internal fun cargarEjemplos(estado: EstadoEditor, a: Origen, b: Origen?) {
    var inicio = 0L
    estado.hacer { p -> EjemplosMovimiento.agregar(p, a, b).also { inicio = it.inicioUs }.proyecto }
    estado.elegir(null)
    estado.pausarEn(inicio)
    estado.avisar("Ejemplos agregados. Deshacer para quitarlos")
}

/**
 * Una foto para los ejemplos, pintada aqui: degradado de [arriba] a [abajo],
 * rejilla (para ver el movimiento, el giro y el rastro), la cruz del centro y
 * una letra grande partida por la mitad. Se guarda en editor/ejemplos y se
 * reutiliza; el numero del nombre cambia si cambia el dibujo.
 */
private fun imagenEjemplo(context: Context, letra: String, arriba: Int, abajo: Int): Origen? = runCatching {
    val w = 1080
    val h = 1350
    val carpeta = File(context.filesDir, "editor/ejemplos").apply { mkdirs() }
    val f = File(carpeta, "ejemplo-${letra.lowercase()}-1.png")
    if (!f.isFile || f.length() == 0L) {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val c = Canvas(bmp)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), arriba, abajo, Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            p.shader = null
            p.color = 0x59FFFFFF
            p.strokeWidth = 3f
            for (x in 0..w step 90) c.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), p)
            for (y in 0..h step 90) c.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), p)
            p.color = 0xB3FFFFFF.toInt()
            p.strokeWidth = 8f
            c.drawLine(w / 2f, 0f, w / 2f, h.toFloat(), p)
            c.drawLine(0f, h / 2f, w.toFloat(), h / 2f, p)
            p.style = Paint.Style.STROKE
            c.drawCircle(w / 2f, h / 2f, w * 0.12f, p)
            p.style = Paint.Style.FILL
            p.color = 0xFFFFFFFF.toInt()
            p.typeface = Typeface.DEFAULT_BOLD
            p.textAlign = Paint.Align.CENTER
            p.textSize = h * 0.5f
            p.setShadowLayer(18f, 0f, 6f, 0x80000000.toInt())
            c.drawText(letra, w / 2f, h / 2f - (p.descent() + p.ascent()) / 2f, p)
            p.clearShadowLayer()
            // Una esquina marcada: se ve si la foto se gira, se refleja o se sale
            p.color = 0xFFFFD84A.toInt()
            c.drawRect(0f, 0f, w * 0.15f, h * 0.08f, p)
            val tmp = File(carpeta, f.name + ".tmp")
            tmp.outputStream().use { if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, it)) error("no se pudo escribir") }
            if (!tmp.renameTo(f)) {
                f.delete()
                if (!tmp.renameTo(f)) error("no se pudo guardar")
            }
        } finally {
            bmp.recycle()
        }
    }
    Origen(f.toURI().toString(), "Ejemplo $letra.png", TipoOrigen.IMAGEN, w, h, bytes = f.length())
}.getOrNull()

/**
 * El proyecto guardado. Si existe pero no se entiende, se aparta con otro
 * nombre antes de empezar uno nuevo: el guardado automatico lo pisaria.
 */
private fun cargar(context: Context): Proyecto? {
    val f = archivo(context)
    if (!f.exists()) return null
    val p = runCatching { f.readText() }.getOrNull()?.let { Persistencia.deTexto(it) }
    if (p == null) runCatching { f.renameTo(File(f.parentFile, "proyecto-ilegible.json")) }
    return p
}

/** Solo un guardado a la vez: el automatico y el de salir usan el mismo archivo temporal. */
private val cerrojoGuardado = Any()

/** Escribe aparte y luego cambia el nombre: un corte a mitad no deja el proyecto roto. */
private fun guardar(context: Context, p: Proyecto) = synchronized(cerrojoGuardado) {
    runCatching {
        val destino = archivo(context)
        destino.parentFile?.mkdirs()
        val tmp = File(destino.parentFile, "proyecto.json.tmp")
        tmp.writeText(Persistencia.aTexto(p))
        if (!tmp.renameTo(destino)) {
            destino.delete()
            tmp.renameTo(destino)
        }
    }
}

private fun compartir(context: Context, uri: Uri?) {
    uri ?: return
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(envio, "Compartir video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
