package com.athar.app.feature.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.athar.app.R
import com.athar.app.core.designsystem.component.CardHeader
import com.athar.app.core.designsystem.glass.GlassCard
import com.athar.app.core.designsystem.theme.Amiri
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.Money
import com.athar.app.core.format.formatNumber
import com.athar.app.feature.debts.DebtTotals

@Composable
fun DebtsCard(debts: DebtTotals, onPin: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier.fillMaxWidth(), accent = AtharColors.Teal, onClick = onOpen) {
        CardHeader(
            title = stringResource(R.string.debts),
            icon = Icons.Rounded.AccountBalanceWallet,
            tint = AtharColors.Teal,
            onPin = onPin,
            trailing = {
                Text(
                    stringResource(R.string.debts_clients, debts.openPeople.formatNumber()),
                    style = MaterialTheme.typography.labelMedium,
                    color = AtharColors.TextSecondary,
                )
            },
        )
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DebtAmount(stringResource(R.string.debts_owed_to_me), debts.owedToMeMinor, AtharColors.Teal, Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(44.dp).background(Color.White.copy(alpha = 0.15f)))
            DebtAmount(stringResource(R.string.debts_i_owe), debts.iOweMinor, AtharColors.Rose, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DebtAmount(label: String, amount: Long, color: Color, modifier: Modifier) {
    Column(modifier.padding(horizontal = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = AtharColors.TextSecondary)
        Row {
            Text(
                Money.format(amount),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                " " + stringResource(R.string.currency_sar),
                style = MaterialTheme.typography.labelMedium,
                color = AtharColors.TextSecondary,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

@Composable
fun VerseCard(modifier: Modifier = Modifier) {
    GlassCard(
        modifier.fillMaxWidth(),
        accent = AtharColors.Violet,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.verse_of_day),
            style = MaterialTheme.typography.labelLarge,
            color = AtharColors.Gold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.sample_verse),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = Amiri,
                fontSize = 24.sp,
                lineHeight = 42.sp,
                textAlign = TextAlign.Center,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.sample_verse_ref),
            style = MaterialTheme.typography.labelMedium,
            color = AtharColors.TextTertiary,
        )
    }
}
