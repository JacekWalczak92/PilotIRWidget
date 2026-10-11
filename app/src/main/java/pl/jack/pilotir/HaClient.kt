package pl.jack.pilotir

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class HaSettings(val url: String, val token: String)

object Settings {
    private const val PREFS = "ha_settings"

    fun load(context: Context): HaSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HaSettings(p.getString("url", "") ?: "", p.getString("token", "") ?: "")
    }

    fun save(context: Context, url: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", url.trim().trimEnd('/'))
            .putString("token", token.trim())
            .apply()
    }
}

object HaClient {

    /** Uruchamia script.<name> przez REST API (script.turn_on — nie czeka na koniec skryptu). */
    suspend fun runScript(context: Context, script: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Settings.load(context)
            require(s.url.isNotBlank() && s.token.isNotBlank()) { "Brak konfiguracji – otwórz apkę" }
            val conn = open("${s.url}/api/services/script/turn_on", s.token).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            try {
                conn.outputStream.use { it.write("""{"entity_id":"script.$script"}""".toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) error("HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
    }

    /** Sprawdza połączenie: GET /api/ powinno zwrócić {"message":"API running."}. */
    suspend fun test(context: Context): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val s = Settings.load(context)
            require(s.url.isNotBlank() && s.token.isNotBlank()) { "Uzupełnij adres i token" }
            val conn = open("${s.url}/api/", s.token)
            try {
                val code = conn.responseCode
                if (code == 401) error("HTTP 401 – zły token")
                if (code !in 200..299) error("HTTP $code")
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun open(url: String, token: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 4000
            readTimeout = 6000
            setRequestProperty("Authorization", "Bearer $token")
        }
}
