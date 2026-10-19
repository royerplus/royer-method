package com.royermethod.app.editor.motor

/**
 * Donde empezar a decodificar un video para llegar bien a un instante.
 *
 * Para saltar se le pide al archivo el fotograma "sync" anterior: uno que se
 * decodifica sin los de antes. Pero no todos los archivos los marcan bien. Los
 * que exporto esta app antes de corregirlo marcaban tambien el B que el
 * codificador manda justo detras de cada IDR, y hay archivos sin indice, que
 * dan todos por sync. Si se empieza ahi, el decodificador no tiene el IDR en
 * que se apoya todo lo demas, y hasta el siguiente (uno o varios segundos) solo
 * saca gris, negro o nada, mientras el sonido sigue.
 *
 * Por eso se mira el paquete donde se cae. Si no es un punto de entrada de
 * verdad, se busca el ultimo que haya antes, mirando cada vez desde mas atras.
 * En un archivo bien hecho solo cuesta leer ese paquete.
 */
internal object EntradaVideo {

    /** Lo que hace falta del lector del archivo (MediaExtractor; en las pruebas, uno de mentira). */
    interface Muestras {
        /** Va al sync que el archivo da para [t] (el anterior); devuelve su instante, o -1. */
        fun buscar(t: Long): Long
        /** Pasa al siguiente paquete en el orden de decodificar; false si no hay. */
        fun avanzar(): Boolean
        /** Instante del paquete actual, -1 si no hay. */
        val instante: Long
        /** ¿Se puede empezar a decodificar en el actual? null si no se entiende. */
        fun entrada(): Boolean?
    }

    /** Primer paso hacia atras; luego se dobla. */
    const val PASO_US = 500_000L
    /** 0,5 s, 1, 2, 4, 8, 16, 32 y 64 s hacia atras como mucho. */
    const val MAX_INTENTOS = 8
    /** Paquetes que se miran en cada intento. */
    const val MAX_MUESTRAS = 4000

    /**
     * Deja [m] donde empezar a decodificar para llegar a [objetivo].
     *
     * @return el instante del paquete donde queda, como [Muestras.buscar]
     */
    fun posicionar(m: Muestras, objetivo: Long): Long {
        val inicio = m.buscar(objetivo)
        // Lo normal: el sync del archivo vale (o no se sabe leer, y se deja como siempre)
        if (inicio < 0 || m.entrada() != false) return inicio
        var retroceso = 0L
        for (intento in 0 until MAX_INTENTOS) {
            // No un paso minimo: con B, el sync de justo antes puede ir detras
            // del de donde se cayo en el orden de decodificar
            retroceso = if (retroceso == 0L) PASO_US else retroceso * 2
            val desde = m.buscar(maxOf(inicio - retroceso, 0L))
            if (desde < 0) break
            if (desde < inicio) {
                // De ahi hacia delante, hasta donde se habia caido: el ultimo
                // punto de entrada de verdad que no pase del objetivo
                var ultima = -1L
                var vistas = 0
                // Lo que va antes de donde se cayo no se ensena mucho despues:
                // pasado eso, ya se esta por detras y no hay nada que buscar
                val tope = maxOf(inicio, objetivo) + PASO_US
                while (m.instante != inicio && m.instante <= tope && vistas < MAX_MUESTRAS) {
                    if (m.instante in 0..objetivo && m.entrada() == true) ultima = m.instante
                    vistas++
                    if (!m.avanzar()) break
                }
                // Buscando su instante se cae en el o antes, y de ahi se pasa por el
                if (ultima >= 0) return m.buscar(ultima)
            }
            // Ya se miro desde el principio del archivo
            if (inicio - retroceso <= 0) break
        }
        // No hay ninguno que se entienda: como siempre
        return m.buscar(objetivo)
    }
}
