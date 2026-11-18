package com.royermethod.pc

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.pc.exportar.Exportador
import com.royermethod.pc.medios.Importar
import com.royermethod.pc.vista.Previsualizador
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Prueba sin ventana del motor de PC: arma un proyecto con los archivos
 * dados, saca fotogramas de la vista previa a PNG y exporta.
 *
 *   Prueba <carpeta-salida> <archivo> [archivo...]
 */
object Prueba {
    @JvmStatic
    fun main(args: Array<String>) {
        val salida = File(args[0]).apply { mkdirs() }
        val origenes = args.drop(1).map { requireNotNull(Importar.origen(File(it))) { "no abre $it" } }
        origenes.forEach { println("origen: $it") }
        var p = AjustesEditor.proyectoNuevo()
        val visuales = origenes.filter { !it.tipo.name.equals("AUDIO") }
        p = Edicion.agregarPrincipal(p, visuales, Long.MAX_VALUE).first
        // PRUEBA_FPS=60 prueba la salida a 60
        System.getenv("PRUEBA_FPS")?.toIntOrNull()?.let { f -> p = p.copy(salida = p.salida.copy(fps = f)) }
        visuales.firstOrNull { it.esVideo }?.let { p = Edicion.agregarSuperpuesto(p, it, 500_000L).first }
        Proyectos.guardar(p)
        val d = Linea.duracion(p)
        println("duracion ${d / 1000} ms, ${p.principal.size} bloques, ${p.capas.size} capas")

        val prev = Previsualizador(Context.app)
        val imagenes = ArrayBlockingQueue<ImageBitmap>(16)
        prev.alImagen = { imagenes.offer(it) }
        prev.proyecto(p)
        prev.cambiarTamano(540, 540)
        for ((i, t) in listOf(0L, 1_000_000L, d / 2, d - 100_000L).withIndex()) {
            imagenes.clear()
            val t0 = System.nanoTime()
            prev.irA(t)
            var img = imagenes.poll(20, TimeUnit.SECONDS)
            // La primera es la del cambio de tamano
            while (true) { img = imagenes.poll(400, TimeUnit.MILLISECONDS) ?: break }
            println("vista $i en ${t / 1000} ms: ${(System.nanoTime() - t0) / 1_000_000} ms")
            img?.let { ImageIO.write(it.toAwtImage(), "png", File(salida, "vista-$i.png")) }
        }
        // Reproducir dos segundos y contar fotogramas
        var cuantos = 0
        prev.alImagen = { cuantos++ }
        prev.irA(0)
        Thread.sleep(500)
        cuantos = 0
        prev.reproducir()
        Thread.sleep(2000)
        prev.pausar()
        println("reproduciendo 2 s: $cuantos fotogramas")
        prev.cerrar()

        val t0 = System.nanoTime()
        val informe = Exportador(Context.app, p, File(salida, "exportado.mp4")).exportar()
        println("exportado en ${(System.nanoTime() - t0) / 1_000_000} ms: $informe")
        println(informe.verificacion.resumen())
        System.exit(0)
    }
}
