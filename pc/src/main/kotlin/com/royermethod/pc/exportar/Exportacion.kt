package com.royermethod.pc.exportar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.royermethod.app.editor.modelo.Proyecto
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * La exportacion en curso, en su propio hilo. La pantalla solo mira.
 * Los videos van a Videos\Royer Method.
 */
object Exportacion {
    var corriendo by mutableStateOf(false)
        private set
    var hechos by mutableLongStateOf(0L)
        private set
    var total by mutableLongStateOf(0L)
        private set
    var arranqueMs by mutableLongStateOf(0L)
        private set
    var informe by mutableStateOf<Informe?>(null)
        private set
    var fallo by mutableStateOf<String?>(null)
        private set

    @Volatile
    private var cancelado = false

    val fraccion: Float get() = if (total > 0) hechos.toFloat() / total else 0f

    val carpeta: File
        get() {
            val home = File(System.getProperty("user.home"))
            val videos = File(home, "Videos").takeIf { it.isDirectory } ?: home
            return File(videos, "Royer Method").apply { mkdirs() }
        }

    fun empezar(context: Context, p: Proyecto): Boolean {
        if (corriendo) return false
        cancelado = false
        hechos = 0; total = 0
        informe = null; fallo = null
        arranqueMs = System.currentTimeMillis()
        corriendo = true
        val nombre = "Royer-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".mp4"
        Thread({
            try {
                informe = Exportador(context, p, File(carpeta, nombre), { cancelado }) { h, t -> hechos = h; total = t }.exportar()
            } catch (e: Cancelado) {
            } catch (e: Throwable) {
                e.printStackTrace()
                fallo = e.message ?: e.toString()
            } finally {
                corriendo = false
            }
        }, "exportar").apply { isDaemon = true }.start()
        return true
    }

    fun cancelar() { cancelado = true }

    fun limpiar() {
        if (corriendo) return
        informe = null; fallo = null; hechos = 0; total = 0
    }
}
