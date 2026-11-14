package com.athar.app.feature.tasks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatGregorian
import java.time.LocalDate

/**
 * One task line: the circle toggles completion, the rest opens the task
 * (when [onClick] is set). Used on the Tasks screen and the home card.
 */
@Composable
fun TaskRow(
    task: Task,
    today: LocalDate,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    showDue: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val fill by animateColorAsState(if (task.done) AtharColors.Gold else Color.Transparent, label = "taskFill")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { (onClick ?: onToggle)() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.5.dp, if (task.done) AtharColors.Gold else AtharColors.TextTertiary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (task.done) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AtharColors.Night, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (task.done) AtharColors.TextTertiary else AtharColors.TextPrimary,
                textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
            )
            val due = task.due
            if (showDue && due != null && !task.done) {
                Text(
                    dueLabel(due, today),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (due.isBefore(today)) AtharColors.Rose else AtharColors.TextTertiary,
                )
            }
        }
    }
}

@Composable
fun dueLabel(due: LocalDate, today: LocalDate): String = when (due) {
    today -> stringResource(R.string.today)
    today.plusDays(1) -> stringResource(R.string.tomorrow)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> due.formatGregorian(currentLocale(), if (due.year == today.year) "EEE d MMM" else "d MMM yyyy")
}
