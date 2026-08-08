package com.royermethod.app.procesado

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reconstruccion de imagen.
 *
 * Lo que habia antes no mejoraba la calidad: reducia bien y afilaba el filo.
 * Afilar solo subraya lo que ya esta; donde el original perdio detalle, sigue
 * sin haberlo. Esto es otra cosa: un modelo entrenado con imagenes
 * estropeadas a proposito, que aprende a devolver la linea limpia y a quitar
 * el ruido de compresion.
 *
 * Se usa a 2x aunque la salida vaya a medir lo mismo que la entrada. Ese es
 * el truco para subir calidad sin subir resolucion: se reconstruye al doble y
 * se vuelve a bajar, de modo que cada pixel final sale del promedio de cuatro
 * reconstruidos y hereda su detalle.
 */
object Waifu2x {

    private const val CARPETA = "waifu2x"

    /** Los cuatro niveles de limpieza mas el de solo aumentar. */
    private val ficheros = listOf(
        "scale2.0x_model.param", "scale2.0x_model.bin",
        "noise1_scale2.0x_model.param", "noise1_scale2.0x_model.bin",
        "noise2_scale2.0x_model.param", "noise2_scale2.0x_model.bin",
        "noise3_scale2.0x_model.param", "noise3_scale2.0x_model.bin"
    )

    @Volatile
    private var cargado = false

    /**
     * Deja listo el modelo del nivel pedido.
     *
     * @param ruido -1 solo reconstruye, 1 a 3 limpia cada vez mas fuerte.
     */
    @Synchronized
    fun preparar(context: Context, ruido: Int): Boolean {
        if (!Rife.disponible()) return false   // comparten la instancia de Vulkan

        val carpeta = File(context.filesDir, CARPETA)
        if (!carpeta.exists()) carpeta.mkdirs()
        for (nombre in ficheros) {
            val destino = File(carpeta, nombre)
            if (destino.exists() && destino.length() > 0) continue
            val ok = runCatching {
                context.assets.open("$CARPETA/$nombre").use { entrada ->
                    destino.outputStream().use { entrada.copyTo(it) }
                }
            }.isSuccess
            if (!ok) return false
        }

        cargado = runCatching { nativoCrear(carpeta.absolutePath, ruido) }.getOrDefault(false)
        return cargado
    }

    /**
     * Reconstruye al doble.
     *
     * La salida tiene que ser de (ancho*2)*(alto*2)*4 bytes.
     */
    fun procesar(entrada: ByteBuffer, salida: ByteBuffer, ancho: Int, alto: Int): Boolean {
        if (!cargado) return false
        return runCatching { nativoProcesar(entrada, salida, ancho, alto) }.getOrDefault(false)
    }

    /**
     * Reconstruye y devuelve al tamano de partida.
     *
     * Los dos pasos juntos son "mejorar sin cambiar de tamano": se reconstruye
     * al doble y se promedia cada cuadrado de cuatro. El detalle que anade el
     * modelo se queda; el tamano no cambia.
     */
    fun mejorarIgual(
        entrada: ByteBuffer,
        doble: ByteBuffer,
        salida: ByteBuffer,
        ancho: Int,
        alto: Int
    ): Boolean {
        if (!cargado) return false
        return runCatching {
            if (!nativoProcesar(entrada, doble, ancho, alto)) return false
            nativoMitad(doble, salida, ancho, alto)
            true
        }.getOrDefault(false)
    }

    /** ¿Esta el modelo cargado y usable ahora mismo? */
    fun listo(): Boolean = cargado

    @Synchronized
    fun cerrar() {
        runCatching { nativoDestruir() }
        cargado = false
    }

    fun buffer(ancho: Int, alto: Int): ByteBuffer =
        ByteBuffer.allocateDirect(ancho * alto * 4).order(ByteOrder.nativeOrder())

    private external fun nativoCrear(carpeta: String, ruido: Int): Boolean
    private external fun nativoProcesar(
        entrada: ByteBuffer, salida: ByteBuffer, ancho: Int, alto: Int
    ): Boolean

    private external fun nativoMitad(
        grande: ByteBuffer, destino: ByteBuffer, ancho: Int, alto: Int
    )

    private external fun nativoDestruir()
}
