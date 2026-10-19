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
import com.royermethod.app.ui.components.Gestos

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
    ANIMACION("Animar"),
    PIEZAS("Partes de la imagen"),
    EFECTOS("Efectos de video"),
    TRANSICION("Transicion"),
    COLOR("Color y filtros"),
    ENCUADRE("Encuadre"),
    TEXTO("Texto"),
    FORMATO("Formato"),
    CURVA("Fotogramas clave"),
    FLUIDEZ("Fluidez"),
    COMPRESION("Compresion"),
    RESOLUCION("Resolucion")
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
    /**
     * Zoom de la linea de tiempo: cuantos dp ocupa un segundo. Mejor cambiarlo
     * con [ponerZoom], que respeta el limite vigente (normal o fino).
     */
    var dpPorSegundo by mutableFloatStateOf(64f)
    /**
     * Zoom fino desbloqueado (mantener pulsado ampliar): deja llegar hasta
     * [ZOOM_FINO_MAX] para colocar claves al milisegundo. Es de esta sesion,
     * no se guarda; [zoomNormal] lo apaga.
     */
    var zoomFino by mutableStateOf(false)
    /**
     * Un panel pide que el de efectos se abra con este efecto (por su id)
     * elegido, p. ej. al convertir una animacion en efecto. Lo consume el
     * panel de efectos y lo vuelve a poner en null.
     */
    var efectoAElegir by mutableStateOf<Long?>(null)
    /** Mensaje corto que se ensena un momento sobre la imagen. */
    var aviso by mutableStateOf<String?>(null)
        private set
    var avisoN by mutableIntStateOf(0)
        private set

    /** Lo llama al cambiar el proyecto (vista previa y guardado). */
    var alCambiar: (Proyecto) -> Unit = {}
    /** Lo llama cuando el usuario mueve el cabezal. */
    var alBuscar: (Long) -> Unit = {}
    /**
     * Para la vista previa en el acto (no un cuadro despues, como al cambiar
     * [reproduciendo]). Lo usa [pausarEn]; EditorScreen lo cablea a
     * Previsualizador.pausar.
     */
    var alPausar: () -> Unit = {}
    /**
     * Mientras suena, donde va la vista previa en un refresco de pantalla
     * (base de System.nanoTime). Lo cablea EditorScreen; null si no hay vista
     * previa (pruebas, escritorio). Da null si la vista previa ya paro.
     */
    var instanteSonando: ((Long) -> Long?)? = null
    /**
     * La linea de tiempo esta moviendo [tiempo] en cada refresco con
     * [instanteSonando]: los avisos de la vista previa, 30 por segundo, sobran.
     */
    var siguiendoReloj = false

    private var gestoAbierto = false
    private var gestoGuardado = false

    /**
     * Lo que el boton atras del sistema cierra antes que la hoja: un
     * desplegable o un catalogo abiertos dentro de un panel. Cada panel apunta
     * aqui el suyo con [alAtras] mientras esta en pantalla; [atras] los prueba
     * del ultimo apuntado al primero. No es estado de Compose: solo se lee al
     * pulsar atras (el BackHandler es de Android, EditorScreen lo consulta).
     */
    private val pilaAtras = ArrayList<() -> Boolean>()

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
        // Si hay un gesto abierto (escribiendo un texto), lo siguiente que
        // cambie tiene que ser un paso nuevo, no pegarse a este
        gestoGuardado = false
        poner(p)
        limpiarSeleccion()
        botones()
    }

    fun rehacer() {
        val p = adelante.removeLastOrNull() ?: return
        atras.addLast(proyecto)
        gestoGuardado = false
        poner(p)
        limpiarSeleccion()
        botones()
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
        val limite = limiteCabezal
        if (tiempo > limite) irA(limite)
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

    /**
     * Hasta donde llega el cabezal: algo mas alla de lo ultimo que haya. Asi se
     * puede ir a cualquier punto y poner ahi un texto o un sonido aunque
     * todavia no haya ningun video.
     */
    val limiteCabezal: Long
        get() = maxOf(duracion, proyecto.audios.maxOfOrNull { it.finUs } ?: 0L) + MARGEN_CABEZAL_US

    fun irA(t: Long) {
        tiempo = t.coerceIn(0, limiteCabezal)
        alBuscar(tiempo)
    }

    /**
     * Deja la reproduccion parada y el cabezal exactamente en [t] (p. ej. al
     * aplicar una animacion: al comienzo del bloque y en pausa).
     *
     * Con `reproduciendo = false; irA(t)` el cabezal no quedaba exacto si
     * estaba sonando: la vista previa recibia la busqueda todavia en marcha,
     * volvia a arrancar el sonido desde [t] y, al llegarle la pausa un cuadro
     * despues, se quedaba donde iba ese reloj (unos milisegundos mas alla).
     * Aqui la pausa llega antes que la busqueda, asi que la vista previa ya
     * esta parada cuando se mueve el cabezal y nada lo vuelve a correr.
     * [alPausar] se llama siempre: puede que la interfaz ya dijera "pausa" y
     * la vista previa todavia no se hubiera enterado; parar algo parado no hace
     * nada.
     */
    fun pausarEn(t: Long) {
        reproduciendo = false
        alPausar()
        irA(t)
    }

    // ------------------------------------------------------------------
    // Zoom de la linea de tiempo
    // ------------------------------------------------------------------

    /** El zoom mayor que se permite ahora mismo. */
    val zoomMaximo: Float
        get() = if (zoomFino) ZOOM_FINO_MAX else ZOOM_MAX

    /**
     * Pone el zoom (dp por segundo) dentro de [ZOOM_MIN]..[zoomMaximo]. Es lo
     * que deben usar el pellizco y los botones de ampliar/reducir de la linea
     * de tiempo, en vez de asignar [dpPorSegundo] con [ZOOM_MAX] a mano: asi el
     * zoom fino, una vez desbloqueado (`zoomFino = true`), llega hasta 1 ms =
     * 60 dp sin tocar nada mas.
     */
    fun ponerZoom(dpPorSegundo: Float) {
        if (dpPorSegundo.isNaN()) return
        this.dpPorSegundo = dpPorSegundo.coerceIn(ZOOM_MIN, zoomMaximo)
    }

    /** Apaga el zoom fino y, si estaba mas ampliado, vuelve al limite normal. */
    fun zoomNormal() {
        zoomFino = false
        if (dpPorSegundo > ZOOM_MAX) dpPorSegundo = ZOOM_MAX
    }

    /**
     * Apunta [cerrar] para el boton atras: devuelve true si cerro algo (y
     * entonces la hoja se queda abierta). Da la funcion que lo quita, para el
     * onDispose de quien lo apunto.
     */
    fun alAtras(cerrar: () -> Boolean): () -> Unit {
        pilaAtras.add(cerrar)
        return { pilaAtras.remove(cerrar) }
    }

    /** El boton atras: true si algun panel lo uso para cerrar lo suyo. */
    fun atras(): Boolean {
        for (i in pilaAtras.indices.reversed()) if (pilaAtras[i]()) return true
        return false
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
        /** Zoom fino: 60 000 dp por segundo, o sea 1 ms = 60 dp (se coloca con el dedo). */
        const val ZOOM_FINO_MAX = 60_000f
        /** Lo que se puede pasar el cabezal del final. */
        const val MARGEN_CABEZAL_US = 30_000_000L
    }
}
