package com.athar.app.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.athar.app.R

/**
 * The live clock views (TextClock / AnalogClock) for clock widgets. Shared by
 * the home-screen widget and the in-app preview so both show the same clock.
 * Sizes are in dp of the widget's content area.
 */
object LiveClockViews {
    fun isLiveClock(data: WidgetData) = data is WidgetData.Clock || data is WidgetData.WideClock || data is WidgetData.WorldClock

    /** Widgets drawn entirely by native views (clocks and the animated prayer designs). */
    fun isLive(data: WidgetData, ws: WidgetStyle) = isLiveClock(data) || PrayerPulse.isAnimated(data, ws)

    fun build(context: Context, data: WidgetData, ws: WidgetStyle, wDp: Float, hDp: Float): RemoteViews? = when (data) {
        is WidgetData.NextPrayer -> if (PrayerPulse.isAnimated(data, ws)) {
            val pad = paddingDp(wDp, hDp)
            PrayerPulse.build(context, data, ws, wDp - 2 * pad, hDp - 2 * pad)
        } else {
            null
        }
        is WidgetData.Clock -> if (data.analog) {
            RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_analog_dark else R.layout.widget_analog_light)
        } else {
            val timeDp = digitalTimeDp(ws, wDp, hDp)
            RemoteViews(context.packageName, ClockLayouts.digital(ws.fontId)).apply {
                setTextColor(R.id.clock_time, ws.textColor)
                setTextColor(R.id.clock_date, ws.accentColor)
                setTextViewTextSize(R.id.clock_time, TypedValue.COMPLEX_UNIT_DIP, timeDp)
                setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_DIP, (timeDp * 0.26f).coerceIn(10f, 22f))
                if (ws.variant == 1) setViewVisibility(R.id.clock_date, View.GONE)
            }
        }
        is WidgetData.WideClock -> {
            val digitDp = minOf(hDp * 0.28f, wDp * 0.12f).coerceIn(14f, 64f)
            RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_wide_clock_dark else R.layout.widget_wide_clock_light).apply {
                setTextColor(R.id.wide_hour, ws.textColor)
                setTextColor(R.id.wide_minute, ws.textColor)
                setTextViewTextSize(R.id.wide_hour, TypedValue.COMPLEX_UNIT_DIP, digitDp)
                setTextViewTextSize(R.id.wide_minute, TypedValue.COMPLEX_UNIT_DIP, digitDp)
            }
        }
        is WidgetData.WorldClock -> {
            val timeDp = (if (ws.variant == 1) minOf(hDp * 0.17f, wDp * 0.2f) else minOf(hDp * 0.26f, wDp * 0.11f)).coerceIn(14f, 56f)
            RemoteViews(context.packageName, if (ws.variant == 1) R.layout.widget_world_clock_stacked else R.layout.widget_world_clock).apply {
                listOf(
                    Triple(R.id.city1_name, R.id.city1_time, R.id.city1_ampm) to data.a,
                    Triple(R.id.city2_name, R.id.city2_time, R.id.city2_ampm) to data.b,
                ).forEach { (ids, city) ->
                    setTextViewText(ids.first, city.name(data.arabic))
                    setTextColor(ids.first, ws.accentColor)
                    setTextColor(ids.second, ws.textColor)
                    setTextColor(ids.third, ws.textColor)
                    setTextViewTextSize(ids.second, TypedValue.COMPLEX_UNIT_DIP, timeDp)
                    setTextViewTextSize(ids.first, TypedValue.COMPLEX_UNIT_DIP, (timeDp * 0.4f).coerceIn(9f, 18f))
                    setTextViewTextSize(ids.third, TypedValue.COMPLEX_UNIT_DIP, (timeDp * 0.36f).coerceIn(8f, 16f))
                    setString(ids.second, "setTimeZone", city.zone)
                    setString(ids.third, "setTimeZone", city.zone)
                }
            }
        }
        else -> null
    }

    fun digitalTimeDp(ws: WidgetStyle, wDp: Float, hDp: Float) =
        minOf(hDp * (if (ws.variant == 1) 0.55f else 0.42f), wDp * 0.24f).coerceIn(18f, 110f)

    /** Same padding rule as the widget, in dp. */
    fun paddingDp(wDp: Float, hDp: Float): Float {
        val small = wDp < 170f || hDp < 100f
        val wideShape = wDp / hDp.coerceAtLeast(1f) > 1.4f
        val scale = minOf(wDp / (if (wideShape) 320f else 160f), hDp / (if (wideShape) 150f else 160f)).coerceIn(0.6f, 1.8f)
        return (if (small) 10f else 14f) * scale.coerceIn(0.7f, 1.3f)
    }
}
