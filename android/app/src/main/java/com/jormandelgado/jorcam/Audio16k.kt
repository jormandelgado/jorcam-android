package com.jormandelgado.jorcam

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.max

/** Saca el audio del video y lo deja en 16 kHz, un solo canal, que es lo que necesita Whisper. */
object Audio16k {
    fun decodificar(ctx: Context, uri: Uri, duracionMs: Long, progreso: (Int) -> Unit): ShortArray? {
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
        var razon = tasa / 16000.0

        var salida = ShortArray(1 shl 20)
        var cuenta = 0
        var entradas = 0L
        var suma = 0.0
        var n = 0
        var k = 1L
        var limite = razon
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
            val indiceSalida = codec.dequeueOutputBuffer(info, 10_000)
            if (indiceSalida >= 0) {
                val buffer = codec.getOutputBuffer(indiceSalida)!!
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val cortos = buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val cuadros = cortos.remaining() / max(canales, 1)
                for (c in 0 until cuadros) {
                    var mezcla = 0.0
                    for (ch in 0 until canales) mezcla += cortos.get().toDouble()
                    suma += mezcla / canales
                    n++
                    entradas++
                    if (entradas >= limite) {
                        val valor = (suma / n).toInt().coerceIn(-32768, 32767)
                        if (cuenta == salida.size) salida = salida.copyOf(salida.size * 2)
                        salida[cuenta++] = valor.toShort()
                        suma = 0.0
                        n = 0
                        k++
                        limite = k * razon
                    }
                }
                codec.releaseOutputBuffer(indiceSalida, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) finSalida = true
                if (duracionMs > 0) {
                    val p = ((info.presentationTimeUs / 1000.0) / duracionMs * 100).toInt().coerceIn(0, 100)
                    if (p != ultimoPorcentaje) {
                        ultimoPorcentaje = p
                        progreso(p)
                    }
                }
            } else if (indiceSalida == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val nuevo = codec.outputFormat
                tasa = nuevo.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                canales = nuevo.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                razon = tasa / 16000.0
                k = 1L
                entradas = 0L
                limite = razon
            }
        }
        codec.stop()
        codec.release()
        extractor.release()
        return salida.copyOf(cuenta)
    }
}
