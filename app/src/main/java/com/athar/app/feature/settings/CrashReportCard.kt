package com.athar.app.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.R
import com.athar.app.core.data.CrashLog
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors

/** Shown only after a crash: the error details, to copy or share with the developer. */
@Composable
fun CrashReportCard() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.last(context)) }
    val copied = stringResource(R.string.crash_copied)
    val current = report ?: return
    GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Rose) {
        CardHeader(stringResource(R.string.crash_title), Icons.Rounded.BugReport, tint = AtharColors.Rose)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.crash_body), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text(
            current.lines().take(8).joinToString("\n"),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = AtharColors.TextTertiary,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(stringResource(R.string.crash_copy), {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Athar crash", current))
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            }, filled = false, color = AtharColors.Rose, icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f))
            GlassButton(stringResource(R.string.crash_share), {
                context.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, current), null)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }, filled = false, color = AtharColors.Rose, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        GlassButton(stringResource(R.string.crash_clear), {
            CrashLog.clear(context)
            report = null
        }, color = AtharColors.Rose, modifier = Modifier.fillMaxWidth())
    }
}
