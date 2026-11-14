package com.athar.app.feature.home.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassProgressBar
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import com.athar.app.feature.tasks.Task
import com.athar.app.feature.tasks.TaskRow
import java.time.LocalDate

/** Today's tasks; ticking here writes straight to the database. */
@Composable
fun TasksCard(
    tasks: List<Task>,
    today: LocalDate,
    onToggle: (Task) -> Unit,
    onPin: () -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = tasks.count { it.done }
    GlassCard(modifier.fillMaxWidth()) {
        CardHeader(
            title = stringResource(R.string.today_tasks),
            icon = Icons.Rounded.TaskAlt,
            onPin = onPin,
            trailing = {
                if (tasks.isNotEmpty()) {
                    Text(
                        stringResource(R.string.tasks_done, done.formatNumber(), tasks.size.formatNumber()),
                        style = MaterialTheme.typography.labelMedium,
                        color = AtharColors.TextSecondary,
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Text(
                stringResource(R.string.home_no_tasks),
                style = MaterialTheme.typography.bodyMedium,
                color = AtharColors.TextSecondary,
            )
        } else {
            GlassProgressBar(progress = done / tasks.size.toFloat(), color = AtharColors.Gold)
            Spacer(Modifier.height(4.dp))
            tasks.forEach { task ->
                TaskRow(task = task, today = today, onToggle = { onToggle(task) })
            }
        }
        Spacer(Modifier.height(8.dp))
        GlassButton(
            text = stringResource(R.string.see_all),
            onClick = onOpenAll,
            filled = false,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
