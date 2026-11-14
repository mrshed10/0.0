package com.athar.app.feature.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TaskAlt
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
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassProgressBar
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import com.athar.app.core.ui.asString
import com.athar.app.feature.home.TaskItem

@Composable
fun TasksCard(
    tasks: List<TaskItem>,
    onToggle: (Long) -> Unit,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = tasks.count { it.done }
    GlassCard(modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(R.string.today_tasks),
            icon = Icons.Rounded.TaskAlt,
            onPin = onPin,
            trailing = {
                Text(
                    stringResource(R.string.tasks_done, done.formatNumber(), tasks.size.formatNumber()),
                    style = MaterialTheme.typography.labelMedium,
                    color = AtharColors.TextSecondary,
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        GlassProgressBar(
            progress = if (tasks.isEmpty()) 0f else done / tasks.size.toFloat(),
            color = AtharColors.Gold,
        )
        Spacer(Modifier.height(6.dp))
        tasks.forEach { task -> TaskRow(task, onToggle) }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: (Long) -> Unit) {
    val fill by animateColorAsState(
        if (task.done) AtharColors.Gold else Color.Transparent,
        label = "taskFill",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(task.id) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
        Spacer(Modifier.width(12.dp))
        Text(
            task.title.asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.done) AtharColors.TextTertiary else AtharColors.TextPrimary,
            textDecoration = if (task.done) TextDecoration.LineThrough else TextDecoration.None,
        )
    }
}
