package com.jormandelgado.jorcam

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/** Pantalla para revisar los subtítulos: un cuadro de edición independiente por cada frase, y el título arriba. */
object RevisionSubtitulos {
    class Resultado(val frases: List<Subtitulos.Frase>, val titulo: String)

    private fun tiempo(ms: Long): String {
        val s = ms / 1000
        return if (s < 60) "$s" else "${s / 60}:${"%02d".format(s % 60)}"
    }

    private fun rango(f: Subtitulos.Frase): String {
        val fin = (f.finMs + 999) / 1000 * 1000
        return if (f.inicioMs < 60_000) "del segundo ${tiempo(f.inicioMs)} al ${tiempo(fin)}" else "de ${tiempo(f.inicioMs)} a ${tiempo(fin)}"
    }

    fun mostrar(
        actividad: AppCompatActivity, frases: List<Subtitulos.Frase>, titulo: String, alTerminar: (Resultado?) -> Unit
    ) {
        val columna = LinearLayout(actividad)
        columna.orientation = LinearLayout.VERTICAL
        columna.setPadding(32, 16, 32, 16)

        fun agregar(etiqueta: String, texto: String): EditText {
            val rotulo = TextView(actividad)
            rotulo.text = etiqueta
            rotulo.textSize = 16f
            rotulo.setPadding(0, 24, 0, 0)
            val campo = EditText(actividad)
            campo.id = View.generateViewId()
            campo.setText(texto)
            campo.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            rotulo.labelFor = campo.id
            columna.addView(rotulo)
            columna.addView(campo)
            return campo
        }

        val campoTitulo = agregar("Título del video (opcional)", titulo)
        val campos = ArrayList<EditText>()
        for ((i, f) in frases.withIndex()) {
            campos.add(agregar("Subtítulo ${i + 1} de ${frases.size}, ${rango(f)}", f.texto))
        }

        val desplazable = ScrollView(actividad)
        desplazable.addView(columna)

        AlertDialog.Builder(actividad)
            .setTitle("Revisa los subtítulos")
            .setMessage("Corrige cada frase por separado. Si dejas una vacía, se borra.")
            .setView(desplazable)
            .setCancelable(false)
            .setPositiveButton("Continuar con estos subtítulos") { _, _ ->
                val nuevas = ArrayList<Subtitulos.Frase>()
                for ((i, f) in frases.withIndex()) {
                    val texto = campos[i].text.toString().replace("\n", " ").replace(Regex("\\s+"), " ").trim()
                    if (texto.isNotEmpty()) nuevas.add(Subtitulos.Frase(f.inicioMs, f.finMs, texto))
                }
                alTerminar(Resultado(nuevas, campoTitulo.text.toString().trim()))
            }
            .setNegativeButton("Guardar sin subtítulos") { _, _ -> alTerminar(null) }
            .show()
    }
}
