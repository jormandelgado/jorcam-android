package com.jormandelgado.jorcam

import android.content.Context
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Busca la cara en algunos cuadros del video (sin internet) para colocar el título y los subtítulos sin tapar el rostro. */
object Rostro {
    /** Alturas en píxeles del video final (cuadro vertical de 1920 de alto). */
    data class Posicion(val cabezaY: Float, val barbillaY: Float, val altoCara: Float)

    data class Colocacion(
        val lineasTitulo: List<String>, val tamTitulo: Float, val tituloInferiorY: Float,
        val subY: Float, val aviso: String?
    )

    private const val TOP_SEGURO = 200f
    private const val BASE_SEGURA = 330f

    /** tiemposOrigenMs: instantes del video original donde mirar. altoFinal: alto del cuadro final en píxeles. */
    fun buscar(ctx: Context, uri: Uri, tiemposOrigenMs: List<Long>, altoFinal: Int): Posicion? {
        val lector = MediaMetadataRetriever()
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build()
        )
        val cajas = ArrayList<RectF>()
        try {
            lector.setDataSource(ctx, uri)
            for (t in tiemposOrigenMs) {
                val cuadro = lector.getFrameAtTime(t * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: continue
                val caras = try {
                    Tasks.await(detector.process(InputImage.fromBitmap(cuadro, 0)), 20, TimeUnit.SECONDS)
                } catch (e: Exception) {
                    emptyList()
                }
                val mayor = caras.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: continue
                val r = mayor.boundingBox
                val escala = altoFinal.toFloat() / cuadro.height
                cajas.add(RectF(r.left * escala, r.top * escala, r.right * escala, r.bottom * escala))
            }
        } catch (e: Exception) {
            return null
        } finally {
            lector.release()
            detector.close()
        }
        if (cajas.isEmpty()) return null
        val cabeza = cajas.minOf { it.top - 0.30f * it.height() }
        val barbilla = cajas.maxOf { it.bottom }
        val ordenadas = cajas.map { it.height() }.sorted()
        return Posicion(cabeza, barbilla, ordenadas[ordenadas.size / 2])
    }

    /** Instante del video original que corresponde a un instante del video ya recortado. */
    fun tiempoOrigen(finalMs: Long, tramos: List<Silencios.Tramo>): Long {
        var restante = finalMs
        for (t in tramos) {
            val dur = t.finMs - t.inicioMs
            if (restante < dur) return t.inicioMs + restante
            restante -= dur
        }
        return tramos.last().finMs
    }

    /** Lo mismo que hace la versión de PC: el título encima de la cabeza y los subtítulos justo debajo de la barbilla. */
    fun colocar(
        titulo: String, posicion: Posicion?, tamTituloPref: Float, tipTitulo: android.graphics.Typeface,
        tamSub: Float, altoFinal: Int
    ): Colocacion {
        val factor = altoFinal / 1920f
        var aviso: String? = null
        var lineas: List<String> = emptyList()
        var tam = tamTituloPref
        var inferior = if (posicion != null) posicion.cabezaY - 45f * factor else 560f * factor
        if (titulo.isNotEmpty()) {
            while (true) {
                val resultado = Estilos.partirTitulo(titulo, tam, tipTitulo, 920f)
                lineas = resultado.first
                val tamAjustado = resultado.second
                val altura = lineas.size * tamAjustado * 1.15f
                tam = tamAjustado
                if (inferior - altura >= TOP_SEGURO * factor || tam <= 48f) break
                tam -= 4f
            }
            val altura = lineas.size * tam * 1.15f
            if (inferior - altura < TOP_SEGURO * factor) {
                inferior = TOP_SEGURO * factor + altura
                aviso = "Hay poco espacio arriba de tu cabeza; el título puede tocarte."
            }
        }
        var subY = if (posicion != null) posicion.barbillaY + 0.12f * posicion.altoCara else 1150f * factor
        subY = min(subY, altoFinal - BASE_SEGURA * factor - tamSub * 1.2f)
        return Colocacion(lineas, tam, max(inferior, 0f), subY, aviso)
    }
}
