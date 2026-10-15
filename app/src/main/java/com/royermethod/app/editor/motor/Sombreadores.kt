package com.royermethod.app.editor.motor

/**
 * Los sombreadores del editor, en GLSL ES 1.00 (OpenGL ES 2).
 *
 * Van como texto plano, sin plantillas de Kotlin dentro, para poder sacarlos
 * tal cual y compilarlos fuera del telefono en las pruebas.
 *
 * Todo trabaja con alfa premultiplicado: las texturas de Android (Bitmap)
 * llegan asi, y mezclar capas premultiplicadas es una suma limpia, sin el
 * borde oscuro que sale al mezclar alfa normal.
 */
object Sombreadores {

    /**
     * Rectangulo que cubre el destino. vPos va de 0 a 1 con el origen arriba
     * a la izquierda, igual en pantalla que en una lamina (ver Lamina).
     */
    const val VERTICE = """
attribute vec2 aPos;
uniform float uVoltear;
varying vec2 vPos;
void main() {
    vPos = vec2(aPos.x * 0.5 + 0.5, 0.5 - aPos.y * 0.5);
    float y = aPos.y;
    if (uVoltear > 0.5) y = -y;
    gl_Position = vec4(aPos.x, y, 0.0, 1.0);
}
"""

    /**
     * Del fotograma del decodificador a una textura normal, derecha y del
     * tamano al que se va a usar.
     *
     * Cuatro muestras bilineales por pixel: al reducir a la mitad, cada una
     * promedia cuatro pixeles del original y entre las cuatro cubren un
     * cuadrado de cuatro por cuatro. Una sola muestra se saltaria pixeles y
     * las lineas finas del anime saldrian con dientes.
     */
    const val COPIA_EXTERNA = """
#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform samplerExternalOES sTex;
uniform mat3 uMapa;
uniform vec2 uPaso;
vec4 en(vec2 c) { return texture2D(sTex, (uMapa * vec3(c, 1.0)).xy); }
void main() {
    vec4 s = en(vPos + vec2(-uPaso.x, -uPaso.y))
           + en(vPos + vec2( uPaso.x, -uPaso.y))
           + en(vPos + vec2(-uPaso.x,  uPaso.y))
           + en(vPos + vec2( uPaso.x,  uPaso.y));
    gl_FragColor = vec4(s.rgb * 0.25, 1.0);
}
"""

    /** Lo mismo desde una textura normal: imagenes y reducciones en cadena. */
    const val COPIA_PLANA = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform sampler2D sTex;
uniform mat3 uMapa;
uniform vec2 uPaso;
vec4 en(vec2 c) { return texture2D(sTex, (uMapa * vec3(c, 1.0)).xy); }
void main() {
    gl_FragColor = (en(vPos + vec2(-uPaso.x, -uPaso.y))
                  + en(vPos + vec2( uPaso.x, -uPaso.y))
                  + en(vPos + vec2(-uPaso.x,  uPaso.y))
                  + en(vPos + vec2( uPaso.x,  uPaso.y))) * 0.25;
}
"""

    /**
     * Un bloque sobre el lienzo.
     *
     * Cada pixel del lienzo pregunta, para cada muestra del obturador, de que
     * punto de la imagen viene (uInv), y promedia. Si el bloque se movio
     * durante el fotograma, las muestras caen en sitios distintos y el
     * promedio es el rastro del movimiento. Es desenfoque de movimiento
     * exacto, no un efecto de difuminado encima.
     *
     * Los bordes del bloque se suavizan con lo que mide un pixel en la
     * textura, sacado de la propia matriz: un bloque girado no sale dentado.
     */
    const val CAPA = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform vec2 uLienzo;
uniform sampler2D sTex;
uniform vec2 uTexel;
uniform mat3 uInv[8];
uniform float uOpac[8];
uniform float uBorde[8];
uniform float uN;
uniform vec4 uRecorte;
uniform float uConMascara;
uniform vec2 uMascDir;
uniform float uMascSuave;
uniform float uRadio;
uniform float uNitidez;
uniform float uBrillo;
uniform float uContraste;
uniform float uSaturacion;
uniform float uExposicion;
uniform float uCalidez;
uniform float uTinte;
uniform float uSombras;
uniform float uLuces;
uniform float uVineta;
uniform float uNeutro;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec4 tomar(vec2 uv) {
    vec2 lo = uRecorte.xy + uTexel * 0.5;
    vec2 hi = uRecorte.zw - uTexel * 0.5;
    return texture2D(sTex, clamp(uv, lo, hi));
}

vec4 muestra(vec2 uv, vec2 pixel) {
    if (uRadio > 0.0) {
        // Disco de muestras en espiral: sin tablas, que GLSL ES 1 no admite
        vec4 s = vec4(0.0);
        for (int j = 0; j < 12; j++) {
            float fj = float(j);
            float r = sqrt((fj + 0.5) / 12.0);
            float a = fj * 2.39996;
            s += tomar(uv + vec2(cos(a), sin(a)) * r * uRadio * pixel);
        }
        return s / 12.0;
    }
    vec4 c = tomar(uv);
    if (uNitidez > 0.0) {
        vec4 a = tomar(uv + vec2(uTexel.x, 0.0));
        vec4 b = tomar(uv - vec2(uTexel.x, 0.0));
        vec4 d = tomar(uv + vec2(0.0, uTexel.y));
        vec4 e = tomar(uv - vec2(0.0, uTexel.y));
        vec4 afilado = c + (c - (a + b + d + e) * 0.25) * uNitidez * 1.6;
        // Limitado al rango de los vecinos: afila sin el cerco blanco
        vec4 suelo = min(min(min(a, b), min(d, e)), c);
        vec4 techo = max(max(max(a, b), max(d, e)), c);
        c = clamp(afilado, suelo, techo);
    }
    return c;
}

vec3 ajustar(vec3 c, vec2 uv) {
    c *= exp2(uExposicion);
    c += uBrillo * 0.25;
    float l = dot(c, LUMA);
    c += uSombras * 0.25 * (1.0 - smoothstep(0.0, 0.55, l));
    c += uLuces * 0.25 * smoothstep(0.45, 1.0, l);
    c = (c - 0.5) * (1.0 + uContraste) + 0.5;
    c.r += uCalidez * 0.08;
    c.b -= uCalidez * 0.08;
    c.g += uTinte * 0.06;
    l = dot(c, LUMA);
    c = mix(vec3(l), c, 1.0 + uSaturacion);
    float d = distance(uv, vec2(0.5));
    c *= 1.0 - uVineta * smoothstep(0.25, 0.8, d);
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec2 p = vPos * uLienzo;
    vec4 suma = vec4(0.0);
    vec2 uvPrimero = vec2(0.5);
    for (int i = 0; i < 8; i++) {
        if (float(i) >= uN) break;
        mat3 m = uInv[i];
        vec2 uv = (m * vec3(p, 1.0)).xy;
        if (i == 0) uvPrimero = uv;
        float pu = max(length(vec2(m[0][0], m[1][0])), 1e-6);
        float pv = max(length(vec2(m[0][1], m[1][1])), 1e-6);
        float cob = clamp((uv.x - uRecorte.x) / pu + 0.5, 0.0, 1.0)
                  * clamp((uRecorte.z - uv.x) / pu + 0.5, 0.0, 1.0)
                  * clamp((uv.y - uRecorte.y) / pv + 0.5, 0.0, 1.0)
                  * clamp((uRecorte.w - uv.y) / pv + 0.5, 0.0, 1.0);
        if (uConMascara > 0.5) {
            float d = dot(p - 0.5 * uLienzo, uMascDir);
            cob *= clamp((uBorde[i] - d) / uMascSuave + 0.5, 0.0, 1.0);
        }
        float a = cob * uOpac[i];
        if (a > 0.0) suma += muestra(uv, vec2(pu, pv)) * a;
    }
    suma /= uN;
    if (uNeutro < 0.5 && suma.a > 0.0) {
        vec3 recto = suma.rgb / suma.a;
        suma.rgb = ajustar(recto, uvPrimero) * suma.a;
    }
    gl_FragColor = suma;
}
"""

    /** Un color liso, premultiplicado. */
    const val SOLIDO = """
precision mediump float;
varying vec2 vPos;
uniform vec4 uColor;
void main() { gl_FragColor = vec4(uColor.rgb * uColor.a, uColor.a); }
"""

    /**
     * La linea de luz del barrido.
     *
     * Nucleo blanco con borde suave y un halo que cae poco a poco. Se calcula
     * en cada muestra del obturador y se promedia, asi que al moverse deja la
     * estela que tendria una luz de verdad delante de una camara a 30 fps.
     */
    const val LINEA = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform vec2 uLienzo;
uniform vec2 uDir;
uniform float uBorde[8];
uniform float uN;
uniform float uGrosor;
uniform vec3 uColor;
void main() {
    vec2 p = vPos * uLienzo;
    float d0 = dot(p - 0.5 * uLienzo, uDir);
    float v = 0.0;
    for (int i = 0; i < 8; i++) {
        if (float(i) >= uN) break;
        float d = abs(d0 - uBorde[i]);
        float nucleo = 1.0 - smoothstep(uGrosor * 0.5 - 0.75, uGrosor * 0.5 + 0.75, d);
        float halo = exp(-d / (uGrosor * 2.2)) * 0.55;
        v += max(nucleo, halo);
    }
    v = clamp(v / uN, 0.0, 1.0);
    gl_FragColor = vec4(uColor * v, v);
}
"""
}
