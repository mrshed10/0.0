package com.athar.app.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import com.athar.app.feature.habits.HabitBubble
import com.athar.app.feature.habits.HabitSummary

/** Up to five habits; tapping a bubble marks today done (stored). */
@Composable
fun HabitsCard(
    habits: List<HabitSummary>,
    onToggle: (Long) -> Unit,
    onPin: () -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier.fillMaxWidth(), accent = AtharColors.Rose) {
        CardHeader(
            stringResource(R.string.habits),
            Icons.Rounded.LocalFireDepartment,
            tint = AtharColors.Rose,
            onPin = onPin,
        )
        Spacer(Modifier.height(14.dp))
        if (habits.isEmpty()) {
            GlassButton(
                stringResource(R.string.add_habit),
                onOpenAll,
                color = AtharColors.Rose,
                filled = false,
                modifier = Modifier.fillMaxWidth(),
            )
            return@GlassCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            habits.take(5).forEach { summary ->
                Column(Modifier.widthIn(max = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    HabitBubble(summary.habit.icon, summary.habit.tone.color, summary.doneToday, { onToggle(summary.habit.id) })
                    Spacer(Modifier.height(6.dp))
                    Text(
                        summary.habit.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = if (summary.currentStreak > 0) AtharColors.Gold else AtharColors.TextTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(summary.currentStreak.formatNumber(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (habits.size > 5) {
            Spacer(Modifier.height(10.dp))
            GlassButton(stringResource(R.string.see_all), onOpenAll, filled = false, modifier = Modifier.fillMaxWidth())
        }
    }
}
