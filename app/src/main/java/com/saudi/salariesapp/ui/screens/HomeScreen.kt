package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.ui.components.EventCard
import com.saudi.salariesapp.ui.components.HeaderDateCard
import com.saudi.salariesapp.ui.components.SectionTitle
import com.saudi.salariesapp.ui.components.rememberNow

/** Home: today's date, a quick "add countdown" button, my countdowns and upcoming payments. Zero ads. */
@Composable
fun HomeScreen(vm: AppViewModel, padding: PaddingValues, onAddCountdown: () -> Unit) {
    val now = rememberNow()
    val today = now.toLocalDate()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val builtIn = remember(today) { PaymentSchedule.builtInEvents(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeaderDateCard(today) }
        item {
            Button(onClick = onAddCountdown, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("  إضافة عداد شخصي")
            }
        }
        if (countdowns.isNotEmpty()) {
            item { SectionTitle("عداداتي") }
            items(countdowns, key = { it.id }) { e -> ManagedEventCard(vm, e, now) }
        }
        item { SectionTitle("المواعيد القادمة") }
        items(builtIn, key = { it.id }) { e -> ManagedEventCard(vm, e, now) }
        item {
            Text(
                "المواعيد محسوبة وفق الجدول المعتاد وقد تتغير عند إعلان الجهات الرسمية موعداً مختلفاً.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** An [EventCard] wired to the view model (reminder, widget pin, delete). */
@Composable
fun ManagedEventCard(vm: AppViewModel, event: EventModel, now: java.time.LocalDateTime) {
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val widget by vm.widget.collectAsStateWithLifecycle()
    EventCard(
        event = event,
        now = now,
        reminderOn = event.id in reminders,
        onToggleReminder = { vm.toggleReminder(event.id) },
        pinnedToWidget = event.id in widget.pinnedIds,
        onToggleWidget = { vm.toggleWidgetPin(event.id) },
        onDelete = if (event.isCustom) ({ vm.deleteCountdown(event.id) }) else null,
    )
}
