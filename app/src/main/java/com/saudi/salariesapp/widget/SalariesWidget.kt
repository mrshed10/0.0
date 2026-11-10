package com.saudi.salariesapp.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
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
import com.saudi.salariesapp.data.AppPreferences
import com.saudi.salariesapp.data.Countdown
import com.saudi.salariesapp.data.EventModel
import com.saudi.salariesapp.data.EventsRepository
import com.saudi.salariesapp.data.HijriCalendar
import com.saudi.salariesapp.data.WidgetStyle
import com.saudi.salariesapp.ui.theme.parseHex
import java.time.LocalDate

/**
 * الودجت الشامل: يعرض أي بطاقة من التطبيق (رواتب، تقاعد، حساب المواطن، مناسبات، عدادات شخصية)
 * بألوان يختارها المستخدم (الخلفية، النص، الأيقونات) وحجم خط قابل للتعديل.
 * يتكيّف المحتوى مع حجم الودجت (صغير / متوسط / كبير).
 */
class SalariesWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // تُقرأ الإعدادات داخل التركيب لتنعكس فوراً عند استدعاء refreshAll بعد تغييرها
            val ctx = LocalContext.current
            val prefs = AppPreferences(ctx)
            val events = EventsRepository(prefs).widgetEvents()
            WidgetContent(prefs.widgetStyle(), events)
        }
    }

    companion object {
        private val SMALL = DpSize(110.dp, 110.dp)
        private val MEDIUM = DpSize(250.dp, 110.dp)
        private val LARGE = DpSize(250.dp, 260.dp)

        suspend fun refreshAll(context: Context) = SalariesWidget().updateAll(context)
    }
}

class SalariesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SalariesWidget()
}

@Composable
private fun WidgetContent(style: WidgetStyle, events: List<EventModel>) {
    val bg = parseHex(style.backgroundHex).copy(alpha = style.backgroundAlpha)
    val text = ColorProvider(parseHex(style.textHex))
    val icon = ColorProvider(parseHex(style.iconHex))
    val s = style.textScale
    val size = LocalSize.current

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(bg))
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_calendar),
                contentDescription = null,
                colorFilter = ColorFilter.tint(icon),
                modifier = GlanceModifier.size((18 * s).dp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = HijriCalendar.formatHijri(LocalDate.now()),
                style = TextStyle(color = text, fontSize = (12 * s).sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(6.dp))

        if (events.isEmpty()) {
            Text("لا توجد مواعيد", style = TextStyle(color = text, fontSize = (13 * s).sp))
        } else if (size.width < 200.dp) {
            // ودجت صغير: الموعد الأقرب بخط كبير
            val e = events.first()
            Text(e.title, style = TextStyle(color = text, fontSize = (13 * s).sp), maxLines = 2)
            Spacer(GlanceModifier.height(4.dp))
            Text(
                Countdown.label(e.remainingDays),
                style = TextStyle(color = icon, fontSize = (24 * s).sp, fontWeight = FontWeight.Bold),
            )
        } else {
            val rows = if (size.height < 200.dp) 3 else 7
            events.take(rows).forEach { e ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        e.title,
                        style = TextStyle(color = text, fontSize = (13 * s).sp),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        Countdown.label(e.remainingDays),
                        style = TextStyle(color = icon, fontSize = (13 * s).sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }
}
