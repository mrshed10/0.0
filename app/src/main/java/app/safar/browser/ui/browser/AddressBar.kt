package app.safar.browser.ui.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import app.safar.browser.engine.BrowserTab
import app.safar.browser.engine.UrlUtils

@Composable
fun AddressBar(
    tab: BrowserTab?,
    siteExcepted: Boolean,
    onSubmit: (String) -> Unit,
    onShieldClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var tfv by remember { mutableStateOf(TextFieldValue("")) }
    val url = tab?.url.orEmpty()

    LaunchedEffect(url, focused) {
        if (!focused) tfv = TextFieldValue(if (url == "about:blank") "" else UrlUtils.display(url))
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 4.dp, end = 4.dp)
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShieldClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = when {
                    tab?.isPrivate == true -> Icons.Outlined.VisibilityOff
                    siteExcepted -> Icons.Outlined.Info
                    tab?.isSecure == true -> Icons.Filled.Lock
                    else -> Icons.Outlined.Shield
                },
                contentDescription = "حماية الموقع",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(2.dp))
        BasicTextField(
            value = tfv,
            onValueChange = { tfv = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textDirection = TextDirection.Ltr,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = {
                if (tfv.text.isNotBlank()) onSubmit(tfv.text)
                focus.clearFocus()
            }),
            decorationBox = { inner ->
                if (tfv.text.isEmpty()) Text("ابحث أو اكتب عنواناً", color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { st ->
                    if (st.isFocused && !focused) {
                        val full = if (url == "about:blank") "" else url
                        tfv = TextFieldValue(full, TextRange(0, full.length)) // تحديد الكل للاستبدال السريع
                    }
                    focused = st.isFocused
                },
        )
        when {
            focused && tfv.text.isNotEmpty() ->
                IconButton(onClick = { tfv = TextFieldValue("") }) { Icon(Icons.Filled.Close, "مسح") }
            tab?.loading == true ->
                IconButton(onClick = { tab.session.stop() }) { Icon(Icons.Filled.Close, "إيقاف") }
            else ->
                IconButton(onClick = { tab?.session?.reload() }) { Icon(Icons.Filled.Refresh, "تحديث") }
        }
    }
}

@Composable
fun PageProgress(tab: BrowserTab?) {
    if (tab?.loading == true && tab.progress in 1..99) {
        LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
    } else Spacer(Modifier.height(2.dp))
}
