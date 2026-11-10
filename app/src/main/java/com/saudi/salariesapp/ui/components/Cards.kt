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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.HijriDates
import com.saudi.salariesapp.data.Remaining
import com.saudi.salariesapp.data.daysLabel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

fun parseHex(hex: String, fallback: Color = Color(0xFF007A5E)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

fun Color.toHex(): String {
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
    )
    return String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
}

/** Current time, ticking every second while on screen. */
@Composable
fun rememberNow(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            now = LocalDateTime.now()
        }
    }
    return now
}

@Composable
fun HeaderDateCard(today: LocalDate = LocalDate.now()) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = HijriDates.formatHijriWithDay(today),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "الموافق ${HijriDates.formatGregorian(today)}",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f),
            )
        }
    }
}

/**
 * A card for one event, with a countdown box and optional actions:
 * reminder bell, "show on widget" star and delete (custom countdowns only).
 */
@Composable
fun EventCard(
    event: EventModel,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    live: Boolean = event.isCustom,
    reminderOn: Boolean? = null,
    onToggleReminder: (() -> Unit)? = null,
    pinnedToWidget: Boolean? = null,
    onToggleWidget: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val accent = parseHex(event.colorHex)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).background(accent, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(text = event.dateHijri, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = event.dateGregorian, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            CountdownBox(event, now, live, accent)
        }
        if (onToggleReminder != null || onToggleWidget != null || onDelete != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (onToggleReminder != null) {
                    IconButton(onClick = onToggleReminder) {
                        Icon(
                            if (reminderOn == true) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = if (reminderOn == true) "إيقاف التنبيه" else "تفعيل التنبيه",
                            tint = if (reminderOn == true) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onToggleWidget != null) {
                    IconButton(onClick = onToggleWidget) {
                        Icon(
                            if (pinnedToWidget == true) Icons.Filled.Star else Icons.Outlined.Star,
                            contentDescription = if (pinnedToWidget == true) "إزالة من الودجت" else "إظهار كودجت على الشاشة الرئيسية",
                            tint = if (pinnedToWidget == true) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/** Remaining-time box: days only, or a live days/hours/minutes/seconds countdown. */
@Composable
fun CountdownBox(event: EventModel, now: LocalDateTime, live: Boolean, accent: Color) {
    val r: Remaining = event.remaining(now)
    val days = event.remainingDays(now.toLocalDate())
    Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.12f)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                (live && r.isPast) || (!live && days < 0) -> Text("انتهى", fontWeight = FontWeight.Bold, color = accent)
                days == 0L && !live -> Text("اليوم", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = accent)
                live -> {
                    Text("متبقي", fontSize = 10.sp, color = accent)
                    Text("${r.days}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
                    Text(daysLabel(r.days), fontSize = 10.sp, color = accent)
                    Text(
                        String.format(Locale.US, "%02d:%02d:%02d", r.hours, r.minutes, r.seconds),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium, color = accent,
                    )
                }
                else -> {
                    Text("متبقي", fontSize = 10.sp, color = accent)
                    Text("$days", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = accent)
                    Text(daysLabel(days), fontSize = 10.sp, color = accent)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.primary,
    )
}
