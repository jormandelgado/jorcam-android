package com.jormandelgado.jorcam

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import kotlin.math.max

/**
 * Convierte la voz en subtítulos: transcribe con Whisper solo los tramos con voz (así los tiempos coinciden con el
 * video ya recortado), limpia el texto, agrega ¿ y ¡, y parte cada frase en líneas que caben en el ancho del video.
 */
object Subtitulos {
    /** Una línea de subtítulo, con tiempos del video FINAL (ya sin silencios), en milisegundos. */
    data class Frase(var inicioMs: Long, var finMs: Long, var texto: String)

    private val PROHIBIDAS = listOf("amara.org", "subtítulos realizados por", "subtitulos realizados por")

    /** Devuelve null si algo falló. Avance: 0 a 60 (decodificar el audio 0 a 20, transcribir 20 a 60). */
    fun transcribir(
        ctx: Context, uri: Uri, tramos: List<Silencios.Tramo>, rutaModelo: String, duracionMs: Long,
        tipografia: Typeface, tamSub: Float, anchoVideo: Int, progreso: (Int) -> Unit, aviso: (String) -> Unit
    ): List<Frase>? {
        val inicio = System.currentTimeMillis()
        val pcm = Audio16k.decodificar(ctx, uri, duracionMs) { p -> progreso(p / 5) } ?: return null
        val contexto = Whisper.iniciar(rutaModelo)
        if (contexto == 0L) {
            aviso("No se pudo cargar el modelo de voz.")
            return null
        }
        val segmentos = ArrayList<Frase>()
        var voz = 0L
        try {
            val total = max(tramos.sumOf { it.finMs - it.inicioMs }, 1L)
            var desplazamiento = 0L
            var hecho = 0L
            for (t in tramos) {
                val dur = t.finMs - t.inicioMs
                val desde = (t.inicioMs * 16).toInt().coerceIn(0, pcm.size)
                val hasta = (t.finMs * 16).toInt().coerceIn(desde, pcm.size)
                if (hasta - desde >= 16 * 300) {
                    val trozo = FloatArray(hasta - desde) { i -> pcm[desde + i] / 32768f }
                    val bytes = Whisper.transcribir(contexto, trozo, 4)
                    for (linea in String(bytes, Charsets.UTF_8).split("\n")) {
                        val partes = linea.split("|", limit = 3)
                        if (partes.size < 3) continue
                        val a = partes[0].toLongOrNull() ?: continue
                        val b = partes[1].toLongOrNull() ?: continue
                        val texto = limpiar(partes[2])
                        if (texto.isEmpty()) continue
                        segmentos.add(Frase(desplazamiento + minOf(a, dur), desplazamiento + minOf(max(b, a), dur), texto))
                    }
                }
                voz += dur
                desplazamiento += dur
                hecho += dur
                progreso(20 + (hecho * 40 / total).toInt())
            }
        } finally {
            Whisper.liberar(contexto)
        }
        aviso("La transcripción de ${voz / 1000} segundos de voz tardó ${(System.currentTimeMillis() - inicio) / 1000} segundos.")
        return dividir(segmentos, tipografia, tamSub, anchoVideo)
    }

    private fun limpiar(texto: String): String {
        var t = texto.replace(Regex("\\[[^\\]]*\\]"), " ").replace(Regex("\\([^)]*\\)"), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        val minuscula = t.lowercase()
        if (PROHIBIDAS.any { minuscula.contains(it) }) return ""
        if (t.none { it.isLetterOrDigit() }) return ""
        return t
    }

    /** Agrega ¿ y ¡ cuando la oración termina en ? o ! y no los trae. */
    private fun puntuar(texto: String): String {
        val oraciones = texto.split(Regex("(?<=[.!?…])\\s+"))
        return oraciones.joinToString(" ") { o ->
            var s = o.trim()
            if (s.endsWith("?") && !s.contains("¿")) s = "¿$s"
            if (s.endsWith("!") && !s.contains("¡")) s = "¡$s"
            s
        }
    }

    private fun partirEnLineas(texto: String, p: Paint, anchoMax: Float): List<String> {
        val lineas = ArrayList<String>()
        var actual = ""
        for (palabra in texto.split(" ").filter { it.isNotEmpty() }) {
            val prueba = if (actual.isEmpty()) palabra else "$actual $palabra"
            if (actual.isNotEmpty() && p.measureText(prueba) > anchoMax) {
                lineas.add(actual)
                actual = palabra
            } else {
                actual = prueba
            }
        }
        if (actual.isNotEmpty()) lineas.add(actual)
        return lineas
    }

    /** Una sola línea por subtítulo; el tiempo de cada frase se reparte según el largo de cada línea. */
    private fun dividir(segmentos: List<Frase>, tipografia: Typeface, tam: Float, anchoVideo: Int): List<Frase> {
        val p = Estilos.pintura(tam, tipografia)
        val anchoMax = anchoVideo * 0.85f
        val resultado = ArrayList<Frase>()
        for (s in segmentos) {
            val lineas = partirEnLineas(puntuar(s.texto), p, anchoMax)
            if (lineas.isEmpty()) continue
            val letras = max(lineas.sumOf { it.length }, 1)
            val duracion = max(s.finMs - s.inicioMs, 300L)
            var t = s.inicioMs
            for (linea in lineas) {
                val parte = duracion * linea.length / letras
                val ini = max(t, if (resultado.isEmpty()) 0L else resultado[resultado.size - 1].finMs)
                val fin = max(ini + 250L, t + parte)
                resultado.add(Frase(ini, fin, linea))
                t += parte
            }
        }
        return resultado
    }
}
