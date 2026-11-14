package com.athar.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.athar.app.core.designsystem.theme.AtharTheme
import com.athar.app.navigation.AtharApp

/** FragmentActivity (a ComponentActivity) so BiometricPrompt can attach. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val startTab = intent?.getStringExtra(EXTRA_TAB)
        setContent {
            AtharTheme {
                AtharApp(startTab = startTab)
            }
        }
    }

    companion object {
        /** Tab to open (from widgets): home, dates, salaries, tasks, habits, debts, prayer. */
        const val EXTRA_TAB = "tab"
    }

    override fun onStop() {
        super.onStop()
        // Debts lock again once the app leaves the screen.
        (application as AtharApplication).container.debtsSession.lock()
    }
}
