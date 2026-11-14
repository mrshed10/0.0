package com.athar.app.core.designsystem.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.core.designsystem.theme.AtharColors

data class GlassBarItem(val label: String, val icon: ImageVector)

/** Floating frosted pill navigation bar. */
@Composable
fun GlassBottomBar(
    items: List<GlassBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        style = LocalGlassStyle.current.copy(cornerRadius = 32.dp, opacity = 0.16f),
        contentPadding = PaddingValues(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            items.forEachIndexed { index, item ->
                BarItem(
                    item = item,
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BarItem(item: GlassBarItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val tint by animateColorAsState(
        if (selected) AtharColors.Gold else AtharColors.TextTertiary,
        label = "barTint",
    )
    val pill by animateColorAsState(
        if (selected) AtharColors.Gold.copy(alpha = 0.16f) else Color.Transparent,
        label = "barPill",
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(26.dp))
            .background(pill)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
