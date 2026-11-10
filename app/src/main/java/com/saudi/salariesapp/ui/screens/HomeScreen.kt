package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saudi.salariesapp.data.EventCategory
import com.saudi.salariesapp.ui.UiState
import com.saudi.salariesapp.ui.components.EventCard
import com.saudi.salariesapp.ui.components.HeaderDateCard
import com.saudi.salariesapp.ui.components.HighlightCountdownCard
import com.saudi.salariesapp.ui.components.SectionTitle

/** الشاشة الرئيسية: التاريخ، زر عداد سريع أعلى الشاشة، الراتب القادم بعدّاد مباشر، ثم كل المواعيد. */
@Composable
fun HomeScreen(state: UiState, onAddCountdown: () -> Unit, contentPadding: PaddingValues) {
    val nextSalary = state.services.firstOrNull { it.id == "gov_salary" }?.next
    val others = state.upcoming.filter { it.id != nextSalary?.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeaderDateCard(state.today) }
        item {
            FilledTonalButton(onClick = onAddCountdown, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AddAlarm, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("إضافة عداد شخصي سريع")
            }
        }
        if (nextSalary != null) {
            item { SectionTitle("الراتب القادم") }
            item { HighlightCountdownCard(nextSalary) }
        }
        val personal = others.filter { it.category == EventCategory.PERSONAL }
        if (personal.isNotEmpty()) {
            item { SectionTitle("عداداتي") }
            items(personal, key = { it.id }) { EventCard(it) }
        }
        item { SectionTitle("المواعيد القادمة") }
        items(others.filter { it.category != EventCategory.PERSONAL }, key = { it.id }) { EventCard(it) }
    }
}
