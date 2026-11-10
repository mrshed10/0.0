package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.Calculators
import com.saudi.salariesapp.data.HijriCalendar
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.ui.components.DualDateInput
import com.saudi.salariesapp.ui.components.rememberDualDateState
import java.time.LocalDate

private val tools = listOf("محوّل التاريخ", "حساب العمر", "الفرق بين تاريخين", "الأسعار والضريبة", "صافي الراتب")

/** الحاسبات والأدوات بواجهة نظيفة بلا أي إعلانات. */
@Composable
fun ToolsScreen(contentPadding: PaddingValues) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tools.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> DateConverter()
                1 -> AgeCalculator()
                2 -> DaysBetween()
                3 -> PriceCalculator()
                4 -> SalaryCalculator()
            }
        }
    }
}

@Composable
private fun ResultCard(lines: List<Pair<String, String>>) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { (k, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun DateConverter() {
    val state = rememberDualDateState(LocalDate.now(), hijri = true)
    Text("أدخل التاريخ بأي تقويم ليظهر ما يوافقه (تقويم أم القرى).")
    DualDateInput(state)
    state.toLocalDate()?.let { d ->
        ResultCard(
            listOf(
                "اليوم" to HijriCalendar.dayName(d.dayOfWeek),
                "هجري" to HijriCalendar.formatHijri(d),
                "ميلادي" to HijriCalendar.formatGregorian(d),
                "بعد/قبل" to PaymentSchedule.daysBetween(LocalDate.now(), d).let {
                    when {
                        it > 0 -> "بعد $it يوماً"
                        it < 0 -> "قبل ${-it} يوماً"
                        else -> "اليوم"
                    }
                },
            )
        )
    }
}

@Composable
private fun AgeCalculator() {
    val state = rememberDualDateState(LocalDate.now().minusYears(25))
    Text("تاريخ الميلاد:")
    DualDateInput(state)
    val birth = state.toLocalDate() ?: return
    val age = Calculators.age(birth, LocalDate.now())
    if (age == null) {
        Text("تاريخ الميلاد في المستقبل", color = MaterialTheme.colorScheme.error)
        return
    }
    ResultCard(
        listOf(
            "العمر بالميلادي" to "${age.years} سنة، ${age.months} شهر، ${age.days} يوم",
            "العمر بالهجري" to "${age.hijriYears} سنة",
            "عدد الأيام" to "%,d يوم".format(age.totalDays),
            "يوم الميلاد" to HijriCalendar.dayName(birth.dayOfWeek),
        )
    )
}

@Composable
private fun DaysBetween() {
    val from = rememberDualDateState(LocalDate.now())
    val to = rememberDualDateState(LocalDate.now().plusMonths(1))
    Text("من:")
    DualDateInput(from)
    Text("إلى:")
    DualDateInput(to)
    val a = from.toLocalDate() ?: return
    val b = to.toLocalDate() ?: return
    val days = PaymentSchedule.daysBetween(a, b)
    ResultCard(listOf("الفرق" to "$days يوماً", "بالأسابيع" to "%.1f أسبوع".format(days / 7.0)))
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || it == '.' }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun money(v: Double) = "%,.2f ر.س".format(v)

@Composable
private fun PriceCalculator() {
    var amount by rememberSaveable { mutableStateOf("") }
    var discount by rememberSaveable { mutableStateOf("") }
    var includesVat by rememberSaveable { mutableStateOf(false) }
    NumberField(amount, { amount = it }, "السعر")
    NumberField(discount, { discount = it }, "نسبة الخصم % (اختياري)")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("السعر المُدخل شامل ضريبة القيمة المضافة 15%", Modifier.weight(1f))
        Switch(checked = includesVat, onCheckedChange = { includesVat = it })
    }
    val a = amount.toDoubleOrNull() ?: return
    val p = Calculators.price(a, includesVat, discount.toDoubleOrNull() ?: 0.0)
    ResultCard(
        listOf("قبل الضريبة" to money(p.beforeVat), "الضريبة (15%)" to money(p.vat), "الإجمالي" to money(p.total))
    )
}

@Composable
private fun SalaryCalculator() {
    var basic by rememberSaveable { mutableStateOf("") }
    var allowances by rememberSaveable { mutableStateOf("") }
    var deduction by rememberSaveable { mutableStateOf("9") }
    NumberField(basic, { basic = it }, "الراتب الأساسي")
    NumberField(allowances, { allowances = it }, "مجموع البدلات (سكن، نقل...)")
    NumberField(deduction, { deduction = it }, "نسبة الاستقطاع من الأساسي %")
    Text(
        "الافتراضي 9% (التقاعد المدني). عدّل النسبة حسب نظامك. النتيجة تقديرية.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val b = basic.toDoubleOrNull() ?: return
    val s = Calculators.salary(b, allowances.toDoubleOrNull() ?: 0.0, deduction.toDoubleOrNull() ?: 0.0)
    ResultCard(
        listOf("الإجمالي" to money(s.gross), "الاستقطاع" to money(s.deduction), "الصافي التقديري" to money(s.net))
    )
}
