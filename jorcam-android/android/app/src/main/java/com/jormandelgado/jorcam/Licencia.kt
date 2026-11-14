package com.jormandelgado.jorcam

import android.content.Context
import android.provider.Settings
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ceil
import kotlin.math.max

/**
 * Licencias de JorCam: claves firmadas con Ed25519, verificadas sin internet.
 * Es el mismo formato que usa la versión de PC (JC- + 95 caracteres): una clave sirve en los dos.
 */
object Licencia {
    private const val PREFIJO = "JC-"
    private val DOMINIO = "JorCam1".toByteArray(Charsets.US_ASCII)
    private val PLANES = mapOf('M' to Pair("Pase mensual", 30), 'A' to Pair("Pase anual", 365))
    private const val DIA_MS = 86_400_000L

    data class Estado(val pro: Boolean, val mensaje: String, val dias: Int)

    private fun hexABytes(hex: String): ByteArray? {
        if (!Regex("^[0-9a-fA-F]{64}$").matches(hex)) return null
        return ByteArray(hex.length / 2) { i -> hex.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
    }

    private fun clavePublica(ctx: Context): ByteArray? {
        val texto = ctx.resources.openRawResource(R.raw.clave_publica).bufferedReader().use { it.readText() }.trim()
        return hexABytes(texto)
    }

    private fun verificar(publica: ByteArray, mensaje: ByteArray, firma: ByteArray): Boolean = try {
        val firmante = Ed25519Signer()
        firmante.init(false, Ed25519PublicKeyParameters(publica, 0))
        firmante.update(mensaje, 0, mensaje.size)
        firmante.verifySignature(firma)
    } catch (e: Exception) {
        false
    }

    /** Devuelve (plan, código) si la clave es auténtica; si no, null. */
    private fun leerClave(ctx: Context, clave: String): Pair<Char, String>? {
        val limpia = clave.filter { !it.isWhitespace() }
        if (!limpia.startsWith(PREFIJO)) return null
        val datos = try {
            Base64.getUrlDecoder().decode(limpia.substring(PREFIJO.length))
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (datos.size != 71 || datos[0].toInt() != 1) return null
        val plan = (datos[1].toInt() and 0xFF).toChar()
        if (!PLANES.containsKey(plan)) return null
        val carga = datos.copyOfRange(0, 7)
        val firma = datos.copyOfRange(7, 71)
        val publica = clavePublica(ctx) ?: return null
        if (!verificar(publica, DOMINIO + carga, firma)) return null
        val codigo = datos.copyOfRange(2, 7).joinToString("") { "%02x".format(it) }
        return Pair(plan, codigo)
    }

    // ---- Estado guardado en el teléfono (en dos lugares, con sello contra ediciones) ----

    private fun llave(ctx: Context): ByteArray {
        val id = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "sin-id"
        return MessageDigest.getInstance("SHA-256").digest(("$id|JorCam-estado").toByteArray())
    }

    private fun sello(ctx: Context, clave: String, plan: String, activada: Long, ultimo: Long): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(llave(ctx), "HmacSHA256"))
        return mac.doFinal("$clave|$activada|$ultimo|$plan".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun archivo(ctx: Context) = File(ctx.filesDir, "licencia.json")

    private fun guardar(ctx: Context, clave: String, plan: String, activada: Long, ultimo: Long) {
        val json = JSONObject()
        json.put("clave", clave)
        json.put("plan", plan)
        json.put("activada", activada)
        json.put("ultimo", ultimo)
        json.put("sello", sello(ctx, clave, plan, activada, ultimo))
        val texto = json.toString()
        try { archivo(ctx).writeText(texto) } catch (e: Exception) { }
        ctx.getSharedPreferences("licencia", Context.MODE_PRIVATE).edit().putString("estado", texto).apply()
    }

    private fun leerUno(ctx: Context, texto: String?): JSONObject? {
        if (texto.isNullOrEmpty()) return null
        return try {
            val j = JSONObject(texto)
            val esperado = sello(ctx, j.getString("clave"), j.getString("plan"), j.getLong("activada"), j.getLong("ultimo"))
            if (esperado == j.getString("sello")) j else null
        } catch (e: Exception) {
            null
        }
    }

    private fun estadosGuardados(ctx: Context): List<JSONObject> {
        val deArchivo = try { if (archivo(ctx).exists()) archivo(ctx).readText() else null } catch (e: Exception) { null }
        val dePrefs = ctx.getSharedPreferences("licencia", Context.MODE_PRIVATE).getString("estado", null)
        return listOfNotNull(leerUno(ctx, deArchivo), leerUno(ctx, dePrefs))
    }

    fun activar(ctx: Context, claveEscrita: String, ahora: Long = System.currentTimeMillis()): Pair<Boolean, String> {
        val leida = leerClave(ctx, claveEscrita)
            ?: return Pair(false, "La clave no es válida. Cópiala completa desde el correo de la compra.")
        val clave = claveEscrita.filter { !it.isWhitespace() }
        val plan = leida.first.toString()
        // Si esta misma clave ya se activó antes, el conteo de días no se reinicia.
        val previas = estadosGuardados(ctx).filter { it.getString("clave") == clave }.map { it.getLong("activada") }
        val activada = previas.minOrNull() ?: ahora
        guardar(ctx, clave, plan, activada, max(ahora, activada))
        val actual = estado(ctx, ahora)
        return if (actual.pro) {
            Pair(true, "Licencia activada: ${PLANES[leida.first]!!.first}. ${actual.mensaje}")
        } else {
            Pair(false, actual.mensaje)
        }
    }

    fun estado(ctx: Context, ahora: Long = System.currentTimeMillis()): Estado {
        val estados = estadosGuardados(ctx)
        if (estados.isEmpty()) return Estado(false, "No hay una licencia activada.", 0)
        val e = estados.minByOrNull { it.getLong("activada") }!!
        val leida = leerClave(ctx, e.getString("clave"))
            ?: return Estado(false, "La licencia guardada no es válida.", 0)
        val plan = leida.first
        val ultimoVisto = estados.maxOf { it.getLong("ultimo") }
        if (ahora < ultimoVisto - DIA_MS) {
            return Estado(false, "La fecha del teléfono parece haber cambiado hacia atrás. Corrígela para usar la licencia.", 0)
        }
        val activada = e.getLong("activada")
        val vence = activada + PLANES[plan]!!.second * DIA_MS
        if (ahora - e.getLong("ultimo") > 3_600_000L) {
            guardar(ctx, e.getString("clave"), plan.toString(), activada, ahora)
        }
        if (ahora >= vence) {
            return Estado(false, "Tu ${PLANES[plan]!!.first.lowercase()} venció. Consigue uno nuevo en jormandelgado.com.", 0)
        }
        val dias = ceil((vence - ahora).toDouble() / DIA_MS).toInt()
        return Estado(true, "Te quedan $dias días de ${PLANES[plan]!!.first.lowercase()}.", dias)
    }
}
