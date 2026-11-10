package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.data.HijriDates
import com.saudi.salariesapp.data.WidgetSettings
import com.saudi.salariesapp.data.allEvents
import com.saudi.salariesapp.data.daysLabel
import com.saudi.salariesapp.ui.components.ColorPicker
import com.saudi.salariesapp.ui.components.SectionTitle
import com.saudi.salariesapp.widget.SalariesWidget
import java.time.LocalDate

/**
 * Widget editor: live preview, background / text / icon colours, which cards to show and
 * how many rows. Used from "More" and as the widget's configuration screen.
 */
@Composable
fun WidgetEditorScreen(vm: AppViewModel, padding: PaddingValues) {
    val settings by vm.widget.collectAsStateWithLifecycle()
    val countdowns by vm.countdowns.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val events = remember(countdowns, today) { allEvents(today, countdowns) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(padding)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("معاينة")
        WidgetPreview(settings, SalariesWidget.selectEvents(events, settings, today).take(settings.maxItems), today)

        SectionTitle("الألوان")
        ColorPicker("لون الخلفية", Color(settings.backgroundColor), { c ->
            vm.updateWidget { it.copy(backgroundColor = c.toArgb()) }
        })
        ColorPicker("لون النص", Color(settings.textColor), { c ->
            vm.updateWidget { it.copy(textColor = c.toArgb()) }
        })
        ColorPicker("لون الأيقونات والأرقام", Color(settings.iconColor), { c ->
            vm.updateWidget { it.copy(iconColor = c.toArgb()) }
        })

        SectionTitle("المحتوى")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("إظهار التاريخ أعلى الودجت", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.showHijriHeader,
                onCheckedChange = { v -> vm.updateWidget { it.copy(showHijriHeader = v) } },
            )
        }
        Text("عدد البطاقات في الودجت الكبير: ${settings.maxItems}")
        Slider(
            value = settings.maxItems.toFloat(),
            onValueChange = { v -> vm.updateWidget { it.copy(maxItems = v.toInt()) } },
            valueRange = 1f..6f,
            steps = 4,
        )
        Text(
            if (settings.pinnedIds.isEmpty()) "لم تختر بطاقات: يعرض الودجت أقرب المواعيد تلقائياً."
            else "البطاقات المختارة تظهر بترتيب اختيارها.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        events.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = e.id in settings.pinnedIds, onCheckedChange = { vm.toggleWidgetPin(e.id) })
                Column(Modifier.weight(1f)) {
                    Text(e.title, fontWeight = FontWeight.Medium)
                    Text(e.category.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** A Compose approximation of the Glance widget so colour changes are visible immediately. */
@Composable
private fun WidgetPreview(s: WidgetSettings, events: List<com.saudi.salariesapp.data.EventModel>, today: LocalDate) {
    val text = Color(s.textColor)
    val icon = Color(s.iconColor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(s.backgroundColor))
            .padding(12.dp),
    ) {
        if (s.showHijriHeader) {
            Text(HijriDates.formatHijriWithDay(today), color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(HijriDates.formatGregorian(today), color = text, fontSize = 11.sp)
            Spacer(Modifier.size(8.dp))
        }
        if (events.isEmpty()) Text("لا توجد مواعيد مختارة", color = text, fontSize = 13.sp)
        events.forEach { e ->
            val days = e.remainingDays(today)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = null, tint = icon, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.dateHijri, color = text, fontSize = 10.sp, maxLines = 1)
                }
                Text(
                    when {
                        days < 0 -> "انتهى"
                        days == 0L -> "اليوم"
                        else -> "$days ${daysLabel(days)}"
                    },
                    color = icon, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
