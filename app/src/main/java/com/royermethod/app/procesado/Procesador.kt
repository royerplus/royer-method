package com.royermethod.app.procesado

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * El trabajo en curso, fuera de la pantalla.
 *
 * Antes el procesado vivia dentro de la pantalla de progreso: era un
 * LaunchedEffect atado a la composicion. Eso significa que al salir de la app
 * y volver, la pantalla se recomponia y el trabajo empezaba de cero, con lo
 * que veinte minutos de interpolacion se tiraban a la basura por mirar un
 * mensaje.
 *
 * Ahora el trabajo vive aqui y lo empuja un servicio en primer plano. La
 * pantalla solo mira. Si se sale y se vuelve, se encuentra el trabajo donde
 * estaba.
 */
object Procesador {

    /** El encargo que se esta haciendo, o el ultimo que se hizo. */
    var trabajo: Trabajo? = null
        private set

    var avance by mutableStateOf(Avance(Tareas.PREPARAR, 0, 0, 0f))
        internal set

    var salida by mutableStateOf<Salida?>(null)
        internal set

    var fallo by mutableStateOf<String?>(null)
        internal set

    var corriendo by mutableStateOf(false)
        internal set

    /** Cuando arranco, para el cronometro de la pantalla. */
    var arranqueMs by mutableLongStateOf(0L)
        internal set

    /** Se pide desde la pantalla; lo cumple el servicio. */
    var cancelado = false
        internal set

    /**
     * Encargos en espera, para dejarlo trabajando solo.
     *
     * Con la cola encendida, procesar no arranca nada: apila. Cuando estan
     * todos, se lanza y van uno detras de otro. Es para dejarlo toda la noche,
     * asi que lo que sale se guarda solo en la galeria: no va a haber nadie
     * delante para darle a guardar.
     */
    val cola = mutableStateListOf<Trabajo>()

    var enLote by mutableStateOf(false)

    /** Cuantos van hechos de la cola. */
    var hechosDeLote by mutableIntStateOf(0)
        internal set

    fun encolar(nuevo: Trabajo) {
        cola.add(nuevo)
    }

    fun quitarDeCola(i: Int) {
        if (i in cola.indices) cola.removeAt(i)
    }

    fun vaciarCola() {
        cola.clear()
        hechosDeLote = 0
    }

    fun encargar(nuevo: Trabajo) {
        trabajo = nuevo
        avance = Avance(Tareas.PREPARAR, 0, 0, 0f)
        salida = null
        fallo = null
        arranqueMs = System.currentTimeMillis()
    }

    /** Deja todo listo para empezar de cero. */
    fun empezar(nuevo: Trabajo) {
        cancelado = false
        encargar(nuevo)
    }

    /** Prepara la cola para lanzarse. */
    fun arrancarCola() {
        cancelado = false
        hechosDeLote = 0
        salida = null
        fallo = null
        arranqueMs = System.currentTimeMillis()
        trabajo = cola.firstOrNull()
    }

    /** ¿Termino todo lo que habia que hacer? */
    val terminado: Boolean
        get() = !corriendo && cola.isEmpty()

    fun cancelar() {
        cancelado = true
    }

    /** Deja el sitio libre para el siguiente encargo. */
    fun limpiar() {
        trabajo = null
        salida = null
        fallo = null
        corriendo = false
        cancelado = false
    }
}

/** Lo que se dice si se intenta mejorar algo mientras el editor exporta. */
const val ESPERA_EXPORTACION = "El editor esta exportando un video. Espera a que termine y vuelve a intentarlo."
