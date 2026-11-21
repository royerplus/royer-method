package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.PasoClip

/**
 * Desenfoque de movimiento del contenido de un video sin el modelo de
 * interpolacion: la estela por flujo optico.
 *
 * Por cada par de fotogramas del archivo (A y el siguiente, B) se estima
 * hacia donde se mueve cada zona de la imagen, todo en la GPU y a poca
 * resolucion: primero a 1/16 buscando lejos y luego a 1/4 afinando cerca de
 * lo encontrado. Despues cada pixel del fotograma que sale promedia varias
 * tomas a lo largo de su vector, repartidas por la exposicion del obturador.
 *
 * Es mucho mas barato que RIFE (unas pasadas cortas en la GPU frente a una
 * red neuronal por cada instante), pero mas tosco: donde un objeto tapa a
 * otro, en los bordes de la imagen o con movimientos muy rapidos (mas de unos
 * 70 px por fotograma) la estela puede salir corrida o faltar.
 */
class Estela {

    private val bajarDos = Programa(Sombreadores.VERTICE, BAJAR_DOS)
    private val bajar = Programa(Sombreadores.VERTICE, BAJAR)
    private val buscarLejos = Programa(Sombreadores.VERTICE, buscar(4))
    private val buscarCerca = Programa(Sombreadores.VERTICE, buscar(2))
    private val suavizar = Programa(Sombreadores.VERTICE, SUAVIZAR)
    private val vecino = Programa(Sombreadores.VERTICE, VECINO)
    private val estirar = Programa(Sombreadores.VERTICE, ESTIRAR)
    private val copia = Programa(Sombreadores.VERTICE, Sombreadores.COPIA_EXACTA)

    private class Estado(val ancho: Int, val alto: Int) {
        var a = Lamina(ancho, alto)
        var b = Lamina(ancho, alto)
        var ptsA = -1L
        var ptsB = -1L
        val cuarto = Lamina(maxOf(1, ancho / 4), maxOf(1, alto / 4))
        val dieciseisavo = Lamina(maxOf(1, ancho / 16), maxOf(1, alto / 16))
        val flujoLejos = Lamina(dieciseisavo.ancho, dieciseisavo.alto)
        val flujoCerca = Lamina(cuarto.ancho, cuarto.alto)
        val flujo = Lamina(cuarto.ancho, cuarto.alto)
        /** El vector mas largo de alrededor, a 1/16: hacia donde se estira. */
        val dominante = Lamina(dieciseisavo.ancho, dieciseisavo.alto)
        /** El par de fotogramas del que es [flujo]. */
        var flujoDe: Pair<Long, Long>? = null
        val salida = Lamina(ancho, alto)
        var claveSalida: Triple<Long, Long, Int>? = null

        fun soltar() {
            listOf(a, b, cuarto, dieciseisavo, flujoLejos, flujoCerca, flujo, dominante, salida).forEach { it.soltar() }
        }
    }

    private val estados = HashMap<FuenteVideo, Estado>()

    /**
     * El fotograma de [paso] con la estela del movimiento.
     *
     * @return null si no hay par de fotogramas con que medir (fin del
     *   archivo, salto fallido); quien llama sigue por el camino normal.
     */
    fun fotograma(fuente: FuenteVideo, paso: PasoClip, ancho: Int, alto: Int, reduccion: Reduccion): Lamina? {
        var e = estados[fuente]
        if (e == null || e.ancho != ancho || e.alto != alto) {
            e?.soltar()
            e = Estado(ancho, alto)
            estados[fuente] = e
        }
        val tomas = paso.muestrasFluido.coerceIn(1, MAX_TOMAS)
        val clave = Triple(paso.tiempoArchivoUs, paso.exposicionUs, tomas)
        if (e.claveSalida == clave) return e.salida

        val t = paso.tiempoArchivoUs
        if (!ventana(fuente, e, t, reduccion)) return null
        while (e.ptsB in 0..t && !fuente.agotada) {
            if (!avanzar(fuente, e, reduccion)) break
        }
        if (e.ptsB <= e.ptsA || e.ptsA < 0) return null

        GLES20.glDisable(GLES20.GL_BLEND)
        val par = e.ptsA to e.ptsB
        if (e.flujoDe != par) {
            medirFlujo(e)
            e.flujoDe = par
        }

        // Donde cae t entre A y B, y cuanto del intervalo cubre la exposicion
        val intervalo = (e.ptsB - e.ptsA).toFloat()
        val centro = ((t - e.ptsA) / intervalo).coerceIn(0f, 1f)
        val largo = (paso.exposicionUs / intervalo).coerceIn(0f, 2f)
        val desdeB = centro >= 0.5f
        e.salida.activar()
        estirar.usar()
        textura(0, if (desdeB) e.b.textura else e.a.textura, estirar, "sFoto")
        textura(1, e.flujo.textura, estirar, "sFlujo")
        textura(2, e.dominante.textura, estirar, "sDominante")
        GLES20.glUniform2f(estirar.u("uTam"), ancho.toFloat(), alto.toFloat())
        GLES20.glUniform1f(estirar.u("uCentro"), centro)
        GLES20.glUniform1f(estirar.u("uBase"), if (desdeB) 1f else 0f)
        GLES20.glUniform1f(estirar.u("uLargo"), largo)
        GLES20.glUniform1f(estirar.u("uN"), tomas.toFloat())
        estirar.dibujar(voltear = true)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        e.claveSalida = clave
        return e.salida
    }

    private fun medirFlujo(e: Estado) {
        // Brillo de A en r y de B en g, a 1/4 y a 1/16
        e.cuarto.activar()
        bajarDos.usar()
        textura(0, e.a.textura, bajarDos, "sA")
        textura(1, e.b.textura, bajarDos, "sB")
        GLES20.glUniform2f(bajarDos.u("uTexel"), 1f / e.ancho, 1f / e.alto)
        bajarDos.dibujar(voltear = true)

        e.dieciseisavo.activar()
        bajar.usar()
        textura(0, e.cuarto.textura, bajar, "sTex")
        GLES20.glUniform2f(bajar.u("uTexel"), 1f / e.cuarto.ancho, 1f / e.cuarto.alto)
        bajar.dibujar(voltear = true)

        // Lejos a 1/16 (hasta 4 de sus pixeles: 64 del video), sin punto de partida
        buscarEn(e, e.dieciseisavo, e.flujoLejos, null)
        // Cerca a 1/4, partiendo de lo de 1/16
        buscarEn(e, e.cuarto, e.flujoCerca, e.flujoLejos)
        // Un poco de suavidad: el vector de un pixel suelto no deja costuras en la estela
        e.flujo.activar()
        suavizar.usar()
        textura(0, e.flujoCerca.textura, suavizar, "sTex")
        GLES20.glUniform2f(suavizar.u("uTexel"), 1f / e.flujo.ancho, 1f / e.flujo.alto)
        suavizar.dibujar(voltear = true)
        // El dominante: sin el, el fondo quieto junto a algo que se mueve no
        // recibe la estela (su propio vector es cero) y el borde sale seco
        e.dominante.activar()
        vecino.usar()
        textura(0, e.flujo.textura, vecino, "sTex")
        GLES20.glUniform2f(vecino.u("uTexel"), 1f / e.flujo.ancho, 1f / e.flujo.alto)
        vecino.dibujar(voltear = true)
    }

    private fun buscarEn(e: Estado, nivel: Lamina, destino: Lamina, previo: Lamina?) {
        val p = if (previo == null) buscarLejos else buscarCerca
        destino.activar()
        p.usar()
        textura(0, nivel.textura, p, "sNivel")
        textura(1, (previo ?: nivel).textura, p, "sPrevio")
        GLES20.glUniform1f(p.u("uConPrevio"), if (previo != null) 1f else 0f)
        GLES20.glUniform2f(p.u("uTexel"), 1f / nivel.ancho, 1f / nivel.alto)
        // Pixeles del video por pixel de este nivel (1/4 o 1/16, redondeado)
        GLES20.glUniform2f(p.u("uPx"), e.ancho.toFloat() / nivel.ancho, e.alto.toFloat() / nivel.alto)
        p.dibujar(voltear = true)
    }

    private fun textura(unidad: Int, id: Int, p: Programa, nombre: String) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0 + unidad)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glUniform1i(p.u(nombre), unidad)
    }

    /** Deja en [e] el fotograma que ya empezo en [t] y el siguiente, como Fluidez. */
    private fun ventana(fuente: FuenteVideo, e: Estado, t: Long, reduccion: Reduccion): Boolean {
        val valida = e.ptsA in 0..t && (e.ptsB > t || fuente.agotada || t - e.ptsB < 1_000_000)
        if (valida) return true
        if (!fuente.irA(t)) return false
        if (fuente.ptsActual < 0) return false
        copiarA(fuente.copiar(e.ancho, e.alto, reduccion), e.a)
        e.ptsA = fuente.ptsActual
        if (fuente.siguiente()) {
            copiarA(fuente.copiar(e.ancho, e.alto, reduccion), e.b)
            e.ptsB = fuente.ptsActual
        } else {
            e.ptsB = e.ptsA
        }
        return true
    }

    private fun avanzar(fuente: FuenteVideo, e: Estado, reduccion: Reduccion): Boolean {
        val viejo = e.a
        e.a = e.b
        e.ptsA = e.ptsB
        e.b = viejo
        if (!fuente.siguiente()) {
            e.ptsB = e.ptsA
            return false
        }
        copiarA(fuente.copiar(e.ancho, e.alto, reduccion), e.b)
        e.ptsB = fuente.ptsActual
        return true
    }

    private fun copiarA(origen: Lamina, destino: Lamina) {
        GLES20.glDisable(GLES20.GL_BLEND)
        destino.activar()
        copia.usar()
        textura(0, origen.textura, copia, "sTex")
        copia.dibujar(voltear = true)
    }

    /** La fuente se cerro: lo suyo sobra. */
    fun olvidar(fuente: FuenteVideo) {
        estados.remove(fuente)?.soltar()
    }

    fun soltar() {
        estados.values.forEach { it.soltar() }
        estados.clear()
        listOf(bajarDos, bajar, buscarLejos, buscarCerca, suavizar, vecino, estirar, copia).forEach { it.soltar() }
    }

    companion object {
        /** Tope de tomas a lo largo del vector, como las muestras del obturador. */
        const val MAX_TOMAS = 32

        private const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

        /** Los vectores van en pixeles del video, de -128 a 127, en r y g. */
        private const val VECTOR = """
vec2 vector(vec4 c) { return (c.rg - 0.5) * 256.0; }
vec4 guardar(vec2 v) { return vec4(clamp(v / 256.0 + 0.5, 0.0, 1.0), 0.0, 1.0); }
"""

        /** Brillo de A (r) y B (g) a 1/4: cuatro tomas bilineales cubren 4x4. */
        private const val BAJAR_DOS = PRECISION + """
varying vec2 vPos;
uniform sampler2D sA;
uniform sampler2D sB;
uniform vec2 uTexel;
const vec3 LUMA = vec3(0.299, 0.587, 0.114);
float brillo(sampler2D s) {
    return dot(texture2D(s, vPos + uTexel * vec2(-1.0, -1.0)).rgb
             + texture2D(s, vPos + uTexel * vec2( 1.0, -1.0)).rgb
             + texture2D(s, vPos + uTexel * vec2(-1.0,  1.0)).rgb
             + texture2D(s, vPos + uTexel * vec2( 1.0,  1.0)).rgb, LUMA) * 0.25;
}
void main() { gl_FragColor = vec4(brillo(sA), brillo(sB), 0.0, 1.0); }
"""

        /** Lo mismo de 1/4 a 1/16, con los dos brillos ya en r y g. */
        private const val BAJAR = PRECISION + """
varying vec2 vPos;
uniform sampler2D sTex;
uniform vec2 uTexel;
void main() {
    vec2 s = texture2D(sTex, vPos + uTexel * vec2(-1.0, -1.0)).rg
           + texture2D(sTex, vPos + uTexel * vec2( 1.0, -1.0)).rg
           + texture2D(sTex, vPos + uTexel * vec2(-1.0,  1.0)).rg
           + texture2D(sTex, vPos + uTexel * vec2( 1.0,  1.0)).rg;
    gl_FragColor = vec4(s * 0.25, 0.0, 1.0);
}
"""

        /**
         * Busqueda por bloques: para cada pixel del nivel, el desplazamiento
         * de hasta [radio] pixeles con el que su vecindario de 3x3 en A se
         * parece mas a B. Un pequeno castigo por la distancia hace que en
         * zonas lisas, donde todo se parece, gane el vector mas corto.
         */
        private fun buscar(radio: Int): String = PRECISION + VECTOR + """
varying vec2 vPos;
uniform sampler2D sNivel;
uniform sampler2D sPrevio;
uniform float uConPrevio;
uniform vec2 uTexel;
uniform vec2 uPx; // pixeles del video por pixel del nivel
void main() {
    vec2 inicio = uConPrevio > 0.5 ? vector(texture2D(sPrevio, vPos)) / uPx : vec2(0.0);
    float a[9];
    for (int j = 0; j < 9; j++) {
        vec2 o = vec2(float(j - (j / 3) * 3) - 1.0, float(j / 3) - 1.0);
        a[j] = texture2D(sNivel, vPos + o * uTexel).r;
    }
    float mejor = 1e9;
    vec2 elegido = inicio;
    for (int y = -$radio; y <= $radio; y++) {
        for (int x = -$radio; x <= $radio; x++) {
            vec2 d = inicio + vec2(float(x), float(y));
            float coste = 0.0;
            for (int j = 0; j < 9; j++) {
                vec2 o = vec2(float(j - (j / 3) * 3) - 1.0, float(j / 3) - 1.0);
                coste += abs(a[j] - texture2D(sNivel, vPos + (o + d) * uTexel).g);
            }
            coste += 0.004 * length(d);
            if (coste < mejor) { mejor = coste; elegido = d; }
        }
    }
    gl_FragColor = guardar(elegido * uPx);
}
"""

        private const val SUAVIZAR = PRECISION + VECTOR + """
varying vec2 vPos;
uniform sampler2D sTex;
uniform vec2 uTexel;
void main() {
    vec2 s = vec2(0.0);
    for (int y = -1; y <= 1; y++)
        for (int x = -1; x <= 1; x++)
            s += vector(texture2D(sTex, vPos + vec2(float(x), float(y)) * uTexel));
    gl_FragColor = guardar(s / 9.0);
}
"""

        /**
         * A 1/16: el vector mas largo en un cuadro de 12x12 del flujo a 1/4
         * (unos 48 px del video alrededor). Es el que marca hacia donde se
         * estira cada pixel.
         */
        private const val VECINO = PRECISION + VECTOR + """
varying vec2 vPos;
uniform sampler2D sTex;
uniform vec2 uTexel;
void main() {
    vec2 mejor = vec2(0.0);
    for (int y = 0; y < 12; y++)
        for (int x = 0; x < 12; x++) {
            vec2 v = vector(texture2D(sTex, vPos + (vec2(float(x), float(y)) - 5.5) * uTexel));
            if (dot(v, v) > dot(mejor, mejor)) mejor = v;
        }
    gl_FragColor = guardar(mejor);
}
"""

        /**
         * Cada pixel promedia [uN] tomas a lo largo del vector dominante de su
         * zona. La toma del instante tau (0 = A, 1 = B) viene de A en p - v*tau
         * o de B en p + v*(1 - tau): se usa el fotograma mas cercano al centro.
         *
         * Cada toma cuenta si su propia estela llega hasta este pixel o si la
         * de este pixel llega hasta ella (la idea del filtro de reconstruccion
         * de McGuire, sin profundidad): asi lo que se mueve deja rastro sobre
         * el fondo quieto, y el fondo no se emborrona con el vector de otro.
         */
        private const val ESTIRAR = PRECISION + VECTOR + """
varying vec2 vPos;
uniform sampler2D sFoto;
uniform sampler2D sFlujo;
uniform sampler2D sDominante;
uniform vec2 uTam;
uniform float uCentro;
uniform float uBase;
uniform float uLargo;
uniform float uN;
void main() {
    vec2 vn = vector(texture2D(sDominante, vPos));
    vec2 vx = vector(texture2D(sFlujo, vPos));
    vec2 desp = vx * (uCentro - uBase);
    if (dot(vn, vn) * uLargo * uLargo < 0.25) {
        gl_FragColor = texture2D(sFoto, vPos - desp / uTam);
        return;
    }
    float alcanceX = length(vx) * uLargo * 0.5;
    vec4 suma = vec4(0.0);
    float pesos = 0.0;
    for (int i = 0; i < 32; i++) {
        if (float(i) >= uN) break;
        float s = ((float(i) + 0.5) / uN - 0.5) * uLargo;
        vec2 y = vPos - (desp + vn * s) / uTam;
        float d = length(vn * s);
        float alcanceY = length(vector(texture2D(sFlujo, y))) * uLargo * 0.5;
        float w = max(clamp(1.0 + alcanceY - d, 0.0, 1.0), clamp(1.0 + alcanceX - d, 0.0, 1.0));
        suma += texture2D(sFoto, y) * w;
        pesos += w;
    }
    gl_FragColor = pesos > 0.0 ? suma / pesos : texture2D(sFoto, vPos - desp / uTam);
}
"""
    }
}
