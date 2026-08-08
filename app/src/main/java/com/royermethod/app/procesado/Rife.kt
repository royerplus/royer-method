package com.royermethod.app.procesado

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generacion de fotogramas intermedios.
 *
 * Se le dan dos fotogramas consecutivos y un instante entre ellos, y devuelve
 * como se veria el video en ese instante. No es una mezcla: el modelo estima
 * hacia donde se mueve cada parte de la imagen y la coloca donde tocaria, que
 * es la diferencia entre un video fluido y uno con fantasmas.
 *
 * Corre en la GPU por Vulkan. En CPU tardaria del orden de veinte veces mas y
 * no tendria sentido ofrecerlo.
 */
object Rife {

    private const val MODELO = "rife-v4.6"
    private val ficheros = listOf("flownet.param", "flownet.bin")

    @Volatile
    private var cargado = false

    @Volatile
    private var biblioteca = false

    init {
        biblioteca = runCatching { System.loadLibrary("royerrife") }.isSuccess
    }

    /** ¿Tiene este telefono con que? */
    fun disponible(): Boolean = biblioteca && runCatching { nativoDisponible() }.getOrDefault(false)

    /** ¿Esta el modelo cargado y usable ahora mismo? */
    fun listo(): Boolean = cargado

    /**
     * Deja el modelo listo.
     *
     * ncnn abre archivos de verdad, y un asset dentro del APK no lo es, asi
     * que la primera vez se copian los diez megas del modelo a la carpeta
     * privada de la app. Solo la primera vez.
     */
    @Synchronized
    fun preparar(context: Context): Boolean {
        if (!biblioteca) return false
        if (cargado) return true

        val carpeta = File(context.filesDir, MODELO)
        if (!carpeta.exists()) carpeta.mkdirs()

        for (nombre in ficheros) {
            val destino = File(carpeta, nombre)
            if (destino.exists() && destino.length() > 0) continue
            val ok = runCatching {
                context.assets.open("$MODELO/$nombre").use { entrada ->
                    destino.outputStream().use { entrada.copyTo(it) }
                }
            }.isSuccess
            if (!ok) return false
        }

        cargado = runCatching { nativoCrear(carpeta.absolutePath) }.getOrDefault(false)
        return cargado
    }

    /**
     * Un fotograma intermedio.
     *
     * Los tres buffers tienen que ser directos y de ancho*alto*4 bytes en
     * RGBA. Directos porque un buffer normal de Java vive en el monton y
     * habria que copiarlo entero para cruzarlo a C++: a cuarenta fotogramas
     * generados por cada uno del original, esa copia sale carisima.
     *
     * RGBA y no RGB porque es lo que entrega OpenGL al leer una imagen: pedir
     * tres canales obligaria a recomponer cada pixel a mano.
     */
    fun interpolar(
        a: ByteBuffer,
        b: ByteBuffer,
        salida: ByteBuffer,
        ancho: Int,
        alto: Int,
        instante: Float
    ): Boolean {
        if (!cargado) return false
        return runCatching {
            nativoInterpolar(a, b, salida, ancho, alto, instante)
        }.getOrDefault(false)
    }

    /** Suma un fotograma al acumulador de la mezcla. */
    fun acumular(fuente: ByteBuffer, acumulador: ByteBuffer, muestras: Int) {
        if (biblioteca) nativoAcumular(fuente, acumulador, muestras)
    }

    /** Divide el acumulador entre las sumas hechas y lo deja a cero. */
    fun promediar(acumulador: ByteBuffer, destino: ByteBuffer, muestras: Int, cuantos: Int) {
        if (biblioteca) nativoPromediar(acumulador, destino, muestras, cuantos)
    }

    /** Acumulador de 16 bits por muestra, para sumar sin desbordar. */
    fun acumuladorPara(ancho: Int, alto: Int): ByteBuffer =
        ByteBuffer.allocateDirect(ancho * alto * 4 * 2).order(ByteOrder.nativeOrder())

    @Synchronized
    fun cerrar() {
        if (!biblioteca) return
        runCatching { nativoDestruir() }
        cargado = false
    }

    /** Buffer RGBA del tamano correcto para esta resolucion. */
    fun buffer(ancho: Int, alto: Int): ByteBuffer =
        ByteBuffer.allocateDirect(ancho * alto * 4).order(ByteOrder.nativeOrder())

    private external fun nativoDisponible(): Boolean
    private external fun nativoCrear(carpeta: String): Boolean
    private external fun nativoInterpolar(
        a: ByteBuffer, b: ByteBuffer, salida: ByteBuffer,
        ancho: Int, alto: Int, instante: Float
    ): Boolean

    private external fun nativoAcumular(fuente: ByteBuffer, acumulador: ByteBuffer, muestras: Int)
    private external fun nativoPromediar(
        acumulador: ByteBuffer, destino: ByteBuffer, muestras: Int, cuantos: Int
    )

    private external fun nativoDestruir()
}
