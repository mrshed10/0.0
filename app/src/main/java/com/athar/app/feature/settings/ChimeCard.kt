package com.athar.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.data.ChimeSound
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.notify.HourlyChime
import kotlinx.coroutines.launch

/** Optional hourly "beep-beep", like old digital watches. */
@Composable
fun ChimeCard() {
    val context = LocalContext.current
    val settings = (context.applicationContext as AtharApplication).container.settings
    val chime by settings.chime.collectAsState()
    val scope = rememberCoroutineScope()

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
        CardHeader(stringResource(R.string.chime_title), Icons.Rounded.Alarm, tint = AtharColors.Teal)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.chime_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(6.dp))
        ChimeSwitch(stringResource(R.string.chime_enable), chime.enabled) { on -> settings.updateChime { it.copy(enabled = on) } }
        if (chime.enabled) {
            Spacer(Modifier.height(8.dp))
            FieldLabel(stringResource(R.string.chime_sound))
            Spacer(Modifier.height(6.dp))
            GlassSegmented(
                options = listOf(
                    stringResource(R.string.chime_sound_classic),
                    stringResource(R.string.chime_sound_single),
                    stringResource(R.string.chime_sound_soft),
                ),
                selectedIndex = chime.sound.ordinal,
                onSelect = { i -> settings.updateChime { it.copy(sound = ChimeSound.entries[i]) } },
                accent = AtharColors.Teal,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.chime_volume), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${chime.volume}%", style = MaterialTheme.typography.labelLarge, color = AtharColors.Teal)
            }
            Slider(
                value = chime.volume.toFloat(),
                onValueChange = { v -> settings.updateChime { it.copy(volume = v.toInt()) } },
                valueRange = 5f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = AtharColors.Teal,
                    activeTrackColor = AtharColors.Teal,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            ChimeSwitch(stringResource(R.string.chime_respect_silent), chime.respectSilent) { on ->
                settings.updateChime { it.copy(respectSilent = on) }
            }
            ChimeSwitch(stringResource(R.string.chime_quiet_hours), chime.quietHours) { on ->
                settings.updateChime { it.copy(quietHours = on) }
            }
            if (chime.quietHours) {
                Row {
                    HourStepper(stringResource(R.string.chime_quiet_from), chime.quietFrom, Modifier.weight(1f)) { h ->
                        settings.updateChime { it.copy(quietFrom = h) }
                    }
                    Spacer(Modifier.width(12.dp))
                    HourStepper(stringResource(R.string.chime_quiet_to), chime.quietTo, Modifier.weight(1f)) { h ->
                        settings.updateChime { it.copy(quietTo = h) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            GlassButton(
                stringResource(R.string.chime_test),
                { scope.launch { HourlyChime.play(context, chime, test = true) } },
                color = AtharColors.Teal,
                filled = false,
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ChimeSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Teal),
        )
    }
}

/** Hour of day, 0–23, shown as 24h "22:00". */
@Composable
private fun HourStepper(label: String, hour: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassButton("−", { onChange((hour + 23) % 24) }, filled = false)
            Text(
                "%02d:00".format(hour),
                style = MaterialTheme.typography.titleMedium,
                color = AtharColors.Teal,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            GlassButton("+", { onChange((hour + 1) % 24) }, filled = false)
        }
    }
}
