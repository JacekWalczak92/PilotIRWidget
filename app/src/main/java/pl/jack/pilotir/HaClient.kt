package pl.jack.pilotir

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class HaSettings(val url: String, val token: String)

object Settings {
    private const val PREFS = "ha_settings"

    fun load(context: Context): HaSettings {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return HaSettings(p.getString("url", "") ?: "", p.getString("token", "") ?: "")
    }

    fun save(context: Context, url: String, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("url", cleanUrl(url))
            .putString("token", token.trim())
            .apply()
    }

    /** Zostawia samo https://host[:port] – usuwa spacje, sklejone adresy i ścieżki typu /lovelace. */
    fun cleanUrl(raw: String): String {
        var s = raw.filterNot { it.isWhitespace() }
        val i = s.lastIndexOf("://")
        if (i >= 0) {
            val start = maxOf(s.lastIndexOf("http://", i), s.lastIndexOf("https://", i))
            if (start > 0) s = s.substring(start)
        } else if (s.isNotEmpty()) {
            s = "https://$s"
        }
        return runCatching {
            val u = URI(s)
            val port = if (u.port > 0) ":${u.port}" else ""
            "${u.scheme}://${u.host}$port"
        }.getOrDefault(s.trimEnd('/'))
    }
}

object HaClient {

    // Klient tylko do zapytań DoH – łączy się po IP, więc sam nie potrzebuje DNS.
    private val bootstrap = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val dohResolvers: List<Pair<String, Dns>> by lazy {
        listOf(
            "Cloudflare DoH" to DnsOverHttps.Builder().client(bootstrap)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
                .build(),
            "Google DoH" to DnsOverHttps.Builder().client(bootstrap)
                .url("https://dns.google/dns-query".toHttpUrl())
                .bootstrapDnsHosts(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("8.8.4.4"))
                .build()
        )
    }

    /** Najpierw DNS systemowy, a gdy zawiedzie – DNS-over-HTTPS (jak bezpieczny DNS w Chrome). */
    private val fallbackDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            try {
                return Dns.SYSTEM.lookup(hostname).also {
                    DiagLog.add("DNS systemowy OK → ${it.joinToString { a -> a.hostAddress ?: "?" }}")
                }
            } catch (e: UnknownHostException) {
                DiagLog.add("DNS systemowy BŁĄD: ${e.message}")
            }
            for ((name, dns) in dohResolvers) {
                try {
                    return dns.lookup(hostname).also {
                        DiagLog.add("$name OK → ${it.joinToString { a -> a.hostAddress ?: "?" }}")
                    }
                } catch (e: Exception) {
                    DiagLog.add("$name BŁĄD: ${e.javaClass.simpleName}: ${e.message}")
                }
            }
            throw UnknownHostException("Nie można rozwiązać $hostname (system ani DoH)")
        }
    }

    private val client = OkHttpClient.Builder()
        .dns(fallbackDns)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** Uruchamia script.<name> przez REST API (script.turn_on — nie czeka na koniec skryptu). */
    suspend fun runScript(context: Context, script: String): Result<Unit> = withContext(Dispatchers.IO) {
        Diag.snapshot(context, "widget: $script")
        val r = retryNetwork {
            val s = Settings.load(context)
            require(s.url.isNotBlank() && s.token.isNotBlank()) { "Brak konfiguracji – otwórz apkę" }
            val req = Request.Builder()
                .url("${s.url}/api/services/script/turn_on")
                .header("Authorization", "Bearer ${s.token}")
                .post("""{"entity_id":"script.$script"}""".toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
            }
        }
        DiagLog.add(r.fold({ "→ OK" }, { "→ BŁĄD ${it.javaClass.simpleName}: ${it.message}" }))
        r
    }

    /** Sprawdza połączenie: GET /api/ powinno zwrócić {"message":"API running."}. */
    suspend fun test(context: Context): Result<String> = withContext(Dispatchers.IO) {
        Diag.snapshot(context, "test z apki")
        val r = retryNetwork {
            val s = Settings.load(context)
            require(s.url.isNotBlank() && s.token.isNotBlank()) { "Uzupełnij adres i token" }
            DiagLog.add("URL: ${s.url}")
            val req = Request.Builder()
                .url("${s.url}/api/")
                .header("Authorization", "Bearer ${s.token}")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 401) error("HTTP 401 – zły token")
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string() ?: ""
            }
        }
        DiagLog.add(r.fold({ "→ OK: $it" }, { "→ BŁĄD ${it.javaClass.simpleName}: ${it.message}" }))
        r
    }

    /** Błędy sieciowe ponawiamy (proces obudzony widgetem bywa chwilę bez sieci). */
    private suspend fun <T> retryNetwork(block: () -> T): Result<T> {
        var last: Throwable? = null
        repeat(3) { attempt ->
            try {
                return Result.success(block())
            } catch (e: IOException) {
                last = e
                DiagLog.add("próba ${attempt + 1}/3: ${e.javaClass.simpleName}: ${e.message}")
                delay(500L + 700L * attempt)
            } catch (e: Exception) {
                return Result.failure(e)
            }
        }
        return Result.failure(last ?: IOException("Brak sieci"))
    }
}
