package app.safar.browser.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val PrivateScheme = darkColorScheme(
    primary = Color(0xFFCDB4FF),
    surface = Color(0xFF221A33),
    surfaceVariant = Color(0xFF342950),
    background = Color(0xFF1A1427),
    onSurface = Color(0xFFEDE6FF),
    onSurfaceVariant = Color(0xFFD6CCF0),
)

/** ثيم التطبيق: ألوان ديناميكية (Android 12+)، وضع داكن، وثيم بنفسجي للتصفح المتخفي. واجهة RTL. */
@Composable
fun SafarTheme(privateMode: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        privateMode -> PrivateScheme
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme(primary = Color(0xFF90CAF9))
        else -> lightColorScheme(primary = Color(0xFF1565C0))
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
