package app.safar.browser.ui.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.safar.browser.BrowserViewModel
import app.safar.browser.engine.UrlUtils

/** قائمة القراءة: غير المقروء بخط عريض، والمقروء عادي. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingListScreen(vm: BrowserViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val list by vm.readingList.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(title = { Text("قائمة القراءة") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
        })
    }) { pad ->
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("اضغط مطوّلاً على أي رابط واختر «الإضافة إلى قائمة القراءة»")
            }
        } else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { it.url }) { r ->
                ListItem(
                    headlineContent = { Text(r.title, maxLines = 2, fontWeight = if (r.read) FontWeight.Normal else FontWeight.Bold) },
                    supportingContent = { Text(UrlUtils.display(r.url) + if (r.read) " • مقروءة" else "", maxLines = 1) },
                    trailingContent = { IconButton(onClick = { vm.removeFromReadingList(r.url) }) { Icon(Icons.Filled.Close, "حذف") } },
                    modifier = Modifier.clickable { vm.markRead(r.url); onOpen(r.url) },
                )
            }
        }
    }
}
