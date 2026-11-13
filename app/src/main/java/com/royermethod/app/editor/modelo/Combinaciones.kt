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

    private fun c(nombre: String, vararg piezas: Movimiento) = Combinacion(nombre, piezas.toList())

    /** Las de entrada (y salida, que son las mismas al reves), por grupos. */
    val entradaPorGrupo: List<Pair<String, List<Combinacion>>> = listOf(
        "Basicas" to listOf(
            c("Aparecer", m(Animacion.APARECER)),
            c("Desde la izquierda", m(Animacion.IZQUIERDA)),
            c("Desde la derecha", m(Animacion.DERECHA)),
            c("Desde abajo", m(Animacion.ABAJO, 0.4f), m(Animacion.APARECER, hasta = 0.6f)),
            c("Desde arriba", m(Animacion.ARRIBA, 0.4f), m(Animacion.APARECER, hasta = 0.6f)),
            c("Diagonal", m(Animacion.IZQUIERDA, 0.5f), m(Animacion.ARRIBA, 0.5f), m(Animacion.APARECER, hasta = 0.5f)),
            c("Caer", m(Animacion.ARRIBA, c = Curva.REBOTE), m(Animacion.APARECER, hasta = 0.3f)),
            c("Rebote", m(Animacion.REBOTE)),
            c("Enfocar", m(Animacion.DESENFOCAR), m(Animacion.ZOOM_ENTRA, 0.4f), m(Animacion.APARECER, hasta = 0.5f)),
            c("Deslizar y enfocar", m(Animacion.DERECHA, 0.5f), m(Animacion.DESENFOCAR), m(Animacion.APARECER, hasta = 0.5f)),
            c("Escribir", m(Animacion.REVELAR, c = Curva.LINEAL)),
            c("Parpadeo", m(Animacion.PARPADEO, c = Curva.LINEAL))
        ),
        "Zoom" to listOf(
            c("Acercar", m(Animacion.ZOOM_ENTRA), m(Animacion.APARECER, hasta = 0.5f)),
            c("Alejar", m(Animacion.ZOOM_SALE), m(Animacion.APARECER, hasta = 0.5f)),
            c("Zoom rapido", m(Animacion.ZOOM_ENTRA, 2.2f, Curva.RAPIDA), m(Animacion.APARECER, hasta = 0.3f)),
            c("Zoom out rapido", m(Animacion.ZOOM_SALE, 2f, Curva.RAPIDA), m(Animacion.APARECER, hasta = 0.3f)),
            // El desenfoque de movimiento del motor ya emborrona el zoom; esto lo remata
            c("Zoom blur", m(Animacion.ZOOM_ENTRA, 2.5f, Curva.RAPIDA), m(Animacion.DESENFOCAR, 1.5f, Curva.RAPIDA)),
            c("Zoom con flash", m(Animacion.ZOOM_ENTRA, 2f, Curva.RAPIDA), m(Animacion.FLASH, hasta = 0.4f)),
            c("Golpe de zoom", m(Animacion.ZOOM_ENTRA, 1.2f, Curva.RAPIDA), m(Animacion.CAMARA, 0.6f, desde = 0.1f)),
            c("Pop", m(Animacion.ZOOM_SALE, 1.6f, Curva.REBOTE), m(Animacion.APARECER, hasta = 0.3f)),
            c("Elastico", m(Animacion.ZOOM_SALE, 1.2f, Curva.ELASTICO)),
            c("Zoom vibrante", m(Animacion.ZOOM_VIBRA, c = Curva.LINEAL), m(Animacion.ZOOM_ENTRA, 0.5f)),
            c("Zoom y giro", m(Animacion.ZOOM_ENTRA, 1.2f), m(Animacion.GIRO, 0.4f), m(Animacion.DESENFOCAR, 0.6f))
        ),
        "Shake" to listOf(
            c("Shake con flash", m(Animacion.CAMARA, 1.4f), m(Animacion.FLASH, hasta = 0.45f)),
            c("Shake de camara", m(Animacion.CAMARA)),
            c("Shake fuerte", m(Animacion.CAMARA, 2.2f), m(Animacion.SACUDIR, 1f)),
            c("Shake RGB", m(Animacion.CAMARA, 1.2f), m(Animacion.RGB, 1.2f)),
            c("Shake flash RGB", m(Animacion.CAMARA, 1.5f), m(Animacion.FLASH, hasta = 0.4f), m(Animacion.RGB, 1.2f)),
            c("Shake negativo", m(Animacion.CAMARA, 1.4f), m(Animacion.NEGATIVO, hasta = 0.5f)),
            c("Golpe", m(Animacion.ZOOM_ENTRA, 1.6f, Curva.REBOTE), m(Animacion.SACUDIR, 0.8f, desde = 0.4f)),
            c("Impacto", m(Animacion.ZOOM_ENTRA, 2.5f, Curva.RAPIDA), m(Animacion.SACUDIR, 1.2f, desde = 0.3f), m(Animacion.DESENFOCAR, 0.8f, hasta = 0.5f)),
            c("Impacto con flash", m(Animacion.ZOOM_ENTRA, 2.5f, Curva.RAPIDA), m(Animacion.CAMARA, 1.4f, desde = 0.2f), m(Animacion.FLASH, hasta = 0.35f)),
            c("Sacudida", m(Animacion.SACUDIR, 1.4f), m(Animacion.SACUDIR_VERTICAL, 1f)),
            c("Shake vertical", m(Animacion.SACUDIR_VERTICAL, 1.4f)),
            c("Temblor suave", m(Animacion.CAMARA, 0.5f, Curva.LINEAL)),
            c("Pendulo", m(Animacion.PENDULO, c = Curva.LINEAL))
        ),
        "Flash y color" to listOf(
            c("Flash", m(Animacion.FLASH)),
            c("Flash suave", m(Animacion.FLASH, 0.55f)),
            c("Flash negro", m(Animacion.FLASH_NEGRO)),
            c("Estrobo", m(Animacion.ESTROBO, c = Curva.LINEAL)),
            c("Separar RGB", m(Animacion.RGB, 1.4f)),
            c("Flash RGB", m(Animacion.FLASH, hasta = 0.5f), m(Animacion.RGB, 1.3f)),
            c("Negativo", m(Animacion.NEGATIVO, c = Curva.LINEAL)),
            c("Negativo y flash", m(Animacion.NEGATIVO, hasta = 0.5f), m(Animacion.FLASH, desde = 0.3f)),
            c("Desde negro", m(Animacion.FLASH_NEGRO, c = Curva.SUAVE))
        ),
        "Giros y latigazos" to listOf(
            c("Latigazo", m(Animacion.DERECHA, 1f, Curva.RAPIDA), m(Animacion.DESENFOCAR, 1.2f, Curva.RAPIDA)),
            c("Latigazo arriba", m(Animacion.ABAJO, 1f, Curva.RAPIDA), m(Animacion.DESENFOCAR, 1.2f, Curva.RAPIDA)),
            c("Latigazo con flash", m(Animacion.DERECHA, 1f, Curva.RAPIDA), m(Animacion.DESENFOCAR, 1.2f, Curva.RAPIDA), m(Animacion.FLASH, desde = 0.1f, hasta = 0.5f)),
            c("Espiral", m(Animacion.GIRO, 2f), m(Animacion.ZOOM_SALE, 1.5f), m(Animacion.APARECER, hasta = 0.4f)),
            c("Giro completo", m(Animacion.GIRO, 3f, Curva.SUAVE), m(Animacion.ZOOM_SALE, 1f), m(Animacion.APARECER, hasta = 0.3f)),
            c("Giro con flash", m(Animacion.GIRO, 1.5f, Curva.RAPIDA), m(Animacion.ZOOM_ENTRA, 1f, Curva.RAPIDA), m(Animacion.FLASH, hasta = 0.4f)),
            c("Rodar", m(Animacion.IZQUIERDA, 1f), m(Animacion.GIRO, 1.5f)),
            c("Caer girando", m(Animacion.ARRIBA, 1f, Curva.REBOTE), m(Animacion.GIRO, 0.5f), m(Animacion.APARECER, hasta = 0.3f))
        ),
        "Glitch" to listOf(
            c("Glitch", m(Animacion.GLITCH, c = Curva.LINEAL), m(Animacion.APARECER, c = Curva.LINEAL, hasta = 0.2f)),
            c("Glitch RGB", m(Animacion.GLITCH, c = Curva.LINEAL), m(Animacion.RGB, 1.5f, Curva.LINEAL)),
            c("Twitch", m(Animacion.TWITCH, c = Curva.LINEAL)),
            c("Twitch con flash", m(Animacion.TWITCH, 1.3f, Curva.LINEAL), m(Animacion.ESTROBO, 0.6f, Curva.LINEAL))
        )
    )

    val entrada: List<Combinacion> = entradaPorGrupo.flatMap { it.second }

    val continuasPorGrupo: List<Pair<String, List<Pair<String, List<Continuo>>>>> = listOf(
        "Al ritmo" to listOf(
            "Shake con flash" to listOf(Continuo(AnimacionContinua.SHAKE_FLASH)),
            "Zoom al ritmo" to listOf(Continuo(AnimacionContinua.ZOOM_RITMO)),
            "Zoom y flash" to listOf(Continuo(AnimacionContinua.ZOOM_RITMO), Continuo(AnimacionContinua.SHAKE_FLASH, 0.5f)),
            "Estrobo" to listOf(Continuo(AnimacionContinua.ESTROBO)),
            "Pulso" to listOf(Continuo(AnimacionContinua.PULSO)),
            "Pulso rapido" to listOf(Continuo(AnimacionContinua.PULSO, velocidad = 2f)),
            "Latido" to listOf(Continuo(AnimacionContinua.LATIDO)),
            "Latido con temblor" to listOf(Continuo(AnimacionContinua.PULSO), Continuo(AnimacionContinua.TEMBLOR, 0.5f))
        ),
        "Suaves" to listOf(
            "Acercamiento" to listOf(Continuo(AnimacionContinua.ACERCAMIENTO)),
            "Alejamiento" to listOf(Continuo(AnimacionContinua.ALEJAMIENTO)),
            "Camara en mano" to listOf(Continuo(AnimacionContinua.CAMARA_EN_MANO)),
            "Respirar" to listOf(Continuo(AnimacionContinua.RESPIRAR)),
            "Flotar" to listOf(Continuo(AnimacionContinua.FLOTAR)),
            "Balanceo" to listOf(Continuo(AnimacionContinua.BALANCEO)),
            "Flotar y balancear" to listOf(Continuo(AnimacionContinua.FLOTAR), Continuo(AnimacionContinua.BALANCEO, 0.6f)),
            "Paneo" to listOf(Continuo(AnimacionContinua.PANEO)),
            "Girar" to listOf(Continuo(AnimacionContinua.GIRAR))
        ),
        "Temblor y glitch" to listOf(
            "Temblor" to listOf(Continuo(AnimacionContinua.TEMBLOR)),
            "Vibrar" to listOf(Continuo(AnimacionContinua.VIBRAR)),
            "Zoom con temblor" to listOf(Continuo(AnimacionContinua.ACERCAMIENTO), Continuo(AnimacionContinua.TEMBLOR, 0.4f)),
            "RGB" to listOf(Continuo(AnimacionContinua.RGB)),
            "RGB y vibrar" to listOf(Continuo(AnimacionContinua.RGB), Continuo(AnimacionContinua.VIBRAR, 0.7f)),
            "Twitch" to listOf(Continuo(AnimacionContinua.TWITCH)),
            "Glitch" to listOf(Continuo(AnimacionContinua.GLITCH)),
            "Glitch y vibrar" to listOf(Continuo(AnimacionContinua.GLITCH), Continuo(AnimacionContinua.VIBRAR, 0.6f))
        )
    )

    val continuas: List<Pair<String, List<Continuo>>> = continuasPorGrupo.flatMap { it.second }
}
