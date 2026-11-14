package com.athar.app.feature.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.component.GlassIconButton
import com.athar.app.core.designsystem.theme.LocalDecorativeFont
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.currentLocale
import com.athar.app.core.format.displayName
import com.athar.app.core.format.formatGregorian
import com.athar.app.core.format.formatHijri
import java.time.LocalDate

/** Greeting, day name and both calendars side by side. */
@Composable
fun HomeHeader(today: LocalDate, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.greeting),
                style = MaterialTheme.typography.bodyMedium,
                color = AtharColors.TextSecondary,
            )
            Text(
                today.dayOfWeek.displayName(locale),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                today.formatGregorian(locale),
                style = MaterialTheme.typography.bodyMedium,
                color = AtharColors.TextSecondary,
            )
            Text(
                today.formatHijri(),
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = LocalDecorativeFont.current),
                color = AtharColors.Gold,
            )
        }
        GlassIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.settings),
            onClick = onOpenSettings,
        )
    }
}
