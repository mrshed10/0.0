package com.athar.app.feature.prayer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.R
import com.athar.app.core.data.TasbihState
import com.athar.app.core.data.todayCount
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.LocalDecorativeFont
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatNumber
import java.time.LocalDate

/** The current dhikr text and target for a tasbih state. */
fun TasbihState.dhikr(): Dhikr =
    SpiritualContent.tasbihPresets.getOrNull(presetIndex)
        ?: Dhikr(customText.ifBlank { "سبحان الله" }, customText.ifBlank { "SubhanAllah" }, customTarget)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasbihPanel(
    state: TasbihState,
    today: LocalDate,
    onTap: () -> Unit,
    onReset: () -> Unit,
    onPreset: (Int) -> Unit,
    onCustom: (String, Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val locale = currentLocale()
    val dhikr = state.dhikr()
    val target = dhikr.count.coerceAtLeast(1)
    val inRound = state.count % target
    val rounds = state.count / target
    var customSheet by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SpiritualContent.tasbihPresets.forEachIndexed { i, preset ->
                GlassChip(
                    SpiritualContent.text(preset, locale),
                    if (i == state.presetIndex) AtharColors.Gold else AtharColors.TextSecondary,
                    Modifier.clickable { onPreset(i) },
                )
            }
            GlassChip(
                "+ " + stringResource(R.string.tasbih_custom),
                if (state.presetIndex >= SpiritualContent.tasbihPresets.size) AtharColors.Gold else AtharColors.Violet,
                Modifier.clickable { customSheet = true },
            )
        }

        GlassCard(Modifier.fillMaxWidth(), accent = AtharColors.Violet, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dhikr.ar,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = LocalDecorativeFont.current),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        val completes = (state.count + 1) % target == 0
                        haptics.performHapticFeedback(if (completes) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
                        onTap()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val stroke = 14.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                    drawArc(
                        Brush.sweepGradient(listOf(AtharColors.Violet, AtharColors.Gold, AtharColors.Violet)),
                        -90f,
                        360f * inRound / target,
                        false,
                        topLeft,
                        arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(inRound.formatNumber(), fontSize = 64.sp, fontWeight = FontWeight.Bold)
                    Text("/ ${target.formatNumber()}", style = MaterialTheme.typography.titleMedium, color = AtharColors.TextSecondary)
                    if (rounds > 0) {
                        Text(
                            stringResource(R.string.tasbih_rounds, rounds.formatNumber()),
                            style = MaterialTheme.typography.labelMedium,
                            color = AtharColors.Gold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.tasbih_hint), style = MaterialTheme.typography.labelMedium, color = AtharColors.TextTertiary)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.tasbih_today, state.todayCount(today).formatNumber()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.tasbih_lifetime, state.lifetime.formatNumber()),
                        style = MaterialTheme.typography.labelMedium,
                        color = AtharColors.TextTertiary,
                    )
                }
                ConfirmButton(
                    text = stringResource(R.string.reset),
                    confirmText = stringResource(R.string.tap_to_confirm),
                    onConfirm = onReset,
                    color = AtharColors.Violet,
                    icon = Icons.Rounded.Refresh,
                )
            }
        }
    }

    if (customSheet) {
        var text by remember { mutableStateOf(state.customText) }
        var targetText by remember { mutableStateOf(state.customTarget.toString()) }
        GlassSheet(onDismiss = { customSheet = false }) {
            Text(stringResource(R.string.tasbih_custom), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            GlassTextField(text, { text = it }, stringResource(R.string.tasbih_custom_hint), Modifier.fillMaxWidth())
            GlassTextField(
                targetText,
                { targetText = it.filter(Char::isDigit).take(5) },
                stringResource(R.string.tasbih_target),
                Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
            )
            GlassButton(
                stringResource(R.string.save),
                {
                    onCustom(text.trim(), targetText.toIntOrNull() ?: 33)
                    customSheet = false
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Morning/evening adhkar; tap a card to count down its repetitions. */
@Composable
fun AdhkarPanel() {
    val locale = currentLocale()
    var evening by rememberSaveable { mutableIntStateOf(0) }
    val list = if (evening == 0) SpiritualContent.morning else SpiritualContent.evening
    val remaining = remember(evening) { mutableStateListOf(*list.map { it.count }.toTypedArray()) }
    val haptics = LocalHapticFeedback.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassSegmented(
            options = listOf(stringResource(R.string.adhkar_morning), stringResource(R.string.adhkar_evening)),
            selectedIndex = evening,
            onSelect = { evening = it },
        )
        list.forEachIndexed { i, dhikr ->
            val left = remaining.getOrElse(i) { 0 }
            val done = left == 0
            GlassCard(
                Modifier.fillMaxWidth(),
                accent = if (done) AtharColors.Teal else null,
                onClick = {
                    if (left > 0) {
                        remaining[i] = left - 1
                        haptics.performHapticFeedback(
                            if (left == 1) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove,
                        )
                    }
                },
            ) {
                Text(
                    dhikr.ar,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalDecorativeFont.current),
                    color = if (done) AtharColors.TextTertiary else AtharColors.TextPrimary,
                )
                if (locale.language != "ar") {
                    Spacer(Modifier.height(4.dp))
                    Text(dhikr.en, style = MaterialTheme.typography.bodySmall, color = AtharColors.TextSecondary)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = AtharColors.Teal, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.done), style = MaterialTheme.typography.labelLarge, color = AtharColors.Teal)
                    } else {
                        GlassChip(stringResource(R.string.times_left, left.formatNumber()), AtharColors.Gold)
                    }
                }
            }
        }
    }
}
