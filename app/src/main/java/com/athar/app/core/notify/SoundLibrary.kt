package com.athar.app.core.notify

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.AtharApplication
import com.athar.app.R
import com.athar.app.core.data.AppContainer
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Sound URIs currently chosen somewhere in the settings (adhan, alerts, voice, salaries). */
fun soundsInUse(container: AppContainer): Set<String> {
    val prayer = container.settings.prayer.value
    val general = container.settings.general.value
    return buildSet {
        prayer.fajrTone?.let(::add)
        prayer.adhanTone?.let(::add)
        prayer.alerts.values.forEach { a -> a.preTone?.let(::add); a.iqamaTone?.let(::add) }
        prayer.voice.preFile?.let(::add)
        prayer.voice.adhanFile?.let(::add)
        prayer.voice.iqamaFile?.let(::add)
        general.salaryTone?.let(::add)
    }
}

/** Settings card: the user's own sounds, kept until the user deletes them. */
@Composable
fun MySoundsCard() {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    val count by produceState(0, version, open) { value = withContext(Dispatchers.IO) { Tones.library(context).size } }
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Gold) {
        CardHeader(stringResource(R.string.sounds_mine), Icons.Rounded.LibraryMusic)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.sounds_body, count.toString()), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(10.dp))
        GlassButton(stringResource(R.string.sounds_manage), { open = true }, filled = false, icon = Icons.Rounded.LibraryMusic, modifier = Modifier.fillMaxWidth())
    }
    if (open) SoundLibrarySheet(selected = null, onDismiss = { open = false; version++ }, onPick = null)
}

/**
 * The sound library. With [onPick] it is a chooser; without, a manager.
 * Sounds can be played, added and (only here, by the user) deleted.
 */
@Composable
fun SoundLibrarySheet(selected: String?, onDismiss: () -> Unit, onPick: ((String) -> Unit)?) {
    val context = LocalContext.current
    val container = (context.applicationContext as AtharApplication).container
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val sounds by produceState(emptyList<SavedSound>(), version) { value = withContext(Dispatchers.IO) { Tones.library(context) } }
    val inUse = remember(version) { soundsInUse(container) }
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    val importFailed = stringResource(R.string.sound_import_failed)
    val deleted = stringResource(R.string.sound_deleted)

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing = null
    }
    DisposableEffect(Unit) { onDispose { stop() } }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                if (Tones.import(context, uri) == null) Toast.makeText(context, importFailed, Toast.LENGTH_SHORT).show()
                version++
            }
        }
    }

    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.sounds_mine), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.sounds_safe_note), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
        GlassButton(
            stringResource(R.string.sounds_add),
            { picker.launch(arrayOf("audio/*")) },
            icon = Icons.Rounded.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        if (sounds.isEmpty()) {
            Text(stringResource(R.string.sounds_empty), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextTertiary)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            sounds.forEach { sound ->
                val isSelected = sound.uri == selected
                val used = sound.uri in inUse
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) AtharColors.Gold.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.05f))
                        .then(if (onPick != null) Modifier.clickable { stop(); onPick(sound.uri) } else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        if (playing == sound.uri) {
                            stop()
                        } else {
                            stop()
                            player = runCatching {
                                MediaPlayer().apply {
                                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
                                    setDataSource(context, Uri.parse(sound.uri))
                                    setOnCompletionListener { stop() }
                                    prepare()
                                    start()
                                }
                            }.getOrNull()
                            playing = if (player != null) sound.uri else null
                        }
                    }) {
                        Icon(
                            if (playing == sound.uri) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = stringResource(R.string.play_test),
                            tint = AtharColors.Gold,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(sound.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(
                                sound.durationMs?.let { formatDuration(it) },
                                formatSize(sound.sizeBytes),
                                if (used) stringResource(R.string.sound_in_use) else null,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (used) AtharColors.Teal else AtharColors.TextTertiary,
                        )
                    }
                    if (isSelected) Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = AtharColors.Gold)
                    Spacer(Modifier.width(4.dp))
                    if (confirmDelete == sound.uri) {
                        Text(
                            stringResource(if (used) R.string.sound_delete_used else R.string.tap_to_confirm),
                            style = MaterialTheme.typography.labelMedium,
                            color = AtharColors.Rose,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(AtharColors.Rose.copy(alpha = 0.15f))
                                .clickable {
                                    if (playing == sound.uri) stop()
                                    Tones.delete(context, sound)
                                    confirmDelete = null
                                    Toast.makeText(context, deleted, Toast.LENGTH_SHORT).show()
                                    version++
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    } else {
                        IconButton(onClick = { confirmDelete = sound.uri }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = stringResource(R.string.delete), tint = AtharColors.Rose)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return String.format(Locale.ENGLISH, "%d:%02d", s / 60, s % 60)
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) String.format(Locale.ENGLISH, "%.1f MB", bytes / 1_000_000f) else "${(bytes / 1000).coerceAtLeast(1)} KB"
