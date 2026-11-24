package app.safar.browser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.data.HistoryLogic
import app.safar.browser.engine.UrlUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** السجل: قائمة زمنية مجمعة حسب اليوم، مع البحث والحذف. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: BrowserViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onClearData: () -> Unit) {
    val all by vm.history.collectAsStateWithLifecycle()
    var q by remember { mutableStateOf("") }
    val list = HistoryLogic.search(all, q)
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM yyyy", Locale("ar")) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("السجل") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
            actions = { IconButton(onClick = onClearData) { Icon(Icons.Outlined.Delete, "حذف بيانات التصفح") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("البحث في السجل") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("لا يوجد سجل") }
            } else LazyColumn {
                var lastDay = ""
                list.forEach { e ->
                    val day = dayFmt.format(Date(e.time))
                    if (day != lastDay) {
                        lastDay = day
                        item(key = "d-$day-${e.time}") {
                            Text(day, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
                        }
                    }
                    item(key = "h-${e.url}-${e.time}") {
                        ListItem(
                            headlineContent = { Text(e.title.ifBlank { e.url }, maxLines = 1) },
                            supportingContent = { Text("${timeFmt.format(Date(e.time))} • ${UrlUtils.display(e.url)}", maxLines = 1) },
                            trailingContent = { IconButton(onClick = { vm.removeHistory(e) }) { Icon(Icons.Filled.Close, "حذف") } },
                            modifier = Modifier.clickable { onOpen(e.url) },
                        )
                    }
                }
            }
        }
    }
}
