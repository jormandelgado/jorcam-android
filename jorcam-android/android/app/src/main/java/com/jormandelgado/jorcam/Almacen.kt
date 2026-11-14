package com.jormandelgado.jorcam

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/** Guarda y abre los videos en Películas/JorCam. */
object Almacen {
    fun guardarVideo(ctx: Context, origen: Uri, nombre: String): Uri? {
        val resolver = ctx.contentResolver
        val valores = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, nombre)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/JorCam")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val destino = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, valores) ?: return null
        try {
            resolver.openInputStream(origen).use { entrada ->
                resolver.openOutputStream(destino).use { salida ->
                    if (entrada == null || salida == null) throw IllegalStateException("No se pudo abrir el archivo")
                    entrada.copyTo(salida)
                }
            }
            valores.clear()
            valores.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(destino, valores, null, null)
            return destino
        } catch (e: Exception) {
            resolver.delete(destino, null, null)
            return null
        }
    }

    fun duracionMs(ctx: Context, uri: Uri): Long {
        val lector = MediaMetadataRetriever()
        return try {
            lector.setDataSource(ctx, uri)
            lector.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            lector.release()
        }
    }

    fun reproducir(ctx: Context, uri: Uri) {
        val intento = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(intento)
    }

    fun abrirVideos(ctx: Context) {
        val intento = Intent(Intent.ACTION_VIEW)
            .setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "vnd.android.cursor.dir/video")
        ctx.startActivity(intento)
    }
}
