package com.saudi.salariesapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.Countdown
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.HijriCalendar
import com.saudi.salariesapp.ui.theme.parseHex
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime

/** بطاقة التاريخ اليومي (هجري أم القرى + ميلادي). */
@Composable
fun HeaderDateCard(today: LocalDate, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD54F)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = HijriCalendar.formatFull(today),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.Black,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "الموافق ${HijriCalendar.formatGregorian(today)}",
                fontSize = 14.sp,
                color = Color.DarkGray,
            )
        }
    }
}

/** بطاقة موعد مع خانة الأيام المتبقية. [trailing] لأزرار إضافية (تثبيت في الودجت، حذف...). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: EventModel,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val accent = parseHex(event.colorHex)
    val cardModifier = modifier.fillMaxWidth()
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 44.dp)
                    .background(accent, RoundedCornerShape(3.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(event.dateHijri, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(event.dateGregorian, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            trailing?.invoke()
            DaysBadge(event.remainingDays, accent)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = cardModifier, shape = RoundedCornerShape(16.dp)) { content() }
    } else {
        Card(modifier = cardModifier, shape = RoundedCornerShape(16.dp)) { content() }
    }
}

@Composable
fun DaysBadge(days: Int, accent: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.14f)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (days <= 0) {
                Text("اليوم", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accent)
            } else {
                Text("متبقي", fontSize = 10.sp, color = accent)
                Text("$days", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
                Text(if (days in 3..10) "أيام" else "يوماً", fontSize = 10.sp, color = accent)
            }
        }
    }
}

/** عداد تنازلي مباشر (أيام، ساعات، دقائق، ثوانٍ) يتحدّث كل ثانية. */
@Composable
fun LiveCountdown(target: LocalDateTime, accent: Color, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(target) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val r = Countdown.remaining(now, target)
    if (r.isOver) {
        Text("🎉 حان الموعد", modifier = modifier, color = accent, fontWeight = FontWeight.Bold)
        return
    }
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        TimeUnit(r.days, "يوم", accent)
        TimeUnit(r.hours, "ساعة", accent)
        TimeUnit(r.minutes, "دقيقة", accent)
        TimeUnit(r.seconds, "ثانية", accent)
    }
}

@Composable
private fun TimeUnit(value: Long, label: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.14f)) {
            Text(
                text = value.toString().padStart(2, '0'),
                modifier = Modifier
                    .width(54.dp)
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = accent,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** بطاقة مميّزة للموعد الأقرب مع عداد مباشر. */
@Composable
fun HighlightCountdownCard(event: EventModel, modifier: Modifier = Modifier) {
    val accent = parseHex(event.colorHex)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(accent, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(event.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${event.dateHijri} • ${event.dateGregorian}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LiveCountdown(event.target, accent)
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}
