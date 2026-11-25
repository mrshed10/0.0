package app.safar.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Preview
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.safar.browser.LinkTarget
import app.safar.browser.engine.UrlUtils
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private val Surface = Color(0xFF1F2023)
private val Header = Color(0xFF2A2B2F)
private val Secondary = Color(0xFFA8ABB3)
private val Accent = Color(0xFF8AB4F8)

class LinkActions(
    val newTab: (String) -> Unit,
    val newTabInGroup: (String) -> Unit,
    val incognito: (String) -> Unit,
    val newWindow: (String) -> Unit,
    val preview: (String) -> Unit,
    val copyAddress: (String) -> Unit,
    val copyText: (String) -> Unit,
    val download: (String) -> Unit,
    val readingList: (String, String) -> Unit,
    val share: (String, String) -> Unit,
)

/**
 * قائمة الضغط المطوّل على الروابط (Long-Press Context Sheet):
 * سطح داكن RTL، بطاقة تعريفية (أيقونة الموقع + العنوان + الرابط المقتطع)، وعشرة إجراءات مرتبة في ثلاث مجموعات
 * ذهنية دون فواصل: فتح (1–5)، نسخ وحفظ (6–9)، مشاركة (10). الأكثر استخداماً على الأطراف.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextSheet(t: LinkTarget, a: LinkActions, onDismiss: () -> Unit) {
    val url = t.url
    val title = t.text.ifBlank { t.title }.ifBlank { url?.let { UrlUtils.display(it) } ?: "" }
    fun act(f: () -> Unit) = { onDismiss(); f() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface, contentColor = Color.White, scrimColor = Color(0x99000000)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            // 0) بطاقة هوية الرابط
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Header).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val host = UrlUtils.host(url ?: t.imageSrc) ?: "?"
                Box(Modifier.size(40.dp).clip(CircleShape).background(Accent.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
                    Text(host.take(1).uppercase(), color = Accent, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        url ?: t.imageSrc.orEmpty(), color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Ltr),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (url != null) {
                // (1–5) إجراءات فتح الصفحة
                Item(Icons.Outlined.AddBox, "الفتح في علامة تبويب جديدة", act { a.newTab(url) })
                Item(Icons.Outlined.Workspaces, "الفتح في علامة تبويب جديدة في مجموعة", act { a.newTabInGroup(url) })
                Item(Icons.Outlined.VisibilityOff, "فتح في علامة تبويب تصفّح متخفٍ", act { a.incognito(url) })
                Item(Icons.AutoMirrored.Outlined.OpenInNew, "الفتح في نافذة جديدة", act { a.newWindow(url) })
                Item(Icons.Outlined.Preview, "معاينة الصفحة", act { a.preview(url) })
                Spacer(Modifier.height(8.dp))
                // (6–9) النسخ والحفظ
                Item(Icons.Outlined.Link, "نسخ عنوان الرابط", act { a.copyAddress(url) })
                if (t.text.isNotBlank()) Item(Icons.Outlined.TextFields, "نسخ نص الرابط", act { a.copyText(t.text) })
                Item(Icons.Outlined.Download, "تنزيل الرابط", act { a.download(url) })
                Item(Icons.Outlined.BookmarkAdd, "الإضافة إلى قائمة القراءة", act { a.readingList(url, title) })
            }
            t.imageSrc?.let { src ->
                Spacer(Modifier.height(8.dp))
                Item(Icons.Outlined.Image, "فتح الصورة في علامة تبويب جديدة", act { a.newTab(src) })
                Item(Icons.Outlined.Download, "تنزيل الصورة", act { a.download(src) })
                Item(Icons.Outlined.ContentCopy, "نسخ عنوان الصورة", act { a.copyAddress(src) })
            }
            Spacer(Modifier.height(8.dp))
            // (10) المشاركة في القاع — أقرب نقطة لحركة الإبهام
            Item(Icons.Outlined.Share, "مشاركة الرابط", act { a.share(url ?: t.imageSrc.orEmpty(), title) })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Secondary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(20.dp))
        Text(label, fontSize = 16.sp)
    }
}

/** معاينة الصفحة: نافذة مصغّرة لمحتوى الرابط قبل فتحه بالكامل. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePreviewSheet(url: String, newSession: (String) -> GeckoSession, onOpen: (String) -> Unit, onDismiss: () -> Unit) {
    val session = remember(url) { newSession(url) }
    DisposableEffect(session) { onDispose { session.close() } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface, contentColor = Color.White) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WebAsset, null, tint = Secondary)
            Spacer(Modifier.width(8.dp))
            Text(UrlUtils.display(url), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { onDismiss(); onOpen(url) }) { Text("فتح في علامة تبويب جديدة", color = Accent) }
        }
        AndroidView(
            factory = { ctx -> GeckoView(ctx).apply { setSession(session) } },
            modifier = Modifier.fillMaxWidth().height(520.dp),
        )
    }
}
