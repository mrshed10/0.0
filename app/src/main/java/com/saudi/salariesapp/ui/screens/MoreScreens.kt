package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.data.HijriDates
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.ui.components.HijriDateSelector
import com.saudi.salariesapp.ui.components.SectionTitle
import com.saudi.salariesapp.ui.components.rememberNow
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoUnit
import java.util.Locale

object MoreRoutes {
    const val OCCASIONS = "occasions"
    const val AGE = "age"
    const val CONVERTER = "converter"
    const val VAT = "vat"
    const val WIDGET = "widget"
    const val SETTINGS = "settings"
}

private data class MoreEntry(val route: String, val title: String, val subtitle: String, val icon: ImageVector)

private val entries = listOf(
    MoreEntry(MoreRoutes.OCCASIONS, "المناسبات", "رمضان، الأعياد، اليوم الوطني ويوم التأسيس", Icons.Filled.Star),
    MoreEntry(MoreRoutes.AGE, "حساب العمر", "بالهجري والميلادي", Icons.Filled.Face),
    MoreEntry(MoreRoutes.CONVERTER, "محول التاريخ", "هجري (أم القرى) ↔ ميلادي", Icons.Filled.DateRange),
    MoreEntry(MoreRoutes.VAT, "حاسبة الفواتير والأسعار", "ضريبة القيمة المضافة 15٪", Icons.Filled.ShoppingCart),
    MoreEntry(MoreRoutes.WIDGET, "تخصيص الودجت", "الألوان والمحتوى المعروض", Icons.Filled.Build),
    MoreEntry(MoreRoutes.SETTINGS, "الإعدادات", "الوضع الداكن وعن التطبيق", Icons.Filled.Settings),
)

@Composable
fun MoreScreen(padding: PaddingValues, onOpen: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(entries) { e ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onOpen(e.route) },
            ) {
                ListItem(
                    headlineContent = { Text(e.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(e.subtitle) },
                    leadingContent = { Icon(e.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                )
            }
        }
    }
}

fun screenPadding(padding: PaddingValues) = PaddingValues(
    start = 16.dp, end = 16.dp,
    top = padding.calculateTopPadding() + 8.dp,
    bottom = padding.calculateBottomPadding() + 16.dp,
)

// ---- Occasions ------------------------------------------------------------------------------

@Composable
fun OccasionsScreen(vm: AppViewModel, padding: PaddingValues) {
    val now = rememberNow()
    val today = now.toLocalDate()
    val events = remember(today) {
        PaymentSchedule.occasionRules.map { PaymentSchedule.occasionEvent(it, today) }.sortedBy { it.target }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = screenPadding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { ManagedEventCard(vm, it, now) }
        item {
            Text(
                "مواعيد المناسبات الهجرية محسوبة وفق تقويم أم القرى، وقد تختلف بيوم حسب رؤية الهلال.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Shared date input ----------------------------------------------------------------------

/** A date chosen in either calendar, always showing the equivalent in the other. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualDateInput(date: LocalDate, onChange: (LocalDate) -> Unit, label: String) {
    var hijri by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !hijri, onClick = { hijri = false }, label = { Text("ميلادي") })
            FilterChip(selected = hijri, onClick = { hijri = true }, label = { Text("هجري") })
        }
        if (hijri) {
            HijriDateSelector(date = date, onChange = onChange, yearsBack = 120)
        } else {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text(HijriDates.formatGregorian(date))
            }
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("تم") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun ResultCard(lines: List<Pair<String, String>>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(k, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(v, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun ScrollScreen(padding: PaddingValues, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenPadding(padding)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

// ---- Age calculator -------------------------------------------------------------------------

@Composable
fun AgeCalculatorScreen(padding: PaddingValues) {
    val today = remember { LocalDate.now() }
    var birth by remember { mutableStateOf(today.minusYears(30)) }
    ScrollScreen(padding) {
        DualDateInput(date = birth, onChange = { if (!it.isAfter(today)) birth = it }, label = "تاريخ الميلاد")
        val g = Period.between(birth, today)
        val h = HijrahDate.from(birth).until(HijrahDate.from(today))
        val nextBirthday = birth.withYear(today.year).let { if (it.isBefore(today)) it.plusYears(1) else it }
        ResultCard(
            listOf(
                "العمر بالميلادي" to "${g.years} سنة و ${g.months} شهر و ${g.days} يوم",
                "العمر بالهجري" to "${h.get(ChronoUnit.YEARS)} سنة و ${h.get(ChronoUnit.MONTHS)} شهر و ${h.get(ChronoUnit.DAYS)} يوم",
                "عدد الأيام" to "${ChronoUnit.DAYS.between(birth, today)} يوم",
                "تاريخ الميلاد هجري" to HijriDates.formatHijri(birth),
                "يوم الميلاد" to HijriDates.dayName(birth.dayOfWeek),
                "عيد الميلاد القادم بعد" to "${ChronoUnit.DAYS.between(today, nextBirthday)} يوم",
            )
        )
    }
}

// ---- Date converter -------------------------------------------------------------------------

@Composable
fun DateConverterScreen(padding: PaddingValues) {
    val today = remember { LocalDate.now() }
    var date by remember { mutableStateOf(today) }
    ScrollScreen(padding) {
        DualDateInput(date = date, onChange = { date = it }, label = "اختر التاريخ")
        val diff = ChronoUnit.DAYS.between(today, date)
        ResultCard(
            listOf(
                "اليوم" to HijriDates.dayName(date.dayOfWeek),
                "هجري" to HijriDates.formatHijri(date),
                "ميلادي" to HijriDates.formatGregorian(date),
                "الفرق عن اليوم" to when {
                    diff == 0L -> "اليوم"
                    diff > 0 -> "بعد $diff يوم"
                    else -> "قبل ${-diff} يوم"
                },
            )
        )
    }
}

// ---- VAT / price calculator -----------------------------------------------------------------

@Composable
fun VatCalculatorScreen(padding: PaddingValues) {
    var amountText by remember { mutableStateOf("") }
    var includesVat by remember { mutableStateOf(false) }
    var rateText by remember { mutableStateOf("15") }
    ScrollScreen(padding) {
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("المبلغ (ريال)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = rateText,
            onValueChange = { rateText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("نسبة الضريبة ٪") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !includesVat, onClick = { includesVat = false }, label = { Text("السعر غير شامل الضريبة") })
            FilterChip(selected = includesVat, onClick = { includesVat = true }, label = { Text("شامل الضريبة") })
        }
        val amount = amountText.toDoubleOrNull()
        val rate = (rateText.toDoubleOrNull() ?: 0.0) / 100
        if (amount != null) {
            val net = if (includesVat) amount / (1 + rate) else amount
            val vat = net * rate
            ResultCard(
                listOf(
                    "السعر قبل الضريبة" to money(net),
                    "قيمة الضريبة" to money(vat),
                    "الإجمالي شامل الضريبة" to money(net + vat),
                )
            )
        }
    }
}

private fun money(v: Double) = String.format(Locale.US, "%,.2f ريال", v)

// ---- Settings -------------------------------------------------------------------------------

@Composable
fun SettingsScreen(vm: AppViewModel, padding: PaddingValues) {
    val dark by vm.darkMode.collectAsStateWithLifecycle()
    ScrollScreen(padding) {
        SectionTitle("المظهر")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dark == null, onClick = { vm.setDarkMode(null) }, label = { Text("حسب النظام") })
            FilterChip(selected = dark == false, onClick = { vm.setDarkMode(false) }, label = { Text("فاتح") })
            FilterChip(selected = dark == true, onClick = { vm.setDarkMode(true) }, label = { Text("داكن") })
        }
        SectionTitle("عن التطبيق")
        Row {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                "تطبيق مواعيد الرواتب والدعم والمناسبات في المملكة. خالٍ تماماً من الإعلانات، " +
                    "ولا يطلب صلاحية الإنترنت؛ كل بياناتك محفوظة على جهازك فقط.",
                fontSize = 14.sp,
            )
        }
    }
}
