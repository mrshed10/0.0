package com.athar.app.core.region

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors

@Composable
fun CountryPickerSheet(selected: Country, onSelect: (Country) -> Unit, onDismiss: () -> Unit) {
    GlassSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.country_label), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.country_hint), style = MaterialTheme.typography.bodyMedium, color = AtharColors.TextSecondary)
        Country.entries.forEach { country ->
            val isSelected = country == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) AtharColors.Gold.copy(alpha = 0.15f) else Color.Transparent)
                    .clickable { onSelect(country) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(country.flag, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(country.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(stringResource(country.currency), style = MaterialTheme.typography.labelLarge, color = AtharColors.TextTertiary)
                if (isSelected) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = AtharColors.Gold)
                }
            }
        }
    }
}
