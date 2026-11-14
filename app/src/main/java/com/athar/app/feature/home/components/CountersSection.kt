package com.athar.app.feature.home.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.SectionTitle
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import com.athar.app.core.format.formatNumber
import com.athar.app.feature.counters.Counter
import com.athar.app.feature.counters.CounterDirection
import com.athar.app.feature.counters.CounterMath
import com.athar.app.feature.counters.label
import java.time.LocalDate

@Composable
fun CountersSection(
    today: LocalDate,
    counters: List<Counter>,
    onPin: (Long) -> Unit,
    onOpenAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.counters), Modifier.weight(1f))
            Text(
                stringResource(R.string.see_all),
                style = MaterialTheme.typography.labelLarge,
                color = AtharColors.Gold,
                modifier = Modifier.clickable(onClick = onOpenAll),
            )
        }
        Spacer(Modifier.height(8.dp))
        if (counters.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth(), onClick = onOpenAll) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = AtharColors.Gold)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.home_add_counter), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(counters, key = { it.id }) { counter ->
                    CounterCard(today, counter, { onPin(counter.id) }, onOpenAll)
                }
            }
        }
    }
}

@Composable
private fun CounterCard(today: LocalDate, counter: Counter, onPin: () -> Unit, onOpen: () -> Unit) {
    val locale = currentLocale()
    val tone = counter.tone.color
    val reading = CounterMath.read(counter, today)

    GlassCard(Modifier.width(176.dp), accent = tone, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (counter.direction == CounterDirection.CountDown) Icons.Rounded.HourglassTop else Icons.Rounded.Timelapse,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                counter.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onPin, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = stringResource(R.string.pin_to_widget),
                    tint = AtharColors.TextTertiary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (reading.days == 0L) {
            Text(
                stringResource(R.string.today),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = tone,
            )
        } else {
            Row {
                Text(
                    reading.days.formatNumber(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    " " + stringResource(R.string.unit_days),
                    style = MaterialTheme.typography.bodySmall,
                    color = AtharColors.TextSecondary,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }
        Text(reading.label(), style = MaterialTheme.typography.labelMedium, color = tone)
        Spacer(Modifier.height(6.dp))
        Text(
            counter.date.formatGregorian(locale, "d MMM yyyy"),
            style = MaterialTheme.typography.labelSmall,
            color = AtharColors.TextTertiary,
        )
        Text(
            counter.date.formatHijri(),
            style = MaterialTheme.typography.labelSmall,
            color = AtharColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
