// Puente entre Kotlin y waifu2x.
//
// waifu2x no afila: reconstruye. Se le entrena con imagenes estropeadas a
// proposito para que aprenda a devolver la linea limpia, y por eso recupera
// canto y quita el ruido de compresion en vez de subrayar lo que ya hay.
//
// Se usa a 2x aunque la salida sea del mismo tamano que la entrada. El truco
// esta en reconstruir al doble y volver a bajar: al reducir, cada pixel del
// resultado sale del promedio de cuatro reconstruidos, asi que hereda ese
// detalle. Es la unica forma honesta de subir calidad sin subir resolucion.

#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>

#include "net.h"
#include "gpu.h"
#include "waifu2x.h"

#define LOG(...) __android_log_print(ANDROID_LOG_WARN, "RoyerW2x", __VA_ARGS__)

namespace {

std::mutex candado;
Waifu2x* motor = nullptr;
int ruidoActual = -99;

}  // namespace

extern "C" {

/**
 * Carga el modelo para un nivel de restauracion.
 *
 * ruido va de 0 a 3: cuanto mas alto, mas agresivo limpiando. Cada nivel es
 * un modelo distinto, asi que cambiarlo obliga a recargar.
 */
JNIEXPORT jboolean JNICALL
Java_com_royermethod_app_procesado_Waifu2x_nativoCrear(
        JNIEnv* env, jobject, jstring carpeta, jint ruido)
{
    std::lock_guard<std::mutex> cierre(candado);

    if (ncnn::get_gpu_count() <= 0)
    {
        if (ncnn::create_gpu_instance() != 0) return JNI_FALSE;
        if (ncnn::get_gpu_count() <= 0) return JNI_FALSE;
    }

    if (motor != nullptr && ruidoActual == ruido) return JNI_TRUE;
    delete motor;
    motor = nullptr;

    const char* ruta = env->GetStringUTFChars(carpeta, nullptr);
    std::string dir(ruta ? ruta : "");
    env->ReleaseStringUTFChars(carpeta, ruta);
    if (dir.empty()) return JNI_FALSE;

    const std::string base = ruido < 0
            ? dir + "/scale2.0x_model"
            : dir + "/noise" + std::to_string(ruido) + "_scale2.0x_model";

    Waifu2x* nuevo = new Waifu2x(ncnn::get_default_gpu_index(), false, 1);
    nuevo->noise = ruido;
    nuevo->scale = 2;
    // Trozos de 200 pixeles. Reconstruir una foto entera de golpe no cabe en
    // la memoria de video de un movil; en trozos, si. El solapado que exige
    // el modelo lo lleva waifu2x por dentro.
    nuevo->tilesize = 200;
    nuevo->prepadding = 18;

    if (nuevo->load(base + ".param", base + ".bin") != 0)
    {
        LOG("no se pudo cargar %s", base.c_str());
        delete nuevo;
        return JNI_FALSE;
    }

    motor = nuevo;
    ruidoActual = ruido;
    return JNI_TRUE;
}

/**
 * Reconstruye al doble de tamano.
 *
 * entrada es ancho*alto*4 en RGBA; salida tiene que ser cuatro veces mayor,
 * (ancho*2)*(alto*2)*4.
 */
JNIEXPORT jboolean JNICALL
Java_com_royermethod_app_procesado_Waifu2x_nativoProcesar(
        JNIEnv* env, jobject, jobject entrada, jobject salida, jint ancho, jint alto)
{
    std::lock_guard<std::mutex> cierre(candado);
    if (motor == nullptr) return JNI_FALSE;

    unsigned char* pe = (unsigned char*)env->GetDirectBufferAddress(entrada);
    unsigned char* ps = (unsigned char*)env->GetDirectBufferAddress(salida);
    if (pe == nullptr || ps == nullptr) return JNI_FALSE;

    // Igual que en el otro modelo: por fuera RGBA porque es lo que da OpenGL,
    // por dentro tres canales, que es el camino probado.
    const size_t dentroPx = (size_t)ancho * alto;
    const size_t fueraPx = dentroPx * 4;
    std::vector<unsigned char> e3(dentroPx * 3);
    std::vector<unsigned char> s3(fueraPx * 3);
    for (size_t i = 0; i < dentroPx; i++)
    {
        e3[i * 3] = pe[i * 4];
        e3[i * 3 + 1] = pe[i * 4 + 1];
        e3[i * 3 + 2] = pe[i * 4 + 2];
    }

    ncnn::Mat dentro(ancho, alto, (void*)e3.data(), (size_t)3, 3);
    ncnn::Mat fuera(ancho * 2, alto * 2, (void*)s3.data(), (size_t)3, 3);

    if (motor->process(dentro, fuera) != 0) return JNI_FALSE;

    for (size_t i = 0; i < fueraPx; i++)
    {
        ps[i * 4] = s3[i * 3];
        ps[i * 4 + 1] = s3[i * 3 + 1];
        ps[i * 4 + 2] = s3[i * 3 + 2];
        ps[i * 4 + 3] = 255;
    }
    return JNI_TRUE;
}

/**
 * Baja a la mitad promediando cada cuadrado de cuatro.
 *
 * Es el paso que convierte "reconstruir al doble" en "mejorar sin cambiar de
 * tamano": cada pixel del resultado es la media exacta de cuatro
 * reconstruidos, asi que hereda su detalle sin heredar su tamano. No es un
 * filtro de reduccion cualquiera, es la media exacta, que para un factor de
 * dos es lo mejor que se puede hacer.
 */
JNIEXPORT void JNICALL
Java_com_royermethod_app_procesado_Waifu2x_nativoMitad(
        JNIEnv* env, jobject, jobject grande, jobject destino, jint ancho, jint alto)
{
    const unsigned char* g = (const unsigned char*)env->GetDirectBufferAddress(grande);
    unsigned char* d = (unsigned char*)env->GetDirectBufferAddress(destino);
    if (g == nullptr || d == nullptr) return;

    const int anchoGrande = ancho * 2;
    for (int y = 0; y < alto; y++)
    {
        const unsigned char* f0 = g + (size_t)(y * 2) * anchoGrande * 4;
        const unsigned char* f1 = f0 + (size_t)anchoGrande * 4;
        unsigned char* s = d + (size_t)y * ancho * 4;
        for (int x = 0; x < ancho; x++)
        {
            const int i = x * 8;
            for (int c = 0; c < 4; c++)
            {
                const int suma = f0[i + c] + f0[i + 4 + c] + f1[i + c] + f1[i + 4 + c];
                s[x * 4 + c] = (unsigned char)((suma + 2) / 4);
            }
        }
    }
}

JNIEXPORT void JNICALL
Java_com_royermethod_app_procesado_Waifu2x_nativoDestruir(JNIEnv*, jobject)
{
    std::lock_guard<std::mutex> cierre(candado);
    delete motor;
    motor = nullptr;
    ruidoActual = -99;
}

}  // extern "C"
