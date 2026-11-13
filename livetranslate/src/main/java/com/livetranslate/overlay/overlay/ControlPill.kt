package com.livetranslate.overlay.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetranslate.overlay.R
import com.livetranslate.overlay.translate.Language
import com.livetranslate.overlay.translate.Languages

private val GlowBrush = Brush.sweepGradient(
    listOf(
        Color(0xFF4285F4), Color(0xFFA142F4), Color(0xFFF06292),
        Color(0xFFFFA726), Color(0xFF66BB6A), Color(0xFF4285F4),
    ),
)

private data class PillColors(
    val surface: Color,
    val onSurface: Color,
    val muted: Color,
    val chip: Color,
    val onChip: Color,
    val accent: Color,
    val onAccent: Color,
)

private val Light = PillColors(
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1F1F1F), muted = Color(0xFF5F6368),
    chip = Color(0xFFD3E3FD), onChip = Color(0xFF041E49), accent = Color(0xFF1A73E8), onAccent = Color.White,
)
private val Dark = PillColors(
    surface = Color(0xFF2A2B2F), onSurface = Color(0xFFE8EAED), muted = Color(0xFFBDC1C6),
    chip = Color(0xFF0B3C82), onChip = Color(0xFFD3E3FD), accent = Color(0xFF8AB4F8), onAccent = Color(0xFF062E6F),
)

/**
 * واجهة النافذة العائمة. لا شريط عنوان ولا إطار نظام: مجرد أشكال دائرية بظل ناعم
 * على خلفية شفافة تماماً (النافذة نفسها PixelFormat.TRANSLUCENT بلا خلفية).
 */
@Composable
fun ControlPill(state: OverlayUiState, actions: OverlayActions) {
    val colors = if (isSystemInDarkTheme()) Dark else Light
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        // هامش داخلي حتى لا يُقص الظل عند حدود النافذة
        Box(Modifier.padding(14.dp)) {
            AnimatedContent(
                targetState = state.mode,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.9f)) togetherWith
                        fadeOut(tween(120)) using SizeTransform(clip = false)
                },
                label = "pill-mode",
            ) { mode ->
                when (mode) {
                    OverlayMode.LAUNCHER -> LauncherPill(colors, actions)
                    OverlayMode.PICKER -> PickerCard(state, colors, actions)
                    OverlayMode.TRANSLATING -> TranslatingPill(state, colors, actions)
                }
            }
        }
    }
}

@Composable
private fun GlowLogo(size: Int, busy: Boolean, colors: PillColors) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(GlowBrush),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((size - 5).dp)
                .clip(CircleShape)
                .background(colors.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size((size - 12).dp),
                    strokeWidth = 2.dp,
                    color = colors.accent,
                )
            } else {
                Icon(Icons.Rounded.Translate, null, tint = colors.accent, modifier = Modifier.size((size - 14).dp))
            }
        }
    }
}

@Composable
private fun RoundIcon(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    size: Int = 32,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
private fun LauncherPill(colors: PillColors, actions: OverlayActions) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .clickable { actions.openPicker() }
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowLogo(36, busy = false, colors)
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.pill_launcher),
            color = colors.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(12.dp))
        RoundIcon(
            Icons.Rounded.Close, stringResource(R.string.pill_close),
            background = Color.Transparent, tint = colors.muted, size = 30,
        ) { actions.closeOverlay() }
    }
}

private enum class Expanded { SOURCE, TARGET }

@Composable
private fun PickerCard(state: OverlayUiState, colors: PillColors, actions: OverlayActions) {
    var expanded by remember { mutableStateOf<Expanded?>(null) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .width(316.dp)
            .shadow(14.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LanguageChip(Languages.nameOf(state.source), expanded == Expanded.SOURCE, colors, Modifier.weight(1f)) {
                expanded = if (expanded == Expanded.SOURCE) null else Expanded.SOURCE
            }
            RoundIcon(
                Icons.AutoMirrored.Rounded.ArrowForward, stringResource(R.string.pill_swap),
                background = Color.Transparent, tint = colors.muted, size = 34,
            ) { actions.swapLanguages() }
            LanguageChip(Languages.nameOf(state.target), expanded == Expanded.TARGET, colors, Modifier.weight(1f)) {
                expanded = if (expanded == Expanded.TARGET) null else Expanded.TARGET
            }
        }

        AnimatedVisibility(
            visible = expanded != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            val list = if (expanded == Expanded.TARGET) Languages.targets else Languages.sources
            val selected = if (expanded == Expanded.TARGET) state.target else state.source
            LanguageList(list, selected, colors) { code ->
                if (expanded == Expanded.TARGET) actions.selectTarget(code) else actions.selectSource(code)
                expanded = null
            }
        }

        if (state.status is EngineStatus.CaptureDenied || state.status is EngineStatus.Error) {
            Text(
                statusText(state),
                color = Color(0xFFD93025),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        Spacer(Modifier.size(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pill = RoundedCornerShape(50)
            Row(
                Modifier
                    .weight(1f)
                    .clip(pill)
                    .background(colors.chip.copy(alpha = 0.55f))
                    .clickable { actions.startTranslating() }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Swipe, null, tint = colors.onChip, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.pill_scroll_translate),
                    color = colors.onChip,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundIcon(
                Icons.Rounded.Translate, stringResource(R.string.pill_scroll_translate),
                background = colors.accent, tint = colors.onAccent, size = 44,
            ) { actions.startTranslating() }
            Spacer(Modifier.width(4.dp))
            RoundIcon(
                Icons.Rounded.Close, stringResource(R.string.pill_close),
                background = Color.Transparent, tint = colors.muted, size = 32,
            ) { actions.stopTranslating() }
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    open: Boolean,
    colors: PillColors,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (open) colors.accent else colors.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (open) colors.onAccent else colors.onChip,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Rounded.KeyboardArrowDown, null,
            tint = if (open) colors.onAccent else colors.onChip,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LanguageList(
    list: List<Language>,
    selected: String,
    colors: PillColors,
    onSelect: (String) -> Unit,
) {
    LazyColumn(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp)
            .padding(top = 8.dp),
    ) {
        items(list, key = { it.code }) { lang ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onSelect(lang.code) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(lang.nameAr, color = colors.onSurface, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (lang.code == selected) {
                    Icon(Icons.Rounded.Check, null, tint = colors.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun TranslatingPill(state: OverlayUiState, colors: PillColors, actions: OverlayActions) {
    val shape = RoundedCornerShape(50)
    val busy = state.status is EngineStatus.Working || state.status is EngineStatus.Downloading
    Row(
        Modifier
            .shadow(10.dp, shape)
            .clip(shape)
            .background(colors.surface)
            .clickable { actions.openPicker() }
            .padding(start = 8.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowLogo(32, busy = busy, colors)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.pill_translating),
                color = colors.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                statusText(state),
                color = colors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 170.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        RoundIcon(
            Icons.Rounded.Close, stringResource(R.string.pill_close),
            background = colors.chip.copy(alpha = 0.5f), tint = colors.onSurface, size = 28,
        ) { actions.stopTranslating() }
    }
}

@Composable
private fun statusText(state: OverlayUiState): String = when (val s = state.status) {
    is EngineStatus.Downloading -> stringResource(R.string.pill_downloading, Languages.nameOf(s.language))
    is EngineStatus.Error -> stringResource(R.string.pill_error, s.message)
    EngineStatus.CaptureDenied -> stringResource(R.string.pill_capture_denied)
    EngineStatus.Working, EngineStatus.Ready ->
        stringResource(R.string.pill_to_language, Languages.nameOf(state.target))
}
