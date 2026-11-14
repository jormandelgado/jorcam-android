import com.google.common.collect.ImmutableList
package com.jormandelgado.jorcam

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Effects
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

/**
 * Corta el video para conservar solo los tramos con voz y los une de forma continua.
 * Usa Media3 Transformer, que vuelve a codificar con el codificador de video del teléfono.
 * IMPORTANTE: debe llamarse desde el hilo principal.
 */
@OptIn(UnstableApi::class)
object Recorte {
    fun recortar(
        ctx: Context, entrada: Uri, tramos: List<Silencios.Tramo>, texto: TextoSobreVideo?, salida: File,
        progreso: (Int) -> Unit, alTerminar: (Boolean, String?) -> Unit
    ) {
        val elementos = tramos.filter { it.finMs - it.inicioMs >= 200 }.map { t ->
            val recorte = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(t.inicioMs)
                .setEndPositionMs(t.finMs)
                .build()
            val item = MediaItem.Builder().setUri(entrada).setClippingConfiguration(recorte).build()
            EditedMediaItem.Builder(item).build()
        }
        if (elementos.isEmpty()) {
            alTerminar(false, "no hay voz que conservar")
            return
        }
        val constructor = Composition.Builder(listOf(EditedMediaItemSequence(elementos)))
        if (texto != null) {
            // El texto se coloca sobre el video ya recortado (los tiempos son los del video final).
            constructor.setEffects(Effects(emptyList(), listOf(OverlayEffect(ImmutableList.of(texto)))))
        }
        val composicion = constructor.build()
        val codificador = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(10_000_000).build())
            .build()
        val manejador = Handler(Looper.getMainLooper())
        val titular = ProgressHolder()
        var terminado = false

        val transformer = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(codificador)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    terminado = true
                    alTerminar(true, null)
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    terminado = true
                    alTerminar(false, exportException.message ?: "error desconocido")
                }
            })
            .build()

        val sondeo = object : Runnable {
            override fun run() {
                if (terminado) return
                if (transformer.getProgress(titular) != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    progreso(titular.progress)
                }
                manejador.postDelayed(this, 500)
            }
        }
        if (salida.exists()) salida.delete()
        transformer.start(composicion, salida.absolutePath)
        manejador.postDelayed(sondeo, 500)
    }
}
