package com.athar.app.feature.prayer

import android.content.Intent
import android.os.Build
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.runtime.rememberCoroutineScope
import com.athar.app.core.notify.PrayerSpeech
import com.athar.app.core.notify.Speaker
import kotlinx.coroutines.launch
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.athar.app.R
import com.athar.app.core.data.PrayerPrefs
import com.athar.app.core.designsystem.component.AnimatedCountdown
import com.athar.app.core.designsystem.component.AnimatedGlassProgress
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatClock
import com.athar.app.core.notify.RingerControl
import com.athar.app.core.notify.ToneRow
import com.athar.app.core.ui.Stepper
import java.time.Duration
import java.util.Locale

private fun hms(d: Duration): String {
    val s = d.seconds.coerceAtLeast(0)
    return String.format(Locale.ENGLISH, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = AtharColors.Night, checkedTrackColor = AtharColors.Gold),
        )
    }
}

/** "± minutes" stepper. */
@Composable
private fun MinutesStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Stepper(
        label,
        stringResource(R.string.minutes_value, value.toString()),
        { n -> onChange((value + n.toInt()).coerceIn(range)) },
        modifier,
    )
}

/** Elapsed time since the adhan, time left until the prayer time ends, and iqama countdown — animated. */
@Composable
fun CurrentPrayerCard(state: PrayerUiState, current: CurrentPrayer) {
    val locale = currentLocale()
    val now = state.now
    val total = Duration.between(current.start, current.end).seconds.coerceAtLeast(1)
    val progress = current.elapsed(now).seconds.toFloat() / total
    val numbers = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = AtharColors.TextPrimary)
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
        CardHeader(
            stringResource(R.string.current_prayer, stringResource(current.prayer.label)),
            Icons.Rounded.Timer,
            tint = AtharColors.Teal,
            trailing = { GlassChip("${(progress * 100).toInt().coerceIn(0, 100)}%", AtharColors.Teal) },
        )
        Spacer(Modifier.height(12.dp))
        AnimatedGlassProgress(progress, colors = listOf(AtharColors.Teal, AtharColors.Gold, AtharColors.Rose))
        Spacer(Modifier.height(6.dp))
        Row {
            Text(current.start.toLocalTime().formatClock(locale), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary, modifier = Modifier.weight(1f))
            Text(current.end.toLocalTime().formatClock(locale), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.elapsed_since_adhan), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                AnimatedCountdown(hms(current.elapsed(now)), numbers)
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.time_left_to_end), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
                AnimatedCountdown(hms(current.left(now)), numbers.copy(color = AtharColors.Rose))
            }
        }
        current.iqamaAt?.let { iqama ->
            Spacer(Modifier.height(12.dp))
            val toIqama = Duration.between(now, iqama)
            if (!toIqama.isNegative) {
                val iqamaTotal = Duration.between(current.start, iqama).seconds.coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.iqama_in, ""), style = MaterialTheme.typography.labelLarge, color = AtharColors.Gold)
                    AnimatedCountdown(hms(toIqama).substring(3), MaterialTheme.typography.labelLarge.copy(color = AtharColors.Gold, fontWeight = FontWeight.Bold))
                }
                Spacer(Modifier.height(6.dp))
                AnimatedGlassProgress(
                    1f - toIqama.seconds.toFloat() / iqamaTotal,
                    colors = listOf(AtharColors.Violet, AtharColors.Gold),
                    height = 6.dp,
                )
            } else {
                GlassChip(stringResource(R.string.iqama_done, iqama.toLocalTime().formatClock(locale)), AtharColors.Teal)
            }
        }
    }
}

/** Per-prayer settings: adhan, pre-alerts, iqama, silent. */
@Composable
fun PrayerSettingsSheet(
    prayer: Prayer,
    prefs: PrayerPrefs,
    onDismiss: () -> Unit,
    onSave: (PrayerAlert, Boolean) -> Unit,
    onApplyAll: (PrayerAlert, Boolean) -> Unit,
) {
    var draft by remember(prayer) { mutableStateOf(prefs.alert(prayer)) }
    var silent by remember(prayer) { mutableStateOf(prayer in prefs.silent.prayers) }
    val name = stringResource(prayer.label)

    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.prayer_settings_title, name), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SwitchRow(stringResource(R.string.adhan_on), draft.adhan, { draft = draft.copy(adhan = it) })

        // Before adhan
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.before_adhan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            SwitchRow(stringResource(R.string.pre_alert), draft.preEnabled, { draft = draft.copy(preEnabled = it) })
            if (draft.preEnabled) {
                Spacer(Modifier.height(8.dp))
                MinutesStepper(stringResource(R.string.minutes_before), draft.preMinutes, 1..120, { draft = draft.copy(preMinutes = it) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FieldLabel(stringResource(R.string.alert_count))
                Spacer(Modifier.height(6.dp))
                GlassSegmented(
                    options = listOf("1", "2", "3"),
                    selectedIndex = draft.preCount - 1,
                    onSelect = { draft = draft.copy(preCount = it + 1) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.alert_times, draft.preOffsets().joinToString("، ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = AtharColors.TextTertiary,
                )
                Spacer(Modifier.height(8.dp))
                ToneRow(stringResource(R.string.tone_label), draft.preTone, { draft = draft.copy(preTone = it) })
            }
        }

        // After adhan
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.after_adhan), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            SwitchRow(stringResource(R.string.iqama_countdown), draft.iqamaEnabled, { draft = draft.copy(iqamaEnabled = it) })
            Spacer(Modifier.height(8.dp))
            MinutesStepper(stringResource(R.string.iqama_after), draft.iqamaMinutes, 0..90, { draft = draft.copy(iqamaMinutes = it) }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                stringResource(R.string.iqama_alert),
                draft.iqamaAlert,
                { draft = draft.copy(iqamaAlert = it) },
                enabled = draft.iqamaEnabled,
            )
            if (draft.iqamaAlert && draft.iqamaEnabled) {
                Spacer(Modifier.height(8.dp))
                ToneRow(stringResource(R.string.tone_label), draft.iqamaTone, { draft = draft.copy(iqamaTone = it) })
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                stringResource(R.string.silent_at_iqama),
                silent,
                { silent = it },
                subtitle = if (prefs.silent.enabled) null else stringResource(R.string.silent_master_off),
            )
        }

        GlassButton(
            stringResource(R.string.save),
            { onSave(draft, silent) },
            icon = Icons.Rounded.Check,
            modifier = Modifier.fillMaxWidth(),
        )
        GlassButton(
            stringResource(R.string.apply_to_all_prayers),
            { onApplyAll(draft, silent) },
            filled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Adhan volume, test playback, play-in-silent and tones. */
@Composable
fun AdhanAudioCard(prefs: PrayerPrefs, viewModel: PrayerViewModel) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    fun stopPreview() {
        player?.let { p ->
            runCatching { p.stop() }
            p.release()
        }
        player = null
        playing = false
    }
    DisposableEffect(Unit) { onDispose { stopPreview() } }
    fun playPreview() {
        stopPreview()
        if (prefs.volume == AdhanVolume.Silent) return
        val uri = prefs.adhanTone?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(if (prefs.adhanInSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, uri)
                setVolume(prefs.volume.factor, prefs.volume.factor)
                setOnCompletionListener { stopPreview() }
                prepare()
                start()
            }
        }.getOrNull()
        playing = player != null
    }

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
        CardHeader(stringResource(R.string.adhan_volume), Icons.Rounded.VolumeUp)
        Spacer(Modifier.height(10.dp))
        GlassSegmented(
            options = listOf(
                stringResource(R.string.volume_alarm),
                stringResource(R.string.volume_medium),
                stringResource(R.string.volume_low),
                stringResource(R.string.volume_silent),
            ),
            selectedIndex = prefs.volume.ordinal,
            onSelect = { stopPreview(); viewModel.setVolume(AdhanVolume.entries[it]) },
        )
        Spacer(Modifier.height(10.dp))
        GlassButton(
            stringResource(if (playing) R.string.stop_test else R.string.play_test),
            { if (playing) stopPreview() else playPreview() },
            filled = false,
            enabled = prefs.volume != AdhanVolume.Silent,
            icon = if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        SwitchRow(
            stringResource(R.string.adhan_in_silent),
            prefs.adhanInSilent,
            { stopPreview(); viewModel.setAdhanInSilent(it) },
            subtitle = stringResource(R.string.adhan_in_silent_hint),
        )
        Spacer(Modifier.height(14.dp))
        ToneRow(stringResource(R.string.tone_fajr), prefs.fajrTone, viewModel::setFajrTone)
        Spacer(Modifier.height(14.dp))
        ToneRow(stringResource(R.string.tone_other_prayers), prefs.adhanTone, { stopPreview(); viewModel.setAdhanTone(it) })
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.adhan_tone_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
    Spacer(Modifier.height(12.dp))
    VoiceAlertsCard(prefs, viewModel)
    LockScreenStopCard()
}

/**
 * Spoken announcements before the prayer, at the adhan and at iqama: which
 * ones, the voice (engine, natural voices, speed, pitch) or a recorded file.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceAlertsCard(prefs: PrayerPrefs, viewModel: PrayerViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val voice = prefs.voice
    var speaking by remember { mutableStateOf(false) }
    // One engine instance for listing voices and testing; rebuilt when the engine changes.
    val speaker = remember(voice.engine) { Speaker(context, voice.engine) }
    DisposableEffect(speaker) { onDispose { speaker.shutdown() } }
    var engines by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    var voices by remember(voice.engine) { mutableStateOf(emptyList<android.speech.tts.Voice>()) }
    LaunchedEffect(speaker) {
        if (speaker.awaitReady()) {
            engines = speaker.engines()
            voices = speaker.voices()
        }
    }
    val dhuhr = stringResource(Prayer.Dhuhr.label)
    val natural = stringResource(R.string.voice_natural)
    val network = stringResource(R.string.voice_network)
    val local = stringResource(R.string.voice_local)

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Teal) {
        CardHeader(stringResource(R.string.voice_title), Icons.Rounded.RecordVoiceOver, tint = AtharColors.Teal)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.voice_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)

        // Which alerts speak, and an optional recorded file for each.
        listOf(
            Triple(R.string.voice_pre_switch, voice.pre, PrayerSpeech.pre(context, dhuhr, 10)),
            Triple(R.string.voice_adhan_switch, voice.adhan, PrayerSpeech.adhan(context, dhuhr)),
            Triple(R.string.voice_iqama_switch, voice.iqama, PrayerSpeech.iqama(context, dhuhr)),
        ).forEachIndexed { i, (label, on, sample) ->
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                stringResource(label),
                on,
                { v -> viewModel.setVoice { when (i) { 0 -> it.copy(pre = v); 1 -> it.copy(adhan = v); else -> it.copy(iqama = v) } } },
                subtitle = sample,
            )
            if (on) {
                Spacer(Modifier.height(6.dp))
                ToneRow(
                    stringResource(R.string.voice_file_label),
                    when (i) { 0 -> voice.preFile; 1 -> voice.adhanFile; else -> voice.iqamaFile },
                    { f -> viewModel.setVoice { when (i) { 0 -> it.copy(preFile = f); 1 -> it.copy(adhanFile = f); else -> it.copy(iqamaFile = f) } } },
                    defaultLabel = stringResource(R.string.voice_file_none),
                )
            }
        }

        // The synthetic voice: engine, voice, speed and pitch.
        Spacer(Modifier.height(14.dp))
        FieldLabel(stringResource(R.string.voice_engine))
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(
                stringResource(R.string.voice_engine_default),
                if (voice.engine == null) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { viewModel.setVoice { it.copy(engine = null, voiceName = null) } },
            )
            engines.forEach { (pkg, label) ->
                GlassChip(
                    label,
                    if (voice.engine == pkg) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { viewModel.setVoice { it.copy(engine = pkg, voiceName = null) } },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        FieldLabel(stringResource(R.string.voice_choose))
        Spacer(Modifier.height(6.dp))
        if (voices.isEmpty()) {
            Text(stringResource(R.string.voice_none_found), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassChip(
                stringResource(R.string.voice_best),
                if (voice.voiceName == null) AtharColors.Gold else AtharColors.TextSecondary,
                Modifier.clickable { viewModel.setVoice { it.copy(voiceName = null) } },
            )
            voices.take(12).forEachIndexed { i, v ->
                GlassChip(
                    stringResource(R.string.voice_item, Speaker.describe(i, v, natural, network, local)),
                    if (voice.voiceName == v.name) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { viewModel.setVoice { it.copy(voiceName = v.name) } },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        VoiceSlider(stringResource(R.string.voice_rate), voice.rate) { r -> viewModel.setVoice { it.copy(rate = r) } }
        VoiceSlider(stringResource(R.string.voice_pitch), voice.pitch) { p -> viewModel.setVoice { it.copy(pitch = p) } }
        Spacer(Modifier.height(10.dp))
        GlassButton(
            stringResource(R.string.voice_test),
            {
                if (!speaking) {
                    speaking = true
                    scope.launch {
                        speaker.speak(PrayerSpeech.pre(context, dhuhr, 10), alarmStream = false, volume = 1f, voice.voiceName, voice.rate, voice.pitch)
                        speaking = false
                    }
                }
            },
            color = AtharColors.Teal,
            filled = false,
            enabled = !speaking,
            icon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.voice_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        Spacer(Modifier.height(6.dp))
        GlassButton(
            stringResource(R.string.voice_install),
            {
                runCatching {
                    context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.recoverCatching {
                    context.startActivity(
                        Intent(android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            filled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VoiceSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(String.format(Locale.ENGLISH, "%.2f×", value), style = MaterialTheme.typography.labelLarge, color = AtharColors.Teal)
    }
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = { onChange((it * 20).toInt() / 20f) },
        valueRange = 0.5f..1.5f,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = AtharColors.Teal,
            activeTrackColor = AtharColors.Teal,
            inactiveTrackColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.15f),
        ),
    )
}

/**
 * Android 14+ may need a one-time permission for the adhan stop screen to
 * appear over the lock screen. Hidden when nothing is needed.
 */
@Composable
private fun LockScreenStopCard() {
    if (Build.VERSION.SDK_INT < 34) return
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var resumed by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumed++ } }
    val allowed = remember(resumed) {
        context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    }
    if (allowed) return
    Spacer(Modifier.height(12.dp))
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Rose) {
        CardHeader(stringResource(R.string.adhan_lock_title), Icons.Rounded.Stop, tint = AtharColors.Rose)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.adhan_lock_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        GlassButton(
            stringResource(R.string.allow),
            {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:" + context.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            color = AtharColors.Rose,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Phone on silent/vibrate during the chosen prayers, with Jumu'ah rule. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SilentModeCard(prefs: PrayerPrefs, viewModel: PrayerViewModel) {
    val context = LocalContext.current
    val silent = prefs.silent
    val lifecycle = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val hasAccess = remember(refresh) { RingerControl.hasPolicyAccess(context) }

    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet) {
        CardHeader(stringResource(R.string.silent_title), Icons.Rounded.DoNotDisturbOn, tint = AtharColors.Violet)
        Spacer(Modifier.height(8.dp))
        SwitchRow(stringResource(R.string.silent_enable), silent.enabled, { on -> viewModel.updateSilent { it.copy(enabled = on) } })
        if (!silent.enabled) return@GlassCard

        if (silent.mode == SilentMode.Silent && !hasAccess) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.silent_need_access), style = MaterialTheme.typography.bodyMedium, color = AtharColors.Rose)
            Spacer(Modifier.height(6.dp))
            GlassButton(
                stringResource(R.string.perm_allow),
                { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                color = AtharColors.Rose,
            )
        }

        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(R.string.silent_prayers))
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Prayer.entries.filter { it.isObligatory }.forEach { p ->
                val on = p in silent.prayers
                GlassChip(
                    (if (on) "✓ " else "") + stringResource(p.label),
                    if (on) AtharColors.Violet else AtharColors.TextTertiary,
                    Modifier.clickable {
                        viewModel.updateSilent { it.copy(prayers = if (on) it.prayers - p else it.prayers + p) }
                    },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(R.string.silent_phone_mode))
        Spacer(Modifier.height(6.dp))
        GlassSegmented(
            options = listOf(stringResource(R.string.mode_silent), stringResource(R.string.mode_vibrate)),
            selectedIndex = silent.mode.ordinal,
            onSelect = { i -> viewModel.updateSilent { it.copy(mode = SilentMode.entries[i]) } },
            accent = AtharColors.Violet,
        )

        Spacer(Modifier.height(12.dp))
        FieldLabel(stringResource(R.string.silent_starts_after))
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Prayer.entries.filter { it.isObligatory && it in silent.prayers }.forEach { p ->
                MinutesStepper(
                    stringResource(p.label),
                    prefs.alert(p).iqamaMinutes,
                    0..90,
                    { viewModel.setIqamaMinutes(p, it) },
                    Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        MinutesStepper(
            stringResource(R.string.silent_duration),
            silent.durationMinutes,
            5..180,
            { m -> viewModel.updateSilent { it.copy(durationMinutes = m) } },
            Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(14.dp))
        SwitchRow(
            stringResource(R.string.jumuah_rule),
            silent.jumuah,
            { on -> viewModel.updateSilent { it.copy(jumuah = on) } },
            subtitle = stringResource(R.string.jumuah_rule_hint),
        )
        if (silent.jumuah) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MinutesStepper(
                    stringResource(R.string.jumuah_start),
                    silent.jumuahStartAfter,
                    0..120,
                    { m -> viewModel.updateSilent { it.copy(jumuahStartAfter = m) } },
                    Modifier.weight(1f),
                )
                MinutesStepper(
                    stringResource(R.string.silent_duration),
                    silent.jumuahDuration,
                    5..180,
                    { m -> viewModel.updateSilent { it.copy(jumuahDuration = m) } },
                    Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.silent_restore_note), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
}

@Composable
fun ResetPrayerDefaults(onReset: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ConfirmButton(
            text = stringResource(R.string.restore_defaults),
            confirmText = stringResource(R.string.tap_to_confirm),
            onConfirm = onReset,
            icon = Icons.Rounded.RestartAlt,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.restore_defaults_hint), style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
    }
}
