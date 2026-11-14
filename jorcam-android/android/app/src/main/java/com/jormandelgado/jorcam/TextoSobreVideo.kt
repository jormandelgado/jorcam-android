package com.jormandelgado.jorcam

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import kotlin.math.min

/**
 * Escribe el título (los primeros segundos, arriba de la cabeza) y los subtítulos (abajo, en una línea)
 * encima del video: solo letras con contorno, sin fondo. Cada cuadro de texto es una imagen transparente
 * del tamaño del video.
 */
@UnstableApi
class TextoSobreVideo(
    private val ancho: Int,
    private val alto: Int,
    private val lineasTitulo: List<String>,
    private val tituloInferiorY: Float,
    private val tamTitulo: Float,
    private val colorTitulo: Int,
    private val tipTitulo: Typeface,
    private val segundosTitulo: Long,
    private val subtitulos: List<Subtitulo>,
    private val subY: Float,
    private val tamSub: Float,
    private val colorSub: Int,
    private val tipSub: Typeface
) : BitmapOverlay() {

    data class Subtitulo(val inicioUs: Long, val finUs: Long, val texto: String)

    private var claveActual = -2
    private var bitmapActual: Bitmap = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val indice = subtitulos.indexOfFirst { presentationTimeUs >= it.inicioUs && presentationTimeUs < it.finUs }
        val hayTitulo = lineasTitulo.isNotEmpty() && presentationTimeUs < segundosTitulo * 1_000_000L
        val clave = (if (hayTitulo) 1 else 0) + 2 * (indice + 1)
        if (clave != claveActual) {
            // Se crea una imagen nueva cada vez que cambia el texto (si no, el video no se entera del cambio).
            bitmapActual = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
            val lienzo = Canvas(bitmapActual)
            if (hayTitulo) dibujarTitulo(lienzo)
            if (indice >= 0) dibujarSubtitulo(lienzo, subtitulos[indice].texto)
            claveActual = clave
        }
        return bitmapActual
    }

    private fun dibujarConContorno(lienzo: Canvas, texto: String, x: Float, baseline: Float, tam: Float, tip: Typeface, color: Int) {
        val contorno = Estilos.pintura(tam, tip).apply {
            style = Paint.Style.STROKE
            strokeWidth = tam * 0.16f
            strokeJoin = Paint.Join.ROUND
            this.color = Estilos.contornoPara(color)
        }
        val relleno = Estilos.pintura(tam, tip).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        lienzo.drawText(texto, x, baseline, contorno)
        lienzo.drawText(texto, x, baseline, relleno)
    }

    private fun dibujarTitulo(lienzo: Canvas) {
        val p = Estilos.pintura(tamTitulo, tipTitulo)
        val alturaLinea = tamTitulo * 1.15f
        var baseline = tituloInferiorY - p.descent()
        for (i in lineasTitulo.indices.reversed()) {
            dibujarConContorno(lienzo, lineasTitulo[i], ancho / 2f, baseline, tamTitulo, tipTitulo, colorTitulo)
            baseline -= alturaLinea
        }
    }

    private fun dibujarSubtitulo(lienzo: Canvas, texto: String) {
        val p = Estilos.pintura(tamSub, tipSub)
        val baseline = subY - p.ascent()
        dibujarConContorno(lienzo, texto, ancho / 2f, min(baseline, alto.toFloat()), tamSub, tipSub, colorSub)
    }
}
