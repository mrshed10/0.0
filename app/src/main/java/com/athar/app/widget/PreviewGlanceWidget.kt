package com.athar.app.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent

/**
 * Renders a design with exactly the same Glance code as the home-screen
 * widget, so the in-app preview is the widget itself (not a look-alike).
 */
private class PreviewGlanceWidget(private val config: WidgetConfig, private val data: WidgetData) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetRoot(-1, config.style, data) }
    }
}

object WidgetPreviewRenderer {
    /** RemoteViews for [config] at [size], or null if this device cannot render it in-app. */
    suspend fun render(context: Context, config: WidgetConfig, data: WidgetData, size: DpSize): RemoteViews? =
        runCatching { PreviewGlanceWidget(config, data).compose(context, size = size) }.getOrNull()
}
