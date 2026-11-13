package com.jormandelgado.jorcam

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Detección de voz y de silencios. Es el mismo algoritmo de la versión de PC: el umbral depende del
 * nivel de tu voz, los ruidos cortos y aislados se ignoran y se deja "aire" antes y después de cada frase.
 */
object Silencios {
    data class Tramo(val inicioMs: Long, val finMs: Long)

    private val SENSIBILIDAD = mapOf("Baja" to 28.0, "Normal" to 20.0, "Alta" to 16.0, "Máxima" to 12.0)
    private const val SILENCIO_MINIMO = 0.40
    private const val MIN_VOZ = 0.25
    private const val EXT_ANTES = 0.08
    private const val EXT_DESPUES = 0.10
    private const val MIN_CORTE_MS = 120L

    /** Devuelve los tramos CON voz (los que se conservan), o null si no se pudo separar la voz del ruido. */
    fun detectarVoz(
        ctx: Context, uri: Uri, duracionMs: Long, sens: String,
        aireAntesMs: Int, aireDespuesMs: Int, progreso: (Int) -> Unit
    ): List<Tramo>? {
        val db = niveles(ctx, uri, duracionMs, progreso) ?: return null
        if (db.size < 100) return null
        val ordenado = db.copyOf()
        ordenado.sort()
        val piso = percentil(ordenado, 10.0).toDouble()
        val voz = percentil(ordenado, 90.0).toDouble()
        if (voz - piso < 6) return null
        val k = SENSIBILIDAD[sens] ?: 20.0
        val alto = max(piso + 8.0, voz - k)
        val bajo = max(piso + 4.0, alto - 8.0)

        // 1. Tramos por encima del umbral
        val tramos = ArrayList<IntArray>()
        var i = 0
        while (i < db.size) {
            if (db[i] > alto) {
                var j = i
                while (j < db.size && db[j] > alto) j++
                tramos.add(intArrayOf(i, j))
                i = j
            } else {
                i++
            }
        }
        // 2. Las pausas cortas dentro de una frase no se cortan
        val huecos = (SILENCIO_MINIMO * 100).toInt()
        val unidos = ArrayList<IntArray>()
        for (t in tramos) {
            if (unidos.isNotEmpty() && t[0] - unidos[unidos.size - 1][1] < huecos) {
                unidos[unidos.size - 1][1] = t[1]
            } else {
                unidos.add(intArrayOf(t[0], t[1]))
            }
        }
        // 3. Los sonidos muy cortos y aislados son ruido; los bordes se extienden un poco para no comer sílabas
        val minVoz = (MIN_VOZ * 100).toInt()
        val extAntes = (EXT_ANTES * 100).toInt()
        val extDespues = (EXT_DESPUES * 100).toInt()
        val resultado = ArrayList<Tramo>()
        for (t in unidos) {
            if (t[1] - t[0] < minVoz) continue
            var ini = t[0]
            while (ini > 0 && t[0] - ini < extAntes && db[ini - 1] > bajo) ini--
            var fin = t[1]
            while (fin < db.size && fin - t[1] < extDespues && db[fin] > bajo) fin++
            val a = max(0L, ini * 10L - aireAntesMs)
            val b = min(duracionMs, fin * 10L + aireDespuesMs)
            if (resultado.isNotEmpty() && a - resultado[resultado.size - 1].finMs < MIN_CORTE_MS) {
                val ultimo = resultado[resultado.size - 1]
                resultado[resultado.size - 1] = Tramo(ultimo.inicioMs, max(ultimo.finMs, b))
            } else {
                resultado.add(Tramo(a, b))
            }
        }
        return if (resultado.isEmpty()) null else resultado
    }

    fun hayCortes(tramos: List<Tramo>, duracionMs: Long): Boolean =
        tramos.size > 1 || tramos.first().inicioMs > MIN_CORTE_MS || tramos.last().finMs < duracionMs - MIN_CORTE_MS

    private fun percentil(orden: FloatArray, p: Double): Float {
        val pos = p / 100.0 * (orden.size - 1)
        val bajo = pos.toInt()
        val alto = min(bajo + 1, orden.size - 1)
        val f = (pos - bajo).toFloat()
        return orden[bajo] * (1 - f) + orden[alto] * f
    }

    private fun coeficienteFiltro(tasa: Int): Double {
        val rc = 1.0 / (2 * PI * 120.0)
        val dt = 1.0 / tasa
        return rc / (rc + dt)
    }

    /** Nivel de sonido en dB cada 10 milisegundos (con un filtro que ignora los graves, como en la PC). */
    private fun niveles(ctx: Context, uri: Uri, duracionMs: Long, progreso: (Int) -> Unit): FloatArray? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ctx, uri, null)
        } catch (e: Exception) {
            extractor.release()
            return null
        }
        var pista = -1
        var formato: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                pista = i
                formato = f
                break
            }
        }
        if (pista < 0 || formato == null) {
            extractor.release()
            return null
        }
        extractor.selectTrack(pista)
        val mimeAudio = formato.getString(MediaFormat.KEY_MIME) ?: return null
        val codec = MediaCodec.createDecoderByType(mimeAudio)
        codec.configure(formato, null, null, 0)
        codec.start()

        val info = MediaCodec.BufferInfo()
        var tasa = formato.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var canales = formato.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var ventana = tasa / 100
        var a = coeficienteFiltro(tasa)
        var acumulado = 0.0
        var cuenta = 0
        var xAnterior = 0.0
        var yAnterior = 0.0
        val niveles = ArrayList<Float>()
        var finEntrada = false
        var finSalida = false
        var ultimoPorcentaje = -1

        while (!finSalida) {
            if (!finEntrada) {
                val indice = codec.dequeueInputBuffer(10_000)
                if (indice >= 0) {
                    val buffer = codec.getInputBuffer(indice)!!
                    val leidos = extractor.readSampleData(buffer, 0)
                    if (leidos < 0) {
                        codec.queueInputBuffer(indice, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        finEntrada = true
                    } else {
                        codec.queueInputBuffer(indice, 0, leidos, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val salida = codec.dequeueOutputBuffer(info, 10_000)
            if (salida >= 0) {
                val buffer = codec.getOutputBuffer(salida)!!
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val cortos = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val cuadros = cortos.remaining() / max(canales, 1)
                for (n in 0 until cuadros) {
                    var suma = 0.0
                    for (c in 0 until canales) suma += cortos.get().toDouble()
                    val x = suma / canales
                    val y = a * (yAnterior + x - xAnterior)
                    xAnterior = x
                    yAnterior = y
                    acumulado += y * y
                    cuenta++
                    if (cuenta >= ventana) {
                        val rms = sqrt(acumulado / cuenta) / 32768.0
                        niveles.add((20 * log10(rms + 1e-9)).toFloat())
                        acumulado = 0.0
                        cuenta = 0
                    }
                }
                codec.releaseOutputBuffer(salida, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) finSalida = true
                if (duracionMs > 0) {
                    val porcentaje = ((info.presentationTimeUs / 1000.0) / duracionMs * 100).toInt().coerceIn(0, 100)
                    if (porcentaje != ultimoPorcentaje) {
                        ultimoPorcentaje = porcentaje
                        progreso(porcentaje)
                    }
                }
            } else if (salida == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val nuevo = codec.outputFormat
                tasa = nuevo.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                canales = nuevo.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                ventana = tasa / 100
                a = coeficienteFiltro(tasa)
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return niveles.toFloatArray()
    }
}
