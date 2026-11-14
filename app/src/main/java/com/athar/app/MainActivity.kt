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
        setContent {
            AtharTheme {
                AtharApp()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Debts lock again once the app leaves the screen.
        (application as AtharApplication).container.debtsSession.lock()
    }
}
