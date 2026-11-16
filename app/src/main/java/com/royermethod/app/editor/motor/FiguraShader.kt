package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.FormaRecorte

/**
 * La figura de un elemento, para las luces: lo que de verdad se ve de el (el
 * alfa de su imagen, su croma y su mascara de forma). Con ella una luz puede
 * seguir el borde del personaje recortado, quedarse solo encima de el o solo
 * por detras.
 *
 * FIGURA es el bloque que comparten los tres sombreadores que la miran. Usa
 * [Sombreadores.RECORTE_VISUAL], el mismo codigo con el que CAPA recorta: la
 * luz no puede ver un borde distinto del que se dibuja.
 */
internal object FiguraShader {

    const val PRECISION = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
"""

    const val FIGURA = """
uniform sampler2D sTex;
uniform vec2 uTexel;
uniform vec4 uRecorte;
uniform vec2 uFormaTam;
float cajaRedonda(vec2 p, vec2 medio, float r) {
    vec2 q = abs(p) - medio + vec2(r);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}
${Sombreadores.RECORTE_VISUAL}
// Cuanto se ve la figura en uv. Fuera del recorte sigue el pixel de la orilla:
// donde la imagen corta al personaje (la cintura, un lado) no hay un borde de
// verdad, y la luz no debe pintar una raya ahi.
float figura(vec2 uv, vec2 pixel) {
    vec2 lo = uRecorte.xy + uTexel * 0.5;
    vec2 hi = uRecorte.zw - uTexel * 0.5;
    float a = quitarColor(texture2D(sTex, clamp(uv, lo, hi))).a;
    if (uFormaTipo > 0.5) a *= formaEn(uv, pixel);
    return a;
}
"""

    /**
     * Primera pasada del campo: la figura, vista desde arriba y derecha, en
     * una lamina pequena. Cuatro muestras por texel para que la orilla quede
     * en su sitio con precision de menos de un texel.
     */
    const val SILUETA = """
${PRECISION}
varying vec2 vPos;
uniform vec4 uRegion; // uv de la esquina del campo y lo que mide en uv
uniform vec2 uCuarto; // un cuarto de texel del campo, en uv
${FIGURA}
void main() {
    vec2 uv = uRegion.xy + vPos * uRegion.zw;
    vec2 pixel = uCuarto * 4.0;
    float a = figura(uv + vec2(-uCuarto.x, -uCuarto.y), pixel)
            + figura(uv + vec2( uCuarto.x, -uCuarto.y), pixel)
            + figura(uv + vec2(-uCuarto.x,  uCuarto.y), pixel)
            + figura(uv + vec2( uCuarto.x,  uCuarto.y), pixel);
    gl_FragColor = vec4(a * 0.25);
}
"""

    /**
     * Desenfoque gaussiano en una direccion, con una anchura distinta en cada
     * canal: de una sola figura salen de una vez varios niveles del campo.
     * Las muestras van de texel en texel del destino; si la fuente mide el
     * doble, cada una ya promedia sus cuatro texeles.
     */
    const val DESENFOQUE = """
${PRECISION}
varying vec2 vPos;
uniform sampler2D sTex;
uniform vec2 uPaso;   // un texel del destino en la direccion de la pasada
uniform vec4 uPeso;   // exp(-0.5 / anchura^2) de cada canal, con la anchura en texeles del destino
uniform float uRadio; // hasta donde llegan las muestras, en texeles
uniform vec4 uCanal;  // con algun 1: los cuatro canales salen de ese canal de la fuente
vec4 en(vec2 p) {
    vec4 c = texture2D(sTex, p);
    return dot(uCanal, vec4(1.0)) > 0.5 ? vec4(dot(c, uCanal)) : c;
}
void main() {
    // Los pesos de la campana sin una exponencial por muestra: el de cada
    // distancia sale del anterior, w(d+1) = w(d) * r(d) y r(d+1) = r(d) * q
    vec4 r = uPeso;
    vec4 q = uPeso * uPeso;
    vec4 w = vec4(1.0);
    vec4 suma = en(vPos);
    vec4 pesos = vec4(1.0);
    for (int i = 1; i <= 60; i++) {
        if (float(i) > uRadio) break;
        w *= r;
        r *= q;
        float d = float(i);
        suma += (en(vPos + uPaso * d) + en(vPos - uPaso * d)) * w;
        pesos += 2.0 * w;
    }
    gl_FragColor = suma / pesos;
}
"""

    /** Como el vertice de las otras luces, con la uv en alta precision: aqui se usa para leer la imagen. */
    const val VERTICE_CONTORNO = """
attribute vec2 aUv;
uniform mat3 uDirecta;
uniform mediump vec2 uLienzo;
uniform lowp float uVoltear;
varying vec2 vUv;
void main() {
    vec3 p = uDirecta * vec3(aUv, 1.0);
    float y = p.z - 2.0 * p.y / uLienzo.y;
    if (uVoltear > 0.5) y = -y;
    gl_Position = vec4(2.0 * p.x / uLienzo.x - p.z, y, 0.0, p.z);
    vUv = aUv;
}
"""

    /**
     * La luz de contorno.
     *
     * El campo son siete copias de la figura cada vez mas desenfocadas (cada
     * nivel, el doble que el anterior). En una orilla recta el desenfocado
     * vale 0.5 justo en el borde y sube hacia dentro: de ese valor sale a que
     * distancia del borde esta cada pixel, sin buscarlo alrededor.
     *
     * - La linea: dentro de la figura, hasta [grosor] del borde. La gruesa se
     *   lee del campo; la fina (menos de lo que el campo distingue) se busca
     *   en la propia imagen con dos anillos de muestras, para que sea exacta.
     * - Hacia dentro y resplandor: el mismo desenfocado, a un lado y al otro
     *   del borde.
     * - La zona: la distancia al camino dibujado. El grosor se multiplica por
     *   ella, asi la linea se afina hasta desaparecer en vez de cortarse.
     * - La luz de un lado: todo se mira un poco desplazado hacia la luz. Un
     *   punto esta en la linea si, yendo hacia la luz, a [grosor] ya se ha
     *   salido de la figura: el borde que la mira de frente se enciende
     *   entero, el que esta de lado se afina y el de detras se apaga, sin
     *   costuras (no hace falta saber hacia donde mira cada trozo).
     */
    const val CONTORNO = """
${PRECISION}
varying vec2 vUv;
uniform mediump vec2 uLienzo;
uniform lowp float uVoltear;
${FIGURA}
uniform sampler2D sCampoA; // r: nivel 0
uniform sampler2D sCampoB; // r, g: niveles 1 y 2
uniform sampler2D sCampoC; // r, g, b, a: niveles 3 a 6 (a un octavo de resolucion)
uniform vec4 uCampo;       // esquina del campo y 1 / lo que mide, todo en unidades del lado corto
uniform float uSigma0;     // anchura del nivel 0, en unidades del lado corto
uniform vec2 uUnidad;      // lo que mide el recorte en unidades de su lado corto
uniform vec4 uCorte;       // donde se dibuja el elemento
uniform vec4 uLimite;      // fuera de aqui no hay luz (la region de una parte)
uniform vec4 uColor;
uniform vec4 uLuz;         // grosor, resplandor, hacia dentro, relleno
uniform vec3 uDireccion;   // cuanto, y hacia donde esta la luz (x, y)
uniform float uOpacidad;
uniform float uPixel;      // un pixel del lienzo, en unidades del lado corto
uniform vec2 uCamino[32];
uniform float uCaminoN;    // menos de 2: el borde entero
uniform vec2 uZona;        // hasta donde llega y desde donde empieza a caer
uniform float uConMascara;
uniform vec4 uMasc;
uniform vec4 uMascDatos;
uniform float uBorde;

float zona(vec2 q) {
    if (uCaminoN < 1.5) return 1.0;
    float d2 = 1000.0;
    vec2 a = uCamino[0];
    for (int j = 1; j < 32; j++) {
        if (float(j) >= uCaminoN) break;
        vec2 b = uCamino[j];
        vec2 ab = b - a;
        vec2 v = q - a;
        vec2 c = v - ab * clamp(dot(v, ab) / max(dot(ab, ab), 0.0000001), 0.0, 1.0);
        d2 = min(d2, dot(c, c));
        a = b;
    }
    return 1.0 - smoothstep(uZona.y, uZona.x, sqrt(d2));
}

// El campo en c con una anchura de uSigma0 * 2^k: mezcla los dos niveles
// vecinos y solo lee las texturas que los tienen.
float campo(vec2 c, float k) {
    k = clamp(k, 0.0, 6.0);
    if (k >= 3.0) return dot(max(vec4(1.0) - abs(vec4(k) - vec4(3.0, 4.0, 5.0, 6.0)), 0.0), texture2D(sCampoC, c));
    vec2 b = texture2D(sCampoB, c).rg;
    if (k >= 2.0) return mix(b.y, texture2D(sCampoC, c).r, k - 2.0);
    if (k >= 1.0) return mix(b.x, b.y, k - 1.0);
    return mix(texture2D(sCampoA, c).r, b.x, k);
}

void main() {
    if (vUv.x < uLimite.x || vUv.y < uLimite.y || vUv.x > uLimite.z || vUv.y > uLimite.w) discard;
    vec2 tam = max(uRecorte.zw - uRecorte.xy, vec2(0.0001));
    vec2 q = (vUv - uRecorte.xy) / tam * uUnidad;
    float z = zona(q);
    if (z < 0.002) discard;
    vec2 c = (q - uCampo.xy) * uCampo.zw;
    float s0 = uSigma0;
    float lado = uDireccion.x;
    vec2 haciaLuz = uDireccion.yz;
    // Lejos de cualquier borde no hay nada que encender. Se sabe con una lectura
    // del campo, a la anchura de lo que mas lejos llega esta luz, y se ahorra
    // todo lo demas: lo que cuesta la luz es su franja, no el elemento entero.
    float llega = max(max(uLuz.x, uLuz.z * (4.5 + 2.25 * lado)), uLuz.y * (11.1 + 5.5 * lado));
    float kLejos = log2(max(llega / 2.3, s0) / s0);
    if (kLejos <= 6.0) {
        float b = campo(c, kLejos);
        if (b < 0.002 || (b > 0.998 && uLuz.w <= 0.0)) discard;
    }
    // Un pixel del lienzo en uv, para suavizar la orilla del recorte y la de la forma
    vec2 pixel = uPixel * tam / uUnidad;
    float ext = figura(vUv, pixel);
    vec4 orilla = clamp(vec4(vUv - uCorte.xy, uCorte.zw - vUv) / vec4(pixel, pixel) + 0.5, 0.0, 1.0);
    float a0 = ext * orilla.x * orilla.y * orilla.z * orilla.w;
    // De todo el contorno (2 en el borde) a solo el que mira a la luz (1 de frente, 0.5 de lado)
    float ganancia = mix(2.0, 1.07, lado);

    // La linea. El grosor baja con la zona: se afina antes de apagarse.
    float w = uLuz.x * z;
    float linea = 0.0;
    if (w > 0.0 && a0 > 0.0) {
        // Lo que se enciende mire a donde mire
        float wt = w * (1.0 - lado);
        if (wt > 0.0) {
            float sw = max(wt / 1.2, s0);
            float umbral = 1.0 / (1.0 + exp(-1.702 * wt / sw));
            float aa = clamp(0.15 * uPixel / sw, 0.004, 0.25);
            linea = (1.0 - smoothstep(umbral - aa, umbral + aa, campo(c, log2(sw / s0)))) * smoothstep(1.0, 2.0, wt / s0);
            float n0 = texture2D(sCampoA, c).r;
            if (n0 > 0.01 && n0 < 0.99) {
                float wr = min(wt, 1.5 * s0);
                vec2 r = wr * tam / uUnidad;
                float m = figura(vUv + r * vec2(1.0, 0.0), pixel);
                m = min(m, figura(vUv + r * vec2(-1.0, 0.0), pixel));
                m = min(m, figura(vUv + r * vec2(0.0, 1.0), pixel));
                m = min(m, figura(vUv + r * vec2(0.0, -1.0), pixel));
                m = min(m, figura(vUv + r * vec2(0.7071, 0.7071), pixel));
                m = min(m, figura(vUv + r * vec2(-0.7071, 0.7071), pixel));
                m = min(m, figura(vUv + r * vec2(0.7071, -0.7071), pixel));
                m = min(m, figura(vUv + r * vec2(-0.7071, -0.7071), pixel));
                // Un segundo anillo a media distancia y girado: los huecos estrechos
                // (entre mechones) que el primero salta por encima
                vec2 r2 = r * 0.5;
                m = min(m, figura(vUv + r2 * vec2(0.9239, 0.3827), pixel));
                m = min(m, figura(vUv + r2 * vec2(-0.9239, 0.3827), pixel));
                m = min(m, figura(vUv + r2 * vec2(0.9239, -0.3827), pixel));
                m = min(m, figura(vUv + r2 * vec2(-0.9239, -0.3827), pixel));
                m = min(m, figura(vUv + r2 * vec2(0.3827, 0.9239), pixel));
                m = min(m, figura(vUv + r2 * vec2(-0.3827, 0.9239), pixel));
                m = min(m, figura(vUv + r2 * vec2(0.3827, -0.9239), pixel));
                m = min(m, figura(vUv + r2 * vec2(-0.3827, -0.9239), pixel));
                // Con menos de un pixel de grosor, lo que baja es la fuerza
                linea = max(linea, (1.0 - m) * clamp(wr / uPixel, 0.0, 1.0));
            }
        }
        if (lado > 0.0)
            linea = max(linea, (1.0 - figura(vUv + w * haciaLuz * tam / uUnidad, pixel)) * clamp(w / uPixel, 0.0, 1.0));
        linea *= a0;
    }

    // Hacia dentro: cae desde el borde. Tambien se recoge con la zona.
    float dentro = uLuz.w;
    if (uLuz.z > 0.0 && a0 > 0.0) {
        float alcance = 1.5 * uLuz.z * (0.4 + 0.6 * z);
        float b = campo(c + haciaLuz * uCampo.zw * (1.5 * alcance * lado), log2(max(alcance, s0) / s0));
        dentro = max(dentro, clamp(ganancia * (1.0 - b), 0.0, 1.0) * min(alcance / s0, 1.0) * 0.85);
    }
    dentro *= a0;

    // Resplandor: lo mismo hacia fuera, con una cola mas ancha y mas tenue.
    float fuera = 0.0;
    if (uLuz.y > 0.0 && ext < 1.0) {
        float alcance = 1.5 * uLuz.y;
        float sk = max(alcance, s0);
        float k = log2(sk / s0);
        float corrido = 1.5 * alcance * lado;
        vec2 paso = haciaLuz * uCampo.zw * corrido;
        fuera = 0.7 * clamp(ganancia * campo(c - paso, k), 0.0, 1.0) + 0.3 * clamp(ganancia * campo(c - paso * 2.46, k + 1.3), 0.0, 1.0);
        // Fuera de la imagen la figura sigue como en su orilla (ver figura()), pero
        // ahi no hay nada dibujado. El resplandor no puede ser mas que el que
        // llegaria desde la orilla: se apaga al alejarse en vez de seguir sin fin
        // a los lados de un cuerpo que la imagen corta.
        float salido = length(max(max(-q, q - uUnidad), 0.0));
        if (salido > 0.0) {
            vec2 d = min(1.702 * (vec2(salido) - corrido * vec2(1.0, 2.46)) / (sk * vec2(1.0, 2.46)), 30.0);
            vec2 tope = clamp(ganancia / (1.0 + exp(d)), 0.0, 1.0);
            fuera = min(fuera, 0.7 * tope.x + 0.3 * tope.y);
        }
        // El campo se acaba: el resplandor llega a cero antes de su orilla
        vec2 lejos = min(c, 1.0 - c);
        fuera *= 0.6 * min(alcance / s0, 1.0) * (1.0 - ext) * smoothstep(0.0, 0.05, min(lejos.x, lejos.y));
    }

    float mascara = 1.0;
    if (uConMascara > 0.5) {
        vec2 px = vec2(gl_FragCoord.x, uVoltear > 0.5 ? gl_FragCoord.y : uLienzo.y - gl_FragCoord.y);
        vec2 m = px - (0.5 * uLienzo + uMascDatos.xy);
        float d = uMascDatos.z > 0.5 ? length(m) : dot(m, uMasc.xy);
        mascara = clamp((uBorde - d) / uMasc.z + 0.5, 0.0, 1.0);
        if (uMasc.w > 0.0 && abs(uBorde) < 15000.0) mascara *= clamp((d - (uBorde - uMasc.w)) / uMasc.z + 0.5, 0.0, 1.0);
        if (uMascDatos.w > 0.5) mascara = 1.0 - mascara;
    }

    float brillo = max(dentro, fuera);
    float a = clamp(linea + brillo * (1.0 - linea), 0.0, 1.0) * smoothstep(0.0, 0.35, z) * uOpacidad * uColor.a * mascara;
    gl_FragColor = vec4(mix(uColor.rgb, vec3(1.0), linea * 0.8) * a, a);
}
"""

    /**
     * Lo que FIGURA necesita saber del elemento. [unidad] es la textura que
     * ya tiene puesta su imagen.
     */
    fun cargar(p: Programa, clip: Clip, texAncho: Int, texAlto: Int, unidad: Int = 0) {
        val rc = clip.encuadre.recorte
        GLES20.glUniform1i(p.u("sTex"), unidad)
        GLES20.glUniform2f(p.u("uTexel"), 1f / texAncho, 1f / texAlto)
        GLES20.glUniform4f(p.u("uRecorte"), rc.izquierda, rc.arriba, 1f - rc.derecha, 1f - rc.abajo)
        val (ax, ay) = unidad(clip, texAncho, texAlto)
        GLES20.glUniform2f(p.u("uFormaTam"), ax, ay)
        val croma = clip.recorteVisual.croma
        GLES20.glUniform1f(p.u("uCromaActivo"), if (croma.efectivo) 1f else 0f)
        if (croma.efectivo) {
            GLES20.glUniform3f(p.u("uCromaColor"), ((croma.color ushr 16) and 255) / 255f,
                ((croma.color ushr 8) and 255) / 255f, (croma.color and 255) / 255f)
            GLES20.glUniform4f(p.u("uCroma"), croma.intensidad, croma.tolerancia, croma.suavidad, croma.limpiar)
            GLES20.glUniform1f(p.u("uCromaInvertir"), if (croma.invertir) 1f else 0f)
        }
        val forma = clip.recorteVisual.mascara
        GLES20.glUniform1f(p.u("uFormaTipo"), forma.tipo.ordinal.toFloat())
        if (forma.tipo != FormaRecorte.NINGUNA) {
            GLES20.glUniform2f(p.u("uFormaCentro"), forma.x, forma.y)
            GLES20.glUniform4f(p.u("uFormaDatos"), forma.ancho, forma.alto, Math.toRadians(forma.giro.toDouble()).toFloat(), forma.pluma)
            GLES20.glUniform1f(p.u("uFormaRadio"), forma.radio)
            GLES20.glUniform1f(p.u("uFormaInvertir"), if (forma.invertir) 1f else 0f)
            val ps = forma.poligono()
            GLES20.glUniform1f(p.u("uPoligonoN"), ps.size.toFloat())
            if (ps.isNotEmpty()) {
                val puntos = FloatArray(ps.size * 2)
                ps.forEachIndexed { i, v -> puntos[i * 2] = v.first; puntos[i * 2 + 1] = v.second }
                GLES20.glUniform2fv(p.u("uPoligono[0]"), ps.size, puntos, 0)
            }
        }
    }

    /** Lo que mide la parte visible del elemento, con su lado corto como unidad. */
    fun unidad(clip: Clip, texAncho: Int, texAlto: Int): Pair<Float, Float> {
        val rc = clip.encuadre.recorte
        val ar = texAncho * rc.ancho / (texAlto * rc.alto).coerceAtLeast(1f)
        return if (ar >= 1f) ar to 1f else 1f to 1f / ar
    }
}
