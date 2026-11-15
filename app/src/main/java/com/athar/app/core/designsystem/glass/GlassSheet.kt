package com.athar.app.core.designsystem.glass

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.athar.app.core.designsystem.theme.AtharColors

/**
 * Bottom sheet drawn in the same window as the aurora background, so it stays
 * real frosted glass (a Material ModalBottomSheet opens its own window and
 * could not blur the background). Show it by composing it; remove to dismiss.
 */
/**
 * Draws sheets at the top of the app instead of where they are declared.
 * A sheet declared inside a scrolling list (e.g. an adhkar card) would
 * otherwise be measured with infinite height and crash the app.
 */
class SheetHost {
    private val stack = mutableStateListOf<Pair<Any, @Composable () -> Unit>>()

    fun show(token: Any, content: @Composable () -> Unit) {
        stack.removeAll { it.first === token }
        stack.add(token to content)
    }

    fun hide(token: Any) {
        stack.removeAll { it.first === token }
    }

    /** Call once, as the last child of the root Box. */
    @Composable
    fun Render() {
        stack.lastOrNull()?.second?.invoke()
    }
}

val LocalSheetHost = staticCompositionLocalOf<SheetHost?> { null }

@Composable
fun GlassSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val host = LocalSheetHost.current
    if (host == null) {
        SheetSurface(onDismiss, content)
        return
    }
    val currentDismiss by rememberUpdatedState(onDismiss)
    val currentContent by rememberUpdatedState(content)
    val token = remember { Any() }
    DisposableEffect(host) {
        host.show(token) { SheetSurface({ currentDismiss() }, currentContent) }
        onDispose { host.hide(token) }
    }
}

@Composable
private fun SheetSurface(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val bar = LocalBottomBarController.current
    DisposableEffect(Unit) {
        bar.hide()
        onDispose { bar.show() }
    }
    BackHandler(onBack = onDismiss)
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visibleState = appear, enter = fadeIn()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }
        AnimatedVisibility(
            visibleState = appear,
            enter = slideInVertically { it } + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                style = LocalGlassStyle.current.copy(opacity = 0.18f, tint = AtharColors.Night),
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}
