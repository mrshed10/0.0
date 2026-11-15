package com.tvremote.keyboard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var openSettingsButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        openSettingsButton = findViewById(R.id.openSettingsButton)

        openSettingsButton.setOnClickListener { openAccessibilitySettings() }
        openSettingsButton.requestFocus() // التركيز على الزر لاستخدامه مباشرة بالأسهم و Enter
    }

    override fun onResume() {
        super.onResume()
        // نحدّث الحالة كلما عاد المستخدم من الإعدادات
        updateStatus()
    }

    private fun updateStatus() {
        if (isServiceEnabled()) {
            statusText.setText(R.string.status_enabled)
            statusText.setBackgroundResource(R.drawable.status_enabled_bg)
        } else {
            statusText.setText(R.string.status_disabled)
            statusText.setBackgroundResource(R.drawable.status_disabled_bg)
        }
    }

    /** يتحقق من قائمة خدمات إمكانية الوصول المفعّلة في النظام */
    private fun isServiceEnabled(): Boolean {
        val expected = ComponentName(this, KeyboardRemoteService::class.java)
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabled.split(':').any { entry ->
            ComponentName.unflattenFromString(entry) == expected
        }
    }

    private fun openAccessibilitySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS) // بديل لبعض أجهزة TV Box التي تخفي شاشة إمكانية الوصول
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                if (intent.action == Settings.ACTION_SETTINGS) {
                    Toast.makeText(this, R.string.settings_fallback, Toast.LENGTH_LONG).show()
                }
                return
            } catch (e: ActivityNotFoundException) {
                // جرّب الخيار التالي
            }
        }
        Toast.makeText(this, R.string.settings_not_found, Toast.LENGTH_LONG).show()
    }
}
