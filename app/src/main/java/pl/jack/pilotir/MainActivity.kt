package pl.jack.pilotir

import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember { Settings.load(ctx) }
    var url by remember { mutableStateOf(initial.url.ifEmpty { "http://homeassistant.local:8123" }) }
    var token by remember { mutableStateOf(initial.token) }
    var msg by remember { mutableStateOf("") }
    var log by remember { mutableStateOf(DiagLog.read(ctx)) }

    Column(
        modifier = Modifier
            .safeDrawingPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Hi-Fi Denon – Home Assistant", style = MaterialTheme.typography.titleLarge)
        Text(
            "Token: profil HA → Bezpieczeństwo → Tokeny długoterminowe.",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            label = { Text("Adres HA (np. http://192.168.1.10:8123)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token, onValueChange = { token = it },
            label = { Text("Long-lived access token") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            Settings.save(ctx, url, token)
            msg = "Sprawdzam…"
            scope.launch {
                msg = HaClient.test(ctx).fold({ "Połączono ✓  $it" }, { "Błąd: ${it.message}" })
                log = DiagLog.read(ctx)
            }
        }) { Text("Zapisz i przetestuj") }

        OutlinedButton(onClick = { msg = pinWidget(ctx) }) { Text("Dodaj widget na ekran główny") }

        if (msg.isNotEmpty()) Text(msg)

        Text("Diagnostyka", style = MaterialTheme.typography.titleMedium)
        Text(
            "Kliknij przycisk na widgecie, wróć tutaj i daj „Odśwież”.",
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { log = DiagLog.read(ctx) }) { Text("Odśwież") }
            OutlinedButton(onClick = {
                ctx.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Pilot IR log", log))
                msg = "Log skopiowany"
            }) { Text("Kopiuj") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, log)
                ctx.startActivity(Intent.createChooser(send, "Udostępnij log"))
            }) { Text("Udostępnij") }
            OutlinedButton(onClick = { DiagLog.clear(ctx); log = "" }) { Text("Wyczyść") }
        }
        Text(
            text = log.ifEmpty { "(log pusty)" }.lines().takeLast(120).joinToString("\n"),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111316))
                .padding(8.dp)
        )
    }
}

private fun pinWidget(ctx: Context): String {
    val mgr = AppWidgetManager.getInstance(ctx)
    if (!mgr.isRequestPinAppWidgetSupported) return "Launcher nie wspiera przypinania – dodaj widget ręcznie z listy widgetów."
    mgr.requestPinAppWidget(ComponentName(ctx, PilotWidgetReceiver::class.java), null, null)
    return "Potwierdź dodanie widgetu w okienku launchera."
}
