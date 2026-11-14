package com.athar.app.core.designsystem.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.athar.app.core.designsystem.theme.AtharColors
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Frosted-glass container: real background blur (Android 12+, tinted fallback
 * below), a translucent tint, an optional accent wash and a light rim.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: GlassStyle = LocalGlassStyle.current,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(style.cornerRadius)
    val hazeStyle = HazeStyle(
        AtharColors.Night,
        HazeTint(style.tint.copy(alpha = style.opacity)),
        style.blurRadius,
        style.noise,
        HazeTint(AtharColors.Night.copy(alpha = 0.75f)),
    )
    val accentWash = if (accent != null) {
        Modifier.background(
            Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent)),
        )
    } else {
        Modifier
    }
    val rim = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = style.borderOpacity),
            Color.White.copy(alpha = style.borderOpacity * 0.2f),
        ),
    )
    val click = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .clip(shape)
            .hazeChild(LocalHazeState.current, hazeStyle)
            .then(accentWash)
            .border(1.dp, rim, shape)
            .then(click)
            .padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}
