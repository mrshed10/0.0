package com.saudi.salariesapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.Countdown
import com.saudi.salariesapp.data.EventCategory
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.ui.ServiceUi
import com.saudi.salariesapp.ui.UiState
import com.saudi.salariesapp.ui.components.EventCard
import com.saudi.salariesapp.ui.components.SectionTitle

private val filters = listOf(
    null to "الكل",
    EventCategory.SALARIES to EventCategory.SALARIES.label,
    EventCategory.RETIREMENT to EventCategory.RETIREMENT.label,
    EventCategory.CITIZEN to EventCategory.CITIZEN.label,
    EventCategory.SUPPORT to EventCategory.SUPPORT.label,
    EventCategory.OCCASIONS to EventCategory.OCCASIONS.label,
)

/**
 * جدول الرواتب والخدمات: الرواتب، التقاعد، التأمينات، حساب المواطن، الضمان، سكني، التأهيل الشامل.
 * لكل خدمة: تنبيه مخصص + خيار "إظهار كودجت على الشاشة الرئيسية" + تعديل يوم الصرف.
 */
@Composable
fun SalariesScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onNotify: (String, Boolean) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onSetDay: (String, Int) -> Unit,
) {
    var filterIndex by rememberSaveable { mutableStateOf(0) }
    val filter = filters[filterIndex].first
    var editing by remember { mutableStateOf<ServiceUi?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filters.forEachIndexed { i, (_, label) ->
                    FilterChip(selected = i == filterIndex, onClick = { filterIndex = i }, label = { Text(label) })
                }
            }
        }
        if (filter != EventCategory.OCCASIONS) {
            val services = state.services.filter { s ->
                filter == null || PaymentSchedule.service(s.id)?.category == filter
            }
            items(services, key = { it.id }) { s ->
                ServiceCard(
                    service = s,
                    onNotify = { onNotify(s.id, it) },
                    onPin = { onPin(s.id, it) },
                    onEditDay = { editing = s },
                )
            }
        }
        if (filter == null || filter == EventCategory.OCCASIONS) {
            item { SectionTitle("المناسبات الوطنية والدينية") }
            items(state.occasions, key = { it.id }) { EventCard(it) }
        }
    }

    editing?.let { s ->
        EditDayDialog(
            service = s,
            onDismiss = { editing = null },
            onSave = { day -> onSetDay(s.id, day); editing = null },
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceUi,
    onNotify: (Boolean) -> Unit,
    onPin: (Boolean) -> Unit,
    onEditDay: () -> Unit,
) {
    var expanded by rememberSaveable(service.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EventCard(event = service.next, onClick = { expanded = !expanded })
        AnimatedVisibility(expanded) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleRow("تنبيه قبل الموعد بيوم ويوم الصرف", service.notify, onNotify)
                    ToggleRow("إظهار كودجت على الشاشة الرئيسية", service.pinned, onPin)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("يوم الصرف الميلادي: ${service.day}", Modifier.weight(1f))
                        TextButton(onClick = onEditDay) { Text("تعديل") }
                    }
                    HorizontalDivider()
                    Text("المواعيد القادمة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    service.schedule.forEach { e ->
                        Row(Modifier.fillMaxWidth()) {
                            Text(e.title.substringAfter(" - "), Modifier.width(72.dp), fontSize = 13.sp)
                            Text(
                                "${e.dateGregorian} • ${e.dateHijri}",
                                Modifier.weight(1f),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(Countdown.label(e.remainingDays), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Text(
                        "إذا وافق يوم الصرف الجمعة أو السبت يُقدَّم إلى الخميس.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDayDialog(service: ServiceUi, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(service.day.toString()) }
    val day = text.toIntOrNull()?.takeIf { it in 1..31 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(service.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("عدّل يوم الصرف إذا أعلنت الجهة عن موعد مختلف.", fontSize = 13.sp)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(2) },
                    label = { Text("اليوم (1-31)") },
                    isError = day == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val default = PaymentSchedule.service(service.id)?.defaultDay
                if (default != null) TextButton(onClick = { text = default.toString() }) { Text("استعادة الافتراضي ($default)") }
            }
        },
        confirmButton = { TextButton(enabled = day != null, onClick = { onSave(day!!) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
