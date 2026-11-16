package com.jormandelgado.jorcam

import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Modo "conectar con la computadora". La app hace lo mismo que la página del teléfono de JorCam de PC:
 *  - le dice a la PC qué cámaras tiene;
 *  - escucha sus órdenes (abrir una cámara, grabar, pausar, reanudar, detener);
 *  - graba el video por su cuenta y, al detener, lo manda a la PC.
 * El audio definitivo lo graba la PC con su micrófono; el del teléfono es solo la referencia para sincronizar.
 * Durante una pausa el teléfono sigue grabando (igual que la página): la PC recorta esos intervalos.
 */
class ModoPC(
    private val camara: CamaraRecorder,
    private val carpeta: File,
    private val decir: (String) -> Unit,
    private val alCambiarEstado: (Boolean) -> Unit
) {
    @Volatile private var activo = false
    @Volatile var conectado = false
        private set
    @Volatile var grabando = false
        private set

    private var base = ""
    private var codigo = ""
    private var hilo: Thread? = null
    @Volatile private var conexionOrden: HttpURLConnection? = null
    private var lista: List<CamaraRecorder.Info> = emptyList()
    @Volatile private var indice = -1
    @Volatile private var anchoPedido = 1920
    @Volatile private var inicioMs = 0L

    private fun etiqueta(i: CamaraRecorder.Info) = "camera ${i.id}, facing ${if (i.frontal) "front" else "back"}"

    // ------------------------------------------------------------------ conexión

    fun conectar(host: String, puerto: Int, claveWifi: String, alTerminar: (Boolean, String) -> Unit) {
        desconectar()
        base = "http://$host:$puerto"
        codigo = claveWifi
        activo = true
        val nuevo = Thread {
            try {
                val c = abrir("/hola", "GET", 6000)
                val respuesta = c.responseCode
                if (respuesta == 403) {
                    activo = false
                    alTerminar(false, "El código no es correcto. Mira el código que muestra la computadora.")
                    return@Thread
                }
                if (respuesta != 200) {
                    activo = false
                    alTerminar(false, "La computadora respondió con un error ($respuesta).")
                    return@Thread
                }
                c.inputStream.close()
                c.disconnect()
            } catch (e: Exception) {
                activo = false
                alTerminar(false, "No pude conectar con la computadora. Revisa el cable o la red, y que JorCam esté abierto en la PC.")
                return@Thread
            }
            lista = try { camara.listar() } catch (e: Exception) { emptyList() }
            if (lista.isEmpty()) {
                activo = false
                alTerminar(false, "No encontré cámaras en el teléfono.")
                return@Thread
            }
            conectado = true
            alCambiarEstado(true)
            alTerminar(true, "Conectado a la computadora. Ahora la controlas desde la PC.")
            val camaras = JSONArray()
            for (c in lista) {
                camaras.put(JSONObject().put("etiqueta", etiqueta(c)).put("ancho", c.ancho).put("alto", c.alto).put("fps", 30))
            }
            evento(JSONObject().put("tipo", "camaras").put("lista", camaras))
            bucle()
        }
        nuevo.name = "jorcam-pc"
        hilo = nuevo
        nuevo.start()
    }

    fun desconectar() {
        val estabaConectado = conectado
        activo = false
        conectado = false
        try { conexionOrden?.disconnect() } catch (e: Exception) { }
        hilo?.interrupt()
        hilo = null
        if (grabando) {
            grabando = false
            try { camara.detener()?.delete() } catch (e: Exception) { }
        }
        if (estabaConectado) alCambiarEstado(false)
    }

    private fun perder(mensaje: String) {
        if (!activo) return
        decir(mensaje)
        desconectar()
    }

    // ------------------------------------------------------------------ red

    private fun abrir(ruta: String, metodo: String, lectura: Int): HttpURLConnection {
        val c = URL(base + ruta).openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.connectTimeout = 6000
        c.readTimeout = lectura
        c.useCaches = false
        if (codigo.isNotEmpty()) c.setRequestProperty("X-JorCam-Codigo", codigo)
        return c
    }

    private fun evento(datos: JSONObject) {
        try {
            datos.put("cliente", "telefono")
            val bytes = datos.toString().toByteArray(Charsets.UTF_8)
            val c = abrir("/evento", "POST", 10_000)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            c.responseCode
            c.disconnect()
        } catch (e: Exception) {
        }
    }

    private fun pedirOrden(): JSONObject? {
        val c = abrir("/orden?c=telefono", "GET", 25_000)
        conexionOrden = c
        return try {
            if (c.responseCode != 200) return null
            val texto = c.inputStream.bufferedReader().use { it.readText() }
            JSONObject(texto)
        } finally {
            c.disconnect()
            conexionOrden = null
        }
    }

    private fun subirVideo(archivo: File): Boolean {
        return try {
            val c = abrir("/chunk", "POST", 180_000)
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/octet-stream")
            c.setFixedLengthStreamingMode(archivo.length())
            archivo.inputStream().use { entrada -> c.outputStream.use { salida -> entrada.copyTo(salida, 64 * 1024) } }
            val ok = c.responseCode == 200
            c.disconnect()
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ órdenes de la PC

    private fun bucle() {
        var fallos = 0
        while (activo) {
            val orden: JSONObject? = try { pedirOrden() } catch (e: Exception) { null }
            if (!activo) break
            if (orden == null) {
                fallos++
                if (fallos >= 4) {
                    perder("Se perdió la conexión con la computadora.")
                    break
                }
                try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
                continue
            }
            fallos = 0
            try {
                manejar(orden)
            } catch (e: Exception) {
                evento(JSONObject().put("tipo", "error").put("mensaje", "Teléfono: ${e.message}"))
            }
        }
    }

    private fun segundos(): Double = (SystemClock.elapsedRealtime() - inicioMs) / 1000.0

    private fun manejar(o: JSONObject) {
        when (o.optString("orden")) {
            "camara" -> if (!grabando) {
                val i = o.optInt("indice", 0)
                val info = lista.getOrNull(i) ?: return
                indice = i
                anchoPedido = o.optInt("ancho", 1920)
                camara.abrirId(info.id, anchoPedido)
            }
            "cerrar" -> if (!grabando) camara.cerrar()
            "grabar" -> {
                val archivo = File(carpeta, "pc_${System.currentTimeMillis()}.mp4")
                try {
                    camara.empezar(archivo, null) // micrófono del teléfono: solo sirve de referencia
                } catch (e: Exception) {
                    evento(JSONObject().put("tipo", "error").put("mensaje", "No se pudo empezar a grabar: ${e.message}"))
                }
            }
            "pausar" -> if (grabando) evento(JSONObject().put("tipo", "pausa").put("t", segundos()))
            "reanudar" -> if (grabando) evento(JSONObject().put("tipo", "reanuda").put("t", segundos()))
            "detener" -> if (grabando) {
                val duracion = segundos()
                val archivo = cerrarGrabacion()
                enviarGrabacion(archivo, duracion, o.optBoolean("descartar", false))
            }
        }
    }

    /** Detiene la grabación del teléfono y devuelve el archivo (esto debe hacerse antes de cerrar la cámara). */
    private fun cerrarGrabacion(): File? {
        grabando = false
        return camara.detener()
    }

    private fun enviarGrabacion(archivo: File?, duracion: Double, descartar: Boolean) {
        if (archivo != null && !descartar) {
            decir("Enviando el video a la computadora.")
            if (!subirVideo(archivo)) decir("No se pudo enviar el video completo a la computadora.")
        }
        archivo?.delete()
        evento(JSONObject().put("tipo", "detenido").put("duracion", duracion).put("fps_vista", 0.0))
    }

    // ------------------------------------------------------------------ avisos de la cámara

    fun alCamaraAbierta() {
        if (!conectado) return
        val info = lista.getOrNull(indice) ?: return
        Thread {
            evento(
                JSONObject().put("tipo", "camara").put("indice", indice).put("etiqueta", etiqueta(info))
                    .put("ancho", camara.tamanoVideo.width).put("alto", camara.tamanoVideo.height).put("fps", 30)
            )
        }.start()
    }

    fun alIniciado() {
        inicioMs = SystemClock.elapsedRealtime()
        grabando = true
        Thread { evento(JSONObject().put("tipo", "iniciado").put("mime", "video/mp4")) }.start()
    }

    fun alError(mensaje: String) {
        if (!conectado) return
        Thread { evento(JSONObject().put("tipo", "error").put("mensaje", mensaje)) }.start()
    }

    /** La app pasa a segundo plano: si estaba grabando, se detiene lo grabado y se manda a la PC (la cámara se va a cerrar). */
    fun alSalirDeApp() {
        if (!conectado || !grabando) return
        val duracion = segundos()
        val archivo = cerrarGrabacion()
        Thread { enviarGrabacion(archivo, duracion, false) }.start()
    }

    /** La app vuelve a primer plano: se vuelve a abrir la cámara que la PC tenía elegida. */
    fun reabrirCamara() {
        if (!conectado || grabando) return
        val info = lista.getOrNull(indice) ?: return
        camara.abrirId(info.id, anchoPedido)
    }
}
