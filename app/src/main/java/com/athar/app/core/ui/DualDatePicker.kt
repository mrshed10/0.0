package com.athar.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.calendar.HijriCalendar
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.displayName
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.format.formatNumber
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Day / month / year steppers in either the Gregorian or the Hijri
 * (Umm al-Qura) calendar, with a live preview in the other calendar.
 * The value is always a [LocalDate]; [calendar] only changes how it is edited.
 */
@Composable
fun DualDatePicker(
    date: LocalDate,
    calendar: CalendarKind,
    onDateChange: (LocalDate) -> Unit,
    onCalendarChange: (CalendarKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    fun safe(block: () -> LocalDate) = runCatching(block).getOrNull()?.let(onDateChange) ?: Unit

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassSegmented(
            options = listOf(stringResource(R.string.calendar_gregorian), stringResource(R.string.calendar_hijri)),
            selectedIndex = calendar.ordinal,
            onSelect = { onCalendarChange(CalendarKind.entries[it]) },
        )

        val day: String
        val month: String
        val year: String
        val onMonth: (Long) -> Unit
        val onYear: (Long) -> Unit
        when (calendar) {
            CalendarKind.Gregorian -> {
                day = date.dayOfMonth.formatNumber()
                month = date.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)
                year = date.year.toString()
                onMonth = { n -> safe { date.plusMonths(n) } }
                onYear = { n -> safe { date.plusYears(n) } }
            }
            CalendarKind.Hijri -> {
                val h = HijriCalendar.fromGregorian(date)
                day = h.day.formatNumber()
                month = stringArrayResource(R.array.hijri_months)[h.month - 1]
                year = h.year.toString()
                onMonth = { n -> safe { HijriCalendar.plusMonths(date, n) } }
                onYear = { n -> safe { HijriCalendar.plusYears(date, n) } }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(stringResource(R.string.day_label), day, { n -> safe { date.plusDays(n) } }, Modifier.weight(0.8f))
            Stepper(stringResource(R.string.month_label), month, onMonth, Modifier.weight(1.4f))
            Stepper(stringResource(R.string.year_label), year, onYear, Modifier.weight(1f))
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            val other = if (calendar == CalendarKind.Gregorian) date.formatHijri() else date.formatGregorian(locale)
            Text(
                "${date.dayOfWeek.displayName(locale)} · $other",
                style = MaterialTheme.typography.labelLarge,
                color = AtharColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            GlassChip(
                stringResource(R.string.today),
                AtharColors.Gold,
                Modifier.clickable { onDateChange(LocalDate.now()) },
            )
        }
    }
}

@Composable
internal fun Stepper(label: String, value: String, onStep: (Long) -> Unit, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = AtharColors.TextTertiary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RepeatingStepButton(Icons.Rounded.Remove) { onStep(-1) }
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RepeatingStepButton(Icons.Rounded.Add) { onStep(1) }
        }
    }
}

/** Tap to step once; hold to keep stepping faster. */
@Composable
internal fun RepeatingStepButton(icon: ImageVector, onStep: () -> Unit) {
    val current by rememberUpdatedState(onStep)
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        val job = scope.launch {
                            current()
                            delay(400)
                            var interval = 120L
                            while (isActive) {
                                current()
                                delay(interval)
                                interval = (interval - 10).coerceAtLeast(40)
                            }
                        }
                        tryAwaitRelease()
                        job.cancel()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AtharColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}
