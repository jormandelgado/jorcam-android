// Puente entre Kotlin y whisper.cpp: carga el modelo y transcribe un trozo de audio (16 kHz, mono).
#include <jni.h>
#include <string>
#include <android/log.h>
#include "whisper.h"

#define ETIQUETA "JorCamWhisper"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_jormandelgado_jorcam_Whisper_iniciar(JNIEnv *env, jobject /*este*/, jstring ruta) {
    const char *r = env->GetStringUTFChars(ruta, nullptr);
    struct whisper_context_params cp = whisper_context_default_params();
    cp.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(r, cp);
    env->ReleaseStringUTFChars(ruta, r);
    if (ctx == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, ETIQUETA, "No se pudo cargar el modelo");
    }
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_jormandelgado_jorcam_Whisper_liberar(JNIEnv * /*env*/, jobject /*este*/, jlong contexto) {
    if (contexto != 0) {
        whisper_free(reinterpret_cast<struct whisper_context *>(contexto));
    }
}

// Devuelve líneas "inicioMs|finMs|texto" codificadas en UTF-8 (como bytes, para no depender de la codificación de JNI).
JNIEXPORT jbyteArray JNICALL
Java_com_jormandelgado_jorcam_Whisper_transcribir(JNIEnv *env, jobject /*este*/, jlong contexto,
                                                   jfloatArray muestras, jint hilos) {
    struct whisper_context *ctx = reinterpret_cast<struct whisper_context *>(contexto);
    std::string salida;
    if (ctx != nullptr) {
        jsize n = env->GetArrayLength(muestras);
        jfloat *datos = env->GetFloatArrayElements(muestras, nullptr);

        static std::string idioma = "es";
        struct whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        p.print_realtime = false;
        p.print_progress = false;
        p.print_timestamps = false;
        p.print_special = false;
        p.translate = false;
        p.language = idioma.c_str();
        p.n_threads = hilos;
        p.offset_ms = 0;
        p.no_context = true;
        p.single_segment = false;
        p.greedy.best_of = 1;

        int rc = whisper_full(ctx, p, datos, n);
        env->ReleaseFloatArrayElements(muestras, datos, JNI_ABORT);

        if (rc == 0) {
            int segmentos = whisper_full_n_segments(ctx);
            for (int i = 0; i < segmentos; i++) {
                long long t0 = whisper_full_get_segment_t0(ctx, i) * 10;  // viene en centésimas de segundo
                long long t1 = whisper_full_get_segment_t1(ctx, i) * 10;
                const char *texto = whisper_full_get_segment_text(ctx, i);
                salida += std::to_string(t0) + "|" + std::to_string(t1) + "|" + (texto ? texto : "") + "\n";
            }
        } else {
            __android_log_print(ANDROID_LOG_ERROR, ETIQUETA, "whisper_full devolvió %d", rc);
        }
    }
    jbyteArray resultado = env->NewByteArray(static_cast<jsize>(salida.size()));
    env->SetByteArrayRegion(resultado, 0, static_cast<jsize>(salida.size()),
                            reinterpret_cast<const jbyte *>(salida.data()));
    return resultado;
}

}  // extern "C"
