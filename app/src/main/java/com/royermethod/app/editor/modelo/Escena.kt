package com.royermethod.app.editor.modelo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Que hay que dibujar en un fotograma, sin dibujar nada.
 *
 * Esta separado del motor de OpenGL a proposito: aqui se decide todo lo que
 * puede salir mal (que bloque toca, en que instante del archivo, donde cae,
 * cuanto se ha movido durante el fotograma) y eso se puede probar en el
 * ordenador sin telefono. El motor solo obedece.
 *
 * El desenfoque de movimiento sale de aqui. Un fotograma de 30 fps no es un
 * instante: es un trozo de 33 ms. Si algo se mueve durante ese trozo, una
 * camara real lo veria corrido. Por eso cada bloque no se describe con una
 * posicion sino con varias, repartidas por el tiempo de exposicion, y el
 * motor las promedia. Es lo que hace que una transicion a 30 fps se vea tan
 * fluida como a 60.
 */
class Escena(
    private val proyecto: Proyecto,
    /** Pixeles del lienzo de salida. */
    private val ancho: Int,
    private val alto: Int,
    /** Medidas propias del contenido de un bloque, en pixeles. */
    private val medidor: Medidor
) {
    private val inicios = Linea.inicios(proyecto.principal)
    private val duracionFotograma = 1_000_000.0 / Linea.FPS

    /**
     * Fraccion del fotograma que se "expone". 0.5 es el obturador de 180
     * grados del cine: rastro suficiente para que el movimiento sea continuo
     * sin que se vuelva una mancha.
     */
    private val obturador = 0.5

    fun cuadro(n: Long): Cuadro = cuadroEn(Linea.tiempoDe(n), n)

    /** Para la vista previa al arrastrar, que no cae en fotogramas exactos. */
    fun cuadroEn(t: Long, n: Long = Linea.fotogramaEn(t)): Cuadro {
        val k = if (proyecto.salida.desenfoqueMovimiento) proyecto.salida.muestras.coerceIn(1, MAX_MUESTRAS) else 1
        val pasos = ArrayList<Paso>()
        val en = Linea.principalEn(proyecto.principal, inicios, t)

        // 1. fondo, 2. pista principal
        pasos.add(PasoSolido(colorFondo(), FloatArray(1) { 1f }))
        if (en != null) {
            if (en.segundo == null) {
                escena(pasos, en.primero, t, t - en.localPrimero, k, Lado())
            } else {
                transicion(pasos, en, t, k)
            }
        }

        // 3. capas, de abajo arriba
        for (c in Linea.capasEn(proyecto.capas, t)) {
            pasos.add(dibujo(c.clip, t, c.inicioUs, k, c.mezcla))
        }

        // Las escenas que al final no se mueven ni se mezclan van directas
        val planos = pasos.map { it.simplificado() }.flatMap {
            if (it is PasoGrupo && it.directo) it.pasos else listOf(it)
        }
        // Lo que queda debajo de algo opaco que cubre todo no se ve: ni se
        // dibuja ni se decodifica (el lado A cuando la escena B ya esta entera
        // encima, todo en el momento negro de un fundido a negro...).
        val tapa = planos.indexOfLast { it is PasoSolido && it.opacidadMedia >= 0.9999f }
        val pasosFinales = if (tapa > 0) planos.subList(tapa, planos.size) else planos
        return Cuadro(n, t, pasosFinales)
    }

    private fun colorFondo(): Int = when (proyecto.fondo) {
        Fondo.BLANCO -> 0xFFFFFFFF.toInt()
        else -> 0xFF000000.toInt()
    }

    /** Instantes de las muestras alrededor de [t], dentro de la exposicion. */
    private fun instantes(t: Long, k: Int): LongArray {
        if (k <= 1) return longArrayOf(t)
        val exp = duracionFotograma * obturador
        return LongArray(k) { i -> (t + ((i + 0.5) / k - 0.5) * exp).toLong() }
    }

    /**
     * Un bloque en [t], con todas sus muestras.
     *
     * @param inicio donde empieza el bloque en la linea de tiempo
     */
    private fun dibujo(
        clip: Clip,
        t: Long,
        inicio: Long,
        k: Int,
        mezcla: Mezcla,
        transicion: ((Int) -> Efecto)? = null
    ): PasoClip {
        val ts = instantes(t, k)
        val matrices = ArrayList<FloatArray>(ts.size)
        val opacidades = FloatArray(ts.size)
        // Una muestra sin mascara lleva el borde tan lejos que no recorta nada
        val bordes = FloatArray(ts.size) { SIN_BORDE }
        var mascara: Mascara? = null
        var desenfoque = 0f
        var escalaMax = 0f
        var anchoMax = 0f
        var altoMax = 0f

        val (cw, ch) = medidor.medidas(clip, alto)
        for ((i, ti) in ts.withIndex()) {
            val local = ti - inicio
            var e = encuadreEn(clip, local)
            var op = e.opacidad
            // Entrada y salida
            val anim = animacion(clip, local)
            e = anim.aplicar(e)
            op *= anim.opacidad
            // Lo que imponga la transicion encima
            var conMascara = false
            if (transicion != null) {
                val a = transicion(i)
                e = a.aplicar(e)
                op *= a.opacidad
                if (a.desenfoque > 0f) desenfoque += a.desenfoque / ts.size
                if (a.mascara != null) {
                    mascara = a.mascara
                    bordes[i] = a.borde
                    conMascara = true
                }
            }
            if (anim.revelar >= 0f && !conMascara) {
                // La mascara de revelar va en el espacio del lienzo, a lo ancho
                // de donde cae el bloque.
                val caja = caja(clip, e, cw, ch)
                mascara = Mascara(1f, 0f, suave = 6f)
                bordes[i] = caja.first + (caja.second - caja.first) * anim.revelar - ancho / 2f
            }
            val m = matriz(clip, e, cw, ch)
            matrices.add(m)
            opacidades[i] = op.coerceIn(0f, 1f)
            escalaMax = max(escalaMax, e.escala)
            val (w, h) = tamano(clip, e, cw, ch)
            anchoMax = max(anchoMax, w)
            altoMax = max(altoMax, h)
        }

        val localCentro = t - inicio
        return PasoClip(
            clip = clip,
            tiempoArchivoUs = clip.tiempoArchivo(localCentro),
            exposicionUs = if (ts.size > 1 || clip.fluido) (duracionFotograma * obturador * clip.velocidad).toLong() else 0,
            matrices = matrices,
            opacidades = opacidades,
            mascara = mascara,
            bordes = bordes,
            color = clip.colorFinal(),
            mezcla = mezcla,
            desenfoque = desenfoque,
            esFondo = false,
            anchoEnLienzo = anchoMax,
            altoEnLienzo = altoMax,
            escalaTexto = escalaMax
        )
    }

    /**
     * El clip ampliado y desenfocado detras, para que no queden franjas.
     *
     * Solo cuando hace falta: si el bloque de delante [delante] tapa todo el
     * lienzo en todas sus muestras, el fondo no se veria y no se pide. La
     * cuenta se hace con las matrices reales, asi que tambien sale cuando el
     * bloque deja hueco solo durante una animacion o una transicion.
     */
    private fun fondoDe(delante: PasoClip, local: Long): PasoClip? {
        val clip = delante.clip
        if (clip.esTexto) return null
        if (cubre(delante)) return null
        val (cw, ch) = medidor.medidas(clip, alto)
        if (cw <= 0f || ch <= 0f) return null
        val e = Encuadre(ajuste = Ajuste.CUBRIR, escala = 1.15f)
        val m = matriz(clip, e, cw, ch)
        return PasoClip(
            clip = clip,
            tiempoArchivoUs = clip.tiempoArchivo(local),
            // La misma exposicion que el clip de delante: si es fluido, los dos
            // piden el mismo fotograma generado y se calcula una sola vez.
            exposicionUs = delante.exposicionUs,
            matrices = listOf(m),
            opacidades = floatArrayOf(1f),
            mascara = null,
            bordes = FloatArray(1),
            color = clip.colorFinal().mas(AjustesColor(brillo = -0.12f, saturacion = 0.1f), 1f),
            mezcla = Mezcla.NORMAL,
            desenfoque = 0f,
            esFondo = true,
            anchoEnLienzo = ancho * 1.15f,
            altoEnLienzo = alto * 1.15f,
            escalaTexto = 1f
        )
    }

    /** ¿El bloque tapa el lienzo entero en todas sus muestras? */
    private fun cubre(p: PasoClip): Boolean {
        if (p.mascara != null) return false
        val r = p.clip.encuadre.recorte
        val margen = 1e-4f
        val esquinas = floatArrayOf(0f, 0f, ancho.toFloat(), 0f, 0f, alto.toFloat(), ancho.toFloat(), alto.toFloat())
        for (m in p.matrices) {
            for (j in 0 until 4) {
                val x = esquinas[j * 2]
                val y = esquinas[j * 2 + 1]
                val u = m[0] * x + m[3] * y + m[6]
                val v = m[1] * x + m[4] * y + m[7]
                if (u < r.izquierda - margen || u > 1f - r.derecha + margen) return false
                if (v < r.arriba - margen || v > 1f - r.abajo + margen) return false
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Transiciones
    // ------------------------------------------------------------------

    /**
     * Lo que una transicion le hace a uno de sus dos lados.
     *
     * Cada lado es una escena entera: su fondo y su bloque. Lo que mueve o
     * descubre la transicion (empujar, la linea, el fundido) se aplica a la
     * escena ya montada, no a cada pieza por separado. Si se aplicara a cada
     * pieza, en el borde suave de un barrido o en la estela de un empujon se
     * mezclarian tres cosas (el bloque, su fondo y lo de detras) y saldria una
     * franja oscura moviendose. Montada aparte, la mezcla es exacta.
     */
    private class Lado(
        /** Al bloque, dentro de su escena: zoom, giro, desenfoque. */
        val alClip: ((Int) -> Efecto)? = null,
        /** A la escena entera: opacidad, desplazamiento, mascara. */
        val aEscena: ((Int) -> Efecto)? = null,
        /**
         * La escena se alarga con sus bordes. Para la que queda debajo de otra
         * que entra empujando: asi en la junta no queda ni un pixel de hueco.
         */
        val extender: Boolean = false
    )

    private fun transicion(pasos: MutableList<Paso>, en: EnPrincipal, t: Long, k: Int) {
        val a = en.primero
        val b = en.segundo!!
        val inicioA = t - en.localPrimero
        val inicioB = t - en.localSegundo
        val solape = Linea.solape(a, b)
        val ts = instantes(t, k)
        // Avance de la transicion en cada muestra. Sin curva lineal: arrancar
        // y frenar suave es lo que hace que no parezca un corte con prisa.
        val ps = FloatArray(ts.size) { i ->
            val p = if (solape > 0) ((ts[i] - inicioB).toDouble() / solape).toFloat() else 1f
            suave(p.coerceIn(0f, 1f))
        }
        val tipo = a.transicion
        val extension = (abs(DIR_LINEA.first) * ancho + abs(DIR_LINEA.second) * alto) / 2f
        val grosorLinea = alto * 0.012f
        fun borde(p: Float): Float {
            // Desde fuera del todo por un lado hasta fuera del todo por el otro,
            // contando el halo de la linea, para que no aparezca ya a medias.
            val margen = grosorLinea * 6f
            return -extension - margen + (2 * extension + 2 * margen) * p
        }
        // Cambio seco en la mitad, para las que lo tapan con algo. El lado A
        // no se apaga: la escena B es opaca y lo tapa, y asi en el fotograma
        // del cambio la mezcla de los dos es exacta y no se oscurece.
        val despues = { i: Int -> Efecto(opacidad = if (ps[i] < 0.5f) 0f else 1f) }

        var ladoA = Lado()
        var ladoB = Lado(aEscena = { i -> Efecto(opacidad = ps[i]) })
        val encima = ArrayList<Paso>()

        when (tipo) {
            Transicion.NEGRO -> {
                ladoB = Lado(aEscena = despues)
                // Negro del todo un momento alrededor del cambio: el cambio
                // queda siempre tapado, caiga donde caiga dentro del fotograma.
                encima.add(PasoSolido(0xFF000000.toInt(), FloatArray(ts.size) { i ->
                    ((1f - abs(ps[i] * 2f - 1f)) * 1.25f).coerceIn(0f, 1f)
                }, debajo = false))
            }

            Transicion.DESTELLO -> {
                ladoB = Lado(aEscena = despues)
                // Pico en el centro, con caida rapida: un destello, no un fundido a blanco
                encima.add(PasoSolido(0xFFFFFFFF.toInt(), FloatArray(ts.size) { i ->
                    ((1f - abs(ps[i] * 2f - 1f)) * 1.15f).coerceIn(0f, 1f).pow(1.6f)
                }, debajo = false))
            }

            Transicion.LINEA, Transicion.BARRIDO -> {
                val suaveBorde = if (tipo == Transicion.LINEA) 1.5f else alto * 0.04f
                val mascara = Mascara(DIR_LINEA.first, DIR_LINEA.second, suaveBorde)
                ladoB = Lado(aEscena = { i -> Efecto(mascara = mascara, borde = borde(ps[i])) })
                if (tipo == Transicion.LINEA) {
                    encima.add(
                        PasoLinea(
                            dirX = DIR_LINEA.first, dirY = DIR_LINEA.second,
                            bordes = FloatArray(ts.size) { borde(ps[it]) },
                            grosor = grosorLinea,
                            color = 0xFFFFFFFF.toInt()
                        )
                    )
                }
            }

            Transicion.EMPUJAR, Transicion.EMPUJAR_ARRIBA -> {
                val horizontal = tipo == Transicion.EMPUJAR
                ladoA = Lado(
                    aEscena = { i -> if (horizontal) Efecto(dx = -ps[i]) else Efecto(dy = -ps[i]) },
                    extender = true
                )
                ladoB = Lado(aEscena = { i -> if (horizontal) Efecto(dx = 1f - ps[i]) else Efecto(dy = 1f - ps[i]) })
            }

            Transicion.DESLIZAR -> {
                // El de debajo se aleja un poco mientras lo tapan: da profundidad
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f - 0.06f * ps[i]) })
                ladoB = Lado(aEscena = { i -> Efecto(dx = 1f - ps[i]) })
            }

            Transicion.ZOOM -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 0.9f * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 0.9f * (1f - ps[i])) },
                    aEscena = { i -> Efecto(opacidad = paso(0.35f, 0.65f, ps[i])) }
                )
            }

            Transicion.DESENFOQUE -> {
                val radio = alto * 0.03f
                ladoA = Lado(alClip = { i -> Efecto(desenfoque = radio * min(1f, ps[i] * 2f)) })
                ladoB = Lado(
                    alClip = { i -> Efecto(desenfoque = radio * min(1f, (1f - ps[i]) * 2f)) },
                    aEscena = { i -> Efecto(opacidad = paso(0.3f, 0.7f, ps[i])) }
                )
            }

            Transicion.GIRO -> {
                ladoA = Lado(alClip = { i -> Efecto(rotacion = 90f * ps[i], escala = 1f + ps[i] * 0.5f) })
                ladoB = Lado(
                    alClip = { i -> Efecto(rotacion = -90f * (1f - ps[i]), escala = 1f + (1f - ps[i]) * 0.5f) },
                    aEscena = despues
                )
            }

            // Fundido, y cualquier cosa que no tenga dibujo propio
            else -> Unit
        }

        escena(pasos, a, t, inicioA, k, ladoA)
        escena(pasos, b, t, inicioB, k, ladoB)
        pasos.addAll(encima)
    }

    /** Un lado de la transicion: su fondo y su bloque, aparte si hace falta. */
    private fun escena(pasos: MutableList<Paso>, clip: Clip, t: Long, inicio: Long, k: Int, lado: Lado) {
        val dentro = ArrayList<Paso>(3)
        val delante = dibujo(clip, t, inicio, k, Mezcla.NORMAL, lado.alClip)
        if (proyecto.fondo == Fondo.DESENFOCADO) fondoDe(delante, t - inicio)?.let { dentro.add(it) }
        dentro.add(delante)

        val ef = lado.aEscena
        if (ef == null) {
            // Se queda en su sitio y entera: directamente sobre el lienzo
            pasos.addAll(dentro)
            return
        }
        val n = instantes(t, k).size
        val efs = List(n) { ef(it) }
        pasos.add(
            PasoGrupo(
                // Con su color de fondo: la escena es opaca y tapa la otra
                pasos = listOf(PasoSolido(colorFondo(), floatArrayOf(1f))) + dentro,
                matrices = efs.map { desplazamiento(it.dx, it.dy) },
                opacidades = FloatArray(n) { efs[it].opacidad.coerceIn(0f, 1f) },
                mascara = efs.firstNotNullOfOrNull { it.mascara },
                bordes = FloatArray(n) { if (efs[it].mascara != null) efs[it].borde else SIN_BORDE },
                extender = lado.extender
            )
        )
    }

    /** Del lienzo a la escena movida [dx], [dy] lienzos. */
    private fun desplazamiento(dx: Float, dy: Float): FloatArray =
        floatArrayOf(1f / ancho, 0f, 0f, 0f, 1f / alto, 0f, -dx, -dy, 1f)

    // ------------------------------------------------------------------
    // Encuadre en el tiempo
    // ------------------------------------------------------------------

    /** El encuadre a [local] del inicio, con los fotogramas clave aplicados. */
    fun encuadreEn(clip: Clip, local: Long): Encuadre {
        val claves = clip.claves
        var e = if (claves.isEmpty()) clip.encuadre else {
            val ordenadas = claves.sortedBy { it.tiempoUs }
            when {
                local <= ordenadas.first().tiempoUs -> ordenadas.first().encuadre
                local >= ordenadas.last().tiempoUs -> ordenadas.last().encuadre
                else -> {
                    var r = ordenadas.last().encuadre
                    for (i in 0 until ordenadas.size - 1) {
                        val a = ordenadas[i]
                        val b = ordenadas[i + 1]
                        if (local >= a.tiempoUs && local < b.tiempoUs) {
                            val f = (local - a.tiempoUs).toFloat() / (b.tiempoUs - a.tiempoUs)
                            r = a.encuadre.hacia(b.encuadre, curva(a.curva, f))
                            break
                        }
                    }
                    r
                }
            }
        }
        // Lo que no se anima sale siempre del clip: recortar o cambiar el
        // ajuste no tiene que obligar a rehacer cada fotograma clave.
        e = e.copy(ajuste = clip.encuadre.ajuste, recorte = clip.encuadre.recorte, espejo = clip.encuadre.espejo)
        return continua(clip, local, e)
    }

    private fun continua(clip: Clip, local: Long, e: Encuadre): Encuadre {
        val s = local / 1_000_000f
        return when (clip.continua) {
            AnimacionContinua.NINGUNA -> e
            AnimacionContinua.ACERCAMIENTO -> {
                val f = (local.toFloat() / clip.duracionUs.coerceAtLeast(1)).coerceIn(0f, 1f)
                e.copy(escala = e.escala * (1f + 0.15f * f))
            }
            AnimacionContinua.PULSO -> {
                // Medio segundo por latido: encaja con la mayoria de musica de edits
                val fase = (s / 0.5f) % 1f
                val golpe = (1f - fase).pow(3)
                e.copy(escala = e.escala * (1f + 0.05f * golpe))
            }
            AnimacionContinua.TEMBLOR -> {
                val amp = 0.012f
                e.copy(
                    x = e.x + amp * ruido(s * 9f, 1.3f),
                    y = e.y + amp * ruido(s * 9f, 7.1f),
                    rotacion = e.rotacion + 0.8f * ruido(s * 7f, 3.7f),
                    escala = e.escala * 1.03f
                )
            }
            AnimacionContinua.BALANCEO -> e.copy(rotacion = e.rotacion + 3f * sin(2f * PI.toFloat() * s / 2f))
        }
    }

    /** Lo que aportan las animaciones de entrada y salida en [local]. */
    private fun animacion(clip: Clip, local: Long): Efecto {
        var r = Efecto()
        if (clip.entrada != Animacion.NINGUNA && clip.duracionEntradaUs > 0) {
            val q = (local.toFloat() / clip.duracionEntradaUs).coerceIn(0f, 1f)
            if (q < 1f) r = r.y(efecto(clip.entrada, q))
        }
        if (clip.salida != Animacion.NINGUNA && clip.duracionSalidaUs > 0) {
            val restante = clip.duracionUs - local
            val q = (restante.toFloat() / clip.duracionSalidaUs).coerceIn(0f, 1f)
            if (q < 1f) r = r.y(efecto(clip.salida, q))
        }
        return r
    }

    /**
     * Una animacion con [q] de 0 (fuera) a 1 (en su sitio). La salida es la
     * misma animacion recorrida al reves: entrar desde la izquierda y salir
     * hacia la izquierda son la misma cuenta.
     */
    private fun efecto(a: Animacion, q: Float): Efecto {
        val f = salidaSuave(q)
        return when (a) {
            Animacion.NINGUNA -> Efecto()
            Animacion.APARECER -> Efecto(opacidad = f)
            Animacion.ZOOM_ENTRA -> Efecto(escala = 1.35f - 0.35f * f, opacidad = min(1f, q * 2f))
            Animacion.ZOOM_SALE -> Efecto(escala = 0.7f + 0.3f * f, opacidad = min(1f, q * 2f))
            Animacion.IZQUIERDA -> Efecto(dx = -(1f - f))
            Animacion.DERECHA -> Efecto(dx = (1f - f))
            Animacion.ARRIBA -> Efecto(dy = -(1f - f))
            Animacion.ABAJO -> Efecto(dy = (1f - f))
            Animacion.GIRO -> Efecto(rotacion = -120f * (1f - f), escala = 0.5f + 0.5f * f, opacidad = min(1f, q * 2f))
            Animacion.REBOTE -> Efecto(escala = rebote(q), opacidad = min(1f, q * 3f))
            Animacion.REVELAR -> Efecto(revelar = f)
        }
    }

    // ------------------------------------------------------------------
    // Geometria
    // ------------------------------------------------------------------

    /** Escala base que da el ajuste, antes de la escala del usuario. */
    private fun base(clip: Clip, e: Encuadre, cw: Float, ch: Float): Float {
        if (clip.esTexto) return 1f
        val w = cw * e.recorte.ancho
        val h = ch * e.recorte.alto
        if (w <= 0f || h <= 0f) return 1f
        return if (e.ajuste == Ajuste.CUBRIR) max(ancho / w, alto / h) else min(ancho / w, alto / h)
    }

    private fun tamano(clip: Clip, e: Encuadre, cw: Float, ch: Float): Pair<Float, Float> {
        val s = base(clip, e, cw, ch) * e.escala
        return cw * s to ch * s
    }

    /** Extremos izquierdo y derecho del bloque sobre el lienzo, en pixeles. */
    private fun caja(clip: Clip, e: Encuadre, cw: Float, ch: Float): Pair<Float, Float> {
        val s = base(clip, e, cw, ch) * e.escala
        val w = cw * e.recorte.ancho * s
        val h = ch * e.recorte.alto * s
        val r = Math.toRadians(e.rotacion.toDouble())
        val mitad = (abs(cos(r)) * w + abs(sin(r)) * h).toFloat() / 2f
        val cx = ancho / 2f + e.x * ancho
        return (cx - mitad) to (cx + mitad)
    }

    /**
     * Del pixel del lienzo a la coordenada de textura del bloque.
     *
     * Es la inversa de colocar el bloque: en vez de mover la imagen al
     * lienzo, cada pixel del lienzo pregunta de que parte de la imagen viene.
     * Asi el sombreador puede pedir varias posiciones por pixel, una por
     * muestra del obturador, y promediarlas. Devuelve una matriz 3x3 por
     * columnas, lista para OpenGL.
     */
    fun matriz(clip: Clip, e: Encuadre, cw: Float, ch: Float): FloatArray {
        val s = base(clip, e, cw, ch) * e.escala
        val w = cw * e.recorte.ancho * s
        val h = ch * e.recorte.alto * s
        // Un bloque encogido a nada (el arranque de un rebote, por ejemplo)
        // dividiria entre cero. Se manda todo fuera de la textura: no se ve.
        if (w < 1e-3f || h < 1e-3f) return FUERA.copyOf()
        val cx = ancho / 2f + e.x * ancho
        val cy = alto / 2f + e.y * alto
        val r = Math.toRadians(e.rotacion.toDouble())
        val c = cos(r).toFloat()
        val sn = sin(r).toFloat()
        // p -> q = p - centro -> girar al reves -> dividir por el tamano -> +0.5
        // Girar al reves en coordenadas con la y hacia abajo: [c s; -s c]
        var a = c / w; var b = sn / w
        var d = -sn / h; var f = c / h
        var tx = -(a * cx + b * cy) + 0.5f
        var ty = -(d * cx + f * cy) + 0.5f
        if (e.espejo) {
            a = -a; b = -b; tx = 1f - tx
        }
        // Del bloque recortado a la textura entera
        val rc = e.recorte
        a *= rc.ancho; b *= rc.ancho; tx = tx * rc.ancho + rc.izquierda
        d *= rc.alto; f *= rc.alto; ty = ty * rc.alto + rc.arriba
        return floatArrayOf(a, d, 0f, b, f, 0f, tx, ty, 1f)
    }

    companion object {
        const val MAX_MUESTRAS = 8

        /**
         * Borde de mascara que no recorta nada. Lejos de cualquier lienzo pero
         * dentro de lo que cabe en un float de media precision (65504).
         */
        const val SIN_BORDE = 30_000f

        /** Matriz que lleva cualquier pixel fuera de la imagen. */
        private val FUERA = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -1f, 1f)

        /** Direccion del barrido: casi horizontal, con algo de inclinacion. */
        private val DIR_LINEA: Pair<Float, Float> = run {
            val ang = Math.toRadians(-14.0)
            cos(ang).toFloat() to sin(ang).toFloat()
        }

        fun suave(x: Float): Float = x * x * (3f - 2f * x)

        fun salidaSuave(x: Float): Float = 1f - (1f - x).pow(3)

        fun paso(a: Float, b: Float, x: Float): Float = suave(((x - a) / (b - a)).coerceIn(0f, 1f))

        fun curva(c: Curva, x: Float): Float = when (c) {
            Curva.LINEAL -> x
            Curva.SUAVE -> if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
            Curva.ENTRADA -> x * x * x
            Curva.SALIDA -> 1 - (1 - x).pow(3)
        }

        /** Sale de pequeno, se pasa un poco y vuelve: el rebote. */
        fun rebote(q: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val x = q - 1f
            return (1f + c3 * x * x * x + c1 * x * x).coerceAtLeast(0f)
        }

        /** Ruido suave y repetible: el mismo instante da siempre el mismo temblor. */
        fun ruido(x: Float, semilla: Float): Float {
            val i = floor(x)
            val f = x - i
            fun h(n: Float): Float {
                val v = sin(n * 127.1f + semilla * 311.7f) * 43758.547f
                return (v - floor(v)) * 2f - 1f
            }
            return h(i) + (h(i + 1f) - h(i)) * suave(f)
        }
    }
}

/** Mide el contenido de un bloque: el archivo, o el texto ya maquetado. */
fun interface Medidor {
    /** Ancho y alto del contenido en pixeles, para un lienzo de [altoLienzo]. */
    fun medidas(clip: Clip, altoLienzo: Int): Pair<Float, Float>
}

/**
 * Lo que una animacion o transicion le hace a un bloque en un instante.
 * Se aplica encima del encuadre que ya traiga.
 */
data class Efecto(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val escala: Float = 1f,
    val rotacion: Float = 0f,
    val opacidad: Float = 1f,
    val desenfoque: Float = 0f,
    val mascara: Mascara? = null,
    val borde: Float = 0f,
    /** -1 sin revelar; 0..1 cuanto se ha descubierto. */
    val revelar: Float = -1f
) {
    fun aplicar(e: Encuadre): Encuadre = e.copy(
        x = e.x + dx,
        y = e.y + dy,
        escala = e.escala * escala,
        rotacion = e.rotacion + rotacion
    )

    /** Dos efectos a la vez (entrada y salida en un bloque muy corto). */
    fun y(o: Efecto): Efecto = Efecto(
        dx + o.dx, dy + o.dy, escala * o.escala, rotacion + o.rotacion,
        opacidad * o.opacidad, desenfoque + o.desenfoque, mascara ?: o.mascara,
        if (mascara != null) borde else o.borde,
        if (revelar >= 0f) revelar else o.revelar
    )
}

/**
 * Un corte recto sobre el lienzo: se ve lo que el borde ya ha barrido.
 *
 * Con d la distancia de un pixel al centro del lienzo medida en la direccion
 * del barrido, el pixel se ve si d < borde. El borde avanza en esa direccion.
 *
 * @param dirX,dirY hacia donde avanza el borde (vector unitario)
 * @param suave pixeles de transicion del borde, para que no salga dentado
 */
data class Mascara(
    val dirX: Float,
    val dirY: Float,
    val suave: Float
)

/** Un fotograma descrito: que dibujar, en orden de abajo arriba. */
data class Cuadro(val numero: Long, val tiempoUs: Long, val pasos: List<Paso>) {
    /** Bloques que tienen que tener su fotograma listo para dibujar este. */
    val clips: List<PasoClip> get() = pasos.flatMap {
        when (it) {
            is PasoClip -> listOf(it)
            is PasoGrupo -> if (it.visible) it.pasos.filterIsInstance<PasoClip>() else emptyList()
            else -> emptyList()
        }
    }
}

sealed class Paso {
    /** Quita muestras repetidas: si nada se mueve, una sola posicion basta. */
    open fun simplificado(): Paso = this
}

/** Un color liso que cubre todo el lienzo. */
data class PasoSolido(
    val argb: Int,
    val opacidades: FloatArray,
    /** Solo informativo: los solidos de fondo van debajo de todo. */
    val debajo: Boolean = true
) : Paso() {
    val opacidadMedia: Float get() = opacidades.average().toFloat()
}

/** La linea brillante de la transicion de barrido. */
data class PasoLinea(
    val dirX: Float,
    val dirY: Float,
    /** Posicion del borde en cada muestra, en pixeles desde el centro. */
    val bordes: FloatArray,
    val grosor: Float,
    val color: Int
) : Paso()

data class PasoClip(
    val clip: Clip,
    /** Instante del archivo en el centro del fotograma. */
    val tiempoArchivoUs: Long,
    /**
     * Cuanto tiempo del archivo cubre la exposicion. Con el clip en modo
     * fluido, el motor genera fotogramas repartidos por este tramo y los
     * promedia: desenfoque de movimiento del contenido, no solo del encuadre.
     */
    val exposicionUs: Long,
    /** Una matriz (3x3 por columnas) por muestra: del lienzo a la textura. */
    val matrices: List<FloatArray>,
    val opacidades: FloatArray,
    val mascara: Mascara?,
    /** Posicion del borde de la mascara en cada muestra. */
    val bordes: FloatArray,
    val color: AjustesColor,
    val mezcla: Mezcla,
    /** Radio de desenfoque en pixeles del lienzo. */
    val desenfoque: Float,
    /** El relleno desenfocado de detras. */
    val esFondo: Boolean,
    /** Lo mas grande que llega a verse, para no decodificar a mas resolucion de la que luce. */
    val anchoEnLienzo: Float,
    val altoEnLienzo: Float,
    /** Para maquetar el texto al tamano al que se va a ver, no estirarlo. */
    val escalaTexto: Float
) : Paso() {

    val muestras: Int get() = matrices.size

    override fun simplificado(): Paso =
        if (quieto(matrices, mascara, bordes)) copy(
            matrices = listOf(matrices[0]),
            opacidades = floatArrayOf(opacidades.average().toFloat()),
            bordes = floatArrayOf(bordes[0])
        ) else this

    /** Si no se ve, no hace falta ni decodificarlo. */
    val visible: Boolean get() = opacidades.any { it > 0.002f }
}

/**
 * Varios pasos montados aparte, en una lamina del tamano del lienzo, y puestos
 * encima como una sola imagen: una escena entera que se mueve, se descubre o
 * se funde de una pieza.
 */
data class PasoGrupo(
    val pasos: List<Paso>,
    /** Una por muestra: del lienzo a la lamina del grupo. */
    val matrices: List<FloatArray>,
    val opacidades: FloatArray,
    val mascara: Mascara?,
    val bordes: FloatArray,
    /** Fuera de la lamina se repiten sus bordes en vez de quedar vacio. */
    val extender: Boolean
) : Paso() {

    override fun simplificado(): Paso {
        val dentro = pasos.map { it.simplificado() }
        return if (quieto(matrices, mascara, bordes)) copy(
            pasos = dentro,
            matrices = listOf(matrices[0]),
            opacidades = floatArrayOf(opacidades.average().toFloat()),
            bordes = floatArrayOf(bordes[0])
        ) else copy(pasos = dentro)
    }

    /** Ni se mueve, ni se transparenta, ni se recorta: se puede dibujar sin montarla aparte. */
    val directo: Boolean
        get() = matrices.size == 1 && mascara == null && opacidades[0] >= 0.9999f &&
            esIdentidad(matrices[0])

    val visible: Boolean get() = opacidades.any { it > 0.002f }

    private fun esIdentidad(m: FloatArray): Boolean =
        abs(m[6]) < 1e-6f && abs(m[7]) < 1e-6f && abs(m[1]) < 1e-9f && abs(m[3]) < 1e-9f
}

/**
 * ¿Lo que se dibuja es igual en todas las muestras? Si solo cambia la
 * opacidad, si: mezclar la misma imagen con varias opacidades es lo mismo
 * que dibujarla una vez con la media.
 */
private fun quieto(matrices: List<FloatArray>, mascara: Mascara?, bordes: FloatArray): Boolean {
    if (matrices.size <= 1) return true
    val m0 = matrices[0]
    for (i in 1 until matrices.size) {
        val m = matrices[i]
        for (j in m.indices) if (abs(m[j] - m0[j]) > 1e-6f) return false
        if (mascara != null && abs(bordes[i] - bordes[0]) > 0.01f) return false
    }
    return true
}
