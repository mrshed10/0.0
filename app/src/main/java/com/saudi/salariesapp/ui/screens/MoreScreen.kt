package com.saudi.salariesapp.ui.screens

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.Countdown
import com.saudi.salariesapp.data.WidgetStyle
import com.saudi.salariesapp.ui.UiState
import com.saudi.salariesapp.ui.components.ColorPicker
import com.saudi.salariesapp.ui.components.SectionTitle
import com.saudi.salariesapp.ui.theme.parseHex
import com.saudi.salariesapp.widget.SalariesWidgetReceiver

/** المزيد: محرّر الودجت (الألوان، الحجم، المحتوى) + المظهر + عن التطبيق. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onSaveStyle: (WidgetStyle) -> Unit,
    onPin: (String, Boolean) -> Unit,
    onThemeMode: (Int) -> Unit,
) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(state.widgetStyle) }
    LaunchedEffect(state.widgetStyle) { style = state.widgetStyle }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = contentPadding.calculateTopPadding() + 8.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionTitle("محرّر الودجت")
        WidgetPreview(style, state.widgetPreview.take(3).map { it.title to Countdown.label(it.remainingDays) })

        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ColorPicker("لون الخلفية", style.backgroundHex, { style = style.copy(backgroundHex = it) })
                ColorPicker("لون النص", style.textHex, { style = style.copy(textHex = it) })
                ColorPicker("لون الأيقونات", style.iconHex, { style = style.copy(iconHex = it) })
                Text("شفافية الخلفية: ${(style.backgroundAlpha * 100).toInt()}%")
                Slider(
                    value = style.backgroundAlpha,
                    onValueChange = { style = style.copy(backgroundAlpha = it) },
                    valueRange = 0.2f..1f,
                )
                Text("حجم الخط: ${"%.1f".format(style.textScale)}×")
                Slider(
                    value = style.textScale,
                    onValueChange = { style = style.copy(textScale = it) },
                    valueRange = 0.8f..1.5f,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onSaveStyle(style)
                        Toast.makeText(context, "تم تحديث الودجت", Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.weight(1f)) { Text("حفظ وتطبيق") }
                    OutlinedButton(onClick = { style = WidgetStyle() }) { Text("الافتراضي") }
                }
            }
        }

        SectionTitle("محتوى الودجت")
        Text(
            "اختر البطاقات التي تظهر في الودجت. إذا لم تختر شيئاً يعرض الودجت أقرب المواعيد تلقائياً.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                state.services.forEach { s -> PinRow(s.name, s.id in state.pinned) { onPin(s.id, it) } }
                if (state.countdowns.isNotEmpty()) HorizontalDivider()
                state.countdowns.forEach { c -> PinRow("⏳ ${c.title}", c.id in state.pinned) { onPin(c.id, it) } }
            }
        }
        OutlinedButton(onClick = {
            val mgr = AppWidgetManager.getInstance(context)
            val ok = mgr.isRequestPinAppWidgetSupported &&
                mgr.requestPinAppWidget(ComponentName(context, SalariesWidgetReceiver::class.java), null, null)
            if (!ok) Toast.makeText(context, "أضف الودجت من قائمة الودجت في الشاشة الرئيسية", Toast.LENGTH_LONG).show()
        }, modifier = Modifier.fillMaxWidth()) { Text("إضافة الودجت إلى الشاشة الرئيسية") }

        SectionTitle("المظهر")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("حسب النظام", "فاتح", "داكن").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = state.themeMode == i,
                    onClick = { onThemeMode(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                ) { Text(label) }
            }
        }

        SectionTitle("عن التطبيق")
        Text(
            "تطبيق مواعيد الرواتب السعودية — بلا إعلانات وبلا تتبّع. كل بياناتك تُحفظ على جهازك فقط. " +
                "المواعيد تقديرية بناءً على الأيام المعتادة للصرف، ويمكنك تعديل يوم أي خدمة من شاشة الرواتب.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** معاينة حيّة للودجت بالألوان المختارة قبل الحفظ. */
@Composable
private fun WidgetPreview(style: WidgetStyle, rows: List<Pair<String, String>>) {
    val bg = parseHex(style.backgroundHex).copy(alpha = style.backgroundAlpha)
    val text = parseHex(style.textHex)
    val icon = parseHex(style.iconHex)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = icon)
            Spacer(Modifier.width(8.dp))
            Text("مواعيدي", color = text, fontWeight = FontWeight.Bold, fontSize = (14 * style.textScale).sp)
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) Text("لا توجد مواعيد", color = text, fontSize = (13 * style.textScale).sp)
        rows.forEach { (title, days) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(title, Modifier.weight(1f), color = text, fontSize = (13 * style.textScale).sp, maxLines = 1)
                Text(days, color = icon, fontWeight = FontWeight.Bold, fontSize = (13 * style.textScale).sp)
            }
        }
    }
}
