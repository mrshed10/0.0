package app.safar.browser.ui.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.browser.data.ToolbarItem
import app.safar.browser.extensions.ExtensionInfo

@Composable
fun BrowserToolbar(
    items: List<ToolbarItem>,
    canGoBack: Boolean,
    canGoForward: Boolean,
    tabCount: Int,
    translateOn: Boolean,
    pinned: List<ExtensionInfo>,
    onAction: (ToolbarItem) -> Unit,
    onExtensionClick: (String) -> Unit,
    menu: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (item) {
                    ToolbarItem.BACK -> Btn(item, enabled = canGoBack, onAction) { Icon(Icons.AutoMirrored.Filled.ArrowBack, item.labelAr) }
                    ToolbarItem.FORWARD -> Btn(item, enabled = canGoForward, onAction) { Icon(Icons.AutoMirrored.Filled.ArrowForward, item.labelAr) }
                    ToolbarItem.HOME -> Btn(item, true, onAction) { Icon(Icons.Filled.Home, item.labelAr) }
                    ToolbarItem.RELOAD -> Btn(item, true, onAction) { Icon(Icons.Filled.Refresh, item.labelAr) }
                    ToolbarItem.TABS_COUNTER -> Btn(item, true, onAction) { TabCounter(tabCount) }
                    ToolbarItem.VERTICAL_TABS -> Btn(item, true, onAction) { Icon(Icons.Outlined.ViewSidebar, item.labelAr) }
                    ToolbarItem.INCOGNITO -> Btn(item, true, onAction) { Icon(Icons.Outlined.VisibilityOff, item.labelAr) }
                    ToolbarItem.EXTENSIONS -> Btn(item, true, onAction) { Icon(Icons.Filled.Extension, item.labelAr) }
                    ToolbarItem.LIVE_TRANSLATE -> Btn(item, true, onAction) {
                        Icon(
                            Icons.Filled.Translate, item.labelAr,
                            tint = if (translateOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ToolbarItem.BOOKMARKS -> Btn(item, true, onAction) { Icon(Icons.Outlined.BookmarkBorder, item.labelAr) }
                    ToolbarItem.SHARE -> Btn(item, true, onAction) { Icon(Icons.Filled.Share, item.labelAr) }
                    ToolbarItem.MENU -> Box {
                        Btn(item, true, onAction) { Icon(Icons.Filled.MoreVert, item.labelAr) }
                        menu()
                    }
                }
            }
        }
        // الإضافات المثبّتة في الشريط
        pinned.forEach { ext ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                IconButton(onClick = { onExtensionClick(ext.id) }) {
                    val icon = ext.icon
                    if (icon != null) Image(icon.asImageBitmap(), ext.name, Modifier.size(24.dp))
                    else Icon(Icons.Filled.Extension, ext.name)
                }
            }
        }
    }
}

@Composable
private fun Btn(item: ToolbarItem, enabled: Boolean, onAction: (ToolbarItem) -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = { onAction(item) },
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
    ) { content() }
}

@Composable
fun TabCounter(count: Int) {
    Box(
        Modifier.size(24.dp).border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(if (count > 99) ":D" else count.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
