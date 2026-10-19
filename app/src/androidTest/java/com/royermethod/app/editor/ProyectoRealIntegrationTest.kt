@file:Suppress("DEPRECATION")

package com.royermethod.app.editor

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.test.InstrumentationTestRunner
import android.util.AtomicFile
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import com.royermethod.app.MainActivity
import com.royermethod.app.editor.exportar.Exportador
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.ui.EstadoEditor
import com.royermethod.app.editor.ui.Panel
import com.royermethod.app.editor.ui.Seleccion
import com.royermethod.app.editor.vista.Previsualizador
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Prueba optativa en un telefono real, sin servicios ni librerias de pruebas externas.
 *
 * Copiar antes un paquete Royer a externalFilesDir/pruebas/replica.royer, o pasar
 * `-e packagePath /ruta/absoluta/proyecto.royer` a `am instrument`. Solo se permite
 * la variante .prueba para conservar la instalacion habitual. La prueba de exportar
 * deja el proyecto importado guardado para revisarlo despues con los controles UI.
 * La prueba de vista previa abre una Activity con una TextureView de benchmark.
 *
 * Salidas: pruebas/replica.mp4 y pruebas/replica-informe.json en externalFilesDir.
 * La exportacion se ejecuta en el hilo de instrumentacion con el motor real GL,
 * decodificadores y codificadores del dispositivo. Tambien se verifica el estado
 * usado por la UI: seleccionar, buscar, activar y deshacer/rehacer gestos agrupados.
 */
class ProyectoRealIntegrationTest : InstrumentationTestCase() {

    /**
     * Benchmark de humo del motor de vista previa sobre una TextureView visible.
     * No prueba gestos ni la disposicion Compose del editor. Informa rendimiento
     * observado, sin convertir un dispositivo lento en un fallo artificial.
     */
    fun testPrevisualizarProyectoReal() {
        val context = instrumentation.targetContext
        assertTrue("Ejecutar en la variante .prueba", context.packageName.endsWith(".prueba"))
        val guardado = File(context.filesDir, "editor/proyecto.json")
        assertTrue("Importa primero el proyecto con testImportarYExportarProyectoReal", guardado.isFile)
        val p = Persistencia.deTexto(guardado.readText(Charsets.UTF_8)) ?: error("No se entiende el proyecto guardado")
        assertTrue("No hay fotogramas para previsualizar", Linea.fotogramas(p) > 0)
        val externas = context.getExternalFilesDir(null) ?: error("No hay almacenamiento externo de la app")
        val archivo = File(externas, "pruebas/preview-informe.json")
        val informe = JSONObject().apply {
            put("estado", "PREPARANDO")
            put("tipoPrueba", "Motor real de vista previa en TextureView; no gestos UI")
            put("app", context.packageName)
            put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("api", Build.VERSION.SDK_INT)
            put("anchoSuperficie", 576)
            put("altoSuperficie", 768)
            put("duracionProyectoUs", Linea.duracion(p))
            put("fotogramasEsperados", Linea.fotogramas(p))
        }
        fun registrar() = escribirAtomico(archivo, informe.toString(2))
        registrar()

        val superficieLista = CountDownLatch(1)
        val primerDibujo = CountDownLatch(1)
        val terminado = CountDownLatch(1)
        val comenzo = AtomicBoolean(false)
        val medir = AtomicBoolean(false)
        val superficie = AtomicReference<Surface?>(null)
        val tiempos = Collections.synchronizedList(mutableListOf<MuestraVista>())
        val publicaciones = Collections.synchronizedList(mutableListOf<MuestraPublicacion>())
        val presentados = Collections.synchronizedList(mutableListOf<MuestraTextura>())
        var actividad: Activity? = null
        var textura: TextureView? = null
        val prev = Previsualizador(context)
        var inicioNs = 0L
        var finNs = 0L

        try {
            actividad = instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            val ventana = requireNotNull(actividad)
            instrumentation.runOnMainSync {
                prev.alTiempo = { us ->
                    if (medir.get() && comenzo.get()) tiempos.add(MuestraVista(System.nanoTime(), us))
                }
                prev.alCuadroPublicado = { cuadro ->
                    if (medir.get() && cuadro.enReproduccion) publicaciones.add(MuestraPublicacion(System.nanoTime(), cuadro))
                }
                prev.alReproducir = { activo ->
                    if (activo) comenzo.set(true)
                    else if (comenzo.get() && medir.get()) {
                        finNs = System.nanoTime()
                        terminado.countDown()
                    }
                }
                val marco = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
                val vista = TextureView(context)
                textura = vista
                vista.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val s = Surface(st)
                        superficie.set(s)
                        prev.adjuntar(s, w, h)
                        superficieLista.countDown()
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = prev.cambiarTamano(w, h)

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                        primerDibujo.countDown()
                        if (medir.get()) presentados.add(MuestraTextura(System.nanoTime(), st.timestamp))
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        prev.soltarSuperficie()
                        superficie.getAndSet(null)?.release()
                        return true
                    }
                }
                prev.proyecto(p)
                prev.irA(0)
                marco.addView(vista, FrameLayout.LayoutParams(576, 768, Gravity.CENTER))
                ventana.setContentView(marco)
            }
            assertTrue("No se creo la superficie de vista previa", superficieLista.await(10, TimeUnit.SECONDS))
            assertTrue("El motor no presento el primer fotograma", primerDibujo.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                inicioNs = System.nanoTime()
                medir.set(true)
                prev.reproducir()
            }
            informe.put("estado", "REPRODUCIENDO")
            registrar()
            val finalizo = terminado.await(30, TimeUnit.SECONDS)
            instrumentation.runOnMainSync { medir.set(false) }
            if (finNs == 0L) finNs = System.nanoTime()
            val muestrasTiempo = synchronized(tiempos) { tiempos.toList() }
            val muestrasPublicadas = synchronized(publicaciones) { publicaciones.toList() }
            val muestrasTextura = synchronized(presentados) { presentados.toList() }
            // Una actualizacion puede llegar al hilo UI antes de que vuelva
            // eglSwapBuffers. Asociar al terminar evita esa carrera y utiliza
            // la marca del buffer, nunca el reloj mutable de reproduccion.
            val porMarca = muestrasPublicadas.associateBy { it.cuadro.nanoTime }
            val muestrasPantalla = muestrasTextura.mapNotNull { muestra ->
                porMarca[muestra.marcaNs]?.let { MuestraVista(muestra.paredNs, it.cuadro.tiempoUs) }
            }
            val sinMarca = muestrasTextura.filter { it.marcaNs !in porMarca }
            val cpu = muestrasPublicadas.map { it.cuadro.cpuMs }
            val swap = muestrasPublicadas.map { it.cuadro.swapMs }
            val motor = estadisticasVista(muestrasPublicadas.map { MuestraVista(it.paredNs, it.cuadro.tiempoUs) }, Linea.fotogramas(p), inicioNs)
                .put("cpuMs", estadisticasDuracion(cpu))
                .put("swapMs", estadisticasDuracion(swap))
                .put("muestrasRender", JSONArray().apply {
                    muestrasPublicadas.forEach { m ->
                        put(JSONObject().put("ms", (m.paredNs - inicioNs) / 1e6)
                            .put("tiempoUs", m.cuadro.tiempoUs).put("fotograma", m.cuadro.fotograma)
                            .put("marcaBufferNs", m.cuadro.nanoTime).put("cpuMs", m.cuadro.cpuMs).put("swapMs", m.cuadro.swapMs))
                    }
                })
            val pantalla = estadisticasVista(muestrasPantalla, Linea.fotogramas(p), inicioNs)
                .put("actualizacionesTotales", muestrasTextura.size)
                .put("sinMarcaAsociada", sinMarca.size)
                .put("marcasRepetidas", muestrasTextura.size - muestrasTextura.distinctBy { it.marcaNs }.size)
                .put("muestrasSinMarca", JSONArray().apply {
                    sinMarca.forEach { put(JSONObject().put("ms", (it.paredNs - inicioNs) / 1e6).put("marcaBufferNs", it.marcaNs)) }
                })
            informe.put("estado", if (finalizo) "COMPLETADO" else "TIMEOUT")
                .put("reproduccionTerminada", finalizo && !prev.reproduciendo)
                .put("duracionRealMs", (finNs - inicioNs) / 1_000_000.0)
                .put("ultimoTiempoUs", prev.tiempo)
                .put("callbacks", estadisticasVista(muestrasTiempo, Linea.fotogramas(p), inicioNs))
                .put("publicacionesMotor", motor)
                .put("presentacionesTexture", pantalla)
                .put("notaPresentaciones", "Publicaciones: swaps EGL exitosos. Presentaciones: actualizaciones TextureView asociadas por SurfaceTexture.timestamp a la marca real de su buffer. Las actualizaciones sin marca se informan aparte; no se adivina su fotograma con el reloj del motor.")
            registrar()
            assertTrue("La reproduccion no termino en 30 segundos; consulta preview-informe.json", finalizo)
            assertFalse("El motor sigue reproduciendo tras el final", prev.reproduciendo)
            assertTrue("El motor no envio callbacks de reproduccion", muestrasTiempo.isNotEmpty())
            assertTrue("El motor no publico cuadros de reproduccion", muestrasPublicadas.isNotEmpty())
            assertTrue("No se presentaron cuadros durante la reproduccion", muestrasPantalla.isNotEmpty())
        } catch (fallo: Throwable) {
            informe.put("estado", "FALLIDO").put("error", fallo.toString()).put("detalle", fallo.stackTraceToString())
            registrar()
            throw fallo
        } finally {
            medir.set(false)
            // GL deja de usar la Surface antes de soltar su referencia o cerrar la Activity.
            prev.pausar()
            prev.soltarSuperficie()
            prev.cerrar()
            instrumentation.runOnMainSync {
                textura?.surfaceTextureListener = null
                superficie.getAndSet(null)?.release()
                actividad?.finish()
            }
        }
    }

    private data class MuestraVista(val paredNs: Long, val tiempoUs: Long)
    private data class MuestraTextura(val paredNs: Long, val marcaNs: Long)
    private data class MuestraPublicacion(val paredNs: Long, val cuadro: Previsualizador.CuadroPublicado)

    private fun estadisticasDuracion(muestras: List<Double>): JSONObject {
        val ordenadas = muestras.sorted()
        val p95 = if (ordenadas.isEmpty()) 0.0 else ordenadas[(kotlin.math.ceil(ordenadas.size * 0.95).toInt() - 1).coerceAtLeast(0)]
        return JSONObject().put("media", if (muestras.isEmpty()) 0.0 else muestras.average())
            .put("p95", p95).put("maximo", muestras.maxOrNull() ?: 0.0)
    }

    private fun estadisticasVista(muestras: List<MuestraVista>, esperados: Long, inicioNs: Long): JSONObject {
        val indices = muestras.map { Linea.fotogramaEn(it.tiempoUs) }
        val unicos = indices.distinct()
        val saltados = indices.zipWithNext().sumOf { (a, b) -> (b - a - 1).coerceAtLeast(0) }
        val maxGapNs = muestras.zipWithNext().maxOfOrNull { (a, b) -> b.paredNs - a.paredNs } ?: 0L
        val periodoNs = if (muestras.size > 1) muestras.last().paredNs - muestras.first().paredNs else 0L
        val fps = if (periodoNs > 0) (muestras.size - 1) * 1_000_000_000.0 / periodoNs else 0.0
        return JSONObject().apply {
            put("cantidad", muestras.size)
            put("indicesUnicos", unicos.size)
            put("fpsMedios", fps)
            put("maxGapMs", maxGapNs / 1_000_000.0)
            put("latenciaPrimerCuadroMs", muestras.firstOrNull()?.let { (it.paredNs - inicioNs) / 1_000_000.0 } ?: JSONObject.NULL)
            put("saltosDeIndice", saltados)
            put("fotogramasSinIndiceObservado", (esperados - unicos.size).coerceAtLeast(0))
            put("muestras", JSONArray().apply {
                muestras.forEach { m ->
                    put(JSONObject().put("ms", (m.paredNs - inicioNs) / 1_000_000.0)
                        .put("tiempoUs", m.tiempoUs).put("fotograma", Linea.fotogramaEn(m.tiempoUs)))
                }
            })
        }
    }

    fun testImportarYExportarProyectoReal() {
        val context = instrumentation.targetContext
        assertTrue("Ejecutar en la variante .prueba para preservar la app habitual", context.packageName.endsWith(".prueba"))
        assertFalse("La exportacion no puede bloquear el hilo UI", Looper.myLooper() == Looper.getMainLooper())

        val externas = context.getExternalFilesDir(null) ?: error("No hay almacenamiento externo de la app")
        val salida = File(externas, "pruebas").apply { check(mkdirs() || isDirectory) }
        val argumentos = (instrumentation as? InstrumentationTestRunner)?.arguments
        val paquete = argumentos?.getString("packagePath")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(salida, "replica.royer")
        val id = UUID.randomUUID().toString()
        val carpeta = File(context.filesDir, "editor/pruebas/$id")
        val archivoInforme = File(salida, "replica-informe.json")
        val destino = File(salida, "replica.mp4")
        val inicio = SystemClock.elapsedRealtime()
        val informe = JSONObject().apply {
            put("estado", "IMPORTANDO")
            put("runId", id)
            put("inicioEpochMs", System.currentTimeMillis())
            put("app", context.packageName)
            put("dispositivo", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("api", Build.VERSION.SDK_INT)
            put("paquete", paquete.absolutePath)
            put("directorioImportado", carpeta.absolutePath)
            put("archivoSalida", destino.absolutePath)
            put("comprobacionTactil", "Revisar en la app despues de esta prueba")
        }

        fun registrar() = escribirAtomico(archivoInforme, informe.toString(2))

        registrar()
        try {
            assertTrue("No existe el paquete: ${paquete.absolutePath}", paquete.isFile)
            val p = paquete.inputStream().use { PaqueteProyecto.leer(it, carpeta) }
            assertTrue("El paquete no contiene video ni capas exportables", Linea.fotogramas(p) > 0)
            val todos = p.principal + p.capas.map { it.clip }
            informe.put("bloquesPrincipales", p.principal.size)
                .put("capas", p.capas.size)
                .put("pistasAudio", p.audios.size)
                .put("efectos", todos.sumOf { it.efectos.size })
                .put("duracionProyectoUs", Linea.duracion(p))
                .put("fotogramasEsperados", Linea.fotogramas(p))

            val guardado = File(context.filesDir, "editor/proyecto.json")
            if (guardado.exists()) {
                val respaldo = File(carpeta, "proyecto-anterior.json")
                guardado.copyTo(respaldo, overwrite = false)
                assertTrue("El respaldo anterior esta vacio", respaldo.length() > 0)
                informe.put("respaldoProyectoAnterior", respaldo.absolutePath)
            } else informe.put("respaldoProyectoAnterior", JSONObject.NULL)
            escribirAtomico(guardado, Persistencia.aTexto(p))
            assertEquals("El proyecto guardado no coincide con el importado", p, Persistencia.deTexto(guardado.readText(Charsets.UTF_8)))
            informe.put("proyectoGuardado", guardado.absolutePath)
            informe.put("estadoEditor", comprobarEstadoEditor(p))

            // Guardar tambien cualquier exportacion previa de este harness.
            if (destino.exists()) {
                val historial = File(salida, "historial/$id").apply { check(mkdirs() || isDirectory) }
                val anterior = File(historial, "replica-anterior.mp4")
                check(destino.renameTo(anterior)) { "No se pudo conservar la exportacion anterior" }
                informe.put("respaldoExportacionAnterior", anterior.absolutePath)
            }
            informe.put("estado", "EXPORTANDO")
            registrar()
            var ultimoAvance = -1L
            val resultado = Exportador(context, p, destino, alAvanzar = { hechos, total ->
                if (hechos == total || hechos / Linea.FPS != ultimoAvance) {
                    ultimoAvance = hechos / Linea.FPS
                    informe.put("fotogramasProcesados", hechos).put("fotogramasTotal", total)
                    informe.put("tiempoProcesoMs", SystemClock.elapsedRealtime() - inicio)
                    registrar()
                }
            }).exportar()
            val v = resultado.verificacion
            informe.put("estado", "COMPLETADO")
                .put("codec", resultado.codec)
                .put("ajusteCodificador", resultado.ajuste ?: JSONObject.NULL)
                .put("bytes", resultado.bytes)
                .put("ancho", resultado.ancho)
                .put("alto", resultado.alto)
                .put("avisosFuentes", resultado.avisos)
                .put("fotogramas", v.fotogramas)
                .put("fps", v.fps)
                .put("irregulares", v.irregulares)
                .put("colgados", v.colgados)
                .put("duracionVideoUs", v.duracionVideoUs)
                .put("duracionAudioUs", v.duracionAudioUs)
                .put("verificacionPerfecta", v.perfecta)
                .put("tiempoProcesoMs", SystemClock.elapsedRealtime() - inicio)
            registrar()

            assertTrue("No se genero el MP4", destino.isFile && resultado.bytes > 0)
            assertEquals("Se perdieron fotogramas al exportar", v.esperados, v.fotogramas.toLong())
            assertEquals("El video tiene marcas de tiempo irregulares", 0, v.irregulares)
            assertEquals("El codificador dejo fotogramas colgados de un IDR", 0, v.colgados)
            assertEquals("Alguna fuente no se pudo leer durante la exportacion", 0, resultado.avisos)
        } catch (fallo: Throwable) {
            informe.put("estado", "FALLIDO")
                .put("error", fallo.toString())
                .put("detalle", fallo.stackTraceToString())
                .put("tiempoProcesoMs", SystemClock.elapsedRealtime() - inicio)
            registrar()
            throw fallo
        }
    }

    /** Usa el proyecto real sin alterar el montaje que se guarda o exporta. */
    private fun comprobarEstadoEditor(p: Proyecto): JSONObject {
        val comprobaciones = JSONArray()
        instrumentation.runOnMainSync {
            val clips = p.principal + p.capas.map { it.clip }
            val c = clips.firstOrNull { it.efectos.isNotEmpty() } ?: clips.first()
            // Un paquete sin FX tambien permite comprobar la edicion, en una copia temporal.
            val base = if (c.efectos.isEmpty()) Edicion.cambiar(p, c.id) {
                it.copy(efectos = listOf(EfectoVideo(TipoEfectoVideo.FLASH, finUs = minOf(it.duracionUs, 500_000L))))
            } else p
            val e = EstadoEditor(base)
            e.elegir(Seleccion.Bloque(c.id))
            e.panel = Panel.EFECTOS
            assertEquals(c.id, e.clipElegido?.id)
            comprobaciones.put("seleccionar bloque y abrir panel de efectos")

            var buscado = -1L
            e.alBuscar = { buscado = it }
            val t = (Edicion.inicio(base, c.id) ?: 0L) + minOf(c.duracionUs / 2, 100_000L)
            e.irA(t)
            assertEquals(t, buscado)
            comprobaciones.put("buscar instante absoluto de la vista previa")

            e.empezarGesto()
            e.cambiarClip(true) { clip -> clip.copy(efectos = clip.efectos.mapIndexed { i, fx -> if (i == 0) fx.copy(intensidad = 0.25f) else fx }) }
            e.cambiarClip(true) { clip -> clip.copy(efectos = clip.efectos.mapIndexed { i, fx -> if (i == 0) fx.copy(intensidad = 0.75f) else fx }) }
            e.terminarGesto()
            val ajustado = e.proyecto
            assertEquals(0.75f, e.clipElegido!!.efectos.first().intensidad, 0f)
            e.deshacer()
            assertEquals("Un arrastre debe deshacerse en un solo paso", base, e.proyecto)
            e.rehacer()
            assertEquals(ajustado, e.proyecto)
            comprobaciones.put("ajustar intensidad y deshacer/rehacer el gesto completo")

            val antes = e.clipElegido!!.efectos.first()
            e.cambiarClip { clip -> clip.copy(efectos = clip.efectos.mapIndexed { i, fx -> if (i == 0) fx.copy(activo = !fx.activo) else fx }) }
            assertEquals(antes.copy(activo = !antes.activo), e.clipElegido!!.efectos.first())
            e.deshacer()
            assertEquals(antes, e.clipElegido!!.efectos.first())
            comprobaciones.put("activar/desactivar efecto conservando ajustes y deshacer")
        }
        return JSONObject().put("comprobaciones", comprobaciones).put("correcto", true)
    }

    /**
     * Diagnostico del arranque del sonido: que dicen el cabezal y los
     * timestamps de AudioTrack en el primer segundo. Lo usa el reloj de la
     * vista previa; sirve para ver por que la imagen se detiene al empezar.
     */
    fun testDiagnosticoArranqueAudio() {
        val context = instrumentation.targetContext
        assertTrue("Ejecutar en la variante .prueba", context.packageName.endsWith(".prueba"))
        val guardado = File(context.filesDir, "editor/proyecto.json")
        assertTrue("Importa primero el proyecto con testImportarYExportarProyectoReal", guardado.isFile)
        val p = Persistencia.deTexto(guardado.readText(Charsets.UTF_8)) ?: error("No se entiende el proyecto guardado")
        val segs = com.royermethod.app.editor.audio.PlanAudio.de(p)
        assertTrue("El proyecto no tiene sonido", segs.isNotEmpty())
        val lecturas = ArrayList<com.royermethod.app.editor.vista.SalidaAudio.Lectura>()
        val inicio = System.nanoTime()
        val s = com.royermethod.app.editor.vista.SalidaAudio(context, segs, com.royermethod.app.editor.audio.PlanAudio.total(p), 0L)
        s.alLeer = { lecturas.add(it) }
        try {
            while (System.nanoTime() - inicio < 1_200_000_000L) {
                s.sonados()
                SystemClock.sleep(4)
            }
        } finally {
            s.parar()
        }
        val filas = JSONArray()
        for (l in lecturas) filas.put(JSONObject().apply {
            put("ms", (l.nanoTime - inicio) / 1e6)
            put("cabezal", l.cabezal)
            put("escritos", l.escritos)
            put("marcaFrames", l.marcaFrames ?: JSONObject.NULL)
            put("marcaMs", l.marcaNanoTime?.let { (it - inicio) / 1e6 } ?: JSONObject.NULL)
            put("posicion", l.posicion)
            put("congelado", l.congelado)
        })
        val externas = context.getExternalFilesDir(null) ?: error("No hay almacenamiento externo de la app")
        escribirAtomico(File(externas, "pruebas/arranque-audio.json"), JSONObject().put("lecturas", filas).toString(1))
    }

    private fun escribirAtomico(archivo: File, texto: String) {
        archivo.parentFile?.let { check(it.mkdirs() || it.isDirectory) }
        val atomico = AtomicFile(archivo)
        var stream: FileOutputStream? = null
        try {
            stream = atomico.startWrite()
            stream.write(texto.toByteArray(Charsets.UTF_8))
            atomico.finishWrite(stream)
        } catch (fallo: Throwable) {
            atomico.failWrite(stream)
            throw fallo
        }
    }
}
