package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.AnimacionAplicada
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.MomentoAnimacion
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.editor.modelo.cambiarAnimacion
import com.royermethod.app.editor.modelo.conAnimacion
import com.royermethod.app.editor.modelo.conAnimacionesUnificadas
import com.royermethod.app.editor.modelo.convertible
import com.royermethod.app.editor.modelo.convertirEnEfecto
import com.royermethod.app.editor.modelo.duracionSugeridaUs
import com.royermethod.app.editor.modelo.siguienteIdAnimacion
import com.royermethod.app.editor.modelo.sinAnimacion

/**
 * Lo que hace el panel "Animar", sin Compose (se prueba en el ordenador).
 *
 * El panel ensena las animaciones puestas en el bloque (las de antes, que el
 * bloque guardaba como entrada/salida/continuas, tambien), deja elegir una,
 * sumar otras del catalogo o cambiar la elegida por otra, y convertirla en
 * efecto. Cada accion del usuario es un solo paso de deshacer; la primera que
 * cambia algo pasa las de antes a la lista en ese mismo paso, sin que cambie
 * nada de lo que se ve.
 *
 * Al poner una del catalogo el cabezal vuelve al comienzo del bloque y queda
 * en pausa. Los ajustes de despues (duracion, piezas) no lo mueven ni tocan la
 * duracion que haya elegido el usuario.
 */
internal object LogicaAnimacion {

    /** Una casilla del catalogo: lo que se pone al tocarla. */
    data class Opcion(
        val nombre: String,
        val momento: MomentoAnimacion,
        val piezas: List<Movimiento> = emptyList(),
        val continuas: List<Continuo> = emptyList()
    )

    /** Como se ensena una casilla: libre, ya puesta en el bloque o la elegida. */
    enum class Marca { DISPONIBLE, PUESTA, ELEGIDA }

    /** Que hizo un toque en el catalogo. */
    enum class Accion {
        /** Se sumo una nueva, conservando las demas. */
        SUMADA,
        /**
         * La elegida paso a ser otra: con la duracion que le hubiera dado el
         * usuario, o con la sugerida para la nueva si no la habia tocado.
         */
        SUSTITUIDA,
        /** Ya estaba puesta: solo pasa a ser la elegida. */
        ELEGIDA,
        /** Era la elegida: no cambia nada (solo se vuelve a ver desde el principio). */
        REPETIDA
    }

    data class Aplicacion(val clip: Clip, val elegidaId: Long, val accion: Accion)

    /** Las animaciones del bloque, como se listan: las de antes primero (ids deterministas). */
    fun aplicadas(c: Clip): List<AnimacionAplicada> = c.conAnimacionesUnificadas().animaciones

    /** Con que pestana se abre: la de la primera que haya, o Entrada. */
    fun momentoInicial(aplicadas: List<AnimacionAplicada>): MomentoAnimacion =
        aplicadas.firstOrNull()?.momento ?: MomentoAnimacion.ENTRADA

    /**
     * La elegida en la pestana [momento]: la de [id] si es de ese momento; si
     * no (o ya no existe, por ejemplo tras deshacer), la ultima de ese momento.
     */
    fun elegida(aplicadas: List<AnimacionAplicada>, id: Long?, momento: MomentoAnimacion): AnimacionAplicada? =
        aplicadas.firstOrNull { it.id == id && it.momento == momento } ?: aplicadas.lastOrNull { it.momento == momento }

    /**
     * Las casillas de una pestana, por grupos: las categorias de siempre y,
     * si las hay, las guardadas por el usuario ("Mias"). Entrada y salida
     * comparten catalogo.
     */
    fun grupos(
        momento: MomentoAnimacion,
        mias: List<Combinaciones.Combinacion> = emptyList(),
        continuasMias: List<Pair<String, List<Continuo>>> = emptyList()
    ): List<Pair<String, List<Opcion>>> = when (momento) {
        MomentoAnimacion.SIEMPRE -> {
            val g = Combinaciones.continuasPorGrupo +
                (if (continuasMias.isNotEmpty()) listOf("Mias" to continuasMias) else emptyList())
            g.map { (nombre, l) -> nombre to l.map { (n, cs) -> Opcion(n, momento, continuas = cs) } }
        }
        else -> {
            val g = Combinaciones.entradaPorGrupo + (if (mias.isNotEmpty()) listOf("Mias" to mias) else emptyList())
            g.map { (nombre, l) -> nombre to l.map { Opcion(it.nombre, momento, piezas = it.piezas) } }
        }
    }

    /** La pastilla del filtro que ensena todas las categorias. */
    const val TODAS = "Todas"

    /**
     * Las categorias que se ensenan con el filtro [categoria]: solo esa, o
     * todas si es null o no existe en esta pestana (las de Siempre son otras).
     */
    fun filtrar(grupos: List<Pair<String, List<Opcion>>>, categoria: String?): List<Pair<String, List<Opcion>>> {
        if (categoria == null) return grupos
        return grupos.filter { it.first == categoria }.ifEmpty { grupos }
    }

    /** Las categorias con alguna casilla puesta en el bloque, para marcarlas en el filtro. */
    fun categoriasConPuestas(grupos: List<Pair<String, List<Opcion>>>, aplicadas: List<AnimacionAplicada>): Set<String> =
        grupos.filter { (_, l) -> l.any { o -> aplicadas.any { corresponde(it, o) } } }.map { it.first }.toSet()

    /**
     * ¿Esta puesta la casilla [o] en [a]? Del mismo momento y con lo mismo, o
     * con su nombre (asi una puesta y retocada sigue marcada en el catalogo).
     */
    fun corresponde(a: AnimacionAplicada, o: Opcion): Boolean =
        a.momento == o.momento && (a.nombre == o.nombre || contenido(a) == contenido(o))

    private fun contenido(a: AnimacionAplicada): Any = if (a.momento == MomentoAnimacion.SIEMPRE) a.continuas else a.piezas
    private fun contenido(o: Opcion): Any = if (o.momento == MomentoAnimacion.SIEMPRE) o.continuas else o.piezas

    fun marca(o: Opcion, aplicadas: List<AnimacionAplicada>, elegida: AnimacionAplicada?): Marca = when {
        elegida != null && corresponde(elegida, o) -> Marca.ELEGIDA
        aplicadas.any { corresponde(it, o) } -> Marca.PUESTA
        else -> Marca.DISPONIBLE
    }

    /** Lo que puede durar una entrada o una salida: de un fotograma al bloque entero. */
    fun limitesDuracion(duracionBloqueUs: Long): Pair<Long, Long> {
        val max = duracionBloqueUs.coerceAtLeast(0)
        return minOf(Linea.tiempoDe(1), max) to max
    }

    /**
     * Sobre que cuentan Empieza y Termina de las piezas de [a] (la primera) y
     * hasta donde se ve (la segunda). La base es la duracion de la animacion,
     * la misma que usa la escena, aunque el bloque se haya quedado mas corto
     * al recortarlo, cambiar su velocidad o dividirlo; se ve hasta el final
     * del bloque.
     */
    fun baseTramos(a: AnimacionAplicada, duracionBloqueUs: Long): Pair<Long, Long> {
        val base = a.duracionUs.coerceAtLeast(0)
        return base to minOf(base, duracionBloqueUs.coerceAtLeast(0))
    }

    /**
     * Duracion de una recien puesta: la sugerida para lo que hace y lo que dura
     * el bloque ([duracionSugeridaUs]), pegada al fotograma si el usuario usa
     * el ajuste a fotogramas. Al sumar, y al sustituir una cuya duracion el
     * usuario no habia cambiado ([duracionAlSustituir]); nunca al retocar.
     */
    fun duracionInicialUs(o: Opcion, duracionBloqueUs: Long, fotogramas: Boolean): Long {
        val s = duracionSugeridaUs(o.piezas, o.momento, duracionBloqueUs)
        if (o.momento == MomentoAnimacion.SIEMPRE) return s
        val (min, max) = limitesDuracion(duracionBloqueUs)
        return TiempoCampo.ajustar(s, min, max, fotogramas)
    }

    /**
     * Un toque en la casilla [o].
     *
     * Con [agregando] (el boton "+") se suma siempre una nueva y las demas se
     * quedan. Sin el: si es la elegida no cambia nada; si ya esta puesta, pasa
     * a ser la elegida; si no, sustituye a la elegida conservando su id, si
     * esta activa y la duracion que le hubiera dado el usuario
     * ([duracionAlSustituir]); y si no hay ninguna elegida en esa pestana, se
     * suma con la duracion sugerida.
     */
    fun aplicar(c: Clip, o: Opcion, elegidaId: Long?, agregando: Boolean, fotogramas: Boolean): Aplicacion {
        val lista = aplicadas(c)
        val elegida = elegida(lista, elegidaId, o.momento)
        if (!agregando) {
            if (elegida != null && corresponde(elegida, o)) return Aplicacion(c, elegida.id, Accion.REPETIDA)
            val puesta = lista.lastOrNull { corresponde(it, o) }
            if (puesta != null) return Aplicacion(c, puesta.id, Accion.ELEGIDA)
            if (elegida != null) {
                val dur = duracionAlSustituir(elegida, o, c.duracionUs, fotogramas)
                val nuevo = c.cambiarAnimacion(elegida.id) {
                    it.copy(nombre = o.nombre, piezas = o.piezas, continuas = o.continuas, duracionUs = dur)
                }
                return Aplicacion(nuevo, elegida.id, Accion.SUSTITUIDA)
            }
        }
        val id = c.siguienteIdAnimacion()
        val a = AnimacionAplicada(
            id, o.nombre, o.momento, piezas = o.piezas, continuas = o.continuas,
            duracionUs = duracionInicialUs(o, c.duracionUs, fotogramas)
        )
        return Aplicacion(c.conAnimacion(a), id, Accion.SUMADA)
    }

    /**
     * La duracion de [a] al cambiarla por [o] sin "+". Si sigue siendo la que
     * se le sugirio a [a] (con lo que lleva, lo que dura el bloque y el mismo
     * ajuste a fotogramas), el usuario no la eligio: pasa a la sugerida de
     * [o], asi un rebote no se queda con la de un flash. Si es otra, la eligio
     * el usuario y se queda. Una que el usuario puso justo en la sugerida
     * cuenta como no tocada; con el bloque recortado despues, como tocada.
     */
    fun duracionAlSustituir(a: AnimacionAplicada, o: Opcion, duracionBloqueUs: Long, fotogramas: Boolean): Long {
        val sugerida = duracionInicialUs(Opcion(a.nombre, a.momento, a.piezas, a.continuas), duracionBloqueUs, fotogramas)
        return if (a.duracionUs == sugerida) duracionInicialUs(o, duracionBloqueUs, fotogramas) else a.duracionUs
    }

    /** Las piezas nuevas de la animacion [id]; sin ninguna, la animacion se quita (como "Ninguna" antes). */
    fun conPiezas(c: Clip, id: Long, piezas: List<Movimiento>): Clip =
        if (piezas.isEmpty()) c.sinAnimacion(id) else c.cambiarAnimacion(id) { it.copy(piezas = piezas) }

    fun conContinuas(c: Clip, id: Long, continuas: List<Continuo>): Clip =
        if (continuas.isEmpty()) c.sinAnimacion(id) else c.cambiarAnimacion(id) { it.copy(continuas = continuas) }

    /** "Entrada · 0.500 s", "Siempre · todo el bloque" (y si esta apagada). */
    fun detalle(a: AnimacionAplicada): String {
        val cuanto = if (a.momento == MomentoAnimacion.SIEMPRE) "todo el bloque" else TiempoPreciso.texto(a.duracionUs)
        return "${a.momento.nombre} · $cuanto" + if (a.activa) "" else " · apagada"
    }

    /** Por que no se ofrece convertirla, o null si se puede. */
    fun motivoNoConvertible(a: AnimacionAplicada): String? = when {
        a.convertible() -> null
        a.momento == MomentoAnimacion.SIEMPRE -> "No se puede convertir: no tiene piezas"
        else -> "No se puede convertir: no tiene piezas ni duracion"
    }

    // ------------------------------------------------------------------
    // Sobre el editor: una accion del usuario, un paso de deshacer
    // ------------------------------------------------------------------

    /**
     * Toca la casilla [o] en el bloque elegido ([aplicar]) y deja el cabezal
     * en pausa al comienzo del bloque. Devuelve lo que paso (la elegida nueva).
     */
    fun aplicar(
        estado: EstadoEditor,
        o: Opcion,
        elegidaId: Long?,
        agregando: Boolean,
        fotogramas: Boolean = AjustesEditor.ajustarFotogramas
    ): Aplicacion? {
        if (estado.clipElegido == null) return null
        var r: Aplicacion? = null
        estado.cambiarClip { c -> aplicar(c, o, elegidaId, agregando, fotogramas).also { r = it }.clip }
        estado.pausarEn(estado.inicioElegido ?: 0L)
        return r
    }

    /** La duracion de [id], sin mover el cabezal. Un arrastre entero es un paso. */
    fun cambiarDuracion(estado: EstadoEditor, id: Long, us: Long, arrastre: Boolean) =
        estado.cambiarClip(arrastre) { c -> c.cambiarAnimacion(id) { it.copy(duracionUs = us.coerceAtLeast(0)) } }

    fun cambiarPiezas(estado: EstadoEditor, id: Long, piezas: List<Movimiento>, arrastre: Boolean) =
        estado.cambiarClip(arrastre) { c -> conPiezas(c, id, piezas) }

    fun cambiarContinuas(estado: EstadoEditor, id: Long, continuas: List<Continuo>, arrastre: Boolean) =
        estado.cambiarClip(arrastre) { c -> conContinuas(c, id, continuas) }

    fun quitar(estado: EstadoEditor, id: Long) = estado.cambiarClip { c -> c.sinAnimacion(id) }

    /**
     * Convierte [id] en un efecto en un solo paso (la animacion se va en el
     * mismo: nunca quedan las dos), y abre Efectos con el efecto nuevo elegido.
     * Devuelve su id, o null si no se pudo (y entonces no cambia nada).
     */
    fun convertir(estado: EstadoEditor, id: Long): Long? {
        var efecto: Long? = null
        estado.cambiarClip { c ->
            val k = c.convertirEnEfecto(id)
            efecto = k?.efecto?.id
            k?.clip ?: c
        }
        val ef = efecto ?: return null
        estado.efectoAElegir = ef
        estado.panel = Panel.EFECTOS
        return ef
    }
}
