package app.safar.browser.ui.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddBox
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Workspaces
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** كل إجراءات القائمة الرئيسية (مستلهمة من قائمة Chrome). */
class MenuActions(
    val reload: () -> Unit,
    val savePage: () -> Unit,
    val toggleBookmark: () -> Unit,
    val back: () -> Unit,
    val forward: () -> Unit,
    val newTab: () -> Unit,
    val newPrivateTab: () -> Unit,
    val addToNewGroup: () -> Unit,
    val history: () -> Unit,
    val clearData: () -> Unit,
    val downloads: () -> Unit,
    val bookmarks: () -> Unit,
    val readingList: () -> Unit,
    val recentTabs: () -> Unit,
    val translate: () -> Unit,
    val toggleDesktop: () -> Unit,
    val readAloud: () -> Unit,
    val share: () -> Unit,
    val siteShield: () -> Unit,
    val zoom: (Int) -> Unit,
    val extensions: () -> Unit,
    val webStore: () -> Unit,
    val settings: () -> Unit,
    val customizeNewTab: () -> Unit,
)

@Composable
fun MainMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    canGoBack: Boolean,
    canGoForward: Boolean,
    bookmarked: Boolean,
    desktopMode: Boolean,
    showZoom: Boolean,
    zoom: Int,
    siteExcepted: Boolean,
    a: MenuActions,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        // شريط الأيقونات العلوي: إعادة التحميل، التنزيل (حفظ الصفحة)، النجمة، الرجوع، التقدم
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onDismiss(); a.reload() }) { Icon(Icons.Filled.Refresh, "إعادة التحميل") }
            IconButton(onClick = { onDismiss(); a.savePage() }) { Icon(Icons.Outlined.Download, "تنزيل الصفحة") }
            IconButton(onClick = { onDismiss(); a.toggleBookmark() }) {
                Icon(if (bookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder, "الإشارات المرجعية",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(enabled = canGoBack, onClick = { onDismiss(); a.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            IconButton(enabled = canGoForward, onClick = { onDismiss(); a.forward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "تقدّم") }
        }
        HorizontalDivider()
        Item(Icons.Outlined.AddBox, "علامة تبويب جديدة", onDismiss, a.newTab)
        Item(Icons.Outlined.VisibilityOff, "علامة تبويب للتصفح المتخفي", onDismiss, a.newPrivateTab)
        Item(Icons.Outlined.Workspaces, "الإضافة إلى مجموعة جديدة", onDismiss, a.addToNewGroup)
        HorizontalDivider()
        Item(Icons.Outlined.History, "السجل", onDismiss, a.history)
        Item(Icons.Outlined.Delete, "حذف بيانات التصفح", onDismiss, a.clearData)
        HorizontalDivider()
        Item(Icons.Outlined.DownloadDone, "عمليات التنزيل", onDismiss, a.downloads)
        Item(Icons.Outlined.Bookmarks, "الإشارات المرجعية", onDismiss, a.bookmarks)
        Item(Icons.Outlined.ChromeReaderMode, "قائمة القراءة", onDismiss, a.readingList)
        Item(Icons.Outlined.Devices, "علامات التبويب الأخيرة", onDismiss, a.recentTabs)
        HorizontalDivider()
        Item(Icons.Outlined.Share, "مشاركة", onDismiss, a.share)
        Item(Icons.Outlined.Translate, "ترجمة", onDismiss, a.translate)
        Item(Icons.AutoMirrored.Outlined.VolumeUp, "الاستماع إلى هذه الصفحة", onDismiss, a.readAloud)
        if (showZoom) {
            // التكبير/التصغير مباشرة من القائمة (يُحفظ لكل موقع)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("تكبير/تصغير", modifier = Modifier.weight(1f))
                IconButton(onClick = { a.zoom(zoom - 10) }) { Icon(Icons.Outlined.Remove, "تصغير") }
                Text("$zoom%", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { a.zoom(zoom + 10) }) { Icon(Icons.Outlined.Add, "تكبير") }
            }
        }
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Outlined.DesktopWindows, null) },
            text = { Text("موقع للكمبيوتر") },
            trailingIcon = { Checkbox(checked = desktopMode, onCheckedChange = { onDismiss(); a.toggleDesktop() }) },
            onClick = { onDismiss(); a.toggleDesktop() },
        )
        Item(Icons.Outlined.Shield, if (siteExcepted) "تفعيل الحماية لهذا الموقع" else "استثناء هذا الموقع من الحماية", onDismiss, a.siteShield)
        HorizontalDivider()
        Item(Icons.Outlined.Extension, "الإضافات", onDismiss, a.extensions)
        Item(Icons.Outlined.Storefront, "سوق Chrome الإلكتروني", onDismiss, a.webStore)
        Item(Icons.Outlined.Settings, "الإعدادات", onDismiss, a.settings)
        Item(Icons.Outlined.Edit, "تخصيص علامة تبويب جديدة", onDismiss, a.customizeNewTab)
    }
}

@Composable
private fun Item(icon: ImageVector, label: String, dismiss: () -> Unit, action: () -> Unit) =
    DropdownMenuItem(leadingIcon = { Icon(icon, null) }, text = { Text(label) }, onClick = { dismiss(); action() })
