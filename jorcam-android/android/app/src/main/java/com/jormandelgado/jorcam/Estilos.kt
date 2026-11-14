package com.jormandelgado.jorcam

import android.graphics.Paint
import android.graphics.Typeface

/** Colores, fuentes y medidas del texto que se escribe sobre el video (equivalentes a los de la versión de PC). */
object Estilos {
    val COLORES: LinkedHashMap<String, Int> = linkedMapOf(
        "Blanco" to 0xFFFFFFFF.toInt(),
        "Negro" to 0xFF000000.toInt(),
        "Amarillo" to 0xFFFFD400.toInt(),
        "Rojo" to 0xFFE00000.toInt(),
        "Azul" to 0xFF1E5BFF.toInt(),
        "Verde" to 0xFF00B050.toInt(),
        "Naranja" to 0xFFFF8C00.toInt(),
        "Rosado" to 0xFFFF69B4.toInt(),
        "Celeste" to 0xFF00BFFF.toInt(),
        "Morado" to 0xFF8A2BE2.toInt(),
        "Gris" to 0xFFA0A0A0.toInt()
    )

    val FUENTES: List<String> = listOf("Clásica (Arial)", "Con serifas (Times New Roman)", "Gruesa (Arial Black)")

    fun tipografia(nombre: String): Typeface = when (nombre) {
        "Con serifas (Times New Roman)" -> Typeface.create(Typeface.SERIF, Typeface.BOLD)
        "Gruesa (Arial Black)" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    fun colorDe(nombre: String): Int = COLORES[nombre] ?: 0xFFFFFFFF.toInt()

    /** Contorno negro para colores claros y blanco para oscuros, para que se lea sobre cualquier fondo. */
    fun contornoPara(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return if (0.299 * r + 0.587 * g + 0.114 * b >= 70) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }

    fun pintura(tam: Float, tipografia: Typeface): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = tam
        typeface = tipografia
        textAlign = Paint.Align.CENTER
    }

    /** Parte el título en hasta dos líneas y achica la letra si no cabe en el ancho (medido con la fuente real). */
    fun partirTitulo(texto: String, tamPreferido: Float, tipografia: Typeface, anchoMax: Float): Pair<List<String>, Float> {
        val palabras = texto.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (palabras.isEmpty()) return Pair(emptyList(), tamPreferido)
        var tam = tamPreferido
        while (true) {
            val p = pintura(tam, tipografia)
            val lineas = partir(palabras, p, anchoMax)
            val cabe = lineas.size <= 2 && lineas.all { p.measureText(it) <= anchoMax }
            if (cabe || tam <= 48f) return Pair(lineas, tam)
            tam -= 4f
        }
    }

    private fun partir(palabras: List<String>, p: Paint, anchoMax: Float): List<String> {
        val todo = palabras.joinToString(" ")
        if (p.measureText(todo) <= anchoMax) return listOf(todo)
        var mejor: List<String>? = null
        var mejorAncho = Float.MAX_VALUE
        for (i in 1 until palabras.size) {
            val a = palabras.subList(0, i).joinToString(" ")
            val b = palabras.subList(i, palabras.size).joinToString(" ")
            val ancho = maxOf(p.measureText(a), p.measureText(b))
            if (ancho < mejorAncho) {
                mejorAncho = ancho
                mejor = listOf(a, b)
            }
        }
        if (mejor != null && mejorAncho <= anchoMax) return mejor
        val lineas = ArrayList<String>()
        var actual = ""
        for (palabra in palabras) {
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

    /** El título pasa a ser el nombre del archivo (sin emojis ni los caracteres que Android no permite). */
    fun nombreDeArchivo(titulo: String): String {
        var t = titulo.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u200D\\u20E3]"), "")
        t = t.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "")
        t = t.replace(Regex("\\s+"), " ").trim().trim('.')
        return if (t.length > 80) t.substring(0, 80).trim() else t
    }

    fun sinEmojis(texto: String): String =
        texto.replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u200D\\u20E3]"), "").replace(Regex("\\s+"), " ").trim()
}
