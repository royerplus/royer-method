package com.royermethod.app.editor.modelo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Cuanto puede apartarse una trayectoria simplificada de la original.
 *
 * [px] son pixeles de exportacion (el ancho y alto del proyecto a su lado
 * corto de salida): medio pixel no se ve ni en una imagen quieta. [opacidad]
 * va de 0 a 1; medio nivel de 255 tampoco se ve.
 */
data class Tolerancia(val px: Float = 0.5f, val opacidad: Float = 0.5f / 255f)

/**
 * Lo que dejo una simplificacion: las claves nuevas y cuanto se apartan de
 * las de antes, medido de nuevo con [Trayectoria.comparar] sobre todos los
 * instantes (no es una estimacion del ajuste).
 *
 * @param instantePeorUs instante del bloque donde la diferencia es mayor
 *   (en proporcion a la tolerancia); -1 si no hay ninguna diferencia
 * @param instantes cuantos instantes se compararon
 */
data class Simplificacion(
    val claves: List<Clave>,
    val antes: Int,
    val despues: Int,
    val errorMaxPx: Float,
    val errorMaxOpacidad: Float,
    val instantePeorUs: Long,
    val instantes: Int,
    val tolerancia: Tolerancia
)

/**
 * Trayectorias de fotogramas clave: evaluarlas, compararlas y quitarles
 * claves que sobran.
 *
 * Las replicas importadas traen una clave por fotograma: cientos de puntos
 * que nadie puede retocar a mano. [simplificar] deja las que hacen falta y
 * pone a cada tramo la curva (con nombre si alguna vale, si no una Bezier)
 * que reproduce el movimiento dentro de la tolerancia.
 *
 * Como se mide el error: en cada instante, la distancia entre las dos poses
 * en pixeles de exportacion de W x H, contando
 * `hypot(dx*W, dy*H) + R*|descala| + R*|dgiro|*pi/180`, con R la mitad de la
 * diagonal del lienzo (lo que se desplaza un punto visible en la esquina al
 * cambiar la escala o el giro). La opacidad se mide aparte, de 0 a 1. Los
 * instantes son los que de verdad se dibujan ([instantes]): cada fotograma
 * de 30 fps, sus muestras del obturador y los tiempos de las claves.
 *
 * Limitacion: el modelo de claves lleva una sola curva por tramo para los
 * cinco canales (escala, x, y, giro, opacidad), y cada tramo va en linea
 * recta entre sus dos poses. Una trayectoria curva en el plano, o canales
 * que se mueven con ritmos distintos, conservan mas claves.
 */
object Trayectoria {

    /** El obturador de [Escena]: medio fotograma de exposicion. */
    private const val OBTURADOR = 0.5

    /**
     * La pose de [claves] en [local]: el mismo calculo que [Clip.encuadreEn]
     * (sin quitar repetidas) y que [PiezaVideo.encuadreEn] (quitandolas).
     * Lo que no se anima sale de la clave; el bloque lo sustituye por el suyo.
     */
    fun poseEn(claves: List<Clave>, local: Long, sinClaves: Encuadre, quitarRepetidas: Boolean): Encuadre {
        if (claves.isEmpty()) return sinClaves
        return poseOrdenada(ordenadas(claves, quitarRepetidas), local)
    }

    /**
     * Los instantes del bloque que se dibujan al exportar: el de cada
     * fotograma de 30 fps (las luces y los flashes van ahi), las [muestras]
     * del obturador alrededor (como [Escena]) y los tiempos de las claves,
     * todo dentro de [0, duracion) y sin repetir.
     *
     * @param inicioUs donde empieza el bloque en la linea: los fotogramas
     *   caen en instantes de la linea, no del bloque
     */
    fun instantes(duracionUs: Long, muestras: Int, claves: List<Clave>, inicioUs: Long = 0): LongArray {
        if (duracionUs <= 0) return LongArray(0)
        val k = muestras.coerceIn(1, Escena.MAX_MUESTRAS)
        val ultimo = duracionUs - 1
        val exp = 1_000_000.0 / Linea.FPS * OBTURADOR
        val ini = inicioUs.coerceAtLeast(0)
        var lista = LongArray(64)
        var n = 0
        fun poner(v: Long) {
            if (n == lista.size) lista = lista.copyOf(n * 2)
            lista[n++] = v
        }
        var f = Linea.fotogramaDe(ini)
        while (true) {
            val t = Linea.tiempoDe(f)
            if (t - ini > ultimo) break
            poner((t - ini).coerceIn(0, ultimo))
            if (k > 1) for (i in 0 until k) {
                // La misma cuenta que Escena.instantes, muestra a muestra
                val ti = (t + ((i + 0.5) / k - 0.5) * exp).toLong()
                poner((ti - ini).coerceIn(0, ultimo))
            }
            f++
        }
        for (c in claves) if (c.tiempoUs in 0..ultimo) poner(c.tiempoUs)
        return unicos(lista.copyOf(n))
    }

    /**
     * Quita claves de [claves] mientras la trayectoria no se aparte mas de
     * [tol] en ningun instante de [instantes]. La primera y la ultima clave
     * se quedan tal cual (la pose final exacta deja que las partes se
     * fusionen), las que comparten tiempo tambien (son un salto), las que
     * caen fuera de los instantes tambien (no hay con que medirlas), y las
     * que quedan conservan su tiempo exacto: solo cambian sus curvas.
     *
     * Primero cada tramo entero; si no cabe, se parte por la clave que mas se
     * aparta de la recta entre sus extremos (como Douglas-Peucker en el
     * tiempo) y despues se intenta juntar de nuevo cada par de tramos. Para
     * cada tramo se prueban las curvas con nombre y, si ninguna vale, una
     * Bezier ajustada. El resultado se mide otra vez con [comparar]; si por
     * lo que fuera se pasara de la tolerancia, se devuelven las claves de
     * antes (nunca empeora).
     *
     * @param ancho,alto pixeles de exportacion del proyecto
     * @param quitarRepetidas como las evalua una parte ([PiezaVideo]); un
     *   bloque no las quita
     */
    fun simplificar(
        claves: List<Clave>,
        instantes: LongArray,
        ancho: Float,
        alto: Float,
        tol: Tolerancia = Tolerancia(),
        quitarRepetidas: Boolean = false
    ): Simplificacion {
        val t = normalizada(tol)
        val ins = unicos(instantes.copyOf())
        val medida = Medida(ancho, alto, t)
        val original = ordenadas(claves, quitarRepetidas)
        // Sin cambiar nada de lo que se ve: de las que comparten tiempo solo
        // cuentan la primera y la ultima.
        val base = sinRepetidasInteriores(original)
        var nuevas = if (base.size <= 2) base else Ajuste(original, base, ins, medida).resolver()
        var m = medir(original, nuevas, ins, medida, quitarRepetidas)
        if (m.px > t.px || m.opacidad > t.opacidad || m.px.isNaN() || m.opacidad.isNaN()) {
            nuevas = base
            m = medir(original, nuevas, ins, medida, quitarRepetidas)
        }
        return Simplificacion(
            claves = nuevas,
            antes = claves.size,
            despues = nuevas.size,
            errorMaxPx = m.px.toFloat(),
            errorMaxOpacidad = m.opacidad.toFloat(),
            instantePeorUs = m.peor,
            instantes = ins.size,
            tolerancia = t
        )
    }

    /** Sin negativos ni NaN: eso seria no tolerar nada. */
    private fun normalizada(t: Tolerancia): Tolerancia {
        fun limpio(v: Float) = if (v.isNaN()) 0f else v.coerceAtLeast(0f)
        val px = limpio(t.px)
        val op = limpio(t.opacidad)
        return if (px == t.px && op == t.opacidad) t else Tolerancia(px, op)
    }

    /**
     * La mayor diferencia entre las trayectorias [a] y [b] en [instantes]:
     * pixeles de exportacion (ver la medida arriba) y opacidad de 0 a 1.
     * Cada maximo por separado.
     */
    fun comparar(
        a: List<Clave>,
        b: List<Clave>,
        instantes: LongArray,
        ancho: Float,
        alto: Float,
        quitarRepetidas: Boolean = false
    ): Pair<Float, Float> {
        val m = medir(ordenadas(a, quitarRepetidas), b, instantes, Medida(ancho, alto, Tolerancia()), quitarRepetidas)
        return m.px.toFloat() to m.opacidad.toFloat()
    }

    // ------------------------------------------------------------------
    // Evaluacion
    // ------------------------------------------------------------------

    internal fun ordenadas(claves: List<Clave>, quitarRepetidas: Boolean): List<Clave> {
        val o = claves.sortedBy { it.tiempoUs }
        return if (quitarRepetidas) o.distinctBy { it.tiempoUs } else o
    }

    /** Pose de una lista ya ordenada y no vacia. */
    internal fun poseOrdenada(o: List<Clave>, local: Long): Encuadre {
        val primera = o.first()
        val ultima = o.last()
        if (local <= primera.tiempoUs) return primera.encuadre
        if (local >= ultima.tiempoUs) return ultima.encuadre
        val i = ultimaHasta(o, local)
        val a = o[i]
        val b = o[i + 1]
        val f = (local - a.tiempoUs).toFloat() / (b.tiempoUs - a.tiempoUs)
        return a.encuadre.hacia(b.encuadre, a.avance(f))
    }

    /** La ultima con tiempo <= [local], como indexOfLast pero por biseccion. */
    private fun ultimaHasta(o: List<Clave>, local: Long): Int {
        var lo = 0
        var hi = o.size - 1
        while (lo < hi) {
            val m = (lo + hi + 1) ushr 1
            if (o[m].tiempoUs <= local) lo = m else hi = m - 1
        }
        return lo
    }

    /** Ordena y quita repetidos. */
    private fun unicos(v: LongArray): LongArray {
        v.sort()
        var n = 0
        for (i in v.indices) if (n == 0 || v[i] != v[n - 1]) v[n++] = v[i]
        return v.copyOf(n)
    }

    /**
     * De varias claves en el mismo tiempo, un bloque solo usa la primera
     * (para llegar) y la ultima (para salir): las de en medio no cambian nada.
     */
    private fun sinRepetidasInteriores(o: List<Clave>): List<Clave> =
        o.filterIndexed { i, k ->
            i == 0 || i == o.size - 1 || k.tiempoUs != o[i - 1].tiempoUs || k.tiempoUs != o[i + 1].tiempoUs
        }

    // ------------------------------------------------------------------
    // Medida
    // ------------------------------------------------------------------

    /** Canales que se animan, en orden: escala, x, y, giro, opacidad. */
    private fun canales(e: Encuadre, destino: FloatArray, en: Int) {
        destino[en] = e.escala
        destino[en + 1] = e.x
        destino[en + 2] = e.y
        destino[en + 3] = e.rotacion
        destino[en + 4] = e.opacidad
    }

    private class Medida(ancho: Float, alto: Float, val tol: Tolerancia) {
        val w = ancho.toDouble().coerceAtLeast(1.0)
        val h = alto.toDouble().coerceAtLeast(1.0)
        /** Media diagonal del lienzo. */
        val r = hypot(w, h) / 2.0
        val rGiro = r * PI / 180.0

        private val tolPx = tol.px.toDouble()
        private val tolOp = tol.opacidad.toDouble()

        /** Diferencias de escala, x, y y giro en pixeles. */
        fun px(de: Float, dx: Float, dy: Float, dr: Float): Double =
            hypot(dx * w, dy * h) + r * abs(de.toDouble()) + rGiro * abs(dr.toDouble())

        /**
         * El error en proporcion a la tolerancia, para comparar tramos. Vale
         * (<= 1) exactamente cuando los dos errores caben en la tolerancia.
         */
        fun relativo(px: Double, op: Double): Double {
            if (px.isNaN() || op.isNaN()) return Double.POSITIVE_INFINITY
            val e = max(proporcion(px, tolPx), proporcion(op, tolOp))
            val cabe = px <= tolPx && op <= tolOp
            return when {
                cabe -> minOf(e, 1.0)
                e <= 1.0 -> Math.nextUp(1.0)
                else -> e
            }
        }

        private fun proporcion(v: Double, t: Double): Double = when {
            v.isNaN() -> Double.POSITIVE_INFINITY
            t > 0.0 -> v / t
            v > 0.0 -> Double.POSITIVE_INFINITY
            else -> 0.0
        }
    }

    private class Resultado(val px: Double, val opacidad: Double, val peor: Long)

    /** [a] ya ordenada; [b] tal cual. */
    private fun medir(a: List<Clave>, b: List<Clave>, instantes: LongArray, m: Medida, quitarRepetidas: Boolean): Resultado {
        val ob = ordenadas(b, quitarRepetidas)
        var px = 0.0
        var op = 0.0
        var peor = -1L
        var peorRel = 0.0
        for (s in instantes) {
            val ea = if (a.isEmpty()) Encuadre() else poseOrdenada(a, s)
            val eb = if (ob.isEmpty()) Encuadre() else poseOrdenada(ob, s)
            if (ea == eb) continue
            val dpx = m.px(eb.escala - ea.escala, eb.x - ea.x, eb.y - ea.y, eb.rotacion - ea.rotacion)
            val dop = abs((eb.opacidad - ea.opacidad).toDouble())
            if (dpx.isNaN() || dop.isNaN()) return Resultado(Double.NaN, Double.NaN, s)
            px = max(px, dpx)
            op = max(op, dop)
            val rel = m.relativo(dpx, dop)
            if (rel > peorRel) {
                peorRel = rel
                peor = s
            }
        }
        return Resultado(px, op, peor)
    }

    // ------------------------------------------------------------------
    // Ajuste
    // ------------------------------------------------------------------

    /** Una curva para un tramo: con nombre o Bezier. */
    private class Forma(val curva: Curva, val bezier: Bezier?)

    /**
     * El trabajo de una simplificacion. [original] ordenada (es la que manda),
     * [base] la misma sin las repetidas que no cuentan.
     */
    private class Ajuste(original: List<Clave>, val base: List<Clave>, val ins: LongArray, val m: Medida) {
        val n = base.size
        val t = LongArray(n) { base[it].tiempoUs }
        val p = FloatArray(n * 5).also { a -> for (i in 0 until n) canales(base[i].encuadre, a, i * 5) }
        /** La pose original en cada instante. */
        val o = FloatArray(ins.size * 5).also { a -> for (q in ins.indices) canales(poseOrdenada(original, ins[q]), a, q * 5) }
        /** Pesos para pasar cada canal a pixeles (la opacidad, a su tolerancia). */
        val pesos = doubleArrayOf(
            m.r, m.w, m.h, m.rGiro,
            if (m.tol.opacidad > 0f) m.tol.px.coerceAtLeast(1e-3f).toDouble() / m.tol.opacidad else 1e6
        )
        /**
         * Claves que se quedan si o si y parten la trayectoria en trozos que
         * se simplifican por separado: la primera y la ultima, las de un salto
         * (comparten tiempo), las que no son numeros, y las que caen fuera de
         * lo que se dibuja (las que encierran el tramo recortado de una parte,
         * o las de despues del final de un bloque recortado): ahi no hay nada
         * con que medir, y si se vuelve a alargar el bloque tienen que estar.
         */
        val fija = BooleanArray(n) { i ->
            i == 0 || i == n - 1 ||
                (i > 0 && t[i - 1] == t[i]) || (i < n - 1 && t[i + 1] == t[i]) ||
                (0 until 5).any { c -> !p[i * 5 + c].isFinite() } ||
                ins.isEmpty() || t[i] < ins.first() || t[i] > ins.last()
        }
        /** Tramos (i, j) que ya se probaron sin exito: no se vuelve a ajustar. */
        private val fallidos = HashSet<Long>()
        /** Forma elegida para el tramo que empieza en cada clave; null = la suya. */
        val formas = arrayOfNulls<Forma>(n)
        val queda = BooleanArray(n)

        fun resolver(): List<Clave> {
            queda[0] = true
            queda[n - 1] = true
            var a = 0
            for (i in 1 until n) {
                if (!fija[i] && i != n - 1) continue
                queda[i] = true
                partir(a, i)
                juntar(a, i)
                a = i
            }
            val r = ArrayList<Clave>()
            for (i in 0 until n) if (queda[i]) {
                val f = formas[i]
                r.add(if (f == null) base[i] else base[i].copy(curva = f.curva, bezier = f.bezier))
            }
            return r
        }

        /** Douglas-Peucker en el tiempo, entre dos claves que se quedan. */
        private fun partir(i: Int, j: Int) {
            if (j <= i + 1) return
            val f = ajustar(i, j)
            if (f != null) {
                formas[i] = f
                return
            }
            val k = corte(i, j)
            queda[k] = true
            partir(i, k)
            partir(k, j)
        }

        /** Intenta quitar cada clave intermedia que dejo [partir]. */
        private fun juntar(desde: Int, hasta: Int) {
            val lista = (desde..hasta).filter { queda[it] }.toMutableList()
            var x = 1
            while (x < lista.size - 1) {
                val a = lista[x - 1]
                val c = lista[x + 1]
                val f = ajustar(a, c)
                if (f != null) {
                    queda[lista[x]] = false
                    formas[a] = f
                    lista.removeAt(x)
                } else x++
            }
        }

        /** La clave de en medio que mas se aparta de ir en linea recta y a ritmo constante. */
        private fun corte(i: Int, j: Int): Int {
            var mejor = (i + j) / 2
            var peor = -1.0
            val dt = (t[j] - t[i]).toDouble()
            for (k in i + 1 until j) {
                val g = if (dt > 0) ((t[k] - t[i]) / dt).toFloat() else 0f
                val (px, op) = distancia(p, k * 5, i, j, g)
                val e = m.relativo(px, op)
                if (e > peor) {
                    peor = e
                    mejor = k
                }
            }
            return mejor
        }

        /** Distancia de la pose en [desde]/[en] a la de ir de i a j con avance [g]. */
        private fun distancia(desde: FloatArray, en: Int, i: Int, j: Int, g: Float): Pair<Double, Double> {
            val a = i * 5
            val b = j * 5
            val e = p[a] + (p[b] - p[a]) * g
            val x = p[a + 1] + (p[b + 1] - p[a + 1]) * g
            val y = p[a + 2] + (p[b + 2] - p[a + 2]) * g
            val r = p[a + 3] + (p[b + 3] - p[a + 3]) * g
            val op = p[a + 4] + (p[b + 4] - p[a + 4]) * g
            return m.px(e - desde[en], x - desde[en + 1], y - desde[en + 2], r - desde[en + 3]) to
                abs((op - desde[en + 4]).toDouble())
        }

        /** Instantes [lo, hi) del tramo de i a j. */
        private fun desdeIndice(v: Long): Int {
            var lo = 0
            var hi = ins.size
            while (lo < hi) {
                val mm = (lo + hi) ushr 1
                if (ins[mm] < v) lo = mm + 1 else hi = mm
            }
            return lo
        }

        /**
         * La forma que deja ir de i a j sin pasarse de la tolerancia, o null.
         * Primero la curva que ya tenia, luego las demas con nombre, y si
         * ninguna vale una Bezier.
         */
        private fun ajustar(i: Int, j: Int): Forma? {
            if (fija.let { f -> (i + 1 until j).any { f[it] } }) return null
            val clave = i.toLong() * n + j
            if (clave in fallidos) return null
            val f = ajustarSinRecordar(i, j)
            if (f == null) fallidos.add(clave)
            return f
        }

        private fun ajustarSinRecordar(i: Int, j: Int): Forma? {
            val lo = desdeIndice(t[i])
            val hi = desdeIndice(t[j])
            if (hi <= lo) return Forma(base[i].curva, base[i].bezier)
            if (!recta(i, j, lo, hi)) return null
            var elegida: Forma? = null
            var mejor = 1.0
            val propia = Forma(base[i].curva, base[i].bezier)
            val candidatas = listOf(propia) + Curva.values().filter { it != base[i].curva || base[i].bezier != null }.map { Forma(it, null) }
            for (f in candidatas) {
                val e = error(i, j, f, lo, hi, mejor)
                // A igualdad, la primera: la que ya tenia y luego por orden
                if (e < mejor || (elegida == null && e <= mejor)) {
                    mejor = e
                    elegida = f
                    if (e == 0.0) break
                }
            }
            return elegida ?: bezier(i, j, lo, hi)
        }

        /**
         * Si algun instante queda lejos de la recta entre las dos poses,
         * ninguna curva lo arregla. La distancia euclidea con pesos es menor
         * o igual que la medida (suma de partes), asi que si ya se pasa, se
         * pasa seguro.
         */
        private fun recta(i: Int, j: Int, lo: Int, hi: Int): Boolean {
            val a = i * 5
            val b = j * 5
            val d = DoubleArray(4) { c -> (p[b + c] - p[a + c]).toDouble() * pesos[c] }
            val dd = d.sumOf { it * it }
            val limite = m.tol.px.toDouble()
            for (q in lo until hi) {
                val oq = q * 5
                var proy = 0.0
                val v = DoubleArray(4) { c -> (o[oq + c] - p[a + c]).toDouble() * pesos[c] }
                if (dd > 0) {
                    for (c in 0 until 4) proy += v[c] * d[c]
                    proy /= dd
                }
                var perp = 0.0
                for (c in 0 until 4) {
                    val r = v[c] - proy * d[c]
                    perp += r * r
                }
                if (sqrt(perp) > limite * (1 + 1e-9) + 1e-9) return false
                // La opacidad quieta entre las dos poses no se arregla con la curva
                if (p[b + 4] == p[a + 4] && abs((o[oq + 4] - p[a + 4]).toDouble()) > m.tol.opacidad) return false
            }
            return true
        }

        /**
         * El mayor error relativo del tramo con la forma [f]. Para en cuanto
         * pasa de [corte]. Misma aritmetica que [poseOrdenada] y que
         * [Clave.avance]: lo que da es el error de verdad.
         */
        fun error(i: Int, j: Int, f: Forma, lo: Int, hi: Int, corte: Double): Double {
            val z = f.bezier
            return if (z != null) error(i, j, lo, hi, corte, 1) { x -> z.en(x) }
            else error(i, j, lo, hi, corte, 1) { x -> Escena.curva(f.curva, x) }
        }

        /** Lo mismo con cualquier avance [g] y mirando uno de cada [paso] instantes. */
        private inline fun error(i: Int, j: Int, lo: Int, hi: Int, corte: Double, paso: Int, g: (Float) -> Float): Double {
            val ta = t[i]
            val tb = t[j]
            val a = i * 5
            val b = j * 5
            var peor = 0.0
            var q = lo
            while (q < hi) {
                val s = ins[q]
                val oq = q * 5
                val px: Double
                val op: Double
                if (s <= t[0]) {
                    // Antes de la primera clave se queda en ella, sin curva
                    px = m.px(p[0] - o[oq], p[1] - o[oq + 1], p[2] - o[oq + 2], p[3] - o[oq + 3])
                    op = abs((p[4] - o[oq + 4]).toDouble())
                } else {
                    val av = g((s - ta).toFloat() / (tb - ta))
                    val e = p[a] + (p[b] - p[a]) * av
                    val xx = p[a + 1] + (p[b + 1] - p[a + 1]) * av
                    val yy = p[a + 2] + (p[b + 2] - p[a + 2]) * av
                    val r = p[a + 3] + (p[b + 3] - p[a + 3]) * av
                    val oo = p[a + 4] + (p[b + 4] - p[a + 4]) * av
                    px = m.px(e - o[oq], xx - o[oq + 1], yy - o[oq + 2], r - o[oq + 3])
                    op = abs((oo - o[oq + 4]).toDouble())
                }
                val rel = m.relativo(px, op)
                if (rel > peor) {
                    peor = rel
                    if (peor > corte) return peor
                }
                q += paso
            }
            return peor
        }

        /**
         * Una Bezier para el tramo: por minimos cuadrados sobre el avance que
         * lleva el original a lo largo de la recta, y si no basta, apretando
         * el peor instante. Se redondea a milesimas si sigue valiendo.
         */
        private fun bezier(i: Int, j: Int, lo: Int, hi: Int): Forma? {
            val a = i * 5
            val b = j * 5
            val d = DoubleArray(5) { c -> (p[b + c] - p[a + c]).toDouble() * pesos[c] }
            val dd = d.sumOf { it * it }
            if (dd <= 0.0) return null
            // Cuanto ha avanzado el original por la recta en cada instante
            val todos = DoubleArray(hi - lo) { k ->
                var u = 0.0
                for (c in 0 until 5) u += (o[(lo + k) * 5 + c] - p[a + c]).toDouble() * pesos[c] * d[c]
                u / dd
            }
            if (!cabeEnBezier(todos, sqrt(2.0) * m.tol.px.coerceAtLeast(1e-3f) / sqrt(dd))) return null
            // Unos 160 puntos bastan para ajustar; se comprueba en todos
            val paso = max(1, (hi - lo) / 160)
            val xs = ArrayList<Float>()
            val us = ArrayList<Double>()
            var q = lo
            while (q < hi) {
                if (ins[q] > t[0]) {
                    xs.add((ins[q] - t[i]).toFloat() / (t[j] - t[i]))
                    us.add(todos[q - lo])
                }
                q += paso
            }
            if (xs.isEmpty()) return null
            // Para buscar vale una Bezier tabulada, mucho mas rapida; la que
            // se acepta se comprueba con la cuenta de verdad
            fun cuadrados(v: DoubleArray): Double {
                val z = Tabulada(aBezier(v))
                var s = 0.0
                for (k in xs.indices) {
                    val e = z.en(xs[k]) - us[k]
                    s += e * e
                }
                return s
            }
            val semillas = listOf(
                doubleArrayOf(1.0 / 3, 1.0 / 3, 2.0 / 3, 2.0 / 3),
                doubleArrayOf(0.42, 0.0, 0.58, 1.0),
                doubleArrayOf(0.42, 0.0, 1.0, 1.0),
                doubleArrayOf(0.0, 0.0, 0.58, 1.0),
                doubleArrayOf(0.25, 0.1, 0.25, 1.0),
                doubleArrayOf(0.1, 0.9, 0.4, 1.0)
            )
            val inicio = semillas.minByOrNull { cuadrados(it) }!!
            val ls = NelderMead.minimo(inicio, 0.1, 240) { cuadrados(it) }
            probar(i, j, lo, hi, ls)?.let { return it }
            // Minimax: el peor instante manda
            val mm = NelderMead.minimo(ls, 0.05, 240) { v ->
                val z = Tabulada(aBezier(v))
                error(i, j, lo, hi, Double.MAX_VALUE, paso) { x -> z.en(x) }
            }
            return probar(i, j, lo, hi, mm)
        }

        /**
         * Si ninguna Bezier del editor puede seguir [u] a menos de [margen]
         * (en avance), no merece la pena buscarla. Una Bezier de las del
         * editor no sale de -0,3 a 1,3 y cambia de sentido dos veces como
         * mucho; si el original se sale de ahi, o va y vuelve mas veces y
         * cada vez mas de dos margenes, no hay ninguna que valga. El margen
         * sale de la tolerancia: la medida es mayor o igual que la distancia
         * con pesos, y la distancia es mayor o igual que lo que se aparta en
         * avance por lo que mide la recta (raiz de 2 por la opacidad).
         */
        private fun cabeEnBezier(u: DoubleArray, margen: Double): Boolean {
            if (u.isEmpty()) return true
            if (u.any { it < -0.3 - margen || it > 1.3 + margen || it.isNaN() }) return false
            // Solo cuentan las idas y vueltas de mas de dos margenes: cualquier
            // curva que pase a menos de un margen de cada punto tiene que darlas
            val umbral = 2 * margen
            var vueltas = 0
            var sentido = 0
            var mayor = u[0]
            var menor = u[0]
            var extremo = u[0]
            for (v in u) {
                when (sentido) {
                    0 -> {
                        mayor = max(mayor, v)
                        menor = minOf(menor, v)
                        if (mayor - menor > umbral) {
                            sentido = if (v == mayor) 1 else -1
                            extremo = v
                        }
                    }
                    1 -> if (v > extremo) extremo = v else if (extremo - v > umbral) {
                        vueltas++
                        sentido = -1
                        extremo = v
                    }
                    else -> if (v < extremo) extremo = v else if (v - extremo > umbral) {
                        vueltas++
                        sentido = 1
                        extremo = v
                    }
                }
                if (vueltas > 2) return false
            }
            return true
        }

        private fun probar(i: Int, j: Int, lo: Int, hi: Int, v: DoubleArray): Forma? {
            val exacta = aBezier(v)
            val redonda = Bezier(milesima(exacta.x1), milesima(exacta.y1), milesima(exacta.x2), milesima(exacta.y2))
            for (z in listOf(redonda, exacta)) {
                val f = Forma(base[i].curva, z)
                if (error(i, j, f, lo, hi, 1.0) <= 1.0) return f
            }
            return null
        }

        private fun milesima(v: Float): Float = (v * 1000f).roundToInt() / 1000f

        /** Dentro de lo que deja el editor de curvas. */
        private fun aBezier(v: DoubleArray): Bezier = Bezier(
            v[0].toFloat().coerceIn(0f, 1f), v[1].toFloat().coerceIn(-0.3f, 1.3f),
            v[2].toFloat().coerceIn(0f, 1f), v[3].toFloat().coerceIn(-0.3f, 1.3f)
        )
    }

    /**
     * Una Bezier apuntada en unos cuantos puntos y unidos con rectas: se
     * aparta de [Bezier.en] menos de una milesima en las curvas normales y
     * es decenas de veces mas rapida. Solo para buscar.
     */
    private class Tabulada(z: Bezier) {
        private val xs = FloatArray(PUNTOS + 1)
        private val ys = FloatArray(PUNTOS + 1)

        init {
            val x1 = z.x1.coerceIn(0f, 1f)
            val x2 = z.x2.coerceIn(0f, 1f)
            for (k in 0..PUNTOS) {
                val t = k / PUNTOS.toFloat()
                val u = 1f - t
                xs[k] = 3f * u * u * t * x1 + 3f * u * t * t * x2 + t * t * t
                ys[k] = 3f * u * u * t * z.y1 + 3f * u * t * t * z.y2 + t * t * t
            }
        }

        fun en(x: Float): Float {
            if (x <= 0f) return 0f
            if (x >= 1f) return 1f
            var lo = 0
            var hi = PUNTOS
            while (hi - lo > 1) {
                val m = (lo + hi) ushr 1
                if (xs[m] <= x) lo = m else hi = m
            }
            val ancho = xs[hi] - xs[lo]
            val f = if (ancho > 0f) (x - xs[lo]) / ancho else 0f
            return ys[lo] + (ys[hi] - ys[lo]) * f
        }

        private companion object {
            const val PUNTOS = 64
        }
    }

    /** Minimo de una funcion sin derivadas (Nelder-Mead), suficiente para cuatro numeros. */
    private object NelderMead {
        fun minimo(inicio: DoubleArray, paso: Double, iteraciones: Int, f: (DoubleArray) -> Double): DoubleArray {
            val d = inicio.size
            val puntos = Array(d + 1) { k -> inicio.copyOf().also { if (k > 0) it[k - 1] += paso } }
            val valores = DoubleArray(d + 1) { f(puntos[it]) }
            repeat(iteraciones) {
                val orden = (0..d).sortedBy { valores[it] }
                val mejor = orden.first()
                val peor = orden.last()
                val segundo = orden[d - 1]
                if (abs(valores[peor] - valores[mejor]) <= 1e-12 * (1 + abs(valores[mejor]))) return puntos[mejor]
                val centro = DoubleArray(d)
                for (k in orden.dropLast(1)) for (c in 0 until d) centro[c] += puntos[k][c] / d
                fun hacia(a: Double) = DoubleArray(d) { c -> centro[c] + a * (puntos[peor][c] - centro[c]) }
                val r = hacia(-1.0)
                val fr = f(r)
                when {
                    fr < valores[mejor] -> {
                        val e = hacia(-2.0)
                        val fe = f(e)
                        if (fe < fr) { puntos[peor] = e; valores[peor] = fe } else { puntos[peor] = r; valores[peor] = fr }
                    }
                    fr < valores[segundo] -> { puntos[peor] = r; valores[peor] = fr }
                    else -> {
                        val c = if (fr < valores[peor]) hacia(-0.5) else hacia(0.5)
                        val fc = f(c)
                        if (fc < minOf(fr, valores[peor])) {
                            puntos[peor] = c
                            valores[peor] = fc
                        } else {
                            for (k in 0..d) if (k != mejor) {
                                for (cc in 0 until d) puntos[k][cc] = puntos[mejor][cc] + 0.5 * (puntos[k][cc] - puntos[mejor][cc])
                                valores[k] = f(puntos[k])
                            }
                        }
                    }
                }
            }
            return puntos[(0..d).minByOrNull { valores[it] }!!]
        }
    }
}
