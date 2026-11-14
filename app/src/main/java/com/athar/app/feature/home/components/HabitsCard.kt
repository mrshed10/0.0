package com.athar.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.formatNumber
import com.athar.app.core.ui.asString
import com.athar.app.feature.home.HabitIcon
import com.athar.app.feature.home.HabitItem

@Composable
fun HabitsCard(
    habits: List<HabitItem>,
    onToggle: (Long) -> Unit,
    onPin: () -> Unit,
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            habits.forEach { HabitBubble(it, onToggle) }
        }
    }
}

@Composable
private fun HabitBubble(habit: HabitItem, onToggle: (Long) -> Unit) {
    val ring = if (habit.doneToday) {
        Modifier.background(Brush.linearGradient(listOf(AtharColors.Gold, AtharColors.Rose)))
    } else {
        Modifier
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
    }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onToggle(habit.id) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).then(ring), contentAlignment = Alignment.Center) {
            Icon(
                habit.icon.vector(),
                contentDescription = null,
                tint = if (habit.doneToday) AtharColors.Night else AtharColors.TextPrimary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(habit.title.asString(), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = if (habit.streak > 0) AtharColors.Gold else AtharColors.TextTertiary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                habit.streak.formatNumber(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun HabitIcon.vector(): ImageVector = when (this) {
    HabitIcon.Quran -> Icons.AutoMirrored.Rounded.MenuBook
    HabitIcon.Walk -> Icons.AutoMirrored.Rounded.DirectionsWalk
    HabitIcon.Water -> Icons.Rounded.WaterDrop
    HabitIcon.Sleep -> Icons.Rounded.Bedtime
}
