package com.royermethod.app.editor.modelo

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/** Luces y trama evaluadas una vez por fotograma, con el reloj de cada efecto. */
data class VfxPantalla(
    val mitades: LuzMitades? = null,
    val resplandor: LuzResplandor? = null,
    val trama: TramaSemitono? = null,
    val arcoiris: LuzArcoiris? = null
) {
    val activo: Boolean get() = mitades != null || resplandor != null || trama != null || arcoiris != null

    companion object { val NEUTRO = VfxPantalla() }
}

data class LuzMitades(
    val fuerza: Float, val lado: Float, val dirX: Float, val dirY: Float,
    val centroX: Float, val centroY: Float, val suavidad: Float,
    val halo: Float, val color: Int
)
data class LuzResplandor(val fuerza: Float, val radio: Float, val umbral: Float, val suavidad: Float, val color: Int)
data class TramaSemitono(val fuerza: Float, val paso: Float, val angulo: Float, val lineas: Boolean, val color: Int)
data class LuzArcoiris(val fuerza: Float, val radio: Float, val umbral: Float, val fase: Float)

private fun Float.finito(defecto: Float, min: Float, max: Float): Float =
    if (isFinite()) coerceIn(min, max) else defecto

/**
 * Mismo criterio que Mitades: si coinciden varias versiones de un tipo manda
 * la ultima activa. Los tipos distintos si se combinan. Ningun efecto abre
 * huecos ni cambia el alfa del elemento.
 */
fun Clip.vfxPantallaEn(localUs: Long): VfxPantalla {
    var mitades: LuzMitades? = null
    var resplandor: LuzResplandor? = null
    var trama: TramaSemitono? = null
    var arcoiris: LuzArcoiris? = null
    for (e in efectos) {
        if (!e.activoEn(localUs)) continue
        val fuerza = e.fuerzaEn(localUs)
        if (fuerza <= 0f) continue
        val a = e.ajustes
        when (e.tipo) {
            TipoEfectoVideo.PULSO_MITADES -> {
                // Tres pares de pulsos por segundo a rapidez 1. El seno hace
                // que cada mitad se apague antes de encender la siguiente.
                val ciclos = e.segundosEn(localUs).toDouble() * 3.0
                val fase = (ciclos - floor(ciclos)) * 2.0
                val pulso = sin((fase - floor(fase)) * PI).toFloat().coerceIn(0f, 1f)
                var lado = if (fase < 1.0) -1f else 1f
                if (a.invertir) lado = -lado
                val rad = Math.toRadians(a.angulo.finito(0f, -3600f, 3600f).toDouble())
                mitades = LuzMitades(fuerza * pulso, lado, cos(rad).toFloat(), sin(rad).toFloat(),
                    a.centroX.finito(0f,-.5f,.5f), a.centroY.finito(0f,-.5f,.5f),
                    a.suavidad.finito(.02f,0f,.2f), a.halo.finito(.5f,0f,1f), a.color)
            }
            TipoEfectoVideo.RESPLANDOR -> resplandor = LuzResplandor(fuerza,
                a.grosor.finito(.018f,.001f,.1f), a.umbral.finito(.6f,0f,.99f),
                .02f + a.halo.finito(.5f,0f,1f) * .25f, a.color)
            TipoEfectoVideo.SEMITONO -> trama = TramaSemitono(fuerza.coerceAtMost(1f),
                a.grosor.finito(.012f,.002f,.1f), Math.toRadians(a.angulo.finito(30f,-3600f,3600f).toDouble()).toFloat(),
                a.vertical, a.color)
            TipoEfectoVideo.ARCOIRIS_BORDES -> {
                val segundos = e.segundosEn(localUs).toDouble()
                arcoiris = LuzArcoiris(fuerza, a.grosor.finito(.004f,.001f,.04f),
                    a.umbral.finito(.08f,0f,.99f), (segundos - floor(segundos)).toFloat())
            }
            else -> Unit
        }
    }
    if (mitades == null && resplandor == null && trama == null && arcoiris == null) return VfxPantalla.NEUTRO
    return VfxPantalla(mitades,resplandor,trama,arcoiris)
}
