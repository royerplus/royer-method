package com.royermethod.app.editor.modelo

/**
 * Animaciones ya combinadas, listas para usar.
 *
 * Son solo listas de [Movimiento]: el usuario puede abrir cualquiera y
 * cambiarle piezas, intensidades o curvas, y guardar la suya.
 */
object Combinaciones {

    data class Combinacion(val nombre: String, val piezas: List<Movimiento>)

    private fun m(t: Animacion, i: Float = 1f, c: Curva = Curva.SALIDA, desde: Float = 0f, hasta: Float = 1f) =
        Movimiento(t, i, c, desde, hasta)

    val entrada: List<Combinacion> = listOf(
        Combinacion("Aparecer", listOf(m(Animacion.APARECER))),
        Combinacion("Acercar", listOf(m(Animacion.ZOOM_ENTRA), m(Animacion.APARECER, hasta = 0.5f))),
        Combinacion("Alejar", listOf(m(Animacion.ZOOM_SALE), m(Animacion.APARECER, hasta = 0.5f))),
        Combinacion("Desde la izquierda", listOf(m(Animacion.IZQUIERDA))),
        Combinacion("Desde la derecha", listOf(m(Animacion.DERECHA))),
        Combinacion("Desde abajo", listOf(m(Animacion.ABAJO, 0.4f), m(Animacion.APARECER, hasta = 0.6f))),
        Combinacion("Caer", listOf(m(Animacion.ARRIBA, c = Curva.REBOTE), m(Animacion.APARECER, hasta = 0.3f))),
        Combinacion("Rebote", listOf(m(Animacion.REBOTE))),
        Combinacion("Golpe", listOf(m(Animacion.ZOOM_ENTRA, 1.6f, Curva.REBOTE), m(Animacion.SACUDIR, 0.8f, desde = 0.4f))),
        Combinacion("Enfocar", listOf(m(Animacion.DESENFOCAR), m(Animacion.ZOOM_ENTRA, 0.4f), m(Animacion.APARECER, hasta = 0.5f))),
        Combinacion("Espiral", listOf(m(Animacion.GIRO, 2f), m(Animacion.ZOOM_SALE, 1.5f), m(Animacion.APARECER, hasta = 0.4f))),
        Combinacion("Zoom y giro", listOf(m(Animacion.ZOOM_ENTRA, 1.2f), m(Animacion.GIRO, 0.4f), m(Animacion.DESENFOCAR, 0.6f))),
        Combinacion("Elastico", listOf(m(Animacion.ZOOM_SALE, 1.2f, Curva.ELASTICO))),
        Combinacion("Deslizar y enfocar", listOf(m(Animacion.DERECHA, 0.5f), m(Animacion.DESENFOCAR), m(Animacion.APARECER, hasta = 0.5f))),
        Combinacion("Escribir", listOf(m(Animacion.REVELAR, c = Curva.LINEAL))),
        Combinacion("Parpadeo", listOf(m(Animacion.PARPADEO, c = Curva.LINEAL)))
    )

    /** Las de salida son las mismas piezas: se recorren al reves. */
    val salida: List<Combinacion> = entrada

    val continuas: List<Pair<String, List<Continuo>>> = listOf(
        "Acercamiento" to listOf(Continuo(AnimacionContinua.ACERCAMIENTO)),
        "Alejamiento" to listOf(Continuo(AnimacionContinua.ALEJAMIENTO)),
        "Pulso" to listOf(Continuo(AnimacionContinua.PULSO)),
        "Temblor" to listOf(Continuo(AnimacionContinua.TEMBLOR)),
        "Balanceo" to listOf(Continuo(AnimacionContinua.BALANCEO)),
        "Flotar" to listOf(Continuo(AnimacionContinua.FLOTAR)),
        "Girar" to listOf(Continuo(AnimacionContinua.GIRAR)),
        "Latido con temblor" to listOf(Continuo(AnimacionContinua.PULSO), Continuo(AnimacionContinua.TEMBLOR, 0.5f)),
        "Flotar y balancear" to listOf(Continuo(AnimacionContinua.FLOTAR), Continuo(AnimacionContinua.BALANCEO, 0.6f))
    )
}
