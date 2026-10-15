package com.royermethod.app.editor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.royermethod.app.editor.modelo.Capa
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PistaAudio
import com.royermethod.app.editor.modelo.Proyecto

/** Lo que esta elegido en la linea de tiempo. */
sealed interface Seleccion {
    /** Un bloque, de la pista principal o de encima. */
    data class Bloque(val id: Long) : Seleccion
    /** La transicion que sale del bloque [id] hacia el siguiente. */
    data class Paso(val id: Long) : Seleccion
    data class Sonido(val id: Long) : Seleccion
}

/** Paneles que se abren abajo para cambiar algo. */
enum class Panel(val titulo: String) {
    VELOCIDAD("Velocidad"),
    VOLUMEN("Volumen"),
    ANIMACION("Animaciones"),
    TRANSICION("Transicion"),
    COLOR("Color y filtros"),
    ENCUADRE("Encuadre"),
    TEXTO("Texto"),
    FORMATO("Formato y fondo"),
    CURVA("Fotograma clave")
}

/**
 * Miniaturas y ondas de sonido. Las pone la plataforma (en el telefono se
 * sacan del archivo); la pantalla solo las pide. Devolver null significa
 * "todavia no": quien las implementa las carga aparte y, como guarda el
 * resultado en estado de Compose, la linea de tiempo se repinta sola al
 * llegar.
 */
interface Recursos {
    /** Fotograma del archivo en [tUs], pequeno. */
    fun miniatura(o: Origen, tUs: Long): ImageBitmap?
    /** Picos del sonido, uno cada [Recursos.PICOS_US], de 0 a 1. */
    fun onda(o: Origen): FloatArray?

    companion object {
        const val PICOS_US = 50_000L
        val NINGUNO = object : Recursos {
            override fun miniatura(o: Origen, tUs: Long): ImageBitmap? = null
            override fun onda(o: Origen): FloatArray? = null
        }
    }
}

/**
 * Todo lo que la pantalla del editor tiene entre manos.
 *
 * El proyecto es inmutable: cada cambio crea uno nuevo y el anterior va a
 * la pila de deshacer. Los arrastres (un deslizador, mover un bloque) se
 * juntan en un solo paso con [empezarGesto] y [terminarGesto]; si no,
 * deshacer iria fotograma a fotograma.
 */
class EstadoEditor(inicial: Proyecto) : Gestos {

    var proyecto by mutableStateOf(inicial)
        private set

    private val atras = ArrayDeque<Proyecto>()
    private val adelante = ArrayDeque<Proyecto>()
    var puedeDeshacer by mutableStateOf(false)
        private set
    var puedeRehacer by mutableStateOf(false)
        private set

    /** Instante del cabezal. */
    var tiempo by mutableLongStateOf(0L)
    var reproduciendo by mutableStateOf(false)
    var seleccion by mutableStateOf<Seleccion?>(null)
    var panel by mutableStateOf<Panel?>(null)
    /** Zoom de la linea de tiempo: cuantos dp ocupa un segundo. */
    var dpPorSegundo by mutableFloatStateOf(64f)
    /** Mensaje corto que se ensena un momento sobre la imagen. */
    var aviso by mutableStateOf<String?>(null)
        private set
    var avisoN by mutableIntStateOf(0)
        private set

    /** Lo llama al cambiar el proyecto (vista previa y guardado). */
    var alCambiar: (Proyecto) -> Unit = {}
    /** Lo llama cuando el usuario mueve el cabezal. */
    var alBuscar: (Long) -> Unit = {}

    private var gestoAbierto = false
    private var gestoGuardado = false

    val duracion: Long get() = Linea.duracion(proyecto)

    // ------------------------------------------------------------------
    // Cambios
    // ------------------------------------------------------------------

    /** Un cambio suelto: un paso de deshacer. */
    fun hacer(f: (Proyecto) -> Proyecto) {
        val nuevo = f(proyecto)
        if (nuevo == proyecto) return
        guardar()
        poner(nuevo)
    }

    /** Un cambio que puede ser parte de un arrastre. */
    fun ajustar(f: (Proyecto) -> Proyecto) {
        if (!gestoAbierto) return hacer(f)
        val nuevo = f(proyecto)
        if (nuevo == proyecto) return
        if (!gestoGuardado) {
            guardar()
            gestoGuardado = true
        }
        poner(nuevo)
    }

    override fun empezar() = empezarGesto()
    override fun terminar() = terminarGesto()

    fun empezarGesto() {
        gestoAbierto = true
        gestoGuardado = false
    }

    fun terminarGesto() {
        gestoAbierto = false
    }

    fun deshacer() {
        val p = atras.removeLastOrNull() ?: return
        adelante.addLast(proyecto)
        poner(p)
        limpiarSeleccion()
        botones()
    }

    fun rehacer() {
        val p = adelante.removeLastOrNull() ?: return
        atras.addLast(proyecto)
        poner(p)
        limpiarSeleccion()
        botones()
    }

    /** Cambia el proyecto entero sin pasar por deshacer (al abrir uno). */
    fun reemplazar(p: Proyecto) {
        atras.clear()
        adelante.clear()
        botones()
        poner(p)
        seleccion = null
        panel = null
    }

    private fun guardar() {
        atras.addLast(proyecto)
        if (atras.size > MAX_DESHACER) atras.removeFirst()
        adelante.clear()
        botones()
    }

    private fun botones() {
        puedeDeshacer = atras.isNotEmpty()
        puedeRehacer = adelante.isNotEmpty()
    }

    private fun poner(p: Proyecto) {
        proyecto = p
        val d = duracion
        if (tiempo > d) irA(d)
        alCambiar(p)
    }

    /** Si lo elegido ya no existe (tras deshacer, por ejemplo), se suelta. */
    private fun limpiarSeleccion() {
        val s = seleccion ?: return
        val existe = when (s) {
            is Seleccion.Bloque -> Edicion.lugar(proyecto, s.id) != null
            is Seleccion.Paso -> proyecto.principal.dropLast(1).any { it.id == s.id }
            is Seleccion.Sonido -> proyecto.audios.any { it.id == s.id }
        }
        if (!existe) {
            seleccion = null
            panel = null
        }
    }

    // ------------------------------------------------------------------
    // Cabezal y avisos
    // ------------------------------------------------------------------

    fun irA(t: Long) {
        tiempo = t.coerceIn(0, maxOf(0, duracion - 1))
        alBuscar(tiempo)
    }

    fun avisar(texto: String) {
        aviso = texto
        avisoN++
    }

    fun elegir(s: Seleccion?) {
        if (s != seleccion) panel = null
        seleccion = s
        if (s is Seleccion.Paso) panel = Panel.TRANSICION
    }

    // ------------------------------------------------------------------
    // Lo elegido
    // ------------------------------------------------------------------

    val idElegido: Long?
        get() = when (val s = seleccion) {
            is Seleccion.Bloque -> s.id
            is Seleccion.Paso -> s.id
            is Seleccion.Sonido -> s.id
            null -> null
        }

    /** El bloque elegido, o el que sale en una transicion elegida. */
    val clipElegido: Clip?
        get() = idElegido?.let { Edicion.clip(proyecto, it) }

    val capaElegida: Capa?
        get() = (seleccion as? Seleccion.Bloque)?.let { s -> proyecto.capas.firstOrNull { it.id == s.id } }

    val sonidoElegido: PistaAudio?
        get() = (seleccion as? Seleccion.Sonido)?.let { s -> proyecto.audios.firstOrNull { it.id == s.id } }

    val enPrincipal: Boolean
        get() = (seleccion as? Seleccion.Bloque)?.let { s -> proyecto.principal.any { it.id == s.id } } == true

    /** Inicio del bloque elegido en la linea. */
    val inicioElegido: Long?
        get() = idElegido?.let { Edicion.inicio(proyecto, it) }

    /** Instante del cabezal dentro del bloque elegido, o null si cae fuera. */
    val localElegido: Long?
        get() {
            val c = clipElegido ?: return null
            val ini = inicioElegido ?: return null
            val l = tiempo - ini
            return if (l in 0..c.duracionUs) l else null
        }

    /** Cambia el bloque elegido. */
    fun cambiarClip(arrastre: Boolean = false, f: (Clip) -> Clip) {
        val id = idElegido ?: return
        val g: (Proyecto) -> Proyecto = { Edicion.cambiar(it, id, f) }
        if (arrastre) ajustar(g) else hacer(g)
    }

    companion object {
        const val MAX_DESHACER = 100
        const val ZOOM_MIN = 8f
        const val ZOOM_MAX = 900f
    }
}
