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
            if (anim.desenfoque > 0f) desenfoque += anim.desenfoque / ts.size
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
        val ajuste = a.ajusteTransicion
        // Avance de la transicion en cada muestra, con su curva. Por defecto
        // suave: arrancar y frenar poco a poco es lo que hace que no parezca
        // un corte con prisa.
        val ps = FloatArray(ts.size) { i ->
            val p = if (solape > 0) ((ts[i] - inicioB).toDouble() / solape).toFloat() else 1f
            curva(ajuste.curva, p.coerceIn(0f, 1f))
        }
        val tipo = a.transicion
        val fuerza = ajuste.intensidad
        val dir = ajuste.direccion
        // Los barridos van un poco inclinados: en recto parecen un fallo de imagen
        val (bx, by) = girar(dir.dx, dir.dy, INCLINACION)
        val extension = (abs(bx) * ancho + abs(by) * alto) / 2f
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
        fun destello(pico: Float, pot: Float) = FloatArray(ts.size) { i ->
            ((1f - abs(ps[i] * 2f - 1f)) * pico).coerceIn(0f, 1f).pow(pot)
        }

        var ladoA = Lado()
        var ladoB = Lado(aEscena = { i -> Efecto(opacidad = ps[i]) })
        val encima = ArrayList<Paso>()

        when (tipo) {
            Transicion.NEGRO -> {
                ladoB = Lado(aEscena = despues)
                // Negro del todo un momento alrededor del cambio: el cambio
                // queda siempre tapado, caiga donde caiga dentro del fotograma.
                encima.add(PasoSolido(0xFF000000.toInt(), destello(1.25f, 1f), debajo = false))
            }

            Transicion.DESTELLO -> {
                ladoB = Lado(aEscena = despues)
                // Pico en el centro, con caida rapida: un destello, no un fundido
                encima.add(PasoSolido(ajuste.color, destello(1.15f, 1.6f), debajo = false))
            }

            Transicion.LINEA, Transicion.BARRIDO -> {
                val suaveBorde = if (tipo == Transicion.LINEA) 1.5f else alto * 0.04f
                val mascara = Mascara(bx, by, suaveBorde)
                ladoB = Lado(aEscena = { i -> Efecto(mascara = mascara, borde = borde(ps[i])) })
                if (tipo == Transicion.LINEA) {
                    encima.add(
                        PasoLinea(
                            dirX = bx, dirY = by,
                            bordes = FloatArray(ts.size) { borde(ps[it]) },
                            grosor = grosorLinea * fuerza.coerceIn(0.3f, 3f),
                            color = ajuste.color
                        )
                    )
                }
            }

            Transicion.EMPUJAR -> {
                ladoA = Lado(aEscena = { i -> Efecto(dx = dir.dx * ps[i], dy = dir.dy * ps[i]) }, extender = true)
                ladoB = Lado(aEscena = { i -> Efecto(dx = -dir.dx * (1f - ps[i]), dy = -dir.dy * (1f - ps[i])) })
            }

            Transicion.DESLIZAR -> {
                // El de debajo se aleja un poco mientras lo tapan: da profundidad
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f - 0.06f * fuerza * ps[i]) })
                ladoB = Lado(aEscena = { i -> Efecto(dx = -dir.dx * (1f - ps[i]), dy = -dir.dy * (1f - ps[i])) })
            }

            Transicion.ZOOM -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 0.9f * fuerza * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 0.9f * fuerza * (1f - ps[i])) },
                    aEscena = { i -> Efecto(opacidad = paso(0.35f, 0.65f, ps[i])) }
                )
            }

            Transicion.DESENFOQUE -> {
                val radio = alto * 0.03f * fuerza
                ladoA = Lado(alClip = { i -> Efecto(desenfoque = radio * min(1f, ps[i] * 2f)) })
                ladoB = Lado(
                    alClip = { i -> Efecto(desenfoque = radio * min(1f, (1f - ps[i]) * 2f)) },
                    aEscena = { i -> Efecto(opacidad = paso(0.3f, 0.7f, ps[i])) }
                )
            }

            Transicion.GIRO -> {
                val g = 90f * fuerza
                ladoA = Lado(alClip = { i -> Efecto(rotacion = g * ps[i], escala = 1f + ps[i] * 0.5f) })
                ladoB = Lado(
                    alClip = { i -> Efecto(rotacion = -g * (1f - ps[i]), escala = 1f + (1f - ps[i]) * 0.5f) },
                    aEscena = despues
                )
            }

            Transicion.CIRCULO -> {
                val radio = kotlin.math.sqrt((ancho * ancho + alto * alto).toFloat()) / 2f + 4f
                val mascara = Mascara(0f, 0f, alto * 0.006f + 1f, radial = true)
                ladoB = Lado(aEscena = { i -> Efecto(mascara = mascara, borde = radio * ps[i]) })
            }

            Transicion.ZOOM_GIRO -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 1.2f * fuerza * ps[i], rotacion = 35f * fuerza * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 1.2f * fuerza * (1f - ps[i]), rotacion = -35f * fuerza * (1f - ps[i])) },
                    aEscena = { i -> Efecto(opacidad = paso(0.4f, 0.6f, ps[i])) }
                )
            }

            Transicion.IMPACTO -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 0.6f * fuerza * ps[i] * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 0.35f * fuerza * (1f - ps[i]) * (1f - ps[i])) },
                    aEscena = despues
                )
                encima.add(PasoSolido(ajuste.color, destello(1.4f, 2.2f), debajo = false))
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

    /** El encuadre a [local] del inicio, con fotogramas clave y movimiento continuo. */
    fun encuadreEn(clip: Clip, local: Long): Encuadre = continua(clip, local, clip.encuadreEn(local))

    private fun continua(clip: Clip, local: Long, e0: Encuadre): Encuadre {
        var e = e0
        for (c in clip.continua) {
            val s = local / 1_000_000f * c.velocidad
            val i = c.intensidad
            e = when (c.tipo) {
                AnimacionContinua.ACERCAMIENTO -> {
                    val f = (local.toFloat() / clip.duracionUs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    e.copy(escala = e.escala * (1f + 0.15f * i * f))
                }
                AnimacionContinua.ALEJAMIENTO -> {
                    val f = (local.toFloat() / clip.duracionUs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    e.copy(escala = e.escala * (1f + 0.15f * i * (1f - f)))
                }
                AnimacionContinua.PULSO -> {
                    // Medio segundo por latido: encaja con la mayoria de musica de edits
                    val fase = (s / 0.5f) % 1f
                    val golpe = (1f - fase).pow(3)
                    e.copy(escala = e.escala * (1f + 0.05f * i * golpe))
                }
                AnimacionContinua.TEMBLOR -> {
                    val amp = 0.012f * i
                    e.copy(
                        x = e.x + amp * ruido(s * 9f, 1.3f),
                        y = e.y + amp * ruido(s * 9f, 7.1f),
                        rotacion = e.rotacion + 0.8f * i * ruido(s * 7f, 3.7f),
                        escala = e.escala * (1f + 0.03f * i)
                    )
                }
                AnimacionContinua.BALANCEO -> e.copy(rotacion = e.rotacion + 3f * i * sin(2f * PI.toFloat() * s / 2f))
                AnimacionContinua.FLOTAR -> e.copy(
                    y = e.y + 0.012f * i * sin(2f * PI.toFloat() * s / 2.4f),
                    x = e.x + 0.006f * i * sin(2f * PI.toFloat() * s / 3.7f)
                )
                AnimacionContinua.GIRAR -> e.copy(rotacion = e.rotacion + 90f * i * s)
            }
        }
        return e
    }

    /** Lo que aportan las animaciones de entrada y salida en [local]. */
    private fun animacion(clip: Clip, local: Long): Efecto {
        var r = Efecto()
        if (clip.entrada.isNotEmpty() && clip.duracionEntradaUs > 0) {
            val q = (local.toFloat() / clip.duracionEntradaUs).coerceIn(0f, 1f)
            if (q < 1f) for (m in clip.entrada) r = r.y(efecto(m, q))
        }
        if (clip.salida.isNotEmpty() && clip.duracionSalidaUs > 0) {
            val restante = clip.duracionUs - local
            val q = (restante.toFloat() / clip.duracionSalidaUs).coerceIn(0f, 1f)
            if (q < 1f) for (m in clip.salida) r = r.y(efecto(m, q))
        }
        return r
    }

    /**
     * Una pieza de animacion con [q] de 0 (fuera) a 1 (en su sitio). La salida
     * es la misma recorrida al reves: entrar desde la izquierda y salir hacia
     * la izquierda son la misma cuenta.
     */
    private fun efecto(m: Movimiento, q: Float): Efecto {
        val tramo = (m.hasta - m.desde).coerceAtLeast(1e-3f)
        val local = ((q - m.desde) / tramo).coerceIn(0f, 1f)
        val f = curva(m.curva, local)
        val falta = 1f - f
        val i = m.intensidad
        return when (m.tipo) {
            Animacion.APARECER -> Efecto(opacidad = (1f - i.coerceIn(0f, 1f) * falta).coerceIn(0f, 1f))
            Animacion.ZOOM_ENTRA -> Efecto(escala = 1f + 0.35f * i * falta)
            Animacion.ZOOM_SALE -> Efecto(escala = (1f - 0.3f * i * falta).coerceAtLeast(0.02f))
            Animacion.IZQUIERDA -> Efecto(dx = -i * falta)
            Animacion.DERECHA -> Efecto(dx = i * falta)
            Animacion.ARRIBA -> Efecto(dy = -i * falta)
            Animacion.ABAJO -> Efecto(dy = i * falta)
            Animacion.GIRO -> Efecto(rotacion = -120f * i * falta)
            Animacion.REBOTE -> Efecto(escala = 1f + (rebote(local) - 1f) * i, opacidad = min(1f, local * 3f))
            Animacion.REVELAR -> Efecto(revelar = f)
            Animacion.DESENFOCAR -> Efecto(desenfoque = alto * 0.04f * i * falta)
            Animacion.SACUDIR -> {
                // Golpes rapidos que se apagan
                val amp = 0.035f * i * falta
                Efecto(dx = amp * sin(local * 38f), rotacion = 4f * i * falta * sin(local * 31f + 1f))
            }
            Animacion.PARPADEO -> {
                // Tres parpadeos y se queda
                val on = sin(local * PI.toFloat() * 7f) > -0.2f || local >= 1f
                Efecto(opacidad = if (on) 1f else (1f - i).coerceIn(0f, 1f) * 0.3f)
            }
        }
    }

    // ------------------------------------------------------------------
    // Geometria
    // ------------------------------------------------------------------

    /**
     * Donde queda el bloque sobre el lienzo con el encuadre [e], en pixeles.
     * Es lo que usa la pantalla para saber que bloque se toca y para dibujar
     * el marco del elegido.
     */
    fun marco(clip: Clip, e: Encuadre): Marco {
        val (cw, ch) = medidor.medidas(clip, alto)
        val s = base(clip, e, cw, ch) * e.escala
        return Marco(
            cx = ancho / 2f + e.x * ancho,
            cy = alto / 2f + e.y * alto,
            ancho = cw * e.recorte.ancho * s,
            alto = ch * e.recorte.alto * s,
            rotacion = e.rotacion
        )
    }

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

        /** Grados de inclinacion de los barridos. */
        private const val INCLINACION = -14.0

        private fun girar(x: Float, y: Float, grados: Double): Pair<Float, Float> {
            val r = Math.toRadians(grados)
            val c = cos(r).toFloat()
            val s = sin(r).toFloat()
            return (x * c - y * s) to (x * s + y * c)
        }

        fun suave(x: Float): Float = x * x * (3f - 2f * x)

        fun salidaSuave(x: Float): Float = 1f - (1f - x).pow(3)

        fun paso(a: Float, b: Float, x: Float): Float = suave(((x - a) / (b - a)).coerceIn(0f, 1f))

        fun curva(c: Curva, x: Float): Float = when (c) {
            Curva.LINEAL -> x
            Curva.SUAVE -> if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
            Curva.ENTRADA -> x * x * x
            Curva.SALIDA -> 1 - (1 - x).pow(3)
            Curva.REBOTE -> rebote(x)
            Curva.ELASTICO -> if (x <= 0f) 0f else if (x >= 1f) 1f
            else (2.0.pow(-10.0 * x) * sin((x * 10 - 0.75) * (2 * PI / 3)) + 1).toFloat()
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
/** Rectangulo girado de un bloque sobre el lienzo, en pixeles. */
data class Marco(val cx: Float, val cy: Float, val ancho: Float, val alto: Float, val rotacion: Float) {
    fun contiene(x: Float, y: Float, margen: Float = 0f): Boolean {
        val r = Math.toRadians(-rotacion.toDouble())
        val dx = x - cx
        val dy = y - cy
        val lx = (dx * cos(r) - dy * sin(r)).toFloat()
        val ly = (dx * sin(r) + dy * cos(r)).toFloat()
        return abs(lx) <= ancho / 2f + margen && abs(ly) <= alto / 2f + margen
    }
}

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
 * del barrido (o sin direccion, si es [radial]), el pixel se ve si d < borde. El borde avanza en esa direccion.
 *
 * @param dirX,dirY hacia donde avanza el borde (vector unitario)
 * @param suave pixeles de transicion del borde, para que no salga dentado
 */
data class Mascara(
    val dirX: Float,
    val dirY: Float,
    val suave: Float,
    /** En circulo: d es la distancia al centro del lienzo y se ve lo de dentro. */
    val radial: Boolean = false
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
