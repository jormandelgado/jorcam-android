package com.jormandelgado.jorcam

import android.content.Context

/** Ajustes guardados en el teléfono. */
class Ajustes(contexto: Context) {
    private val p = contexto.getSharedPreferences("jorcam", Context.MODE_PRIVATE)

    var quitarSilencios: Boolean
        get() = p.getBoolean("quitar_silencios", true)
        set(v) { p.edit().putBoolean("quitar_silencios", v).apply() }

    var sensibilidad: String
        get() = p.getString("sensibilidad", "Alta") ?: "Alta"
        set(v) { p.edit().putString("sensibilidad", v).apply() }

    var aireAntesMs: Int
        get() = p.getInt("aire_antes_ms", 500)
        set(v) { p.edit().putInt("aire_antes_ms", v).apply() }

    var aireDespuesMs: Int
        get() = p.getInt("aire_despues_ms", 200)
        set(v) { p.edit().putInt("aire_despues_ms", v).apply() }

    var micNombre: String
        get() = p.getString("mic_nombre", "") ?: ""
        set(v) { p.edit().putString("mic_nombre", v).apply() }

    var titColor: String
        get() = p.getString("tit_color", "Blanco") ?: "Blanco"
        set(v) { p.edit().putString("tit_color", v).apply() }

    var titFuente: String
        get() = p.getString("tit_fuente", "Gruesa (Arial Black)") ?: "Gruesa (Arial Black)"
        set(v) { p.edit().putString("tit_fuente", v).apply() }

    var titTam: Int
        get() = p.getInt("tit_tam", 100)
        set(v) { p.edit().putInt("tit_tam", v).apply() }

    var vozPropia: Boolean
        get() = p.getBoolean("voz_propia", false)
        set(v) { p.edit().putBoolean("voz_propia", v).apply() }

    var ultimoVideo: String
        get() = p.getString("ultimo_video", "") ?: ""
        set(v) { p.edit().putString("ultimo_video", v).apply() }
}
