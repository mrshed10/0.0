package com.tvtools.controlpanel

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * الشاشة الرئيسية: ست بطاقات كبيرة يُتنقّل بينها بأسهم الريموت (D-pad) وتُفتح بزر OK.
 */
class MainActivity : Activity() {

    private class Tile(
        val icon: Int,
        val title: Int,
        val subtitle: Int,
        val onClick: () -> Unit,
    )

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusView = findViewById(R.id.status)

        val tiles = listOf(
            Tile(R.drawable.ic_search, R.string.tile_explorer, R.string.tile_explorer_sub) {
                startActivity(Intent(this, SystemActivityExplorerActivity::class.java))
            },
            Tile(R.drawable.ic_tune, R.string.tile_tweaks, R.string.tile_tweaks_sub) {
                startActivity(Intent(this, SystemTweaksActivity::class.java))
            },
            Tile(R.drawable.ic_apps, R.string.tile_activities, R.string.tile_activities_sub) {
                startActivity(Intent(this, ActivityBrowserActivity::class.java))
            },
            Tile(R.drawable.ic_settings, R.string.tile_settings, R.string.tile_settings_sub) {
                SystemIntents.launchFirst(this, SystemIntents.androidSettings())
            },
            Tile(R.drawable.ic_code, R.string.tile_developer, R.string.tile_developer_sub) {
                openDeveloperOptions()
            },
            Tile(R.drawable.ic_usb, R.string.tile_usb, R.string.tile_usb_sub) {
                showUsbDebuggingDialog()
            },
            Tile(R.drawable.ic_info, R.string.tile_about, R.string.tile_about_sub) {
                SystemIntents.launchFirst(this, SystemIntents.deviceInfo())
            },
            Tile(R.drawable.ic_bolt, R.string.tile_shortcuts, R.string.tile_shortcuts_sub) {
                startActivity(Intent(this, ShortcutsActivity::class.java))
            },
        )

        val rows = listOf<LinearLayout>(findViewById(R.id.row1), findViewById(R.id.row2))
        val perRow = (tiles.size + rows.size - 1) / rows.size
        tiles.forEachIndexed { index, tile ->
            val row = rows[index / perRow]
            val view = layoutInflater.inflate(R.layout.item_tile, row, false)
            view.findViewById<ImageView>(R.id.icon).setImageResource(tile.icon)
            view.findViewById<TextView>(R.id.title).setText(tile.title)
            view.findViewById<TextView>(R.id.subtitle).setText(tile.subtitle)
            view.setOnClickListener { tile.onClick() }
            view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.07f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(150).start()
                v.elevation = if (hasFocus) 12f else 0f
            }
            row.addView(view)
        }
        rows[0].getChildAt(0)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val on = getString(R.string.state_on)
        val off = getString(R.string.state_off)
        fun s(b: Boolean) = if (b) on else off
        statusView.text = getString(
            R.string.status_line,
            s(AdbHelper.isDeveloperOptionsEnabled(this)),
            s(AdbHelper.isUsbDebuggingEnabled(this)),
            s(AdbHelper.canWriteSecureSettings(this)),
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            "${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    private fun openDeveloperOptions() {
        if (AdbHelper.isDeveloperOptionsEnabled(this)) {
            launchDeveloperOptionsOrExplore()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dev_disabled_title)
            .setMessage(R.string.dev_disabled_message)
            .setPositiveButton(R.string.open_about) { _, _ ->
                SystemIntents.launchFirst(this, SystemIntents.deviceInfo())
            }
            .setNeutralButton(R.string.try_anyway) { _, _ ->
                launchDeveloperOptionsOrExplore()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * إذا حذفت الشركة ACTION_APPLICATION_DEVELOPMENT_SETTINGS وكل بدائله،
     * نفتح مستكشف الأنشطة مصفّى على "dev" ليجد المستخدم الشاشة المخفية بنفسه.
     */
    private fun launchDeveloperOptionsOrExplore() = launchOrExplore(SystemIntents.developerOptions())

    private fun launchOrExplore(candidates: List<Intent>) {
        if (SystemIntents.launchFirst(this, candidates, showError = false)) return
        Toast.makeText(this, R.string.dev_missing_explore, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, SystemActivityExplorerActivity::class.java)
                .putExtra(SystemActivityExplorerActivity.EXTRA_QUERY, "dev"),
        )
    }

    /**
     * إذا كانت صلاحية WRITE_SECURE_SETTINGS ممنوحة: تبديل فوري للتصحيح.
     * وإلا: فتح خيارات المطور عند خيار تصحيح USB مع شرح طريقة منح الصلاحية.
     */
    private fun showUsbDebuggingDialog() {
        val canWrite = AdbHelper.canWriteSecureSettings(this)
        val usbOn = AdbHelper.isUsbDebuggingEnabled(this)
        val wifiOn = AdbHelper.isWirelessDebuggingEnabled(this)

        val labels = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (canWrite) {
            labels += getString(if (usbOn) R.string.usb_turn_off else R.string.usb_turn_on)
            actions.add { toggleResult(AdbHelper.setUsbDebugging(this, !usbOn)) }
            if (AdbHelper.supportsWirelessDebugging) {
                labels += getString(if (wifiOn) R.string.wifi_adb_turn_off else R.string.wifi_adb_turn_on)
                actions.add { toggleResult(AdbHelper.setWirelessDebugging(this, !wifiOn)) }
            }
        }
        labels += getString(R.string.usb_open_settings)
        actions.add { launchOrExplore(SystemIntents.usbDebugging()) }
        labels += getString(R.string.tile_tweaks)
        actions.add { startActivity(Intent(this, SystemTweaksActivity::class.java)) }
        if (!canWrite) {
            labels += getString(R.string.usb_how_to_grant)
            actions.add { showGrantHelp() }
        }

        val title = getString(
            R.string.usb_dialog_title,
            getString(if (usbOn) R.string.state_on else R.string.state_off),
        )
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleResult(success: Boolean) {
        Toast.makeText(
            this,
            if (success) R.string.toggle_done else R.string.toggle_failed,
            Toast.LENGTH_SHORT,
        ).show()
        updateStatus()
    }

    private fun showGrantHelp() {
        AlertDialog.Builder(this)
            .setTitle(R.string.usb_how_to_grant)
            .setMessage(getString(R.string.grant_help_message, AdbHelper.grantCommand(this)))
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}
