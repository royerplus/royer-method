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
uniform float uRadio;
uniform vec2 uRadioDir;
uniform float uEspejoBordes;
uniform float uConRegionFuente;
uniform vec4 uRegionFuente;
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

vec4 muestra(vec2 uv, vec2 pixel, vec2 desp, vec2 ejeX, vec2 ejeY) {
    if (uRadio > 0.0 || uRadioDir.x > 0.0 || uRadioDir.y > 0.0) {
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
    if (uRgb > 0.0) {
        // Rojo de un lado y azul del otro. El alfa, el mayor de los tres:
        // en un texto el borde rojo y el azul se ven fuera de la letra.
        vec4 r = tomar(uv + desp);
        vec4 b = tomar(uv - desp);
        c = vec4(r.r, c.g, b.b, max(c.a, max(r.a, b.a)));
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
        cob = mix(cob, 1.0, clamp(uEspejoBordes, 0.0, 1.0));
        // Las partes conservan la geometria de la foto completa. Este corte
        // viaja con la textura; no vuelve a encajar cada mitad en el lienzo.
        // Bordes compartidos complementarios: al juntarse no aparece una raya.
        if (uConRegionFuente > 0.5) {
            cob *= (uRegionFuente.x <= 0.0 ? 1.0 : step(uRegionFuente.x, uv.x))
                 * (uRegionFuente.y <= 0.0 ? 1.0 : step(uRegionFuente.y, uv.y))
                 * (uRegionFuente.z >= 1.0 ? 1.0 : 1.0 - step(uRegionFuente.z, uv.x))
                 * (uRegionFuente.w >= 1.0 ? 1.0 : 1.0 - step(uRegionFuente.w, uv.y));
        }
        if (uConMascara > 0.5) {
            float d = uMascRadial > 0.5 ? length(p - 0.5 * uLienzo) : dot(p - 0.5 * uLienzo, uMascDir);
            float mascara = clamp((uBorde[i] - d) / uMascSuave + 0.5, 0.0, 1.0);
            cob *= uMascInvertida > 0.5 ? 1.0 - mascara : mascara;
        }
        float a = cob * uOpac[i];
        // Un pixel del lienzo hacia la derecha, en la textura: la separacion del RGB
        vec2 desp = uRgb * vec2(m[0][0], m[0][1]);
        vec2 ejeX = uRadioDir.x * vec2(m[0][0], m[0][1]);
        vec2 ejeY = uRadioDir.y * vec2(m[1][0], m[1][1]);
        if (a > 0.0) suma += muestra(uv, vec2(pu, pv), desp, ejeX, ejeY) * a;
    }
    suma /= uN;
    if (suma.a > 0.0 && (uNeutro < 0.5 || uLuz != 0.0 || uNegativo > 0.0)) {
        vec3 recto = suma.rgb / suma.a;
        if (uNeutro < 0.5) recto = ajustar(recto, uvPrimero);
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
