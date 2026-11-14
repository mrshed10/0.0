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
import com.athar.app.core.data.TasbihState
import com.athar.app.feature.home.NextSalary
import com.athar.app.feature.prayer.dhikr
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun SalaryCard(
    today: LocalDate,
    salary: NextSalary?,
    onPin: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    GlassCard(modifier.fillMaxHeight(), accent = AtharColors.Teal, onClick = onOpen) {
        CardHeader(stringResource(R.string.salary), Icons.Rounded.Payments, tint = AtharColors.Teal, onPin = onPin)
        Spacer(Modifier.height(12.dp))
        if (salary == null) {
            Text(stringResource(R.string.add_salary), style = MaterialTheme.typography.bodyLarge, color = AtharColors.Teal)
            return@GlassCard
        }
        val days = ChronoUnit.DAYS.between(today, salary.date)
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
            salary.date.formatGregorian(locale, "EEEE d MMMM"),
            style = MaterialTheme.typography.bodySmall,
            color = AtharColors.TextSecondary,
        )
        Text(
            salary.name,
            style = MaterialTheme.typography.labelSmall,
            color = AtharColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Tap to count, long-press to reset the round. Shares state with the Tasbih page. */
@Composable
fun TasbihCard(state: TasbihState, onTap: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val dhikr = state.dhikr()
    val target = dhikr.count.coerceAtLeast(1)
    val inRound = state.count % target
    GlassCard(
        modifier = modifier.fillMaxHeight(),
        accent = AtharColors.Violet,
        onClick = {
            val completes = (state.count + 1) % target == 0
            haptics.performHapticFeedback(if (completes) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
            onTap()
        },
        onLongClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onReset()
        },
    ) {
        CardHeader(stringResource(R.string.tasbih), Icons.Rounded.TouchApp, tint = AtharColors.Violet)
        Spacer(Modifier.height(12.dp))
        Row {
            Text(
                inRound.formatNumber(),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                " / " + target.formatNumber(),
                style = MaterialTheme.typography.bodyMedium,
                color = AtharColors.TextSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Text(
            dhikr.ar,
            style = MaterialTheme.typography.bodySmall,
            color = AtharColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        GlassProgressBar(progress = inRound / target.toFloat(), color = AtharColors.Violet)
    }
}
