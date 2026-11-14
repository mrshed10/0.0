package com.athar.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.LocalGlassStyle
import com.athar.app.core.designsystem.theme.AtharColors

/** Icon badge + title + optional trailing content + "pin as widget" action. */
@Composable
fun CardHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = AtharColors.Gold,
    onPin: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        if (onPin != null) {
            IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = stringResource(R.string.pin_to_widget),
                    tint = AtharColors.TextTertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Small translucent label pill. */
@Composable
fun GlassChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Thin rounded progress bar with a gradient fill. */
@Composable
fun GlassProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))),
        )
    }
}

/** Round frosted icon button. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier.size(48.dp),
        style = LocalGlassStyle.current.copy(cornerRadius = 24.dp),
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(22.dp))
    }
}

/** Section title above free-floating content (e.g. a horizontal list). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}
