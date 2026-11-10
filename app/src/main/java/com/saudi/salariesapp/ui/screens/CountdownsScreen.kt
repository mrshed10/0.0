package com.saudi.salariesapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saudi.salariesapp.data.CustomCountdown
import com.saudi.salariesapp.data.HijriCalendar
import com.saudi.salariesapp.ui.UiState
import com.saudi.salariesapp.ui.components.LiveCountdown
import com.saudi.salariesapp.ui.theme.parseHex

/** العدادات التنازلية المخصصة للمستخدم مع عدّاد مباشر لكل واحد. */
@Composable
fun CountdownsScreen(
    state: UiState,
    contentPadding: PaddingValues,
    onDelete: (String) -> Unit,
    onPin: (String, Boolean) -> Unit,
) {
    var toDelete by remember { mutableStateOf<CustomCountdown?>(null) }

    if (state.countdowns.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(contentPadding).padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "لا توجد عدادات بعد.\nاضغط زر + لإضافة عداد لأي موعد: اختبار، زواج، سداد فاتورة...",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(state.countdowns, key = { it.id }) { c ->
            val accent = parseHex(c.colorHex)
            val pinned = c.id in state.pinned
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = accent)
                            val d = c.target.toLocalDate()
                            val time = "%02d:%02d".format(c.target.hour, c.target.minute)
                            val dateText = if (c.enteredAsHijri)
                                "${HijriCalendar.formatHijri(d)} • ${HijriCalendar.formatGregorian(d)}"
                            else "${HijriCalendar.formatGregorian(d)} • ${HijriCalendar.formatHijri(d)}"
                            Text("$dateText • $time", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onPin(c.id, !pinned) }) {
                            Icon(
                                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (pinned) "إزالة من الودجت" else "إظهار في الودجت",
                                tint = if (pinned) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { toDelete = c }) {
                            Icon(Icons.Filled.Delete, contentDescription = "حذف")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LiveCountdown(c.target, accent)
                }
            }
        }
    }

    toDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("حذف العداد") },
            text = { Text("هل تريد حذف \"${c.title}\"؟") },
            confirmButton = { TextButton(onClick = { onDelete(c.id); toDelete = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("إلغاء") } },
        )
    }
}
