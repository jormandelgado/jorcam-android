package com.jormandelgado.jorcam

/** Motor de voz local (whisper.cpp). Funciona sin internet una vez descargado el modelo. */
object Whisper {
    private var cargada = false

    init {
        try {
            System.loadLibrary("jorcam_whisper")
            cargada = true
        } catch (e: UnsatisfiedLinkError) {
            cargada = false
        }
    }

    val disponible: Boolean
        get() = cargada

    external fun iniciar(ruta: String): Long
    external fun liberar(contexto: Long)
    external fun transcribir(contexto: Long, muestras: FloatArray, hilos: Int): ByteArray
}
