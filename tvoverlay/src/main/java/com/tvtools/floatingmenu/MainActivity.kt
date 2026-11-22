package com.tvtools.floatingmenu

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.system.OverlayPermission
import com.tvtools.floatingmenu.ui.FocusEffects

/** شاشة الإعداد: شرح الصلاحيات ومنحها، تشغيل/إيقاف الخدمة، وإدارة الاختصارات. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var txtOverlayStatus: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var txtAdbHelp: TextView
    private lateinit var groupNotifications: View
    private lateinit var txtServiceStatus: TextView
    private lateinit var btnToggleService: Button
    private lateinit var switchBubble: Switch
    private lateinit var switchBoot: Switch

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        txtOverlayStatus = findViewById(R.id.txt_overlay_status)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        txtAdbHelp = findViewById(R.id.txt_adb_help)
        groupNotifications = findViewById(R.id.group_notifications)
        txtServiceStatus = findViewById(R.id.txt_service_status)
        btnToggleService = findViewById(R.id.btn_toggle_service)
        switchBubble = findViewById(R.id.switch_bubble)
        switchBoot = findViewById(R.id.switch_boot)

        txtAdbHelp.text = getString(R.string.perm_adb_help, OverlayPermission.adbCommand(this))

        btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        findViewById<Button>(R.id.btn_grant_notifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            }
        }

        btnToggleService.setOnClickListener {
            if (OverlayService.isRunning) {
                prefs.serviceEnabled = false
                OverlayService.stop(this)
            } else if (ensureOverlayPermission()) {
                prefs.serviceEnabled = true
                OverlayService.start(this)
            }
            refreshLater()
        }
        findViewById<Button>(R.id.btn_open_menu).setOnClickListener {
            if (ensureOverlayPermission()) {
                prefs.serviceEnabled = true
                OverlayService.start(this, OverlayService.ACTION_SHOW_MENU)
                refreshLater()
            }
        }
        findViewById<Button>(R.id.btn_manage_apps).setOnClickListener {
            startActivity(Intent(this, AppPickerActivity::class.java))
        }
        findViewById<Button>(R.id.btn_reset_position).setOnClickListener {
            prefs.resetBubblePosition()
            restartBubbleIfRunning()
        }

        switchBubble.isChecked = prefs.bubbleVisible
        switchBubble.setOnCheckedChangeListener { _, checked ->
            prefs.bubbleVisible = checked
            restartBubbleIfRunning()
        }
        switchBoot.isChecked = prefs.startOnBoot
        switchBoot.setOnCheckedChangeListener { _, checked -> prefs.startOnBoot = checked }

        FocusEffects.applyTree(findViewById(R.id.main_root))

        if (prefs.firstRun) {
            prefs.firstRun = false
            if (!OverlayPermission.isGranted(this)) showPermissionDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // بعد الرجوع من شاشة الصلاحية: شغّل الخدمة تلقائياً إذا كان المستخدم يريدها
        if (OverlayPermission.isGranted(this) && prefs.serviceEnabled && !OverlayService.isRunning) {
            OverlayService.start(this)
            refreshLater()
        }
        switchBubble.isChecked = prefs.bubbleVisible
        refreshUi()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshUi()
    }

    private fun refreshUi() {
        val granted = OverlayPermission.isGranted(this)
        txtOverlayStatus.setText(if (granted) R.string.perm_granted else R.string.perm_not_granted)
        txtOverlayStatus.setTextColor(getColor(if (granted) R.color.success else R.color.danger))
        btnGrantOverlay.isVisible = !granted
        txtAdbHelp.isVisible = !granted

        groupNotifications.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

        val running = OverlayService.isRunning
        txtServiceStatus.setText(if (running) R.string.service_running else R.string.service_stopped)
        txtServiceStatus.setTextColor(getColor(if (running) R.color.success else R.color.text_secondary))
        btnToggleService.setText(if (running) R.string.service_stop else R.string.service_start)
    }

    /** الخدمة تبدأ بشكل غير متزامن، لذا نحدّث الواجهة بعد لحظة. */
    private fun refreshLater() {
        handler.postDelayed({ refreshUi() }, 400)
    }

    private fun restartBubbleIfRunning() {
        if (OverlayService.isRunning) OverlayService.start(this, OverlayService.ACTION_REFRESH_BUBBLE)
    }

    private fun ensureOverlayPermission(): Boolean {
        if (OverlayPermission.isGranted(this)) return true
        Toast.makeText(this, R.string.perm_required_toast, Toast.LENGTH_SHORT).show()
        requestOverlayPermission()
        return false
    }

    private fun requestOverlayPermission() {
        if (!OverlayPermission.openSettings(this)) {
            Toast.makeText(this, R.string.perm_settings_missing, Toast.LENGTH_LONG).show()
            txtAdbHelp.isVisible = true
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.perm_dialog_title)
            .setMessage(R.string.perm_dialog_message)
            .setPositiveButton(R.string.perm_dialog_ok) { _, _ -> requestOverlayPermission() }
            .setNegativeButton(R.string.perm_dialog_later, null)
            .show()
    }

    private companion object {
        const val REQ_NOTIFICATIONS = 10
    }
}
