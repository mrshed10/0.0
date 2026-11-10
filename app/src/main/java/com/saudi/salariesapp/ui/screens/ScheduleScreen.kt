package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.data.HijriDates
import com.saudi.salariesapp.data.MonthlyPaymentRule
import com.saudi.salariesapp.data.PaymentSchedule
import com.saudi.salariesapp.data.daysLabel
import com.saudi.salariesapp.ui.components.SectionTitle
import com.saudi.salariesapp.ui.components.parseHex
import com.saudi.salariesapp.ui.components.rememberNow
import java.time.temporal.ChronoUnit

/**
 * Payment schedule for one or more rules (used by the Salaries, Retirement and Citizen
 * Account tabs): the next payment as a countdown card, then the coming months' dates.
 */
@Composable
fun ScheduleScreen(vm: AppViewModel, rules: List<MonthlyPaymentRule>, padding: PaddingValues) {
    val now = rememberNow()
    val today = now.toLocalDate()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(rules, key = { it.id }) { rule ->
            val upcoming = remember(rule, today) { PaymentSchedule.upcomingPayments(rule, today, 7) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle(rule.title)
                ManagedEventCard(vm, PaymentSchedule.paymentEvent(rule, upcoming.first()), now)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(
                            "المواعيد القادمة",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            fontWeight = FontWeight.Bold,
                        )
                        upcoming.drop(1).forEachIndexed { i, d ->
                            if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                            val days = ChronoUnit.DAYS.between(today, d)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(PaymentSchedule.paymentTitle(rule, d), fontSize = 14.sp)
                                    Text(
                                        "${HijriDates.dayName(d.dayOfWeek)} ${HijriDates.formatHijri(d)} · ${HijriDates.formatGregorian(d)}",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    "$days ${daysLabel(days)}",
                                    fontWeight = FontWeight.Bold,
                                    color = parseHex(rule.category.defaultColorHex),
                                )
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "يُصرف في يوم العمل السابق أو التالي إذا وافق موعد الصرف عطلة نهاية الأسبوع (الجمعة والسبت).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
