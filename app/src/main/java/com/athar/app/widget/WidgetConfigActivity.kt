package com.athar.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.athar.app.core.designsystem.glass.AuroraBackground
import com.athar.app.core.designsystem.glass.LocalHazeState
import com.athar.app.core.designsystem.theme.AtharFontsTheme
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * Opens when a widget is added (or reconfigured from the launcher / app):
 * choose its content and style with a live preview.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_CANCELED, result)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val store = WidgetStore(this)
        val manager = AppWidgetManager.getInstance(this)
        val wide = manager.getAppWidgetInfo(appWidgetId)?.provider?.className?.contains("Small") != true
        // Portrait size of the placed widget (dp), so the preview has the same proportions.
        val options = manager.getAppWidgetOptions(appWidgetId)
        val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val previewSize = if (w > 0 && h > 0) androidx.compose.ui.unit.DpSize(w.dp, h.dp) else null
        val initial = store.get(appWidgetId)
            ?: store.pending
            ?: WidgetProviders.defaultConfig(this, appWidgetId)
            ?: WidgetConfig(WidgetContent(WidgetType.NextPrayer))

        setContent {
            AtharFontsTheme {
                val haze = remember { HazeState() }
                CompositionLocalProvider(LocalHazeState provides haze) {
                    Box(Modifier.fillMaxSize()) {
                        AuroraBackground(haze)
                        WidgetEditorScreen(
                            initial = initial,
                            wide = wide,
                            previewSize = previewSize,
                            onCancel = { finish() },
                            onSave = { config ->
                                store.put(appWidgetId, config)
                                store.pending = null
                                lifecycleScope.launch {
                                    WidgetUpdater.refresh(this@WidgetConfigActivity, appWidgetId)
                                    setResult(RESULT_OK, result)
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
