package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.modelo.TipoEfectoVideo.BARRIDO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.CIRCULO
import com.royermethod.app.editor.modelo.TipoEfectoVideo.DESENFOQUE_DIRECCIONAL
import com.royermethod.app.editor.modelo.TipoEfectoVideo.MITADES

/**
 * Ejemplos editables de las herramientas de movimiento, hechos con una o dos
 * fotos. Cada bloque lleva pocos efectos con nombre (las combinaciones rapidas
 * del catalogo y algun efecto suelto), no claves por cuadro: despues se
 * cambian desde los paneles como si los hubiera puesto el usuario, uno a uno.
 *
 *  1. Mitades: arriba y abajo llegan desde fuera y se juntan; al final la
 *     izquierda y la derecha se separan hacia los lados.
 *  2. Circulo y anillo: un circulo descubre la foto, un anillo con el centro
 *     desplazado deja ver lo de debajo y al final otro circulo la cierra.
 *     Con dos fotos va en una capa encima de la otra, para que se vea que
 *     hay debajo; con una sola, el bloque descubre el fondo del proyecto.
 *  3. Latigazo y sacudida: la combinacion con desenfoque direccional y bordes
 *     espejo, y la sacudida de siempre con su desenfoque. Cada efecto es
 *     independiente aunque compartan grupo.
 *  4. Barrido y zoom fino: un barrido inclinado de entrada y un golpe de zoom
 *     y giro con fotogramas clave a 0,005 s uno de otro.
 *
 * No usa transiciones: cada ejemplo empieza con un corte.
 */
object EjemplosMovimiento {

    const val MITADES_US = 2_000_000L
    const val CIRCULO_US = 2_400_000L
    const val LATIGAZO_US = 2_000_000L
    const val BARRIDO_US = 2_000_000L

    /** Donde cae cada cosa dentro de su bloque, para las pruebas y los volcados. */
    const val SEPARAR_US = 400_000L
    const val ANILLO_DESDE_US = 850_000L
    const val CERRAR_US = 500_000L
    const val LATIGAZO_DESDE_US = 300_000L
    const val SACUDIDA_DESDE_US = 1_100_000L
    const val BARRIDO_DURA_US = 500_000L
    /** El golpe de zoom: sube en 5 ms (menos de un fotograma) y vuelve con calma. */
    const val GOLPE_US = 1_000_000L
    const val GOLPE_ARRIBA_US = 1_005_000L
    const val GOLPE_VUELTA_US = 1_400_000L

    const val GRUPO_SEPARAR = "Mitades que se separan"
    const val GRUPO_CERRAR = "Circulo que se cierra"
    const val GRUPO_BARRIDO = "Barrido de entrada"

    /**
     * @param proyecto el proyecto con los ejemplos al final de la linea principal
     * @param inicioUs donde empieza el primero
     * @param ids los bloques nuevos de la linea principal, en orden
     * @param capa la capa del circulo y el anillo (con dos fotos), o null
     */
    data class Resultado(val proyecto: Proyecto, val inicioUs: Long, val ids: List<Long>, val capa: Long?)

    /**
     * Agrega los ejemplos al final de la linea principal de [p] sin tocar nada
     * de lo que ya habia. Con un proyecto vacio, los ejemplos son el proyecto.
     *
     * @param a la foto de casi todos los ejemplos
     * @param b otra foto: lo que se ve debajo del circulo y el anillo, y la del
     *   barrido. Sin ella todo sale de [a]
     */
    fun agregar(p: Proyecto, a: Origen, b: Origen? = null): Resultado {
        var q = p
        fun id(): Long = q.conId().let { (n, r) -> q = r; n }
        val bloques = listOf(
            mitades(id(), a),
            if (b != null) Clip(id(), origen = b, hastaUs = CIRCULO_US) else circuloYAnillo(id(), a),
            latigazoYSacudida(id(), a),
            barridoYZoom(id(), b ?: a)
        )
        val principal = q.principal + bloques
        val primero = q.principal.size
        val inicios = Linea.inicios(principal)
        var capas = q.capas
        var capa: Long? = null
        if (b != null) {
            // Encima del segundo bloque, en la primera fila que este libre ahi
            val c = circuloYAnillo(id(), a)
            val ini = inicios[primero + 1]
            capas = capas + Capa(ini, Linea.filaLibre(capas, ini, ini + c.duracionUs), c)
            capa = c.id
        }
        return Resultado(q.copy(principal = principal, capas = capas), inicios[primero], bloques.map { it.id }, capa)
    }

    private fun combinacion(nombre: String) = CatalogoEfectos.combinaciones.first { it.nombre == nombre }

    /** Las mitades se juntan al entrar (la combinacion rapida) y se separan al salir. */
    fun mitades(id: Long, o: Origen): Clip {
        val c = CatalogoEfectos.aplicar(combinacion("Mitades que se juntan"), Clip(id, origen = o, hastaUs = MITADES_US), 0)
        // La misma herramienta con otros ajustes: corte vertical, de 0 a media
        // pantalla y acelerando. Cada mitad sale hacia su lado
        val ini = MITADES_US - SEPARAR_US
        val n = c.siguienteIdEfecto()
        return c.copy(efectos = c.efectos + listOf(
            EfectoVideo(MITADES, ini, MITADES_US, id = n, grupo = GRUPO_SEPARAR,
                ajustes = AjustesEfecto(angulo = 0f, desde = 0f, hasta = 0.5f, curva = Curva.ENTRADA, vertical = true)),
            // El rastro crece a la vez que aceleran
            EfectoVideo(DESENFOQUE_DIRECCIONAL, ini, MITADES_US, fundidoEntradaUs = SEPARAR_US, id = n + 1,
                grupo = GRUPO_SEPARAR, ajustes = AjustesEfecto(angulo = 0f))
        ))
    }

    /** Circulo que se abre, anillo con el centro arriba a la derecha y circulo que se cierra. */
    fun circuloYAnillo(id: Long, o: Origen): Clip {
        var c = Clip(id, origen = o, hastaUs = CIRCULO_US)
        c = CatalogoEfectos.aplicar(combinacion("Circulo que se abre"), c, 0)
        c = CatalogoEfectos.aplicar(combinacion("Anillo que se abre"), c, ANILLO_DESDE_US)
        val anillo = c.efectos.last().id
        c = c.cambiarEfecto(anillo) { it.copy(ajustes = it.ajustes.copy(centroX = 0.25f, centroY = -0.2f)) }
        // Al reves que el de entrada: de todo el radio a nada, acelerando
        val ini = CIRCULO_US - CERRAR_US
        return c.copy(efectos = c.efectos + EfectoVideo(CIRCULO, ini, CIRCULO_US, id = c.siguienteIdEfecto(),
            grupo = GRUPO_CERRAR, ajustes = AjustesEfecto(desde = 1f, hasta = 0f, curva = Curva.ENTRADA)))
    }

    /** Las dos combinaciones de golpe: latigazo con rastro direccional y sacudida con desenfoque. */
    fun latigazoYSacudida(id: Long, o: Origen): Clip {
        var c = Clip(id, origen = o, hastaUs = LATIGAZO_US)
        c = CatalogoEfectos.aplicar(combinacion("Latigazo con desenfoque"), c, LATIGAZO_DESDE_US)
        // La SACUDIDA tal cual: ni sus ajustes ni su reloj cambian
        return CatalogoEfectos.aplicar(combinacion("Sacudida con desenfoque"), c, SACUDIDA_DESDE_US)
    }

    /**
     * Barrido inclinado que descubre la foto y un golpe de zoom con giro hecho
     * con tres fotogramas clave: reposo, el golpe 5 ms despues y la vuelta.
     */
    fun barridoYZoom(id: Long, o: Origen): Clip = Clip(
        id, origen = o, hastaUs = BARRIDO_US,
        efectos = listOf(EfectoVideo(BARRIDO, 0, BARRIDO_DURA_US, id = 1, grupo = GRUPO_BARRIDO,
            ajustes = AjustesEfecto(angulo = 14f, suavidad = 0.04f))),
        claves = listOf(
            Clave(GOLPE_US, Encuadre(), Curva.LINEAL),
            Clave(GOLPE_ARRIBA_US, Encuadre(escala = 1.18f, rotacion = -4f), Curva.SALIDA),
            Clave(GOLPE_VUELTA_US, Encuadre())
        )
    )
}
