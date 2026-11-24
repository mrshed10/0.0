package app.safar.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.safar.browser.data.Bookmark
import app.safar.browser.data.HistoryEntry
import app.safar.browser.data.HistoryLogic
import app.safar.browser.data.NEW_TAB_BACKGROUNDS
import app.safar.browser.data.NewTabSettings
import app.safar.browser.engine.UrlUtils

/** صفحة علامة التبويب الجديدة (أصلية، بلا تتبع): اختصارات، إشارات مرجعية، وآخر الزيارات — قابلة للتخصيص. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTabPage(
    cfg: NewTabSettings,
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    onOpen: (String) -> Unit,
    onCustomize: () -> Unit,
) {
    val bgColor = NEW_TAB_BACKGROUNDS.getOrNull(cfg.background)?.takeIf { it != 0 }?.let { Color(it) }
    val onBg = if (bgColor != null) Color.White else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.fillMaxSize()
            .background(bgColor ?: MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("سفر", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = onBg, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        if (cfg.showShortcuts) {
            val tiles = (cfg.shortcuts.map { it.title to it.url } +
                HistoryLogic.topSites(history, 8).map { (it.title.ifBlank { UrlUtils.display(it.url) }) to it.url })
                .distinctBy { UrlUtils.host(it.second) }.take(8)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.forEach { (title, url) -> Tile(title, url, onBg) { onOpen(url) } }
            }
            Spacer(Modifier.height(20.dp))
        }
        if (cfg.showBookmarks && bookmarks.isNotEmpty()) {
            Section("الإشارات المرجعية", onBg)
            bookmarks.take(6).forEach { b -> Line(b.title.ifBlank { b.url }, b.url, onBg) { onOpen(b.url) } }
            Spacer(Modifier.height(12.dp))
        }
        if (cfg.showRecent && history.isNotEmpty()) {
            Section("آخر الزيارات", onBg)
            history.take(6).forEach { h -> Line(h.title.ifBlank { h.url }, h.url, onBg) { onOpen(h.url) } }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCustomize, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Outlined.Edit, null, tint = onBg); Spacer(Modifier.width(6.dp)); Text("تخصيص", color = onBg)
        }
    }
}

@Composable
private fun Tile(title: String, url: String, color: Color, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text((UrlUtils.host(url) ?: title).take(1).uppercase(), fontWeight = FontWeight.Bold, fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = color, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Section(t: String, color: Color) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.padding(vertical = 6.dp))

@Composable
private fun Line(title: String, url: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = color)
            Text(UrlUtils.display(url), maxLines = 1, fontSize = 12.sp, color = color.copy(alpha = .7f))
        }
    }
}
