package com.jormandelgado.jorcam

import android.content.Context
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.TextView
import java.util.Locale

/** Anuncia por voz cada acción: con el lector de pantalla (TalkBack, Jieshuo) o con la voz propia de JorCam. */
class Anunciador(contexto: Context, private val vista: View, private val registro: TextView) {
    var vozPropia: Boolean = false
    private var tts: TextToSpeech? = null
    private var ttsListo = false

    init {
        tts = TextToSpeech(contexto.applicationContext) { estado ->
            if (estado == TextToSpeech.SUCCESS) {
                val resultado = tts?.setLanguage(Locale("es", "VE"))
                if (resultado == TextToSpeech.LANG_MISSING_DATA || resultado == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("es"))
                }
                ttsListo = true
            }
        }
    }

    fun decir(texto: String) {
        vista.post {
            registro.text = texto
            if (vozPropia && ttsListo) {
                tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "jorcam")
            } else {
                vista.announceForAccessibility(texto)
            }
        }
    }

    fun cerrar() {
        tts?.stop()
        tts?.shutdown()
    }
}
