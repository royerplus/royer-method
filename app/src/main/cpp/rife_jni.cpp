// Puente entre Kotlin y RIFE.
//
// RIFE es el modelo que genera fotogramas intermedios: se le dan dos imagenes
// consecutivas y un instante entre ellas, y devuelve como se veria el video en
// ese instante. Corre sobre Vulkan a traves de ncnn, o sea en la GPU del
// telefono, que es la unica forma de que esto no tarde horas.
//
// Aqui solo hay fontaneria. Las imagenes viajan en ByteBuffer directos para
// que no haya ni una copia entre Java y C++: a 960 fotogramas por segundo de
// video, copiar cada imagen dos veces seria mas caro que el propio modelo.

#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>

#include "net.h"
#include "gpu.h"
#include "rife.h"

#define LOG(...) __android_log_print(ANDROID_LOG_WARN, "RoyerRife", __VA_ARGS__)

namespace {

std::mutex candado;
RIFE* motor = nullptr;
bool instanciaGpu = false;

}  // namespace

extern "C" {

/**
 * ¿Puede este telefono?
 *
 * Hace falta Vulkan con computacion. Practicamente cualquier movil de 2019 en
 * adelante lo tiene, pero hay que preguntarlo antes de ofrecer la funcion en
 * vez de dejar que reviente al pulsar procesar.
 */
JNIEXPORT jboolean JNICALL
Java_com_royermethod_app_procesado_Rife_nativoDisponible(JNIEnv*, jobject)
{
    std::lock_guard<std::mutex> cierre(candado);
    if (!instanciaGpu)
    {
        if (ncnn::create_gpu_instance() != 0) return JNI_FALSE;
        instanciaGpu = true;
    }
    return ncnn::get_gpu_count() > 0 ? JNI_TRUE : JNI_FALSE;
}

/**
 * Carga el modelo. carpeta apunta a un directorio con flownet.param y
 * flownet.bin ya copiados desde los assets: ncnn abre archivos de verdad y un
 * asset dentro del APK no lo es.
 */
JNIEXPORT jboolean JNICALL
Java_com_royermethod_app_procesado_Rife_nativoCrear(JNIEnv* env, jobject, jstring carpeta)
{
    std::lock_guard<std::mutex> cierre(candado);

    if (!instanciaGpu)
    {
        if (ncnn::create_gpu_instance() != 0)
        {
            LOG("sin instancia de Vulkan");
            return JNI_FALSE;
        }
        instanciaGpu = true;
    }
    if (ncnn::get_gpu_count() <= 0)
    {
        LOG("sin GPU compatible");
        return JNI_FALSE;
    }

    if (motor != nullptr) return JNI_TRUE;

    const char* ruta = env->GetStringUTFChars(carpeta, nullptr);
    std::string dir(ruta ? ruta : "");
    env->ReleaseStringUTFChars(carpeta, ruta);
    if (dir.empty()) return JNI_FALSE;

    // rife_v4 = true: la version 4.6 es la unica que admite pedir un instante
    // concreto entre dos fotogramas. Las anteriores solo saben dar el punto
    // medio, y encadenar puntos medios para llegar a 960 fps acumula error.
    const int gpu = ncnn::get_default_gpu_index();
    RIFE* nuevo = new RIFE(gpu,
                           /*tta_mode*/ false,
                           /*tta_temporal_mode*/ false,
                           /*uhd_mode*/ false,
                           /*num_threads*/ 1,
                           /*rife_v2*/ false,
                           /*rife_v4*/ true);

    if (nuevo->load(dir) != 0)
    {
        LOG("no se pudo cargar el modelo de %s", dir.c_str());
        delete nuevo;
        return JNI_FALSE;
    }

    motor = nuevo;
    LOG("modelo cargado, gpu %d", gpu);
    return JNI_TRUE;
}

/**
 * Genera el fotograma intermedio.
 *
 * a y b son los dos fotogramas de origen y salida recibe el resultado, los
 * tres en RGB de tres bytes por pixel. instante va de 0 a 1: 0 devuelve el
 * primero, 1 el segundo, 0.5 el punto medio.
 */
JNIEXPORT jboolean JNICALL
Java_com_royermethod_app_procesado_Rife_nativoInterpolar(
        JNIEnv* env, jobject,
        jobject a, jobject b, jobject salida,
        jint ancho, jint alto, jfloat instante)
{
    std::lock_guard<std::mutex> cierre(candado);
    if (motor == nullptr) return JNI_FALSE;

    unsigned char* pa = (unsigned char*)env->GetDirectBufferAddress(a);
    unsigned char* pb = (unsigned char*)env->GetDirectBufferAddress(b);
    unsigned char* ps = (unsigned char*)env->GetDirectBufferAddress(salida);
    if (pa == nullptr || pb == nullptr || ps == nullptr)
    {
        LOG("los buffers tienen que ser directos");
        return JNI_FALSE;
    }

    // Se trabaja en tres canales, no en cuatro.
    //
    // Los buffers vienen y van en RGBA porque es lo que entrega OpenGL, pero
    // por dentro se convierten. El camino de cuatro canales de rife-ncnn-vulkan
    // esta a medias: hay sitios que escriben tres bytes por pixel den igual los
    // canales que haya, y eso desplazaba cada fila. Convertir cuesta unos pocos
    // milisegundos frente a los doscientos que cuesta el modelo, y a cambio se
    // usa el camino que esta de verdad probado.
    const size_t pixeles = (size_t)ancho * alto;
    std::vector<unsigned char> a3(pixeles * 3);
    std::vector<unsigned char> b3(pixeles * 3);
    std::vector<unsigned char> s3(pixeles * 3);
    for (size_t i = 0; i < pixeles; i++)
    {
        a3[i * 3] = pa[i * 4];
        a3[i * 3 + 1] = pa[i * 4 + 1];
        a3[i * 3 + 2] = pa[i * 4 + 2];
        b3[i * 3] = pb[i * 4];
        b3[i * 3 + 1] = pb[i * 4 + 1];
        b3[i * 3 + 2] = pb[i * 4 + 2];
    }

    ncnn::Mat imagen0(ancho, alto, (void*)a3.data(), (size_t)3, 3);
    ncnn::Mat imagen1(ancho, alto, (void*)b3.data(), (size_t)3, 3);
    ncnn::Mat resultado(ancho, alto, (void*)s3.data(), (size_t)3, 3);

    if (motor->process(imagen0, imagen1, instante, resultado) != 0) return JNI_FALSE;

    for (size_t i = 0; i < pixeles; i++)
    {
        ps[i * 4] = s3[i * 3];
        ps[i * 4 + 1] = s3[i * 3 + 1];
        ps[i * 4 + 2] = s3[i * 3 + 2];
        ps[i * 4 + 3] = 255;
    }
    return JNI_TRUE;
}

/**
 * Suma un fotograma al acumulador.
 *
 * El desenfoque de movimiento sale de promediar los fotogramas generados: si
 * se generan doscientos cuarenta por segundo y se sacan treinta, cada uno de
 * salida es la media de ocho. Eso es lo que deja el rastro continuo en vez de
 * la estela escalonada.
 *
 * Va en C++ porque son varios millones de sumas por fotograma de salida y en
 * Kotlin costarian mas que el propio modelo. El acumulador es de enteros de
 * dieciseis bits: cuarenta fotogramas por 255 son 10200, entran de sobra.
 */
JNIEXPORT void JNICALL
Java_com_royermethod_app_procesado_Rife_nativoAcumular(
        JNIEnv* env, jobject, jobject fuente, jobject acumulador, jint muestras)
{
    const unsigned char* src = (const unsigned char*)env->GetDirectBufferAddress(fuente);
    unsigned short* acc = (unsigned short*)env->GetDirectBufferAddress(acumulador);
    if (src == nullptr || acc == nullptr) return;
    for (int i = 0; i < muestras; i++) acc[i] = (unsigned short)(acc[i] + src[i]);
}

/** Divide el acumulador entre el numero de sumas y lo vuelca a bytes. */
JNIEXPORT void JNICALL
Java_com_royermethod_app_procesado_Rife_nativoPromediar(
        JNIEnv* env, jobject, jobject acumulador, jobject destino, jint muestras, jint cuantos)
{
    unsigned short* acc = (unsigned short*)env->GetDirectBufferAddress(acumulador);
    unsigned char* dst = (unsigned char*)env->GetDirectBufferAddress(destino);
    if (acc == nullptr || dst == nullptr || cuantos <= 0) return;
    const int mitad = cuantos / 2;
    for (int i = 0; i < muestras; i++)
    {
        int v = (acc[i] + mitad) / cuantos;
        dst[i] = (unsigned char)(v > 255 ? 255 : v);
        acc[i] = 0;
    }
}

JNIEXPORT void JNICALL
Java_com_royermethod_app_procesado_Rife_nativoDestruir(JNIEnv*, jobject)
{
    std::lock_guard<std::mutex> cierre(candado);
    delete motor;
    motor = nullptr;
    if (instanciaGpu)
    {
        ncnn::destroy_gpu_instance();
        instanciaGpu = false;
    }
}

}  // extern "C"
