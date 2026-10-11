package pl.jack.pilotir

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Prosty log do pliku w apce – podgląd, kopiowanie i udostępnianie na ekranie głównym apki. */
object DiagLog {
    @Volatile private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (file == null) file = File(context.applicationContext.filesDir, "diag.log")
    }

    @Synchronized
    fun add(msg: String) {
        Log.d("PilotIR", msg)
        val f = file ?: return
        runCatching {
            f.appendText("${fmt.format(Date())}  $msg\n")
            if (f.length() > 64_000) f.writeText(f.readLines().takeLast(300).joinToString("\n", postfix = "\n"))
        }
    }

    fun read(context: Context): String {
        init(context)
        return file?.takeIf { it.exists() }?.readText() ?: ""
    }

    @Synchronized
    fun clear(context: Context) {
        init(context)
        file?.delete()
    }
}

/** Zrzut stanu sieci i ograniczeń w tle w momencie wywołania. */
object Diag {
    fun snapshot(context: Context, origin: String) {
        DiagLog.init(context)
        val sb = StringBuilder("=== [$origin] ")
        runCatching {
            val v = context.packageManager.getPackageInfo(context.packageName, 0).versionName
            sb.append("app v$v, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        }
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val n = cm.activeNetwork
            if (n == null) {
                sb.append("\n    sieć: BRAK aktywnej sieci")
            } else {
                val c = cm.getNetworkCapabilities(n)
                val t = buildList {
                    if (c?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) add("wifi")
                    if (c?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) add("komórkowa")
                    if (c?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) add("VPN")
                }
                sb.append("\n    sieć: $t, internet=${c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                sb.append(", validated=${c?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                val lp = cm.getLinkProperties(n)
                if (Build.VERSION.SDK_INT >= 28) {
                    sb.append("\n    prywatny DNS: ${lp?.isPrivateDnsActive} ${lp?.privateDnsServerName ?: ""}")
                }
                sb.append("\n    serwery DNS: ${lp?.dnsServers?.joinToString { it.hostAddress ?: "?" }}")
            }
            val ds = when (cm.restrictBackgroundStatus) {
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED -> "wył."
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED -> "wł. (apka zwolniona)"
                ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED -> "WŁ. – apka bez danych w tle"
                else -> "?"
            }
            sb.append("\n    oszczędzanie danych: $ds")
        }.onFailure { sb.append("\n    sieć: błąd odczytu ${it.message}") }
        runCatching {
            if (Build.VERSION.SDK_INT >= 28) {
                val am = context.getSystemService(ActivityManager::class.java)
                sb.append("\n    ograniczenie w tle: ${am.isBackgroundRestricted}")
            }
            val pm = context.getSystemService(PowerManager::class.java)
            sb.append(", oszczędzanie baterii: ${pm.isPowerSaveMode}")
            sb.append(", bez optymalizacji baterii: ${pm.isIgnoringBatteryOptimizations(context.packageName)}")
        }
        DiagLog.add(sb.toString())
    }
}
