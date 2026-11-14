package com.jormandelgado.jorcam

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pantalla principal de JorCam para Android (modo bolsillo / grabación directa en el teléfono).
 * Visor de la cámara a pantalla completa, controles accesibles y avisos por voz de cada acción.
 */
class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private enum class Fase { LISTO, GRABANDO, PAUSADO, PROCESANDO }

    private lateinit var ajustes: Ajustes
    private lateinit var anunciador: Anunciador
    private lateinit var camara: CamaraRecorder
    private lateinit var superficie: SurfaceView
    private lateinit var panel: ScrollView
    private lateinit var registro: TextView
    private lateinit var barra: ProgressBar
    private lateinit var btnGrabar: Button
    private lateinit var btnPausar: Button
    private lateinit var btnDetener: Button
    private lateinit var btnDescartar: Button
    private lateinit var btnCamara: Button
    private lateinit var btnCargar: Button
    private lateinit var btnReproducir: Button
    private lateinit var btnCarpeta: Button
    private lateinit var btnLicencia: Button
    private lateinit var btnControles: Button
    private lateinit var spinnerMic: Spinner
    private lateinit var spinnerSens: Spinner
    private lateinit var spinnerAntes: Spinner
    private lateinit var spinnerDespues: Spinner
    private lateinit var checkSilencios: CheckBox
    private lateinit var checkVoz: CheckBox
    private lateinit var campoTitulo: EditText
    private lateinit var spinnerTitColor: Spinner
    private lateinit var spinnerTitFuente: Spinner
    private lateinit var spinnerTitTam: Spinner

    private val principal = Handler(Looper.getMainLooper())
    private val ejecutor = Executors.newSingleThreadExecutor()
    private var fase = Fase.LISTO
    private var superficieLista = false
    private var aperturaPendiente = false
    private var tamanoPendiente = Size(0, 0)
    private var microfonos: List<AudioDeviceInfo> = emptyList()
    private var ultimoPorcentaje = -1
    private var iniciando = true

    private val opcionesSens = listOf("Baja", "Normal", "Alta", "Máxima")
    private val opcionesAire = listOf(0, 100, 200, 300, 500, 800, 1000)
    private val opcionesColor = Estilos.COLORES.keys.toList()
    private val opcionesTamTit = listOf(80, 90, 100, 110, 120)

    private val pedirPermisos = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) iniciarCamara(false) else decir("Faltan los permisos de cámara y micrófono. Actívalos en los ajustes del teléfono.")
    }

    private val elegirVideo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) procesarCargado(uri)
    }

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) // la pantalla siempre encendida al grabar

        superficie = findViewById(R.id.vista)
        panel = findViewById(R.id.panel)
        registro = findViewById(R.id.registro)
        barra = findViewById(R.id.barra)
        btnGrabar = findViewById(R.id.btnGrabar)
        btnPausar = findViewById(R.id.btnPausar)
        btnDetener = findViewById(R.id.btnDetener)
        btnDescartar = findViewById(R.id.btnDescartar)
        btnCamara = findViewById(R.id.btnCamara)
        btnCargar = findViewById(R.id.btnCargar)
        btnReproducir = findViewById(R.id.btnReproducir)
        btnCarpeta = findViewById(R.id.btnCarpeta)
        btnLicencia = findViewById(R.id.btnLicencia)
        btnControles = findViewById(R.id.btnControles)
        spinnerMic = findViewById(R.id.spinnerMic)
        spinnerSens = findViewById(R.id.spinnerSens)
        spinnerAntes = findViewById(R.id.spinnerAntes)
        spinnerDespues = findViewById(R.id.spinnerDespues)
        checkSilencios = findViewById(R.id.checkSilencios)
        checkVoz = findViewById(R.id.checkVoz)
        campoTitulo = findViewById(R.id.campoTitulo)
        spinnerTitColor = findViewById(R.id.spinnerTitColor)
        spinnerTitFuente = findViewById(R.id.spinnerTitFuente)
        spinnerTitTam = findViewById(R.id.spinnerTitTam)

        ajustes = Ajustes(this)
        anunciador = Anunciador(this, superficie, registro)
        anunciador.vozPropia = ajustes.vozPropia
        camara = CamaraRecorder(this, superficie)
        superficie.holder.addCallback(this)

        // El panel de controles ocupa la mitad de abajo; el visor se ve completo detrás (y con el botón de arriba se ocultan).
        val lp = panel.layoutParams
        lp.height = (resources.displayMetrics.heightPixels * 0.5).toInt()
        panel.layoutParams = lp

        configurarCamara()
        configurarAjustes()
        configurarBotones()
        cargarMicrofonos()
        registrarCambiosDeAudio()
        actualizar()

        val estado = Licencia.estado(this)
        decir("JorCam. " + if (estado.pro) estado.mensaje else "Versión gratuita. Las funciones Pro se activan con una licencia, en el botón Licencia.")

        if (tienePermisos()) {
            // la cámara se abre cuando el visor esté listo (surfaceCreated)
        } else {
            pedirPermisos.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
        iniciando = false
    }

    override fun onResume() {
        super.onResume()
        if (superficieLista && tienePermisos() && fase == Fase.LISTO) iniciarCamara(camara.frontal)
    }

    override fun onPause() {
        if (fase == Fase.GRABANDO || fase == Fase.PAUSADO) finalizar(true) // si sales de la app grabando, se guarda
        camara.cerrar()
        super.onPause()
    }

    override fun onDestroy() {
        anunciador.cerrar()
        ejecutor.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ visor

    override fun surfaceCreated(holder: SurfaceHolder) {
        superficieLista = true
        if (tienePermisos() && fase == Fase.LISTO) iniciarCamara(camara.frontal)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (aperturaPendiente && width == tamanoPendiente.width && height == tamanoPendiente.height) continuarApertura()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        superficieLista = false
    }

    private fun tienePermisos(): Boolean =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun iniciarCamara(frontal: Boolean) {
        if (!tienePermisos() || !superficieLista) return
        try {
            camara.abrir(frontal)
        } catch (e: Exception) {
            decir("No se pudo abrir la cámara: ${e.message}")
        }
    }

    private fun continuarApertura() {
        if (!aperturaPendiente) return
        aperturaPendiente = false
        camara.continuarApertura()
    }

    private fun ajustarVista(tam: Size) {
        val pantallaAncho = resources.displayMetrics.widthPixels
        val pantallaAlto = resources.displayMetrics.heightPixels
        val relacion = tam.height.toFloat() / tam.width.toFloat() // la imagen se muestra vertical
        var ancho = pantallaAncho
        var alto = (ancho / relacion).toInt()
        if (alto > pantallaAlto) {
            alto = pantallaAlto
            ancho = (alto * relacion).toInt()
        }
        val lp = superficie.layoutParams as FrameLayout.LayoutParams
        lp.width = ancho
        lp.height = alto
        lp.gravity = Gravity.CENTER
        superficie.layoutParams = lp
    }

    private fun configurarCamara() {
        camara.alVistaLista = { tam ->
            runOnUiThread {
                ajustarVista(tam)
                tamanoPendiente = tam
                aperturaPendiente = true
                superficie.holder.setFixedSize(tam.width, tam.height)
                principal.postDelayed({ continuarApertura() }, 600)
            }
        }
        camara.alAbrirCamara = {
            runOnUiThread { decir(if (camara.frontal) "Cámara frontal activa." else "Cámara trasera activa.") }
        }
        camara.alIniciar = {
            runOnUiThread {
                fase = Fase.GRABANDO
                decir("Grabando.")
                actualizar()
            }
        }
        camara.alError = { mensaje -> runOnUiThread { decir(mensaje) } }
    }

    // ------------------------------------------------------------------ botones

    private fun configurarBotones() {
        btnGrabar.setOnClickListener { grabar() }
        btnPausar.setOnClickListener { pausarOReanudar() }
        btnDetener.setOnClickListener { finalizar(true) }
        btnDescartar.setOnClickListener { finalizar(false) }
        btnCamara.setOnClickListener { cambiarCamara() }
        btnCargar.setOnClickListener { cargarVideo() }
        btnReproducir.setOnClickListener { reproducirUltimo() }
        btnCarpeta.setOnClickListener { abrirCarpeta() }
        btnLicencia.setOnClickListener { dialogoLicencia() }
        btnControles.setOnClickListener {
            val oculto = panel.visibility == View.GONE
            panel.visibility = if (oculto) View.VISIBLE else View.GONE
            btnControles.setText(if (oculto) R.string.ocultar_controles else R.string.mostrar_controles)
            decir(if (oculto) "Controles visibles." else "Controles ocultos. El visor de la cámara queda a pantalla completa.")
        }
    }

    private fun actualizar() {
        val listo = fase == Fase.LISTO
        val grabando = fase == Fase.GRABANDO || fase == Fase.PAUSADO
        btnGrabar.isEnabled = listo
        btnPausar.isEnabled = grabando
        btnPausar.setText(if (fase == Fase.PAUSADO) R.string.reanudar else R.string.pausar)
        btnDetener.isEnabled = grabando
        btnDescartar.isEnabled = grabando
        btnCamara.isEnabled = listo
        btnCargar.isEnabled = listo
        val pro = Licencia.estado(this).pro
        btnCargar.setText(if (pro) R.string.cargar else R.string.cargar_pro)
        spinnerSens.isEnabled = pro
        spinnerAntes.isEnabled = pro
        spinnerDespues.isEnabled = pro
        campoTitulo.isEnabled = pro
        campoTitulo.hint = getString(if (pro) R.string.titulo else R.string.titulo_pro)
        spinnerTitColor.isEnabled = pro
        spinnerTitFuente.isEnabled = pro
        spinnerTitTam.isEnabled = pro
        barra.visibility = if (fase == Fase.PROCESANDO) View.VISIBLE else View.GONE
    }

    private fun decir(texto: String) = anunciador.decir(texto)

    // ------------------------------------------------------------------ grabar

    private fun marca(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun grabar() {
        if (fase != Fase.LISTO) return
        if (!tienePermisos()) {
            pedirPermisos.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }
        val mic = micElegido()
        decir("Micrófono: ${if (mic != null) nombreMic(mic) else "automático"}.")
        try {
            camara.empezar(File(cacheDir, "crudo_${marca()}.mp4"), mic)
        } catch (e: Exception) {
            decir("No se pudo empezar a grabar: ${e.message}")
        }
    }

    private fun pausarOReanudar() {
        if (fase == Fase.GRABANDO) {
            camara.pausar()
            fase = Fase.PAUSADO
            decir("En pausa.")
        } else if (fase == Fase.PAUSADO) {
            camara.reanudar()
            fase = Fase.GRABANDO
            decir("Grabando de nuevo.")
        }
        actualizar()
    }

    private fun finalizar(guardar: Boolean) {
        if (fase != Fase.GRABANDO && fase != Fase.PAUSADO) return
        val archivo = camara.detener()
        if (archivo == null) {
            fase = Fase.LISTO
            decir("La grabación fue demasiado corta y no se guardó.")
            actualizar()
            return
        }
        if (!guardar) {
            val carpeta = File(getExternalFilesDir(null), "descartados")
            carpeta.mkdirs()
            archivo.renameTo(File(carpeta, archivo.name))
            fase = Fase.LISTO
            decir("Grabación descartada. Lista para grabar de nuevo.")
            actualizar()
            return
        }
        fase = Fase.PROCESANDO
        ultimoPorcentaje = -1
        actualizar()
        decir("Guardando el video.")
        val titulo = campoTitulo.text.toString()
        ejecutor.execute { procesar(Uri.fromFile(archivo), archivo, "JorCam_${marca()}", 0L, titulo) }
    }

    private fun cambiarCamara() {
        if (fase != Fase.LISTO) {
            decir("Solo se puede cambiar de cámara cuando no se está grabando.")
            return
        }
        iniciarCamara(!camara.frontal) // el aviso "Cámara frontal/trasera activa" sale cuando la nueva cámara está lista
    }

    // ------------------------------------------------------------------ procesar y guardar

    private fun procesar(entrada: Uri, temporal: File?, nombre: String, limiteMs: Long, tituloEscrito: String) {
        try {
            val duracion = Almacen.duracionMs(this, entrada)
            if (limiteMs > 0 && duracion > limiteMs) {
                decir("El video dura más de ${limiteMs / 60000} minutos. Ese es el máximo.")
                principal.post { terminar() }
                return
            }
            val pro = Licencia.estado(this).pro
            // El título solo se pone en videos grabados con JorCam (no en los que se cargan), y solo con licencia Pro.
            val titulo = if (pro && limiteMs == 0L) Estilos.sinEmojis(tituloEscrito) else ""
            var nombreFinal = nombre
            if (titulo.isNotEmpty()) {
                val deTitulo = Estilos.nombreDeArchivo(tituloEscrito)
                if (deTitulo.isNotEmpty()) nombreFinal = deTitulo
            }

            var tramos: List<Silencios.Tramo>? = null
            if (ajustes.quitarSilencios && duracion > 0) {
                decir("Buscando silencios.")
                val sens = if (pro) ajustes.sensibilidad else "Alta"
                val antes = if (pro) ajustes.aireAntesMs else 500
                val despues = if (pro) ajustes.aireDespuesMs else 200
                tramos = Silencios.detectarVoz(this, entrada, duracion, sens, antes, despues) { p -> avanzar(p / 4) }
                if (tramos == null) decir("No pude separar la voz del ruido; no se quita ningún silencio.")
            }
            val hayCortes = tramos != null && Silencios.hayCortes(tramos, duracion)
            if (tramos != null && !hayCortes) decir("No encontré silencios que quitar.")

            val lista: List<Silencios.Tramo>? = when {
                hayCortes -> tramos
                titulo.isNotEmpty() && duracion > 0 -> listOf(Silencios.Tramo(0L, duracion))
                else -> null
            }
            if (lista == null) {
                guardarFinal(entrada, nombreFinal, temporal, null)
                return
            }

            val textoVideo: TextoSobreVideo? = if (titulo.isNotEmpty()) prepararTexto(entrada, lista, titulo) else null
            val conservado = lista.sumOf { it.finMs - it.inicioMs }
            if (hayCortes) {
                decir("Se van a quitar ${(duracion - conservado) / 1000} segundos de silencio: el video pasa de ${texto(duracion)} a ${texto(conservado)}.")
            }
            if (textoVideo != null) decir("Escribiendo el título sobre el video.")
            val salida = File(cacheDir, "salida_${marca()}.mp4")
            principal.post {
                Recorte.recortar(this, entrada, lista, textoVideo, salida, { p -> avanzar(25 + p * 3 / 4) }) { ok, error ->
                    if (ok) {
                        ejecutor.execute { guardarFinal(Uri.fromFile(salida), nombreFinal, temporal, salida) }
                    } else {
                        decir("No se pudo recortar ($error). Guardo el video sin cambios.")
                        ejecutor.execute { guardarFinal(entrada, nombreFinal, temporal, salida) }
                    }
                }
            }
        } catch (e: Exception) {
            decir("Error al guardar: ${e.message}")
            principal.post { terminar() }
        }
    }

    /** Busca la cara y calcula dónde va el título, igual que en la versión de PC. */
    private fun prepararTexto(entrada: Uri, lista: List<Silencios.Tramo>, titulo: String): TextoSobreVideo {
        decir("Buscando tu cara para colocar el título sin taparla.")
        val (ancho, alto) = dimensionesFinales(entrada)
        val total = lista.sumOf { it.finMs - it.inicioMs }
        val instantes = listOf(400L, 1500L, 3000L, 4500L, 8000L, 16000L, 26000L, 38000L).filter { it < total }
        val tiempos = instantes.map { Rostro.tiempoOrigen(it, lista) }
        val posicion = if (tiempos.isEmpty()) null else Rostro.buscar(this, entrada, tiempos, alto)
        if (posicion == null) decir("No encontré tu cara; coloco el título en una posición estándar.")
        val tipTitulo = Estilos.tipografia(ajustes.titFuente)
        val colocacion = Rostro.colocar(titulo, posicion, ajustes.titTam.toFloat(), tipTitulo, 60f, alto)
        colocacion.aviso?.let { decir(it) }
        return TextoSobreVideo(
            ancho, alto, colocacion.lineasTitulo, colocacion.tituloInferiorY, colocacion.tamTitulo,
            Estilos.colorDe(ajustes.titColor), tipTitulo, 5L,
            emptyList(), colocacion.subY, 60f, Estilos.colorDe("Blanco"), Estilos.tipografia("Clásica (Arial)")
        )
    }

    /** Ancho y alto del video ya derecho (vertical), teniendo en cuenta la marca de rotación. */
    private fun dimensionesFinales(uri: Uri): Pair<Int, Int> {
        val lector = android.media.MediaMetadataRetriever()
        return try {
            lector.setDataSource(this, uri)
            val w = lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1080
            val h = lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1920
            val giro = lector.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (giro == 90 || giro == 270) Pair(h, w) else Pair(w, h)
        } catch (e: Exception) {
            Pair(1080, 1920)
        } finally {
            lector.release()
        }
    }

    private fun texto(ms: Long): String {
        val s = ms / 1000
        return if (s >= 60) "${s / 60} minutos y ${s % 60} segundos" else "$s segundos"
    }

    private fun guardarFinal(origen: Uri, nombre: String, temporalEntrada: File?, temporalSalida: File?) {
        val uri = Almacen.guardarVideo(this, origen, "$nombre.mp4")
        temporalEntrada?.delete()
        temporalSalida?.delete()
        if (uri != null) {
            ajustes.ultimoVideo = uri.toString()
            decir("Listo. Video guardado en Películas, carpeta JorCam.")
        } else {
            decir("No se pudo guardar el video.")
        }
        principal.post { terminar() }
    }

    private fun terminar() {
        fase = Fase.LISTO
        actualizar()
    }

    private fun avanzar(porcentaje: Int) {
        runOnUiThread {
            val p = porcentaje.coerceIn(0, 100)
            barra.progress = p
            val decena = p / 10 * 10
            if (decena > ultimoPorcentaje && p < 100) {
                ultimoPorcentaje = decena
                decir("Guardando: $decena por ciento.")
            }
        }
    }

    // ------------------------------------------------------------------ cargar, reproducir, carpeta

    private fun cargarVideo() {
        if (fase != Fase.LISTO) return
        if (!Licencia.estado(this).pro) {
            decir("Cargar videos es una función Pro. Activa tu licencia en el botón Licencia.")
            return
        }
        elegirVideo.launch(arrayOf("video/*"))
    }

    private fun procesarCargado(uri: Uri) {
        fase = Fase.PROCESANDO
        ultimoPorcentaje = -1
        actualizar()
        decir("Video recibido. Revisando el audio.")
        ejecutor.execute { procesar(uri, null, "limpio_${marca()}", 20 * 60_000L, "") }
    }

    private fun reproducirUltimo() {
        val guardado = ajustes.ultimoVideo
        if (guardado.isEmpty()) {
            decir("Todavía no hay un video guardado.")
            return
        }
        try {
            decir("Abriendo el último video.")
            Almacen.reproducir(this, Uri.parse(guardado))
        } catch (e: Exception) {
            decir("No encontré una aplicación para reproducir el video.")
        }
    }

    private fun abrirCarpeta() {
        try {
            decir("Abriendo tus videos.")
            Almacen.abrirVideos(this)
        } catch (e: Exception) {
            decir("No encontré una aplicación para ver los videos. Búscalos en Películas, carpeta JorCam.")
        }
    }

    // ------------------------------------------------------------------ licencia

    private fun dialogoLicencia() {
        val campo = EditText(this)
        campo.hint = getString(R.string.clave_licencia)
        val estado = Licencia.estado(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.licencia)
            .setMessage(estado.mensaje)
            .setView(campo)
            .setPositiveButton(R.string.activar) { _, _ ->
                val (_, mensaje) = Licencia.activar(this, campo.text.toString())
                decir(mensaje)
                actualizar()
            }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    // ------------------------------------------------------------------ micrófono

    private fun tipoMic(tipo: Int): String = when (tipo) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Micrófono del teléfono"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Auriculares con cable"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "Micrófono USB"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Micrófono Bluetooth"
        else -> "Entrada de audio"
    }

    private fun nombreMic(d: AudioDeviceInfo): String = "${tipoMic(d.type)} ${d.productName}".trim()

    private fun cargarMicrofonos() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        microfonos = am.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource }
        val nombres = listOf("Automático") + microfonos.map { nombreMic(it) }
        spinnerMic.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nombres)
        val indice = microfonos.indexOfFirst { nombreMic(it) == ajustes.micNombre }
        spinnerMic.setSelection(if (indice >= 0) indice + 1 else 0)
    }

    private fun micElegido(): AudioDeviceInfo? {
        val posicion = spinnerMic.selectedItemPosition
        return if (posicion > 0 && posicion - 1 < microfonos.size) microfonos[posicion - 1] else null
    }

    private fun registrarCambiosDeAudio() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(agregados: Array<out AudioDeviceInfo>) {
                runOnUiThread { cargarMicrofonos(); decir("Se detectó un nuevo dispositivo de audio.") }
            }

            override fun onAudioDevicesRemoved(quitados: Array<out AudioDeviceInfo>) {
                runOnUiThread { cargarMicrofonos() }
            }
        }, null)
    }

    // ------------------------------------------------------------------ ajustes

    private fun configurarAjustes() {
        checkSilencios.isChecked = ajustes.quitarSilencios
        checkVoz.isChecked = ajustes.vozPropia
        spinnerSens.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSens)
        spinnerSens.setSelection(opcionesSens.indexOf(ajustes.sensibilidad).coerceAtLeast(0))
        val textos = opcionesAire.map { "$it" }
        spinnerAntes.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, textos)
        spinnerAntes.setSelection(opcionesAire.indexOf(ajustes.aireAntesMs).coerceAtLeast(0))
        spinnerDespues.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, textos)
        spinnerDespues.setSelection(opcionesAire.indexOf(ajustes.aireDespuesMs).coerceAtLeast(0))

        spinnerTitColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesColor)
        spinnerTitColor.setSelection(opcionesColor.indexOf(ajustes.titColor).coerceAtLeast(0))
        spinnerTitFuente.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Estilos.FUENTES)
        spinnerTitFuente.setSelection(Estilos.FUENTES.indexOf(ajustes.titFuente).coerceAtLeast(0))
        spinnerTitTam.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesTamTit.map { "$it" })
        spinnerTitTam.setSelection(opcionesTamTit.indexOf(ajustes.titTam).coerceAtLeast(0))
        spinnerTitColor.onItemSelectedListener = alElegir { i -> ajustes.titColor = opcionesColor[i] }
        spinnerTitFuente.onItemSelectedListener = alElegir { i -> ajustes.titFuente = Estilos.FUENTES[i] }
        spinnerTitTam.onItemSelectedListener = alElegir { i -> ajustes.titTam = opcionesTamTit[i] }

        checkSilencios.setOnCheckedChangeListener { _, marcado -> ajustes.quitarSilencios = marcado }
        checkVoz.setOnCheckedChangeListener { _, marcado ->
            ajustes.vozPropia = marcado
            anunciador.vozPropia = marcado
            decir(if (marcado) "Voz propia de JorCam activada." else "Se usará tu lector de pantalla.")
        }
        spinnerSens.onItemSelectedListener = alElegir { i -> ajustes.sensibilidad = opcionesSens[i] }
        spinnerAntes.onItemSelectedListener = alElegir { i -> ajustes.aireAntesMs = opcionesAire[i] }
        spinnerDespues.onItemSelectedListener = alElegir { i -> ajustes.aireDespuesMs = opcionesAire[i] }
        spinnerMic.onItemSelectedListener = alElegir { i ->
            val seleccion = if (i > 0 && i - 1 < microfonos.size) nombreMic(microfonos[i - 1]) else ""
            if (seleccion != ajustes.micNombre) {
                ajustes.micNombre = seleccion
                if (!iniciando) decir(if (seleccion.isEmpty()) "Micrófono automático." else "Micrófono: $seleccion.")
            }
        }
    }

    private fun alElegir(accion: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(padre: AdapterView<*>?, vista: View?, posicion: Int, id: Long) {
            accion(posicion)
        }

        override fun onNothingSelected(padre: AdapterView<*>?) {}
    }
}
