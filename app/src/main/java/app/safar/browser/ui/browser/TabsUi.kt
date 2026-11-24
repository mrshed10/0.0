package app.safar.browser.ui.browser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.safar.browser.engine.BrowserTab
import app.safar.browser.engine.TabGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.safar.browser.engine.UrlUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsSheet(
    tabs: List<BrowserTab>,
    groups: List<TabGroup> = emptyList(),
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: (private: Boolean) -> Unit,
    onCloseAllPrivate: () -> Unit,
    onDismiss: () -> Unit,
) {
    var groupFilter by remember { mutableStateOf<Long?>(null) }
    val shown = if (groupFilter == null) tabs else tabs.filter { it.groupId == groupFilter }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (groups.isNotEmpty()) {
            // مجموعات التبويبات
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = groupFilter == null, onClick = { groupFilter = null }, label = { Text("الكل (${tabs.size})") })
                groups.forEach { g ->
                    FilterChip(
                        selected = groupFilter == g.id, onClick = { groupFilter = g.id },
                        label = { Text("${g.name} (${tabs.count { it.groupId == g.id }})") },
                        leadingIcon = { Box(Modifier.size(10.dp).background(Color(g.color), CircleShape)) },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onNew(false); onDismiss() }, label = { Text("تبويب جديد") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            AssistChip(onClick = { onNew(true); onDismiss() }, label = { Text("تبويب متخفٍ") }, leadingIcon = { Icon(Icons.Outlined.VisibilityOff, null) })
            if (tabs.any { it.isPrivate }) AssistChip(onClick = onCloseAllPrivate, label = { Text("إغلاق المتخفية") })
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().height(460.dp).padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it.id }) { tab ->
                TabCard(tab, tab.id == selectedId, { onSelect(tab.id); onDismiss() }, { onClose(tab.id) }, groups.firstOrNull { it.id == tab.groupId })
            }
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, selected: Boolean, onClick: () -> Unit, onClose: () -> Unit, group: TabGroup? = null) {
    Card(
        onClick = onClick,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (tab.isPrivate) Color(0xFF342950) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier.height(110.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab.isPrivate) Icon(Icons.Outlined.VisibilityOff, null, Modifier.padding(end = 4.dp), tint = Color.White)
                Text(
                    tab.title.ifBlank { UrlUtils.display(tab.url) }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f),
                    color = if (tab.isPrivate) Color.White else Color.Unspecified,
                )
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "إغلاق") }
            }
            Spacer(Modifier.weight(1f))
            if (group != null) Text("● ${group.name}", color = Color(group.color), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Text(UrlUtils.display(tab.url), maxLines = 1, style = MaterialTheme.typography.bodySmall,
                color = if (tab.isPrivate) Color.White.copy(alpha = .7f) else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** لوح التبويبات العمودية الجانبي (مفيد مع عدد كبير من التبويبات والشاشات الكبيرة). */
@Composable
fun VerticalTabsPanel(
    visible: Boolean,
    tabs: List<BrowserTab>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (visible) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).clickable(onClick = onDismiss))
    AnimatedVisibility(visible, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
        Surface(Modifier.fillMaxHeight().width(300.dp), tonalElevation = 6.dp) {
            Column {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("التبويبات (${tabs.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = onNew) { Icon(Icons.Filled.Add, "جديد") }
                }
                LazyColumn {
                    items(tabs, key = { it.id }) { tab ->
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (tab.id == selectedId) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                .clickable { onSelect(tab.id); onDismiss() }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (tab.isPrivate) Icon(Icons.Outlined.VisibilityOff, null, Modifier.padding(end = 6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tab.title.ifBlank { UrlUtils.display(tab.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(UrlUtils.display(tab.url), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onClose(tab.id) }) { Icon(Icons.Filled.Close, "إغلاق") }
                        }
                    }
                }
            }
        }
    }
}
