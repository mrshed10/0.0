package com.athar.app.feature.home.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassProgressBar
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatNumber
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SalaryCard(today: LocalDate, payDate: LocalDate, onPin: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val days = ChronoUnit.DAYS.between(today, payDate)

    GlassCard(modifier.fillMaxHeight(), accent = AtharColors.Teal) {
        CardHeader(stringResource(R.string.salary), Icons.Rounded.Payments, tint = AtharColors.Teal, onPin = onPin)
        Spacer(Modifier.height(12.dp))
        if (days == 0L) {
            Text(
                stringResource(R.string.today),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = AtharColors.Teal,
            )
        } else {
            Row {
                Text(
                    days.formatNumber(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    " " + stringResource(R.string.unit_days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AtharColors.TextSecondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Text(
            payDate.formatGregorian(locale, "EEEE d MMMM"),
            style = MaterialTheme.typography.bodySmall,
            color = AtharColors.TextSecondary,
        )
        Text(
            stringResource(R.string.salary_country_sa),
            style = MaterialTheme.typography.labelSmall,
            color = AtharColors.TextTertiary,
        )
    }
}

/** Tap to count, long-press to reset. A cycle is 33. */
@Composable
fun TasbihCard(count: Int, onTap: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    GlassCard(
        modifier = modifier.fillMaxHeight(),
        accent = AtharColors.Violet,
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onTap()
        },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onReset()
        },
    ) {
        CardHeader(stringResource(R.string.tasbih), Icons.Rounded.TouchApp, tint = AtharColors.Violet)
        Spacer(Modifier.height(12.dp))
        Text(count.formatNumber(), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.tasbih_hint),
            style = MaterialTheme.typography.bodySmall,
            color = AtharColors.TextSecondary,
        )
        Spacer(Modifier.height(10.dp))
        GlassProgressBar(progress = (count % 33) / 33f, color = AtharColors.Violet)
    }
}
