package app.safar.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.safar.browser.data.QuickAction
import app.safar.browser.engine.ClosedTab
import app.safar.browser.engine.UrlUtils
import app.safar.browser.extensions.ChromeStore
import app.safar.browser.extensions.StoreAnalysis

/** علامات التبويب الأخيرة: إعادة فتح ما أُغلق مؤخراً مع سجل تنقله. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentTabsSheet(closed: List<ClosedTab>, onReopen: (ClosedTab) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("علامات التبويب الأخيرة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (closed.isEmpty()) Text("لا توجد علامات تبويب مغلقة مؤخراً", Modifier.padding(16.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            closed.forEach { t ->
                ListItem(
                    headlineContent = { Text(t.title.ifBlank { t.url }, maxLines = 1) },
                    supportingContent = { Text(UrlUtils.display(t.url), maxLines = 1) },
                    modifier = Modifier.clickable { onReopen(t); onDismiss() },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** حذف بيانات التصفح: نطاق زمني + أنواع البيانات. */
@Composable
fun ClearDataDialog(onDismiss: () -> Unit, onConfirm: (sinceMillis: Long, history: Boolean, cookies: Boolean, cache: Boolean) -> Unit) {
    val ranges = listOf("آخر ساعة" to 3_600_000L, "آخر 24 ساعة" to 86_400_000L, "آخر 7 أيام" to 604_800_000L, "كل الأوقات" to Long.MAX_VALUE)
    var range by remember { mutableStateOf(3) }
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(true) }
    var cache by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف بيانات التصفح") },
        text = {
            Column {
                Column {
                    ranges.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (label, _) ->
                                val i = ranges.indexOfFirst { it.first == label }
                                FilterChip(selected = range == i, onClick = { range = i }, label = { Text(label) })
                            }
                        }
                    }
                }
                CheckRow("سجل التصفح", history) { history = it }
                CheckRow("ملفات تعريف الارتباط وبيانات المواقع", cookies) { cookies = it }
                CheckRow("الصور والملفات المخزنة مؤقتاً", cache) { cache = it }
                Text("نطاق الوقت يُطبق على السجل. الكوكيز والملفات المؤقتة تُحذف كلها (قيد في GeckoView).",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val since = if (ranges[range].second == Long.MAX_VALUE) 0L else System.currentTimeMillis() - ranges[range].second
                onConfirm(since, history, cookies, cache); onDismiss()
            }) { Text("حذف البيانات") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun CheckRow(label: String, v: Boolean, on: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { on(!v) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = v, onCheckedChange = on); Text(label)
    }

/** شريط صغير فوق صفحة إضافة في متجر Chrome: "إضافة إلى سفر". */
@Composable
fun WebStoreBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(Color(0xE61A73E8)).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Storefront, null, tint = Color.White)
        Spacer(Modifier.size(8.dp))
        Text("إضافة إلى سفر", color = Color.White)
    }
}

/** نتيجة تحليل إضافة من متجر Chrome: التوافق + البدائل الموقّعة على AMO. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebStoreSheet(analyze: suspend () -> StoreAnalysis, onInstall: (String) -> Unit, onDismiss: () -> Unit) {
    var result by remember { mutableStateOf<StoreAnalysis?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { analyze() }.onSuccess { result = it }.onFailure { error = it.message } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("إضافة من متجر Chrome", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            when {
                error != null -> Text("تعذر التحليل: $error", color = MaterialTheme.colorScheme.error)
                result == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.size(8.dp)); Text("جارٍ تنزيل الحزمة وتحليلها…")
                }
                else -> {
                    val r = result!!
                    val color = when (r.compat.level) {
                        ChromeStore.Level.COMPATIBLE -> Color(0xFF2E7D32)
                        ChromeStore.Level.PARTIAL -> Color(0xFFEF6C00)
                        ChromeStore.Level.INCOMPATIBLE -> MaterialTheme.colorScheme.error
                    }
                    Text("${r.displayName}  •  الإصدار ${r.compat.version}  •  Manifest V${r.compat.manifestVersion}")
                    Text("التوافق مع سفر: ${r.compat.level.labelAr}", color = color, style = MaterialTheme.typography.titleMedium)
                    r.compat.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "سفر يعمل بمحرك Firefox (GeckoView) الذي يثبّت الإضافات الموقّعة من Mozilla فقط، " +
                            "لذلك نثبّت النسخة الرسمية نفسها من متجر Firefox إن وُجدت:",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (r.amo.isEmpty()) {
                        Text("لم يُعثر على نسخة متوافقة مع أندرويد على متجر Firefox لهذه الإضافة.", color = MaterialTheme.colorScheme.error)
                    }
                    r.amo.forEach { m ->
                        ListItem(
                            headlineContent = { Text(m.name) },
                            supportingContent = { Text("متجر Firefox • ${m.users} مستخدم يومياً") },
                            trailingContent = { FilledTonalButton(onClick = { onInstall(m.xpiUrl); onDismiss() }) { Text("تثبيت") } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** زر الاختصار السريع بجانب شريط العنوان. */
@Composable
fun QuickActionButton(action: QuickAction, speaking: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(
            when (action) {
                QuickAction.NEW_TAB, QuickAction.AUTO -> Icons.Outlined.AddBox
                QuickAction.SHARE -> Icons.Outlined.Share
                QuickAction.VOICE_SEARCH -> Icons.Outlined.Mic
                QuickAction.TRANSLATE -> Icons.Outlined.Translate
                QuickAction.BOOKMARK -> Icons.Outlined.StarBorder
                QuickAction.READ_ALOUD -> if (speaking) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.VolumeUp
            },
            contentDescription = action.labelAr,
        )
    }
}
