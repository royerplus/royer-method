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
     * tamano al que se va a usar: primera de las dos pasadas de [Reduccion].
     *
     * Lanczos de radio 2 abierto segun lo que se reduce: al bajar a la mitad
     * cada pixel del resultado mira 9 del original en esa direccion, todos
     * cuentan y las lineas finas del anime no salen con dientes ni titilan.
     * Un filtro fijo de pocas muestras se saltaria pixeles al reducir mucho.
     *
     * Lo que sale se limita al rango de los pixeles que caen justo debajo
     * (el "pie"): los lobulos negativos de Lanczos dan filo pero tambien un
     * cerco claro alrededor de las lineas oscuras, y asi se queda el filo
     * sin el cerco.
     *
     * Las muestras no salen del borde de la imagen: el decodificador guarda
     * relleno fuera de lo visible y se colaria como una raya de color.
     */
    const val REDUCIR_EXTERNA = """
#extension GL_OES_EGL_image_external : require
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform samplerExternalOES sTex;
uniform mat3 uMapa;
uniform vec2 uDir;
uniform float uOrigen;
uniform float uEscala;
float lanczos2(float x) {
    x = abs(x);
    if (x < 0.0001) return 1.0;
    if (x >= 2.0) return 0.0;
    float p = 3.14159265 * x;
    return 2.0 * sin(p) * sin(0.5 * p) / (p * p);
}
void main() {
    float c = dot(vPos, uDir) * uOrigen;
    float radio = 2.0 * uEscala;
    float pie = 0.5 * uEscala + 0.5;
    // Hasta 20 muestras; si no llegan, se separan (cada una ya promedia dos)
    float paso = max(1.0, 2.0 * radio / 19.0);
    float p0 = paso > 1.0 ? c - radio + 0.5 * paso : floor(c - radio) + 0.5;
    vec2 fijo = vPos * (vec2(1.0) - uDir);
    vec3 suma = vec3(0.0);
    float pesos = 0.0;
    vec3 lo = vec3(1.0);
    vec3 hi = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float p = p0 + float(i) * paso;
        if (p > c + radio) break;
        float d = p - c;
        vec2 q = fijo + uDir * (clamp(p, 0.5, uOrigen - 0.5) / uOrigen);
        vec3 t = texture2D(sTex, (uMapa * vec3(q, 1.0)).xy).rgb;
        float w = lanczos2(d / uEscala);
        suma += t * w;
        pesos += w;
        if (abs(d) <= pie) { lo = min(lo, t); hi = max(hi, t); }
    }
    gl_FragColor = vec4(clamp(suma / max(pesos, 0.0001), lo, hi), 1.0);
}
"""

    /** La segunda pasada de [Reduccion], desde la lamina de la primera. */
    const val REDUCIR_PLANA = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform sampler2D sTex;
uniform mat3 uMapa;
uniform vec2 uDir;
uniform float uOrigen;
uniform float uEscala;
float lanczos2(float x) {
    x = abs(x);
    if (x < 0.0001) return 1.0;
    if (x >= 2.0) return 0.0;
    float p = 3.14159265 * x;
    return 2.0 * sin(p) * sin(0.5 * p) / (p * p);
}
void main() {
    float c = dot(vPos, uDir) * uOrigen;
    float radio = 2.0 * uEscala;
    float pie = 0.5 * uEscala + 0.5;
    float paso = max(1.0, 2.0 * radio / 19.0);
    float p0 = paso > 1.0 ? c - radio + 0.5 * paso : floor(c - radio) + 0.5;
    vec2 fijo = vPos * (vec2(1.0) - uDir);
    vec3 suma = vec3(0.0);
    float pesos = 0.0;
    vec3 lo = vec3(1.0);
    vec3 hi = vec3(0.0);
    for (int i = 0; i < 20; i++) {
        float p = p0 + float(i) * paso;
        if (p > c + radio) break;
        float d = p - c;
        vec2 q = fijo + uDir * (clamp(p, 0.5, uOrigen - 0.5) / uOrigen);
        vec3 t = texture2D(sTex, (uMapa * vec3(q, 1.0)).xy).rgb;
        float w = lanczos2(d / uEscala);
        suma += t * w;
        pesos += w;
        if (abs(d) <= pie) { lo = min(lo, t); hi = max(hi, t); }
    }
    gl_FragColor = vec4(clamp(suma / max(pesos, 0.0001), lo, hi), 1.0);
}
"""

    /**
     * Cuatro muestras bilineales por pixel, a un cuarto de pixel de la copia
     * a cada lado: al reducir a la mitad cubren un cuadrado de cuatro por
     * cuatro. Basta para la cadena de mitades del fondo desenfocado, que de
     * todas formas se va a emborronar.
     */
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

    /** Sin reducir ni filtrar de nuevo: la copia no debe perder nitidez en Mali. */
    const val COPIA_EXACTA = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying vec2 vPos;
uniform sampler2D sTex;
void main() { gl_FragColor = texture2D(sTex, vPos); }
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
uniform float uMascRadial;
uniform float uMascInvertida;
uniform vec2 uMascCentro;
uniform float uMascGrosor;
uniform float uRadio;
uniform vec2 uRadioDir;
uniform float uEspejoBordes;
uniform float uConRegionFuente;
uniform vec4 uRegionFuente;
uniform vec4 uBordesCompartidos;
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
uniform float uLuz;
uniform float uRgb;
uniform float uNegativo;
uniform float uClaridad;
uniform float uVivacidad;
uniform float uDesvanecer;
uniform float uGrano;
// Numero de fotograma de la linea de tiempo (0..63): el grano cambia en cada uno
uniform float uSemilla;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

vec4 tomar(vec2 uv) {
    vec2 lo = uRecorte.xy + uTexel * 0.5;
    vec2 hi = uRecorte.zw - uTexel * 0.5;
    if (uEspejoBordes > 0.0) {
        vec2 q = (uv - lo) / max(hi - lo, uTexel);
        q = vec2(1.0) - abs(mod(q, 2.0) - vec2(1.0));
        uv = lo + q * (hi - lo);
    }
    return texture2D(sTex, clamp(uv, lo, hi));
}

vec4 tomarRgb(vec2 uv, vec2 desp) {
    vec4 c = tomar(uv);
    if (uRgb > 0.0) {
        vec4 r = tomar(uv + desp);
        vec4 b = tomar(uv - desp);
        c = vec4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));
    }
    return c;
}

// ---- Nitidez: AMD FidelityFX CAS 1.20190610 (MIT), camino sin escalado con
// CAS_BETTER_DIAGONALS y pesos por canal (CAS_SLOW). Trabaja sobre el color
// premultiplicado tal como llega de la textura.
vec4 afilarCas(vec2 uv, vec4 centro, float nitidez, vec2 paso) {
    vec2 dx = vec2(paso.x, 0.0);
    vec2 dy = vec2(0.0, paso.y);
    vec3 a = tomar(uv - dx - dy).rgb;
    vec3 b = tomar(uv - dy).rgb;
    vec3 c = tomar(uv + dx - dy).rgb;
    vec3 d = tomar(uv - dx).rgb;
    vec3 e = centro.rgb;
    vec3 f = tomar(uv + dx).rgb;
    vec3 g = tomar(uv - dx + dy).rgb;
    vec3 h = tomar(uv + dy).rgb;
    vec3 i = tomar(uv + dx + dy).rgb;
    // Minimo y maximo suaves: cruz mas caja 3x3, por eso llegan hasta 2
    vec3 mnCruz = min(min(min(d, e), min(f, b)), h);
    vec3 mxCruz = max(max(max(d, e), max(f, b)), h);
    vec3 mn = mnCruz + min(mnCruz, min(min(a, c), min(g, i)));
    vec3 mx = mxCruz + max(mxCruz, max(max(a, c), max(g, i)));
    // Cuanto cabe afilar sin salirse de 0..1, dividido por el maximo
    vec3 amp = sqrt(clamp(min(mn, 2.0 - mx) / max(mx, vec3(0.0001)), 0.0, 1.0));
    // Lobulo negativo en cruz: de -1/8 (poco cerco) a -1/5 (el maximo de CAS)
    vec3 w = amp * (-1.0 / mix(8.0, 5.0, clamp(nitidez, 0.0, 1.0)));
    vec3 r = (b * w + d * w + f * w + h * w + e) / (1.0 + 4.0 * w);
    // CAS con su mando a 0 ya afila: se funde con el original para que 0 sea
    // nada. Hasta 0.5 llega a CAS entero; de ahi a 1 dobla lo que mueve, que
    // CAS solo se queda corto para un "filo" que se note en el telefono.
    // Lo que se dobla por encima de CAS no sale del minimo y el maximo de la
    // caja 3x3: sin ese tope habia pixeles a negro o blanco puro y tonos
    // cambiados (marron a verde en el pelo). Hasta 0.5 queda CAS tal cual.
    vec3 cas = mix(e, r, min(nitidez * 2.0, 1.0));
    vec3 mn3 = min(mnCruz, min(min(a, c), min(g, i)));
    vec3 mx3 = max(mxCruz, max(max(a, c), max(g, i)));
    r = clamp(mix(e, r, nitidez * 2.0), min(cas, mn3), max(cas, mx3));
    return vec4(clamp(r, 0.0, centro.a), centro.a);
}

// ---- Claridad: contraste local de radio grande en una sola pasada.
// Formula de Poly Haven imgvwr (MIT): base + cantidad * (base - desenfoque) *
// mascara de medios tonos. El desenfoque aqui es un anillo disperso de 16
// tomas (8 a R y 8 a R/2 giradas 22.5 grados) pesadas por parecido con el
// centro: una linea negra casi no entra en la media, asi el cerco y las
// copias fantasma que da un anillo de pocas tomas quedan muy rebajados.
const mat2 GIRO45 = mat2(0.70710678, 0.70710678, -0.70710678, 0.70710678);
const mat2 GIRO22 = mat2(0.92387953, 0.38268343, -0.38268343, 0.92387953);

float lumaEntorno(vec2 uv, float lCentro, float radio) {
    vec2 eje = vec2(radio, 0.0);
    float suma = 0.0;
    float pesos = 0.0;
    for (int j = 0; j < 8; j++) {
        vec4 t1 = tomar(uv + eje * uTexel);
        vec4 t2 = tomar(uv + (GIRO22 * eje) * (0.5 * uTexel));
        float l1 = dot(t1.rgb, LUMA) / max(t1.a, 0.001);
        float l2 = dot(t2.rgb, LUMA) / max(t2.a, 0.001);
        // Peso 1/2 a 0.25 de diferencia de luma, casi 0 a partir de 0.6
        float d1 = (l1 - lCentro) * 4.0;
        float d2 = (l2 - lCentro) * 4.0;
        float w1 = t1.a * exp2(-d1 * d1);
        float w2 = t2.a * exp2(-d2 * d2);
        suma += l1 * w1 + l2 * w2;
        pesos += w1 + w2;
        eje = GIRO45 * eje;
    }
    return pesos > 0.0001 ? suma / pesos : lCentro;
}

vec3 claridad(vec3 c, float lCentro, float lEntorno, float cantidad) {
    float detalle = lCentro - lEntorno;
    // Freno suave: los saltos grandes crecen poco, los pequenos casi enteros
    detalle = detalle / (1.0 + 2.0 * abs(detalle));
    float l = dot(c, LUMA);
    float medios = smoothstep(0.05, 0.25, l) * (1.0 - smoothstep(0.80, 0.98, l));
    return c + vec3(cantidad * detalle * medios);
}

// ---- Exposicion: GPUImageExposureFilter (rgb * 2^ev, BSD-3) hecho en luz
// lineal aproximada, con un hombro suave: por encima de la rodilla las luces
// se acercan a 1 sin cortarse.
vec3 exposicion(vec3 c, float ev) {
    vec3 lin = pow(max(c, vec3(0.000001)), vec3(2.2)) * exp2(ev);
    vec3 hombro = 0.8 + 0.2 * (1.0 - exp((0.8 - lin) / 0.2));
    lin = mix(lin, hombro, step(0.8, lin));
    return pow(lin, vec3(1.0 / 2.2));
}

// ---- Contraste: sigmoidal-contrast de ImageMagick (MagickCore/enhance.c).
// Positivo = curva en S escalada para que 0 y 1 queden fijos; negativo = su
// inversa. Sin tanh ni atanh, que GLSL ES 1.00 no tiene.
vec3 contraste(vec3 c, float k) {
    float a = abs(k) * 8.0;
    if (a < 0.01) return c;
    float s0 = 1.0 / (1.0 + exp(0.5 * a));
    float s1 = 1.0 / (1.0 + exp(-0.5 * a));
    if (k > 0.0) {
        vec3 s = 1.0 / (1.0 + exp(a * (0.5 - c)));
        return (s - s0) / (s1 - s0);
    }
    vec3 y = clamp((s1 - s0) * c + s0, 0.0001, 0.9999);
    return 0.5 - log(1.0 / y - 1.0) / a;
}

// ---- Sombras y luces: GPUImageHighlightShadowFilter (BSD-3). GPUImage solo
// levanta sombras y solo baja luces; el lado contrario usa la ganancia
// inversa, asi el negro y el blanco no se mueven nunca.
// Los suelos de 0.0001 y no menos: en mediump un 1e-6 se queda en 0 y el
// negro con sombras hacia abajo daria 0 * infinito.
float curvaSombras(float l, float s) {
    float x = max(l, 0.0001);
    return clamp(pow(x, 1.0 / (s + 1.0)) - 0.76 * pow(x, 2.0 / (s + 1.0)) - l, 0.0, 1.0);
}

float curvaLuces(float l, float h) {
    float u = max(1.0 - l, 0.0001);
    return clamp(1.0 - (pow(u, 1.0 / (2.0 - h)) - 0.8 * pow(u, 2.0 / (2.0 - h))) - l, -1.0, 0.0);
}

vec3 sombrasLuces(vec3 c, float sombras, float luces) {
    float l = clamp(dot(c, vec3(0.33333333)), 0.0, 1.0);
    if (sombras != 0.0) {
        float g = (l + curvaSombras(l, abs(sombras) * 1.4)) / max(l, 0.0001);
        c *= sombras > 0.0 ? g : 1.0 / max(g, 0.0001);
        l = clamp(dot(c, vec3(0.33333333)), 0.0, 1.0);
    }
    if (luces != 0.0) {
        float bajar = -curvaLuces(l, 1.0 - abs(luces) * 1.4);
        float u = 1.0 - l;
        if (luces < 0.0) c *= (l - bajar) / max(l, 0.0001);
        else c = 1.0 - (1.0 - c) * (u / max(u + bajar, 0.0001));
    }
    return c;
}

// ---- Desvanecer (mate): levanta los negros. GPUImageLevelsFilter haria
// mix(minSalida, 1.0, c); aqui el levantamiento se apaga hacia las luces para
// que el blanco siga blanco, y se quita un poco de color como en el mate.
vec3 desvanecer(vec3 c, float f) {
    vec3 u = 1.0 - c;
    c += f * 0.25 * u * u;
    float l = dot(c, LUMA);
    return mix(vec3(l), c, 1.0 - 0.25 * f);
}

// ---- Saturacion: GPUImageSaturationFilter (BSD-3), con tope de gama: al
// subir, ningun canal sale de 0..1, asi el tono no cambia al recortar.
vec3 saturacion(vec3 c, float s) {
    float l = dot(c, LUMA);
    float f = 1.0 + s;
    if (f > 1.0) {
        float mx = max(c.r, max(c.g, c.b));
        float mn = min(c.r, min(c.g, c.b));
        float topeAlto = mx > l + 0.0001 ? (1.0 - l) / (mx - l) : 10000.0;
        float topeBajo = mn < l - 0.0001 ? l / (l - mn) : 10000.0;
        f = min(f, max(min(topeAlto, topeBajo), 1.0));
    }
    return mix(vec3(l), c, f);
}

// ---- Vivacidad que respeta lo ya saturado: misma mezcla que la saturacion,
// pesada por (1 - saturacion HSV)^2. Los colores apagados suben mucho y los
// intensos del anime casi nada, asi no se queman.
vec3 vivacidad(vec3 c, float v) {
    float mx = max(c.r, max(c.g, c.b));
    float mn = min(c.r, min(c.g, c.b));
    float sat = (mx - mn) / max(mx, 0.0001);
    // Bajando, k no pasa de -1: con 1 + k < 0 el color se daria la vuelta
    float k = v * (v > 0.0 ? 1.5 : 1.0) * (1.0 - sat) * (1.0 - sat);
    float l = dot(c, LUMA);
    return mix(vec3(l), c, 1.0 + k);
}

// ---- Grano. Hash sin seno de David Hoskins (MIT, shadertoy.com/view/4djSRW)
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// FilmGrain de SweetFX (CeeJay.dk, MIT): ruido gaussiano por Box-Muller que
// multiplica el color. Relacion senal/ruido 2: los claros reciben menos y el
// negro, al multiplicar, ninguno. [semilla] sale del numero de fotograma.
vec3 grano(vec3 c, vec2 pixel, float semilla, float cantidad) {
#ifndef GL_FRAGMENT_PRECISION_HIGH
    // Sin highp (Mali-400 y parecidos) el hash da cuatro valores y el grano
    // seria un aclarado plano: mejor nada
    return c;
#else
    vec2 q = floor(pixel) + vec2(semilla * 37.0, semilla * 61.0);
    float u1 = max(hash12(q), 0.0001);
    float u2 = hash12(q + vec2(19.19, 47.47));
    float gauss = sqrt(-2.0 * log(u1)) * cos(6.2831853 * u2);
    float inv = 1.0 - (c.r + c.g + c.b) / 3.0;
    return c * (1.0 + gauss * cantidad * 0.35 * inv * inv);
#endif
}

vec4 muestra(vec2 uv, vec2 pixel, vec2 desp, vec2 ejeX, vec2 ejeY, float fase) {
    if (uRadio <= 0.0 && (uRadioDir.x != 0.0 || uRadioDir.y != 0.0)) {
        // Solo en una direccion: muestras repartidas por igual a lo largo de
        // la linea, como el rastro de un movimiento. El disco de abajo deja
        // copias separadas cuando el rastro es largo. Una muestra cada ~3 px
        // del lienzo (entre 16 y 64): con menos, un rastro de 500 px deja
        // rayas finas. Cada muestra del obturador y cada pixel corren las
        // suyas una fraccion del paso ([fase] y un ruido fijo), asi que lo
        // que queda es grano y no lineas. Va en cualquier angulo: una x o una
        // y negativas inclinan la linea hacia el otro lado.
        float n = clamp(ceil(2.0 * (abs(uRadioDir.x) + abs(uRadioDir.y)) / 3.0), 16.0, 64.0);
        float ruido = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) - 0.5;
        vec4 s = vec4(0.0);
        for (int j = 0; j < 64; j++) {
            if (float(j) >= n) break;
            float t = (float(j) + 0.5 + fase + ruido / uN) / n * 2.0 - 1.0;
            s += tomarRgb(uv + t * (ejeX + ejeY), desp);
        }
        return s / n;
    }
    if (uRadio > 0.0 || uRadioDir.x != 0.0 || uRadioDir.y != 0.0) {
        // Disco de muestras en espiral: sin tablas, que GLSL ES 1 no admite
        vec4 s = vec4(0.0);
        for (int j = 0; j < 12; j++) {
            float fj = float(j);
            float r = sqrt((fj + 0.5) / 12.0);
            float a = fj * 2.39996;
            vec2 delta = vec2(cos(a), sin(a)) * r * uRadio * pixel
                       + cos(a) * r * ejeX + sin(a) * r * ejeY;
            s += tomarRgb(uv + delta, desp);
        }
        return s / 12.0;
    }
    vec4 c = tomar(uv);
    // Un texel de la fuente, o un pixel del lienzo si la foto se ve mas chica
    // que su textura: asi el filo se nota igual en cualquier tamano
    if (uNitidez > 0.0) c = afilarCas(uv, c, uNitidez, max(uTexel, pixel));
    if (uRgb > 0.0) {
        // Rojo de un lado y azul del otro. El alfa, el mayor de los tres:
        // en un texto el borde rojo y el azul se ven fuera de la letra.
        vec4 r = tomar(uv + desp);
        vec4 b = tomar(uv - desp);
        c = vec4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));
    }
    return c;
}

// Orden: lo que mira la imagen tal cual (claridad), luz y color de la toma
// (exposicion, calidez, brillo), curvas de tono, color y al final el grano,
// para que ningun paso lo escale ni lo tina. [uv] es la de la textura (vineta) y [p]
// el pixel del lienzo (grano).
vec3 ajustar(vec3 c, vec2 uv, vec2 p, float lEntorno) {
    c = clamp(c, 0.0, 1.0);
    if (uClaridad != 0.0)
        c = clamp(claridad(c, dot(c, LUMA), lEntorno, uClaridad > 0.0 ? uClaridad * 1.5 : uClaridad), 0.0, 1.0);
    if (uExposicion != 0.0) c = exposicion(c, uExposicion * 1.5);
    // Calidez, tinte y brillo, los de siempre: sumar es lo que hace
    // GPUImageBrightnessFilter, y oscurece tambien el blanco
    c.r += uCalidez * 0.08;
    c.b -= uCalidez * 0.08;
    c.g += uTinte * 0.06;
    c = clamp(c + uBrillo * 0.25, 0.0, 1.0);
    if (uContraste != 0.0) c = contraste(c, uContraste);
    if (uSombras != 0.0 || uLuces != 0.0) c = clamp(sombrasLuces(c, uSombras, uLuces), 0.0, 1.0);
    if (uDesvanecer > 0.0) c = desvanecer(c, uDesvanecer);
    if (uSaturacion != 0.0) c = saturacion(c, uSaturacion);
    if (uVivacidad != 0.0) c = vivacidad(c, uVivacidad);
    float d = distance(uv, vec2(0.5));
    c *= 1.0 - uVineta * smoothstep(0.25, 0.8, d);
    c = clamp(c, 0.0, 1.0);
    // El grano va en celdas de 1/540 del lado corto del lienzo: mide lo mismo
    // en la vista previa que en el video exportado. Con menos de 540 px cada
    // pixel muestra una celda entera, mientras que el video exportado, visto
    // a ese tamano, junta varias y se ve mas suave: se baja en proporcion.
    if (uGrano > 0.0) {
        float corto = min(uLienzo.x, uLienzo.y);
        c = grano(c, p * (540.0 / corto), uSemilla, uGrano * min(1.0, corto / 540.0));
    }
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
        cob = mix(cob, 1.0, clamp(uEspejoBordes, 0.0, 1.0));
        // Las partes conservan la geometria de la foto completa. Este corte
        // viaja con la textura; no vuelve a encajar cada mitad en el lienzo.
        // Cada corte expuesto ocupa un pixel del lienzo, tambien al girarlo.
        // Solo las juntas de igual matriz en esta muestra son complementarias:
        // suavizar ambas y mezclarlas por OVER dejaria una raya con alfa0.75.
        if (uConRegionFuente > 0.5) {
            vec4 suave = clamp(vec4(uv.x - uRegionFuente.x, uv.y - uRegionFuente.y,
                                    uRegionFuente.z - uv.x, uRegionFuente.w - uv.y)
                               / vec4(pu, pv, pu, pv) + 0.5, 0.0, 1.0);
            vec4 duro = vec4(step(uRegionFuente.x, uv.x), step(uRegionFuente.y, uv.y),
                             1.0 - step(uRegionFuente.z, uv.x), 1.0 - step(uRegionFuente.w, uv.y));
            vec4 lados = mix(suave, duro, uBordesCompartidos);
            cob *= (uRegionFuente.x <= 0.0 ? 1.0 : lados.x)
                 * (uRegionFuente.y <= 0.0 ? 1.0 : lados.y)
                 * (uRegionFuente.z >= 1.0 ? 1.0 : lados.z)
                 * (uRegionFuente.w >= 1.0 ? 1.0 : lados.w);
        }
        if (uConMascara > 0.5) {
            vec2 q = p - (0.5 * uLienzo + uMascCentro);
            float d = uMascRadial > 0.5 ? length(q) : dot(q, uMascDir);
            float mascara = clamp((uBorde[i] - d) / uMascSuave + 0.5, 0.0, 1.0);
            // Anillo: tambien se corta por dentro, [uMascGrosor] antes del borde.
            // Un borde de SIN_BORDE es una muestra sin mascara y se ve entera.
            if (uMascGrosor > 0.0 && abs(uBorde[i]) < 15000.0)
                mascara *= clamp((d - (uBorde[i] - uMascGrosor)) / uMascSuave + 0.5, 0.0, 1.0);
            cob *= uMascInvertida > 0.5 ? 1.0 - mascara : mascara;
        }
        float a = cob * uOpac[i];
        // Un pixel del lienzo hacia la derecha, en la textura: la separacion del RGB
        vec2 desp = uRgb * vec2(m[0][0], m[0][1]);
        vec2 ejeX = uRadioDir.x * vec2(m[0][0], m[0][1]);
        vec2 ejeY = uRadioDir.y * vec2(m[1][0], m[1][1]);
        float fase = (float(i) + 0.5) / uN - 0.5;
        if (a > 0.0) suma += muestra(uv, vec2(pu, pv), desp, ejeX, ejeY, fase) * a;
    }
    suma /= uN;
    if (suma.a > 0.0 && (uNeutro < 0.5 || uLuz != 0.0 || uNegativo > 0.0)) {
        vec3 recto = suma.rgb / suma.a;
        if (uNeutro < 0.5) {
            // Claridad: luz del entorno, una vez por pixel desde la muestra 0.
            // Radio de 2% del lado corto de la textura, igual en vista y video
            float lEntorno = 0.0;
            if (uClaridad != 0.0)
                lEntorno = lumaEntorno(uvPrimero, dot(recto, LUMA), 0.02 / max(uTexel.x, uTexel.y));
            recto = ajustar(recto, uvPrimero, p, lEntorno);
        }
        recto = mix(recto, vec3(1.0) - recto, uNegativo);
        recto = uLuz > 0.0 ? mix(recto, vec3(1.0), uLuz) : recto * (1.0 + uLuz);
        suma.rgb = recto * suma.a;
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
