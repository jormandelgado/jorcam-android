package com.jormandelgado.jorcam

import java.text.Normalizer

/**
 * Emojis automáticos para los subtítulos. Importante: NO entiende el sentido de la frase; busca palabras clave
 * (sin importar tildes ni mayúsculas) y pone, como mucho, un emoji al final de la línea. Es moderado a propósito:
 * nunca en dos líneas seguidas ni repite el mismo emoji. Todo se puede corregir en la pantalla de revisión.
 */
object Emojis {
    // Raíces de 3 letras o menos deben coincidir con la palabra completa; las demás, con el comienzo de la palabra.
    private val REGLAS: List<Pair<List<String>, String>> = listOf(
        listOf("plata", "dinero", "pagar", "pago", "cobr", "billete", "precio", "barato", "ahorr", "dolar", "ingres", "vender", "venta") to "💸",
        listOf("negocio", "emprend", "empresa", "trabajo", "trabajar", "empleo", "oficina") to "💼",
        listOf("amor", "carino", "corazon", "enamor", "querid") to "❤️",
        listOf("increible", "espectacular", "asombr", "wow") to "🤩",
        listOf("feliz", "felicidad", "alegr", "content", "genial", "excelente", "maravill") to "😊",
        listOf("jaja", "jeje", "risa", "gracios", "chiste", "divertid") to "😂",
        listOf("triste", "llor", "pena", "extran") to "😢",
        listOf("miedo", "susto", "nervio", "temor", "panico", "asust") to "😨",
        listOf("fuego", "caliente", "quema", "ardiente") to "🔥",
        listOf("idea", "pensar", "piens", "imagin", "creativ", "aprend") to "💡",
        listOf("mira", "miren", "ver", "vean", "observ") to "👀",
        listOf("fuerza", "fuerte", "gimnasio", "ejercicio", "entren", "muscul", "esfuerzo") to "💪",
        listOf("natacion", "nadar", "nadad", "piscina", "alberca") to "🏊",
        listOf("agua") to "💧",
        listOf("fruta", "pulpa", "mango", "fresa", "naranja", "limon", "pina", "parchita", "guayaba", "lechosa", "cambur", "mora") to "🍓",
        listOf("comer", "comida", "hambre", "almuerz", "cena", "desayun", "delici", "sabros", "cocin", "receta") to "🍽️",
        listOf("cafe", "cafecito") to "☕",
        listOf("music", "cancion", "cantar", "canto", "bailar", "baile", "ritmo") to "🎵",
        listOf("telefono", "celular", "movil", "whatsapp", "llamada", "mensaje") to "📱",
        listOf("computador", "laptop", "internet", "programa", "aplicacion", "app", "software") to "💻",
        listOf("inteligencia", "robot") to "🤖",
        listOf("video", "grabar", "grabacion", "camara", "tiktok", "contenido") to "🎥",
        listOf("estudi", "universidad", "libro", "leer", "lectura", "clase", "examen", "tarea", "sociolog") to "📚",
        listOf("casa", "hogar", "familia") to "🏠",
        listOf("venezuela", "venezolan", "caracas") to "🇻🇪",
        listOf("tiempo", "hora", "reloj", "minuto") to "⏰",
        listOf("cuidado", "atencion", "importante", "advert", "peligro") to "⚠️",
        listOf("gracias", "agradec") to "🙏",
        listOf("hola", "saludos", "bienvenid", "buenas") to "👋",
        listOf("exito", "ganar", "gane", "logro", "meta", "campeon", "victoria", "triunf") to "🏆",
        listOf("dormir", "sueno", "cansad", "descans") to "😴",
        listOf("viaj", "vuelo", "avion", "turismo") to "✈️",
        listOf("carro", "auto", "carretera", "manej", "conducir") to "🚗",
        listOf("lluvi", "llovi", "tormenta") to "🌧️",
        listOf("sol", "calor", "playa", "verano") to "☀️",
        listOf("regalo", "cumple", "fiesta", "celebr") to "🎉",
        listOf("secreto", "confiden") to "🤫",
        listOf("duda", "pregunta") to "🤔",
        listOf("accesib", "inclusi", "discapacid") to "♿",
        listOf("salud", "medic", "doctor", "hospital", "enfermed") to "🏥"
    )

    private val PATRON_EMOJI = Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\u2B00-\\u2BFF]")

    private fun normalizar(t: String): String =
        Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun emojiPara(texto: String): String? {
        val palabras = normalizar(texto).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
        for (palabra in palabras) {
            for ((raices, emoji) in REGLAS) {
                for (raiz in raices) {
                    val coincide = if (raiz.length <= 3) palabra == raiz else palabra.startsWith(raiz)
                    if (coincide) return emoji
                }
            }
        }
        return null
    }

    /** Agrega el emoji al final de cada línea que lo merezca (modifica y devuelve la misma lista). */
    fun agregar(frases: List<Subtitulos.Frase>): List<Subtitulos.Frase> {
        var ultimoEmoji = ""
        var ultimaPosicion = -10
        for ((i, f) in frases.withIndex()) {
            if (PATRON_EMOJI.containsMatchIn(f.texto)) {
                ultimaPosicion = i
                continue
            }
            if (i - ultimaPosicion < 2) continue
            val emoji = emojiPara(f.texto) ?: continue
            if (emoji == ultimoEmoji) continue
            f.texto = f.texto + " " + emoji
            ultimoEmoji = emoji
            ultimaPosicion = i
        }
        return frases
    }
}
