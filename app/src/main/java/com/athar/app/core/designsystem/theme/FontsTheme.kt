package com.athar.app.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.athar.app.AtharApplication
import com.athar.app.core.fonts.FontRegistry

/** [AtharTheme] with the fonts the user picked in Settings › Fonts. */
@Composable
fun AtharFontsTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = (context.applicationContext as AtharApplication).container.settings
    val fonts by settings.fonts.collectAsState()
    val ui = remember(fonts.uiId, fonts.latinId) { FontRegistry.composeFamily(context, fonts.uiId, fonts.latinId) }
    val decorative = remember(fonts.decorativeId) { FontRegistry.composeFamily(context, fonts.decorativeId) }
    AtharTheme(fontFamily = ui, decorativeFamily = decorative, content = content)
}
