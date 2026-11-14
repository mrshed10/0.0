package com.athar.app.feature.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.component.GlassChip
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.formatClock
import com.athar.app.core.format.formatRemaining
import com.athar.app.feature.prayer.NextPrayer
import com.athar.app.feature.prayer.PrayerTime
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun NextPrayerCard(
    now: LocalDateTime,
    prayers: List<PrayerTime>,
    next: NextPrayer,
    isSample: Boolean,
    onPin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val minutesLeft = Duration.between(now, next.at).toMinutes()

    GlassCard(modifier.fillMaxWidth(), accent = AtharColors.Gold) {
        CardHeader(
            title = stringResource(R.string.next_prayer),
            icon = Icons.Rounded.Mosque,
            onPin = onPin,
            trailing = {
                if (isSample) GlassChip(stringResource(R.string.sample_data), AtharColors.Gold)
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(next.prayer.label),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.in_time, formatRemaining(minutesLeft)),
                    style = MaterialTheme.typography.titleMedium,
                    color = AtharColors.Gold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                next.at.toLocalTime().formatClock(locale),
                style = MaterialTheme.typography.headlineSmall,
                color = AtharColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            prayers.forEach { slot ->
                val isNext = slot.prayer == next.prayer
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(slot.prayer.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isNext) AtharColors.Gold else AtharColors.TextTertiary,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                    )
                    Text(
                        slot.time.formatClock(locale, withPeriod = false),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isNext) AtharColors.TextPrimary else AtharColors.TextSecondary,
                    )
                }
            }
        }
    }
}
