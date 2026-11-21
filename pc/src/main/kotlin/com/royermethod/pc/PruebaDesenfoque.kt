package com.royermethod.pc

import android.content.Context
import com.royermethod.app.editor.modelo.Ajuste
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.pc.exportar.Exportador
import com.royermethod.pc.medios.Importar
import java.io.File

/**
 * Prueba sin ventana del desenfoque de movimiento: exporta lo mismo con
 * distintas muestras y obturadores y apunta cuanto tarda cada uno.
 *
 *   PruebaDesenfoque <carpeta-salida> <imagen> <video>
 *
 * Animacion: la imagen cruza el lienzo girando (claves rapidas). Video: el
 * video con Fluido por estela de flujo optico, con varias tomas.
 */
object PruebaDesenfoque {
    @JvmStatic
    fun main(args: Array<String>) {
        val salida = File(args[0]).apply { mkdirs() }
        val imagen = requireNotNull(Importar.origen(File(args[1])))
        val video = requireNotNull(Importar.origen(File(args[2])))
        val base = AjustesEditor.proyectoNuevo()
        val informe = StringBuilder()

        fun exportar(nombre: String, p: Proyecto) {
            val t0 = System.nanoTime()
            val r = Exportador(Context.app, p, File(salida, "$nombre.mp4")).exportar()
            val ms = (System.nanoTime() - t0) / 1_000_000
            val linea = "$nombre: ${ms} ms, ${r.bytes / 1024} KB, ${r.verificacion.resumen()}"
            println(linea)
            informe.appendLine(linea)
        }

        // Animacion: ida y vuelta girando en un segundo
        var a = Edicion.agregarPrincipal(base, listOf(imagen), Long.MAX_VALUE).first
        val e = Encuadre(ajuste = Ajuste.ENCAJAR, escala = 0.45f)
        a = a.copy(principal = a.principal.map {
            it.copy(hastaUs = 1_000_000, encuadre = e, claves = listOf(
                Clave(0, e.copy(x = -0.45f), Curva.LINEAL),
                Clave(500_000, e.copy(x = 0.45f, rotacion = 180f), Curva.LINEAL),
                Clave(999_000, e.copy(x = -0.45f, rotacion = 360f), Curva.LINEAL)
            ))
        })
        val casos = System.getenv("CASOS")?.split(",") ?: listOf("anim", "video")
        if ("anim" in casos) {
            for (o in listOf(0.5f, 1f)) for (n in listOf(1, 4, 8, 16, 32)) {
                if (n == 1 && o != 0.5f) continue
                exportar("anim-n$n-o${(o * 360).toInt()}",
                    a.copy(salida = a.salida.copy(desenfoqueMovimiento = n > 1, muestras = n, obturador = o)))
            }
        }

        // Video con Fluido por estela
        var v = Edicion.agregarPrincipal(base, listOf(video), Long.MAX_VALUE).first
        v = v.copy(principal = v.principal.map { it.copy(hastaUs = minOf(it.hastaUs, 2_000_000)) })
        if ("video" in casos) {
            exportar("video-sin", v.copy(salida = v.salida.copy(desenfoqueMovimiento = false)))
            val fl = v.copy(principal = v.principal.map { it.copy(fluido = true) })
            for (o in listOf(0.5f, 1f)) for (n in listOf(8, 16, 32)) {
                exportar("video-estela-n$n-o${(o * 360).toInt()}",
                    fl.copy(salida = fl.salida.copy(estelaFluido = true, generadosFluido = n, obturador = o)))
            }
        }
        File(salida, "tiempos.txt").writeText(informe.toString())
        System.exit(0)
    }
}
