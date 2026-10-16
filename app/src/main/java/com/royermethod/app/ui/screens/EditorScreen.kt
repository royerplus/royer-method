package com.royermethod.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.Uri
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
import com.royermethod.app.editor.exportar.Exportacion
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.Proyecto
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
    val estado = remember {
        val guardado = cargar(context)?.takeIf { it.principal.isNotEmpty() || it.capas.isNotEmpty() || it.audios.isNotEmpty() }
        EstadoEditor(guardado ?: AjustesEditor.proyectoNuevo())
    }
    val prev = remember { Previsualizador(context) }
    val recursos = remember { RecursosAndroid(context) }
    var operandoProyecto by remember { mutableStateOf(false) }
    var porGuardar by remember { mutableStateOf<Proyecto?>(null) }

    DisposableEffect(Unit) {
        estado.alCambiar = { prev.proyecto(it) }
        estado.alBuscar = { prev.irA(it) }
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
            val resultado = withContext(Dispatchers.IO) {
                val carpeta = File(context.filesDir, "editor/importados/${UUID.randomUUID()}")
                runCatching {
                    val entrada = context.contentResolver.openInputStream(uri) ?: error("No se puede abrir el archivo")
                    entrada.use { PaqueteProyecto.leer(it, carpeta) }
                }.onFailure { carpeta.deleteRecursively() }
            }
            resultado.onSuccess { p ->
                estado.elegir(null)
                estado.panel = null
                estado.hacer { p }
                estado.irA(0)
                estado.avisar("Proyecto abierto. Puedes deshacer para volver al anterior")
            }.onFailure { estado.avisar(it.message ?: "No se pudo abrir el proyecto") }
            operandoProyecto = false
        }
    }
    val guardarProyecto = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(PaqueteProyecto.MIME)) { uri ->
        val proyecto = porGuardar
        porGuardar = null
        if (uri != null && proyecto != null && !operandoProyecto) alcance.launch {
            operandoProyecto = true
            estado.avisar("Guardando proyecto con sus archivos...")
            val resultado = withContext(Dispatchers.IO) {
                runCatching {
                    val salida = context.contentResolver.openOutputStream(uri, "wt") ?: error("No se puede guardar el archivo")
                    salida.use { stream ->
                        PaqueteProyecto.escribir(proyecto, stream) { o -> context.contentResolver.openInputStream(Uri.parse(o.uri)) }
                    }
                }
            }
            resultado.onSuccess { estado.avisar("Proyecto guardado con sus fotos, videos y sonidos") }
                .onFailure { estado.avisar(it.message ?: "No se pudo guardar el proyecto") }
            operandoProyecto = false
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
