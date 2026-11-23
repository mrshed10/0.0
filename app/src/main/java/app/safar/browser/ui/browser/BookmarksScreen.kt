package app.safar.browser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(vm: BrowserViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by vm.bookmarks.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("الإشارات المرجعية") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("لا توجد إشارات مرجعية بعد") }
        } else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.url }) { b ->
                ListItem(
                    headlineContent = { Text(b.title.ifBlank { b.url }, maxLines = 1) },
                    supportingContent = { Text(b.url, maxLines = 1) },
                    trailingContent = { IconButton(onClick = { vm.removeBookmark(b.url) }) { Icon(Icons.Filled.Delete, "حذف") } },
                    modifier = Modifier.clickable { onOpen(b.url) },
                )
            }
        }
    }
}
