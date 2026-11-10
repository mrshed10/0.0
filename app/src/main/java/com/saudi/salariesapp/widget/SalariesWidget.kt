package com.saudi.salariesapp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.saudi.salariesapp.MainActivity
import com.saudi.salariesapp.R
import com.saudi.salariesapp.data.AppPrefs
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.HijriDates
import com.saudi.salariesapp.data.WidgetSettings
import com.saudi.salariesapp.data.daysLabel
import java.time.LocalDate

/**
 * The all-in-one home-screen widget. Shows any events the user pinned (salaries, retirement,
 * citizen account, occasions or personal countdowns) in the colours the user picked.
 */
class SalariesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = AppPrefs(context)
        val settings = prefs.loadWidgetSettings()
        val today = LocalDate.now()
        val events = selectEvents(prefs.allEvents(today), settings, today)

        provideContent {
            WidgetContent(settings, events, today)
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val WIDE = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 250.dp)

        /** Pinned events in the order pinned, or the nearest upcoming ones if nothing is pinned. */
        fun selectEvents(all: List<EventModel>, s: WidgetSettings, today: LocalDate): List<EventModel> {
            val upcoming = all.filter { it.remainingDays(today) >= 0 }
            return if (s.pinnedIds.isEmpty()) upcoming
            else s.pinnedIds.mapNotNull { id -> all.firstOrNull { it.id == id } }
        }

        suspend fun refreshAll(context: Context) {
            SalariesWidget().updateAll(context)
        }
    }
}

@Composable
private fun WidgetContent(s: WidgetSettings, events: List<EventModel>, today: LocalDate) {
    val size = LocalSize.current
    val text = ColorProvider(Color(s.textColor))
    val icon = ColorProvider(Color(s.iconColor))
    val rows = when {
        size.height < 180.dp -> if (size.width < 200.dp) 1 else 2
        else -> s.maxItems
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color(s.backgroundColor))
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        if (s.showHijriHeader) {
            Text(
                text = HijriDates.formatHijriWithDay(today),
                style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text = HijriDates.formatGregorian(today),
                style = TextStyle(color = text, fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(8.dp))
        }

        if (events.isEmpty()) {
            Text("لا توجد مواعيد مختارة", style = TextStyle(color = text, fontSize = 13.sp))
        }

        events.take(rows).forEach { e ->
            val days = e.remainingDays(today)
            Row(
                modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_calendar),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(icon),
                    modifier = GlanceModifier.size(18.dp),
                )
                Spacer(GlanceModifier.width(6.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = e.title,
                        style = TextStyle(color = text, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                    )
                    Text(text = e.dateHijri, style = TextStyle(color = text, fontSize = 10.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = when {
                        days < 0 -> "انتهى"
                        days == 0L -> "اليوم"
                        else -> "$days ${daysLabel(days)}"
                    },
                    style = TextStyle(color = icon, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

class SalariesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalariesWidget()
}
