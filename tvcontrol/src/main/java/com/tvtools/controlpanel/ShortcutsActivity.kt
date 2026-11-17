package com.tvtools.controlpanel

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ListView
import android.widget.TextView
import com.tvtools.controlpanel.SystemIntents.action
import com.tvtools.controlpanel.SystemIntents.component

/** قائمة اختصارات جاهزة لشاشات النظام، بما فيها شاشات لا تظهر في قائمة إعدادات التلفاز. */
class ShortcutsActivity : Activity() {

    private class Shortcut(val title: Int, val intents: List<Intent>)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)

        val shortcuts = listOfNotNull(
            Shortcut(R.string.sc_android_settings, SystemIntents.androidSettings()),
            Shortcut(R.string.sc_tv_settings, SystemIntents.tvSettings()),
            Shortcut(R.string.tile_developer, SystemIntents.developerOptions()),
            Shortcut(R.string.tile_about, SystemIntents.deviceInfo()),
            Shortcut(
                R.string.sc_apps,
                action(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS) +
                    action(Settings.ACTION_APPLICATION_SETTINGS),
            ),
            SystemIntents.manageUnknownSources?.let { Shortcut(R.string.sc_unknown_sources, action(it)) },
            Shortcut(R.string.sc_home_app, action(Settings.ACTION_HOME_SETTINGS)),
            Shortcut(R.string.sc_wifi, action(Settings.ACTION_WIFI_SETTINGS)),
            Shortcut(R.string.sc_network, action(Settings.ACTION_WIRELESS_SETTINGS)),
            Shortcut(R.string.sc_bluetooth, action(Settings.ACTION_BLUETOOTH_SETTINGS)),
            Shortcut(R.string.sc_display, action(Settings.ACTION_DISPLAY_SETTINGS)),
            Shortcut(R.string.sc_sound, action(Settings.ACTION_SOUND_SETTINGS)),
            Shortcut(R.string.sc_language, action(Settings.ACTION_LOCALE_SETTINGS)),
            Shortcut(R.string.sc_keyboard, action(Settings.ACTION_INPUT_METHOD_SETTINGS)),
            Shortcut(R.string.sc_date, action(Settings.ACTION_DATE_SETTINGS)),
            Shortcut(R.string.sc_storage, action(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
            Shortcut(R.string.sc_accessibility, action(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
            Shortcut(R.string.sc_security, action(Settings.ACTION_SECURITY_SETTINGS)),
            Shortcut(
                R.string.sc_testing,
                listOf(
                    component("com.android.settings", "com.android.settings.Settings\$TestingSettingsActivity"),
                    component("com.android.phone", "com.android.phone.settings.RadioInfo"),
                ),
            ),
            Shortcut(
                R.string.sc_sysui_tuner,
                listOf(component("com.android.systemui", "com.android.systemui.tuner.TunerActivity")),
            ),
        )

        val settingsIcon = getDrawable(R.drawable.ic_settings)
        val rows = shortcuts.map { sc ->
            Row(
                title = getString(sc.title),
                subtitle = SystemIntents.describe(sc.intents.first()),
                icon = settingsIcon,
            ) { SystemIntents.launchFirst(this, sc.intents) }
        }

        findViewById<TextView>(R.id.header).setText(R.string.tile_shortcuts)
        val list = findViewById<ListView>(R.id.list)
        list.adapter = RowAdapter(this, rows)
        list.setOnItemClickListener { _, _, position, _ -> rows[position].onClick() }
        list.requestFocus()
    }
}
