package com.athar.app.core.notify

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.athar.app.R
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.theme.AtharColors
import kotlinx.coroutines.launch

/**
 * Shows the selected tone and lets the user pick a system sound, import an
 * audio file (e.g. a full adhan mp3), or go back to the default.
 */
@Composable
fun ToneRow(
    label: String,
    tone: String?,
    onChange: (String?) -> Unit,
    modifier: Modifier = Modifier,
    /** Shown when no tone is chosen (default: "Default"). */
    defaultLabel: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val title = remember(tone, defaultLabel) { Tones.title(context, tone, defaultLabel) }
    var library by remember { mutableStateOf(false) }
    val importFailed = stringResource(R.string.sound_import_failed)

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            }
            onChange(uri?.toString())
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val saved = Tones.import(context, uri)
                if (saved != null) onChange(saved) else android.widget.Toast.makeText(context, importFailed, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = AtharColors.Gold)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = AtharColors.TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tone != null) {
                Text(
                    stringResource(R.string.tone_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = AtharColors.Rose,
                    modifier = Modifier.clickable { onChange(null) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(
                stringResource(R.string.tone_system),
                {
                    systemPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, label)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone?.let(Uri::parse)),
                    )
                },
                filled = false,
                icon = Icons.Rounded.MusicNote,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                stringResource(R.string.tone_file),
                { filePicker.launch(arrayOf("audio/*")) },
                filled = false,
                icon = Icons.Rounded.AudioFile,
                modifier = Modifier.weight(1f),
            )
        }
        GlassButton(
            stringResource(R.string.sounds_mine),
            { library = true },
            filled = false,
            icon = Icons.Rounded.LibraryMusic,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (library) {
        SoundLibrarySheet(
            selected = tone,
            onDismiss = { library = false },
            onPick = {
                onChange(it)
                library = false
            },
        )
    }
}
