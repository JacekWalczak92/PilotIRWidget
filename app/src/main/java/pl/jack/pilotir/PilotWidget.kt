package pl.jack.pilotir

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val TextMain = Color(0xFFF2F4F7)
private val TextDim = Color(0xFF8E97A4)

class PilotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PilotWidget()
}

class PilotWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Remote(currentState(StatusKey) ?: "")
        }
    }

    companion object {
        val StatusKey = stringPreferencesKey("status")
    }
}

/** Kliknięcie przycisku → script.turn_on + krótki status na widgecie. */
class RunScriptAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val script = parameters[ScriptKey] ?: return
        val label = parameters[LabelKey] ?: script

        setStatus(context, glanceId, "… $label")
        val result = HaClient.runScript(context, script)
        setStatus(
            context, glanceId,
            result.fold({ "✓ $label" }, { "✗ ${it.message?.take(40) ?: "błąd"}" })
        )
    }

    private suspend fun setStatus(context: Context, glanceId: GlanceId, text: String) {
        updateAppWidgetState(context, glanceId) { it[PilotWidget.StatusKey] = text }
        PilotWidget().update(context, glanceId)
    }

    companion object {
        val ScriptKey = ActionParameters.Key<String>("script")
        val LabelKey = ActionParameters.Key<String>("label")
    }
}

private fun runScript(script: String, label: String) =
    actionRunCallback<RunScriptAction>(
        actionParametersOf(RunScriptAction.ScriptKey to script, RunScriptAction.LabelKey to label)
    )

@Composable
private fun Remote(status: String) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.remote_body))
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Górny pasek: nazwa + status + POWER
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                // Nazwa, pod spodem marka i status
                Text(
                    "Pilot Hi-Fi",
                    style = TextStyle(color = ColorProvider(TextMain), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    "Denon",
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                )
                Text(
                    status.ifEmpty { "gotowy" },
                    style = TextStyle(color = ColorProvider(TextDim), fontSize = 9.sp),
                    maxLines = 1
                )
            }
            RoundKey(R.drawable.ic_power, "Zasilanie", "pilot_ir_power", R.drawable.btn_power, 48.dp)
        }

        Spacer(GlanceModifier.height(14.dp))

        // Źródła
        Row(GlanceModifier.fillMaxWidth()) {
            SourceKey(R.drawable.ic_bluetooth, "BT", "pilot_ir_bluetooth", "Bluetooth", GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            SourceKey(R.drawable.ic_vinyl, "WINYL", "pilot_ir_analog", "Winyl (analog)", GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.width(8.dp))
            SourceKey(R.drawable.ic_web_radio, "NET", "pilot_ir_radio_internetowe", "Radio internetowe", GlanceModifier.defaultWeight())
        }

        Spacer(GlanceModifier.height(14.dp))

        // Kołyska głośności:  −   🔇   +
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(60.dp)
                .background(ImageProvider(R.drawable.rocker_bg)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RockerKey(R.drawable.ic_volume_down, "Ciszej", "pilot_ir_ciszej", GlanceModifier.defaultWeight())
            Box(GlanceModifier.width(56.dp), contentAlignment = Alignment.Center) {
                RoundKey(R.drawable.ic_volume_off, "Wycisz", "pilot_ir_mute", R.drawable.btn_round, 44.dp)
            }
            RockerKey(R.drawable.ic_volume_up, "Głośniej", "pilot_ir_glosniej", GlanceModifier.defaultWeight())
        }
    }
}

@Composable
private fun RoundKey(icon: Int, label: String, script: String, bg: Int, size: Dp) {
    Box(
        modifier = GlanceModifier
            .size(size)
            .background(ImageProvider(bg))
            .cornerRadius(size / 2)
            .clickable(runScript(script, label)),
        contentAlignment = Alignment.Center
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(size * 0.5f))
    }
}

@Composable
private fun SourceKey(icon: Int, short: String, script: String, label: String, modifier: GlanceModifier) {
    Column(
        modifier = modifier
            .height(64.dp)
            .background(ImageProvider(R.drawable.btn_key))
            .cornerRadius(16.dp)
            .clickable(runScript(script, label)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(24.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text(
            short,
            style = TextStyle(color = ColorProvider(TextDim), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun RockerKey(icon: Int, label: String, script: String, modifier: GlanceModifier) {
    Box(
        modifier = modifier
            .height(60.dp)
            .cornerRadius(30.dp)
            .clickable(runScript(script, label)),
        contentAlignment = Alignment.Center
    ) {
        Image(ImageProvider(icon), contentDescription = label, modifier = GlanceModifier.size(28.dp))
    }
}
