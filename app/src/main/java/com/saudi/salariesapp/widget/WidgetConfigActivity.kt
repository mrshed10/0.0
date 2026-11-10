package com.saudi.salariesapp.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.saudi.salariesapp.data.AppViewModel
import com.saudi.salariesapp.ui.screens.WidgetEditorScreen
import com.saudi.salariesapp.ui.theme.SalariesTheme

/** Opened by the launcher when the widget is added (or re-configured): colour & content editor. */
class WidgetConfigActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val resultIntent = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        // If the user backs out, the launcher must not add the widget.
        setResult(RESULT_CANCELED, resultIntent)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            val dark by viewModel.darkMode.collectAsStateWithLifecycle()
            SalariesTheme(darkOverride = dark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("تخصيص الودجت", fontWeight = FontWeight.Bold) }) },
                    bottomBar = {
                        Button(
                            onClick = {
                                lifecycleScope.launch {
                                    SalariesWidget.refreshAll(this@WidgetConfigActivity)
                                    setResult(RESULT_OK, resultIntent)
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        ) { Text("إضافة الودجت") }
                    },
                ) { padding ->
                    WidgetEditorScreen(viewModel, padding)
                }
            }
        }
    }
}
