package com.jormandelgado.jorcam

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Modelos de voz de Whisper: se bajan una sola vez (con reanudación si se corta internet) y se guardan en el teléfono. */
object Modelo {
    val OPCIONES: LinkedHashMap<String, String> = linkedMapOf(
        "Rápido, menos preciso (tiny, 75 MB)" to "ggml-tiny.bin",
        "Equilibrado (base, 142 MB)" to "ggml-base.bin",
        "Más preciso y más lento (small, 466 MB)" to "ggml-small.bin"
    )
    const val PREDETERMINADO = "Equilibrado (base, 142 MB)"
    private const val URL_BASE = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    private fun carpeta(ctx: Context): File {
        val c = File(ctx.getExternalFilesDir(null), "modelos")
        c.mkdirs()
        return c
    }

    fun archivo(ctx: Context, nombre: String): File = File(carpeta(ctx), nombre)
    private fun marca(ctx: Context, nombre: String): File = File(carpeta(ctx), "$nombre.ok")
    fun listo(ctx: Context, nombre: String): Boolean = archivo(ctx, nombre).exists() && marca(ctx, nombre).exists()

    private fun abrir(direccion: String, desde: Long): HttpURLConnection {
        var actual = direccion
        for (i in 0 until 6) {
            val c = URL(actual).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            c.setRequestProperty("User-Agent", "JorCam")
            if (desde > 0) c.setRequestProperty("Range", "bytes=$desde-")
            val codigo = c.responseCode
            if (codigo in 300..399) {
                val siguiente = c.getHeaderField("Location") ?: throw IOException("Redirección vacía")
                actual = URL(URL(actual), siguiente).toString()
                c.disconnect()
                continue
            }
            return c
        }
        throw IOException("Demasiadas redirecciones")
    }

    /** Descarga el modelo (hasta 40 intentos, retomando donde quedó). Devuelve true si quedó completo. */
    fun descargar(ctx: Context, nombre: String, avance: (Int) -> Unit, aviso: (String) -> Unit): Boolean {
        if (listo(ctx, nombre)) return true
        val parcial = File(carpeta(ctx), "$nombre.part")
        var intentos = 0
        var ultimo = -1
        while (intentos < 40) {
            intentos++
            try {
                val tenido = if (parcial.exists()) parcial.length() else 0L
                val conexion = abrir(URL_BASE + nombre, tenido)
                val codigo = conexion.responseCode
                var total = -1L
                var anexar = false
                if (codigo == 206) {
                    total = conexion.getHeaderField("Content-Range")?.substringAfter("/")?.toLongOrNull() ?: -1L
                    anexar = true
                } else if (codigo == 200) {
                    total = conexion.contentLengthLong
                    if (tenido > 0) parcial.delete()
                } else if (codigo == 416 && tenido > 0) {
                    terminar(ctx, nombre, parcial)
                    return true
                } else {
                    throw IOException("Respuesta $codigo del servidor")
                }
                var descargado = if (anexar) tenido else 0L
                conexion.inputStream.use { entrada ->
                    FileOutputStream(parcial, anexar).use { salida ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val n = entrada.read(buffer)
                            if (n < 0) break
                            salida.write(buffer, 0, n)
                            descargado += n
                            if (total > 0) {
                                val p = (descargado * 100 / total).toInt()
                                if (p / 10 * 10 > ultimo && p < 100) {
                                    ultimo = p / 10 * 10
                                    avance(ultimo)
                                }
                            }
                        }
                    }
                }
                if (total <= 0 || descargado >= total) {
                    terminar(ctx, nombre, parcial)
                    return true
                }
            } catch (e: Exception) {
                if (intentos == 1 || intentos % 5 == 0) aviso("Se cortó la descarga; reintentando (intento $intentos de 40).")
                try { Thread.sleep(5_000) } catch (i: InterruptedException) { return false }
            }
        }
        return false
    }

    private fun terminar(ctx: Context, nombre: String, parcial: File) {
        val destino = archivo(ctx, nombre)
        if (destino.exists()) destino.delete()
        parcial.renameTo(destino)
        marca(ctx, nombre).writeText("ok")
    }
}
