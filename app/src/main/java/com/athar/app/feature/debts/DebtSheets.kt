package com.athar.app.feature.debts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.athar.app.R
import com.athar.app.core.region.LocalCurrency
import com.athar.app.core.calendar.CalendarKind
import com.athar.app.core.designsystem.component.ConfirmButton
import com.athar.app.core.designsystem.component.FieldLabel
import com.athar.app.core.designsystem.component.GlassButton
import com.athar.app.core.designsystem.component.GlassSegmented
import com.athar.app.core.designsystem.component.GlassTextField
import com.athar.app.core.designsystem.glass.GlassSheet
import com.athar.app.core.designsystem.theme.AtharColors
import com.athar.app.core.format.Money
import com.athar.app.core.ui.DualDatePicker

@Composable
fun PersonEditorSheet(
    initial: Person,
    onDismiss: () -> Unit,
    onSave: (Person) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var phone by remember(initial.id) { mutableStateOf(initial.phone) }
    var address by remember(initial.id) { mutableStateOf(initial.address) }

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_person else R.string.edit_person),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassTextField(name, { name = it }, stringResource(R.string.person_name_hint), Modifier.fillMaxWidth())
        GlassTextField(address, { address = it }, stringResource(R.string.person_address_hint), Modifier.fillMaxWidth())
        GlassTextField(
            phone,
            { phone = it },
            stringResource(R.string.person_phone_hint),
            Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Phone,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!initial.isNew) {
                ConfirmButton(
                    text = stringResource(R.string.delete),
                    confirmText = stringResource(R.string.delete_person_confirm),
                    onConfirm = { onDelete(initial.id) },
                    icon = Icons.Rounded.DeleteOutline,
                )
            }
            GlassButton(
                text = stringResource(R.string.save),
                onClick = { onSave(initial.copy(name = name.trim(), phone = phone.trim(), address = address.trim())) },
                enabled = name.isNotBlank(),
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun EntryEditorSheet(
    initial: DebtEntry,
    onDismiss: () -> Unit,
    onSave: (DebtEntry) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var amountText by remember(initial.id) {
        mutableStateOf(if (initial.amountMinor > 0) Money.toInput(initial.amountMinor) else "")
    }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var date by remember(initial.id) { mutableStateOf(initial.date) }
    var calendar by remember { mutableStateOf(CalendarKind.Gregorian) }
    val amount = Money.parse(amountText)
    val valid = amount != null && amount > 0
    val accent = if (type == EntryType.Gave) AtharColors.Teal else AtharColors.Rose

    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(if (initial.isNew) R.string.add_entry else R.string.edit_entry),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        GlassSegmented(
            options = listOf(stringResource(R.string.entry_gave), stringResource(R.string.entry_took)),
            selectedIndex = type.ordinal,
            onSelect = { type = EntryType.entries[it] },
            accent = accent,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.amount_label) + " (" + LocalCurrency.current + ")")
            GlassTextField(
                amountText,
                { amountText = it },
                "0.00",
                Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Decimal,
            )
            if (amountText.isNotBlank() && !valid) {
                Text(stringResource(R.string.amount_invalid), style = MaterialTheme.typography.labelSmall, color = AtharColors.Rose)
            }
        }
        GlassTextField(note, { note = it }, stringResource(R.string.note_hint), Modifier.fillMaxWidth())
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(stringResource(R.string.date_label))
            DualDatePicker(date, calendar, { date = it }, { calendar = it })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!initial.isNew) {
                ConfirmButton(
                    text = stringResource(R.string.delete),
                    confirmText = stringResource(R.string.tap_to_confirm),
                    onConfirm = { onDelete(initial.id) },
                    icon = Icons.Rounded.DeleteOutline,
                )
            }
            GlassButton(
                text = stringResource(R.string.save),
                onClick = {
                    if (amount != null) onSave(initial.copy(type = type, amountMinor = amount, note = note, date = date))
                },
                enabled = valid,
                color = accent,
                icon = Icons.Rounded.Check,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun DebtSortSheet(selected: DebtSort, onDismiss: () -> Unit, onSelect: (DebtSort) -> Unit) {
    GlassSheet(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.sort_by),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        DebtSort.entries.forEach { option ->
            val active = option == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (active) AtharColors.Teal.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(option.label),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) AtharColors.Teal else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                if (active) Icon(Icons.Rounded.Check, contentDescription = null, tint = AtharColors.Teal)
            }
        }
    }
}

val DebtSort.label: Int
    get() = when (this) {
        DebtSort.AmountDesc -> R.string.sort_amount_desc
        DebtSort.AmountAsc -> R.string.sort_amount_asc
        DebtSort.Overdue -> R.string.sort_overdue
        DebtSort.Name -> R.string.sort_name
        DebtSort.Recent -> R.string.sort_recent
    }
