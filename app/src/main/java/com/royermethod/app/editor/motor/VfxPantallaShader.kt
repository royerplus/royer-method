package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.VfxPantalla

/** Bloques para CAPA. FUNCIONES se coloca despues de tomar() y antes de main(). */
object VfxPantallaShader {
    const val UNIFORMES = """
uniform vec4 uFxMitades; // fuerza, lado, suavidad, halo
uniform vec4 uFxMitadesGeo; // direccion xy, centro xy
uniform vec3 uFxMitadesColor;
uniform vec4 uFxBloom; // fuerza, radio, umbral, suavidad del umbral
uniform vec3 uFxBloomColor;
uniform vec4 uFxTrama; // fuerza, paso, angulo, lineas
uniform vec3 uFxTramaColor;
uniform vec4 uFxArcoiris; // fuerza, radio, umbral, fase
"""

    const val FUNCIONES = """
// Siempre toma la fuente ya premultiplicada y con croma, no la textura cruda.
float fxBorde(vec2 uv, vec2 radio) {
    vec3 dx = tomar(uv + vec2(radio.x,0.0)).rgb - tomar(uv - vec2(radio.x,0.0)).rgb;
    vec3 dy = tomar(uv + vec2(0.0,radio.y)).rgb - tomar(uv - vec2(0.0,radio.y)).rgb;
    return clamp((length(dx) + length(dy)) * 0.5, 0.0, 1.0);
}
vec3 fxLuz(vec2 uv) {
    vec4 c = tomar(uv);
    float l = dot(c.rgb / max(c.a,0.00001), vec3(0.2126,0.7152,0.0722));
    return c.rgb * smoothstep(uFxBloom.z,uFxBloom.z + max(uFxBloom.w,0.001),l);
}
vec4 aplicarVfxPantalla(vec4 c, vec2 uv) {
    if (uFxBloom.x <= 0.0 && uFxMitades.x <= 0.0 && uFxArcoiris.x <= 0.0 && uFxTrama.x <= 0.0) return c;
    if (c.a < 0.00001) return c;
    vec3 rgb = c.rgb / c.a;
    vec2 tam = max(uRecorte.zw-uRecorte.xy,vec2(0.00001));
    vec2 aspecto = max(uFormaTam,vec2(0.00001));
    vec2 local = (uv-uRecorte.xy)/tam;
    vec2 q = (local-0.5)*aspecto;
    // Resplandor de luces: disco disperso de dos radios y centro, con umbral.
    // Son 13 muestras solo cuando esta encendido; el alfa nunca se rellena.
    if (uFxBloom.x > 0.0) {
        vec2 radio = uFxBloom.y * tam / aspecto;
        vec3 luz = fxLuz(uv) * 0.2;
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            float angulo = fi * 2.39996323;
            float r = sqrt((fi+0.5)/12.0);
            luz += fxLuz(uv+vec2(cos(angulo),sin(angulo))*radio*r) * (0.8/12.0);
        }
        vec3 emision = luz * uFxBloomColor * uFxBloom.x * 2.5;
        rgb = 1.0 - (1.0-rgb) * exp(-emision);
    }
    // Iluminacion alterna. Halo=0: luz amplia. Halo=1: luz de los contornos.
    if (uFxMitades.x > 0.0) {
        float d = dot(q-uFxMitadesGeo.zw*aspecto,uFxMitadesGeo.xy);
        float aa = max(max(uTexel.x*aspecto.x/tam.x,uTexel.y*aspecto.y/tam.y),0.0001);
        float suave = max(uFxMitades.z,aa);
        float region = smoothstep(-suave,suave,d*uFxMitades.y);
        float concentracion = 1.0;
        if (uFxMitades.w > 0.0) {
            float borde = fxBorde(uv,max(uTexel,0.003*tam/aspecto));
            float junta = exp(-abs(d)/max(0.006+uFxMitades.w*0.025,aa));
            concentracion = mix(1.0,clamp(borde*4.0+junta,0.0,1.0),uFxMitades.w);
        }
        float luz = region * uFxMitades.x * concentracion;
        vec3 color = mix(uFxMitadesColor,vec3(1.0),clamp(luz-0.8,0.0,0.4)*uFxMitades.w);
        rgb = 1.0 - (1.0-rgb) * exp(-color*luz*1.7);
    }
    if (uFxArcoiris.x > 0.0) {
        float borde = fxBorde(uv,max(uTexel,uFxArcoiris.y*tam/aspecto));
        float seleccion = smoothstep(uFxArcoiris.z,uFxArcoiris.z+0.12,borde);
        float fase = local.x+local.y*0.7+uFxArcoiris.w;
        vec3 color = 0.5+0.5*cos(vec3(0.0,2.0943951,4.1887902)+fase*6.2831853);
        rgb = 1.0-(1.0-rgb)*exp(-color*seleccion*uFxArcoiris.x*2.5);
    }
    if (uFxTrama.x > 0.0) {
        float co = cos(uFxTrama.z), si = sin(uFxTrama.z);
        vec2 rot = vec2(q.x*co+q.y*si,-q.x*si+q.y*co)/max(uFxTrama.y,0.002);
        vec2 celda = fract(rot)-0.5;
        float l = clamp(dot(rgb,vec3(0.2126,0.7152,0.0722)),0.0,1.0);
        float r = uFxTrama.w > 0.5 ? (1.0-l)*0.5 : sqrt(1.0-l)*0.5;
        float d = uFxTrama.w > 0.5 ? abs(celda.x) : length(celda);
        float aa = clamp(max(uTexel.x*aspecto.x/tam.x,uTexel.y*aspecto.y/tam.y)/max(uFxTrama.y,0.002),0.015,0.2);
        float punto = 1.0-smoothstep(r-aa,r+aa,d);
        rgb = mix(rgb,uFxTramaColor,punto*uFxTrama.x);
    }
    return vec4(clamp(rgb,0.0,1.0)*c.a,c.a);
}
"""

    /** Todos los indicadores se escriben en cada dibujo para no contaminar grupos ni vecinos. */
    fun cargar(p: Programa, v: VfxPantalla) {
        val m=v.mitades
        GLES20.glUniform4f(p.u("uFxMitades"),m?.fuerza?:0f,m?.lado?:0f,m?.suavidad?:0f,m?.halo?:0f)
        if(m!=null) {
            GLES20.glUniform4f(p.u("uFxMitadesGeo"),m.dirX,m.dirY,m.centroX,m.centroY)
            color(p,"uFxMitadesColor",m.color)
        }
        val b=v.resplandor
        GLES20.glUniform4f(p.u("uFxBloom"),b?.fuerza?:0f,b?.radio?:0f,b?.umbral?:0f,b?.suavidad?:0f)
        if(b!=null) color(p,"uFxBloomColor",b.color)
        val t=v.trama
        GLES20.glUniform4f(p.u("uFxTrama"),t?.fuerza?:0f,t?.paso?:0f,t?.angulo?:0f,if(t?.lineas==true)1f else 0f)
        if(t!=null) color(p,"uFxTramaColor",t.color)
        val a=v.arcoiris
        GLES20.glUniform4f(p.u("uFxArcoiris"),a?.fuerza?:0f,a?.radio?:0f,a?.umbral?:0f,a?.fase?:0f)
    }

    private fun color(p: Programa, nombre: String, c: Int) = GLES20.glUniform3f(p.u(nombre),
        ((c ushr 16) and 255)/255f,((c ushr 8) and 255)/255f,(c and 255)/255f)
}
