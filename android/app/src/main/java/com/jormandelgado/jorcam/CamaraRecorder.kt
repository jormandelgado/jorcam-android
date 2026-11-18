package com.jormandelgado.jorcam

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioDeviceInfo
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import java.io.File

/**
 * Cámara en vivo (visor) y grabación de video vertical con Camera2 + MediaRecorder.
 * - Pide 30 cuadros por segundo fijos cuando la cámara lo permite.
 * - Graba en 1920x1080 con la marca de rotación vertical (así es como todos los teléfonos guardan video vertical).
 * - Permite elegir el micrófono (incluido uno externo).
 * Todas las funciones de aviso (alError, alIniciar...) se llaman desde un hilo de fondo.
 */
class CamaraRecorder(private val ctx: Context, private val superficie: SurfaceView) {
    private val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var hilo: HandlerThread? = null
    private var manejador: Handler? = null
    private var dispositivo: CameraDevice? = null
    private var sesion: CameraCaptureSession? = null
    private var grabador: MediaRecorder? = null
    private var idActual: String? = null
    private var caracteristicas: CameraCharacteristics? = null
    private var archivo: File? = null

    var tamanoVista: Size = Size(1920, 1080)
        private set
    var tamanoVideo: Size = Size(1920, 1080)
        private set
    var frontal: Boolean = false
        private set
    var grabando: Boolean = false
        private set
    var pausado: Boolean = false
        private set

    /** La cámara está abierta y mostrando la vista (sirve para saber cuándo hay imagen que mandar a la PC). */
    val abierta: Boolean
        get() = dispositivo != null && sesion != null

    /** Giro del sensor (casi siempre 90 en la trasera y 270 en la frontal): la PC lo usa para enderezar la vista previa. */
    val orientacionSensor: Int
        get() = caracteristicas?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

    var alError: ((String) -> Unit)? = null
    var alVistaLista: ((Size) -> Unit)? = null
    var alAbrirCamara: (() -> Unit)? = null
    var alIniciar: (() -> Unit)? = null

    // ---------- Abrir la cámara ----------

    data class Info(val id: String, val frontal: Boolean, val ancho: Int, val alto: Int)

    private var anchoMaximo = 1920

    /** Todas las cámaras del teléfono, la trasera principal primero, con el tamaño de video que ofrece cada una. */
    fun listar(): List<Info> {
        val encontradas = ArrayList<Pair<Info, Long>>()
        for (id in manager.cameraIdList) {
            try {
                val c = manager.getCameraCharacteristics(id)
                val cara = c.get(CameraCharacteristics.LENS_FACING)
                if (cara != CameraCharacteristics.LENS_FACING_BACK && cara != CameraCharacteristics.LENS_FACING_FRONT) continue
                val mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: continue
                val videos = mapa.getOutputSizes(MediaRecorder::class.java)?.toList() ?: continue
                val mejor = videos.filter { it.width * 9 == it.height * 16 && it.width <= 1920 }.maxByOrNull { it.width } ?: continue
                val sensor = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                val area = if (sensor != null) sensor.width.toLong() * sensor.height else 0L
                encontradas.add(Pair(Info(id, cara == CameraCharacteristics.LENS_FACING_FRONT, mejor.width, mejor.height), area))
            } catch (e: Exception) {
            }
        }
        val traseras = encontradas.filter { !it.first.frontal }.sortedByDescending { it.second }.map { it.first }
        val frontales = encontradas.filter { it.first.frontal }.sortedByDescending { it.second }.map { it.first }
        return traseras + frontales
    }

    /** Abre una cámara concreta (la que pide la computadora), con un ancho máximo de video. */
    fun abrirId(id: String, anchoMaximo: Int) {
        cerrarDispositivo()
        this.anchoMaximo = anchoMaximo
        if (hilo == null) {
            hilo = HandlerThread("jorcam-camara").also { it.start() }
            manejador = Handler(hilo!!.looper)
        }
        val c = manager.getCameraCharacteristics(id)
        frontal = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        idActual = id
        caracteristicas = c
        elegirTamanos(c)
        alVistaLista?.invoke(tamanoVista)
    }

    fun abrir(frontal: Boolean) {
        cerrarDispositivo()
        anchoMaximo = 1920
        this.frontal = frontal
        if (hilo == null) {
            hilo = HandlerThread("jorcam-camara").also { it.start() }
            manejador = Handler(hilo!!.looper)
        }
        val id = elegirId(frontal)
        if (id == null) {
            alError?.invoke("No encuentro esa cámara en el teléfono.")
            return
        }
        idActual = id
        val c = manager.getCameraCharacteristics(id)
        caracteristicas = c
        elegirTamanos(c)
        // La pantalla ajusta el tamaño del visor y luego llama a continuarApertura().
        alVistaLista?.invoke(tamanoVista)
    }

    private fun elegirId(frontal: Boolean): String? {
        val objetivo = if (frontal) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        var mejor: String? = null
        var area = -1L
        for (id in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(id)
            if (c.get(CameraCharacteristics.LENS_FACING) != objetivo) continue
            val tam = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE) ?: continue
            val a = tam.width.toLong() * tam.height
            if (a > area) {
                area = a
                mejor = id
            }
        }
        return mejor
    }

    private fun elegirTamanos(c: CameraCharacteristics) {
        val mapa = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
        val tope = anchoMaximo
        val videos = mapa.getOutputSizes(MediaRecorder::class.java)?.toList() ?: emptyList()
        tamanoVideo = videos.filter { it.width * 9 == it.height * 16 && it.width <= tope }.maxByOrNull { it.width }
            ?: videos.filter { it.width <= tope }.maxByOrNull { it.width * it.height }
            ?: Size(1280, 720)
        val vistas = mapa.getOutputSizes(SurfaceHolder::class.java)?.toList() ?: emptyList()
        tamanoVista = vistas.filter { it.width * tamanoVideo.height == it.height * tamanoVideo.width && it.width <= 1920 }
            .maxByOrNull { it.width }
            ?: vistas.filter { it.width <= 1920 }.maxByOrNull { it.width * it.height }
            ?: tamanoVideo
    }

    @SuppressLint("MissingPermission")
    fun continuarApertura() {
        val id = idActual ?: return
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    dispositivo = camera
                    crearSesionVista()
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    dispositivo = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    dispositivo = null
                    alError?.invoke("Error de la cámara (código $error).")
                }
            }, manejador)
        } catch (e: Exception) {
            alError?.invoke("No se pudo abrir la cámara: ${e.message}")
        }
    }

    // ---------- Visor en vivo ----------

    private fun configurar(b: CaptureRequest.Builder) {
        val c = caracteristicas ?: return
        val modosEnfoque = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (modosEnfoque != null && modosEnfoque.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        }
        // 30 cuadros por segundo fijos si se puede (evita que la cámara baje los cuadros con poca luz).
        val rangos = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val elegido = rangos?.filter { it.upper == 30 }?.maxByOrNull { it.lower } ?: rangos?.maxByOrNull { it.upper }
        if (elegido != null) b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, elegido)
    }

    private fun crearSesionVista() {
        val d = dispositivo ?: return
        val vista = superficie.holder.surface
        if (!vista.isValid) {
            alError?.invoke("La pantalla de la cámara todavía no está lista.")
            return
        }
        try {
            d.createCaptureSession(listOf(vista), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    sesion = s
                    try {
                        val r = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                        r.addTarget(vista)
                        configurar(r)
                        s.setRepeatingRequest(r.build(), null, manejador)
                        alAbrirCamara?.invoke()
                    } catch (e: Exception) {
                        alError?.invoke("No se pudo mostrar la cámara: ${e.message}")
                    }
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    alError?.invoke("No se pudo iniciar la vista de la cámara.")
                }
            }, manejador)
        } catch (e: Exception) {
            alError?.invoke("No se pudo iniciar la vista de la cámara: ${e.message}")
        }
    }

    // ---------- Grabación ----------

    /** Códec con el que se grabó el último video ("HEVC" o "H.264"). */
    var codecUsado: String = "H.264"
        private set

    /**
     * Prepara el grabador. Para la PC (que luego vuelve a codificar el video) se graba con la máxima calidad:
     * HEVC a 10 Mbps si el teléfono lo permite (misma nitidez que H.264 con mucho menos peso) y, si no, H.264 a 16 Mbps.
     * En el modo directo se mantiene H.264 a 5 Mbps.
     */
    private fun prepararGrabador(destino: File, microfono: AudioDeviceInfo?, paraPC: Boolean): MediaRecorder {
        val intentos = if (paraPC) {
            listOf(Pair(MediaRecorder.VideoEncoder.HEVC, 10_000_000), Pair(MediaRecorder.VideoEncoder.H264, 16_000_000))
        } else {
            listOf(Pair(MediaRecorder.VideoEncoder.H264, 5_000_000))
        }
        var ultimoError: Exception? = null
        for ((codec, bitrate) in intentos) {
            val g = MediaRecorder()
            try {
                g.setAudioSource(MediaRecorder.AudioSource.MIC)
                g.setVideoSource(MediaRecorder.VideoSource.SURFACE)
                g.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                g.setOutputFile(destino.absolutePath)
                g.setVideoEncodingBitRate(bitrate)
                g.setVideoFrameRate(30)
                g.setVideoSize(tamanoVideo.width, tamanoVideo.height)
                g.setVideoEncoder(codec)
                g.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                g.setAudioEncodingBitRate(128_000)
                g.setAudioSamplingRate(44_100)
                g.setOrientationHint(caracteristicas?.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90)
                if (microfono != null) g.setPreferredDevice(microfono)
                g.prepare()
                codecUsado = if (codec == MediaRecorder.VideoEncoder.HEVC) "HEVC" else "H.264"
                return g
            } catch (e: Exception) {
                ultimoError = e
                try { g.reset() } catch (x: Exception) { }
                g.release()
            }
        }
        throw ultimoError ?: IllegalStateException("No se pudo preparar el grabador.")
    }

    fun empezar(destino: File, microfono: AudioDeviceInfo?, paraPC: Boolean = false) {
        val d = dispositivo ?: throw IllegalStateException("La cámara no está lista.")
        archivo = destino
        sesion?.close()
        sesion = null

        val g = prepararGrabador(destino, microfono, paraPC)
        grabador = g

        val vista = superficie.holder.surface
        val destinoGrabador = g.surface
        d.createCaptureSession(listOf(vista, destinoGrabador), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                sesion = s
                try {
                    val r = d.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    r.addTarget(vista)
                    r.addTarget(destinoGrabador)
                    configurar(r)
                    s.setRepeatingRequest(r.build(), null, manejador)
                    g.start()
                    grabando = true
                    pausado = false
                    alIniciar?.invoke()
                } catch (e: Exception) {
                    alError?.invoke("No se pudo empezar a grabar: ${e.message}")
                }
            }

            override fun onConfigureFailed(s: CameraCaptureSession) {
                alError?.invoke("No se pudo preparar la grabación.")
            }
        }, manejador)
    }

    fun pausar() {
        if (grabando && !pausado) {
            try { grabador?.pause(); pausado = true } catch (e: Exception) { alError?.invoke("No se pudo pausar.") }
        }
    }

    fun reanudar() {
        if (grabando && pausado) {
            try { grabador?.resume(); pausado = false } catch (e: Exception) { alError?.invoke("No se pudo reanudar.") }
        }
    }

    /** Detiene la grabación y devuelve el archivo, o null si fue demasiado corta. */
    fun detener(): File? {
        if (!grabando) return null
        try { sesion?.stopRepeating() } catch (e: Exception) { }
        try { sesion?.abortCaptures() } catch (e: Exception) { }
        var valido = true
        try { grabador?.stop() } catch (e: RuntimeException) { valido = false }
        try { grabador?.reset() } catch (e: Exception) { }
        grabador?.release()
        grabador = null
        grabando = false
        pausado = false
        val resultado = archivo
        archivo = null
        sesion?.close()
        sesion = null
        crearSesionVista()
        if (!valido) {
            resultado?.delete()
            return null
        }
        return resultado
    }

    // ---------- Cerrar ----------

    private fun cerrarDispositivo() {
        try { sesion?.close() } catch (e: Exception) { }
        sesion = null
        try { dispositivo?.close() } catch (e: Exception) { }
        dispositivo = null
    }

    fun cerrar() {
        if (grabando) {
            try { grabador?.stop() } catch (e: Exception) { }
            grabador?.release()
            grabador = null
            grabando = false
            pausado = false
        }
        cerrarDispositivo()
        hilo?.quitSafely()
        hilo = null
        manejador = null
    }
}
