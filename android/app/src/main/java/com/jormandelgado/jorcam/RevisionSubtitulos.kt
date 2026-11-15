package com.jormandelgado.jorcam

import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Pantalla de revisión de subtítulos (ocupa toda la pantalla, sin cuadros de diálogo):
 * los botones están arriba, siempre a la mano; cada frase tiene su cuadro de edición y un botón para escuchar solo ese tramo.
 * El botón Atrás equivale a "Guardar sin subtítulos".
 */
class RevisionSubtitulos(private val actividad: AppCompatActivity) {
    class Resultado(val frases: List<Subtitulos.Frase>, val titulo: String)

    private val pantalla: View = actividad.findViewById(R.id.pantallaRevision)
    private val lista: LinearLayout = actividad.findViewById(R.id.listaRevision)
    private val btnContinuar: Button = actividad.findViewById(R.id.btnRevContinuar)
    private val btnSin: Button = actividad.findViewById(R.id.btnRevSin)
    private val manejador = Handler(Looper.getMainLooper())

    private var reproductor: MediaPlayer? = null
    private var respuesta: ((Resultado?) -> Unit)? = null
    private var campoTitulo: EditText? = null
    private var campos: List<EditText> = emptyList()
    private var originales: List<Subtitulos.Frase> = emptyList()

    var alCambiarAbierta: ((Boolean) -> Unit)? = null

    val abierta: Boolean
        get() = pantalla.visibility == View.VISIBLE

    private fun tiempo(ms: Long): String {
        val s = ms / 1000
        return if (s < 60) "$s" else "${s / 60}:${"%02d".format(s % 60)}"
    }

    private fun rango(f: Subtitulos.Frase): String {
        val fin = (f.finMs + 999) / 1000 * 1000
        return if (f.inicioMs < 60_000) "del segundo ${tiempo(f.inicioMs)} al ${tiempo(fin)}" else "de ${tiempo(f.inicioMs)} a ${tiempo(fin)}"
    }

    private fun agregarCampo(etiqueta: String, texto: String): EditText {
        val rotulo = TextView(actividad)
        rotulo.text = etiqueta
        rotulo.textSize = 16f
        rotulo.setTextColor(Color.WHITE)
        rotulo.setPadding(0, 32, 0, 0)
        val campo = EditText(actividad)
        campo.id = View.generateViewId()
        campo.setText(texto)
        campo.setBackgroundColor(Color.WHITE)
        campo.setTextColor(Color.BLACK)
        campo.setPadding(16, 16, 16, 16)
        campo.textSize = 18f
        campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        rotulo.labelFor = campo.id
        lista.addView(rotulo)
        lista.addView(campo)
        return campo
    }

    /** aOrigenMs convierte un instante del video final (ya sin silencios) en el instante correspondiente del video original. */
    fun mostrar(
        frases: List<Subtitulos.Frase>, titulo: String, entrada: Uri,
        aOrigenMs: (Long) -> Long, alTerminar: (Resultado?) -> Unit
    ) {
        respuesta = alTerminar
        originales = frases
        lista.removeAllViews()
        campoTitulo = agregarCampo("Título del video (opcional)", titulo)
        val nuevos = ArrayList<EditText>()
        for ((i, f) in frases.withIndex()) {
            nuevos.add(agregarCampo("Subtítulo ${i + 1} de ${frases.size}, ${rango(f)}", f.texto))
            val boton = Button(actividad)
            boton.text = "Reproducir este tramo"
            boton.contentDescription = "Reproducir este tramo, subtítulo ${i + 1}"
            boton.minHeight = 140
            boton.setOnClickListener {
                val inicio = aOrigenMs(f.inicioMs)
                val fin = aOrigenMs(maxOf(f.finMs - 1, f.inicioMs)) + 1
                reproducir(entrada, inicio, maxOf(fin, inicio + 400))
            }
            lista.addView(boton)
        }
        campos = nuevos
        btnContinuar.setOnClickListener { terminar(true) }
        btnSin.setOnClickListener { terminar(false) }
        ocultarFondo(true)
        pantalla.visibility = View.VISIBLE
        alCambiarAbierta?.invoke(true)
    }

    /** Equivale a "Guardar sin subtítulos" (es lo que hace el botón Atrás). */
    fun cancelar() {
        if (abierta) terminar(false)
    }

    private fun terminar(continuar: Boolean) {
        detener()
        var resultado: Resultado? = null
        if (continuar) {
            val nuevas = ArrayList<Subtitulos.Frase>()
            for ((i, f) in originales.withIndex()) {
                val texto = campos[i].text.toString().replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                if (texto.isNotEmpty()) nuevas.add(Subtitulos.Frase(f.inicioMs, f.finMs, texto))
            }
            resultado = Resultado(nuevas, campoTitulo?.text?.toString()?.trim() ?: "")
        }
        pantalla.visibility = View.GONE
        ocultarFondo(false)
        lista.removeAllViews()
        alCambiarAbierta?.invoke(false)
        val devolver = respuesta
        respuesta = null
        devolver?.invoke(resultado)
    }

    private fun ocultarFondo(ocultar: Boolean) {
        val valor = if (ocultar) View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        for (id in listOf(R.id.panel, R.id.btnControles)) actividad.findViewById<View>(id).importantForAccessibility = valor
    }

    // ---- Escuchar solo un tramo del video original ----

    private fun reproducir(uri: Uri, inicio: Long, fin: Long) {
        detener()
        val mp = MediaPlayer()
        reproductor = mp
        var iniciado = false
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            mp.setDataSource(actividad, uri)
            mp.setOnPreparedListener { p -> p.seekTo(inicio, MediaPlayer.SEEK_CLOSEST) }
            mp.setOnSeekCompleteListener { p ->
                if (!iniciado) {
                    iniciado = true
                    p.start()
                    manejador.postDelayed({ if (reproductor === p) detener() }, fin - inicio)
                }
            }
            mp.setOnCompletionListener { detener() }
            mp.setOnErrorListener { _, _, _ ->
                detener()
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            detener()
        }
    }

    private fun detener() {
        manejador.removeCallbacksAndMessages(null)
        val mp = reproductor
        reproductor = null
        if (mp != null) {
            try { mp.stop() } catch (e: Exception) { }
            mp.release()
        }
    }
}
