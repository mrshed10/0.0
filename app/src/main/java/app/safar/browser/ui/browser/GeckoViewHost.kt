package app.safar.browser.ui.browser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.safar.browser.engine.BrowserTab
import org.mozilla.geckoview.GeckoView

/** GeckoView واحد يُعاد استخدامه؛ عند تبديل التبويب نبدّل الجلسة المرتبطة فقط (أخف وأسرع). */
@Composable
fun GeckoViewHost(tab: BrowserTab?, onViewReady: (GeckoView) -> Unit, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx -> GeckoView(ctx).also(onViewReady) },
        update = { view ->
            val s = tab?.session
            if (s != null && view.session !== s && s.isOpen) {
                view.releaseSession()
                view.setSession(s)
            }
        },
    )
}
