package com.tvremote.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var openSettingsButton: Button
    private lateinit var addMappingButton: Button
    private lateinit var mappingsContainer: LinearLayout
    private lateinit var emptyText: TextView
    private lateinit var guardStatusText: TextView
    private lateinit var guardHelpText: TextView
    private lateinit var guardCommandText: TextView
    private lateinit var guardToggleButton: Button
    private lateinit var overlayStatusText: TextView
    private lateinit var overlayHelpText: TextView
    private lateinit var overlayCommandText: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var overlayIconButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        openSettingsButton = findViewById(R.id.openSettingsButton)
        addMappingButton = findViewById(R.id.addMappingButton)
        mappingsContainer = findViewById(R.id.mappingsContainer)
        emptyText = findViewById(R.id.emptyText)
        guardStatusText = findViewById(R.id.guardStatusText)
        guardHelpText = findViewById(R.id.guardHelpText)
        guardCommandText = findViewById(R.id.guardCommandText)
        guardToggleButton = findViewById(R.id.guardToggleButton)
        overlayStatusText = findViewById(R.id.overlayStatusText)
        overlayHelpText = findViewById(R.id.overlayHelpText)
        overlayCommandText = findViewById(R.id.overlayCommandText)
        overlayPermissionButton = findViewById(R.id.overlayPermissionButton)
        overlayIconButton = findViewById(R.id.overlayIconButton)
        overlayPermissionButton.setOnClickListener { openOverlayPermission() }
        overlayIconButton.setOnClickListener {
            FloatingOverlay.setIconEnabled(this, !FloatingOverlay.isIconEnabled(this))
            KeyboardRemoteService.instance?.refreshOverlay()
            updateOverlay()
        }
        guardToggleButton.setOnClickListener {
            ServiceGuard.setGuardEnabled(this, !ServiceGuard.isGuardEnabled(this))
            updateGuard()
        }

        openSettingsButton.setOnClickListener { openAccessibilitySettings() }
        addMappingButton.setOnClickListener { showCaptureDialog() }
        addMappingButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        updateGuard()
        updateOverlay()
        KeyboardRemoteService.instance?.refreshOverlay() // بعد منح الصلاحية نرقّي النافذة فوراً
        renderMappings()
        ServiceGuard.schedulePeriodic(this)
        repairInBackground()
    }

    override fun onPause() {
        super.onPause()
        KeyboardRemoteService.captureMode = false // أمان: لا نترك الالتقاط مفعّلاً
    }

    // ───────────────────────── حالة الخدمة ─────────────────────────

    private fun updateStatus() {
        if (KeyboardRemoteService.isEnabled(this)) {
            statusText.setText(R.string.status_enabled)
            statusText.setBackgroundResource(R.drawable.status_enabled_bg)
        } else {
            statusText.setText(R.string.status_disabled)
            statusText.setBackgroundResource(R.drawable.status_disabled_bg)
        }
    }

    // ───────────────────────── النافذة العائمة ─────────────────────────

    private fun updateOverlay() {
        val granted = FloatingOverlay.canDrawOverlays(this)
        overlayStatusText.setText(if (granted) R.string.overlay_on else R.string.overlay_no_permission)
        val help = if (granted) View.GONE else View.VISIBLE
        overlayHelpText.visibility = help
        overlayCommandText.visibility = help
        overlayPermissionButton.visibility = help
        overlayIconButton.setText(
            if (FloatingOverlay.isIconEnabled(this)) R.string.overlay_icon_hide
            else R.string.overlay_icon_show
        )
    }

    private fun openOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.overlay_settings_missing, Toast.LENGTH_LONG).show()
        }
    }

    // ───────────────────────── الحماية التلقائية ─────────────────────────

    private fun updateGuard() {
        val hasPermission = ServiceGuard.hasPermission(this)
        val on = ServiceGuard.isGuardEnabled(this)
        guardStatusText.setText(
            when {
                !hasPermission -> R.string.guard_no_permission
                on -> R.string.guard_on
                else -> R.string.guard_off
            }
        )
        val showHelp = if (hasPermission) View.GONE else View.VISIBLE
        guardHelpText.visibility = showHelp
        guardCommandText.visibility = showHelp
        guardToggleButton.visibility = if (hasPermission) View.VISIBLE else View.GONE
        guardToggleButton.setText(if (on) R.string.guard_turn_off else R.string.guard_turn_on)
    }

    /** إذا كانت الخدمة مُطفأة وعندنا الصلاحية: نعيد تفعيلها فور فتح التطبيق */
    private fun repairInBackground() {
        if (!ServiceGuard.canRepair(this)) return
        val app = applicationContext
        Thread {
            if (ServiceGuard.repairIfNeeded(app)) {
                runOnUiThread {
                    if (!isFinishing) {
                        Toast.makeText(app, R.string.guard_repaired, Toast.LENGTH_SHORT).show()
                        updateStatus()
                    }
                }
            }
        }.start()
    }

    private fun openAccessibilitySettings() {
        val intents = listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS) // بديل لأجهزة تخفي شاشة إمكانية الوصول
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

    // ───────────────────────── 1) التقاط الزر ─────────────────────────

    private fun showCaptureDialog() {
        val dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.add_mapping)
            .setMessage(R.string.capture_message)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            when {
                // زر الرجوع في الريموت يغلق النافذة
                keyCode == KeyEvent.KEYCODE_BACK -> false
                // الأسهم و Enter محجوزة للتنقّل في الواجهة
                keyCode in RESERVED_KEYS -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        Toast.makeText(this, R.string.key_reserved, Toast.LENGTH_SHORT).show()
                    }
                    false
                }
                event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 -> {
                    dialog.dismiss()
                    showActionChooser(keyCode)
                    true
                }
                else -> true // نستهلك ACTION_UP والتكرار
            }
        }
        // أثناء النافذة تمرّر الخدمة كل الأزرار حتى لو كانت مخصّصة مسبقاً
        dialog.setOnShowListener { KeyboardRemoteService.captureMode = true }
        dialog.setOnDismissListener { KeyboardRemoteService.captureMode = false }
        dialog.show()
    }

    // ───────────────────────── 2) اختيار الوظيفة ─────────────────────────

    private fun showActionChooser(keyCode: Int) {
        val actions = RemoteAction.values()
        val labels = actions.map { getString(it.labelRes) }.toTypedArray()
        val current = KeyMappingStore.get(this, keyCode)
        val checked = if (current == null) -1 else actions.indexOf(current)

        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(getString(R.string.choose_action_for, KeyMappingStore.keyLabel(keyCode)))
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                KeyMappingStore.put(this, keyCode, actions[which])
                dialog.dismiss()
                renderMappings()
                Toast.makeText(
                    this,
                    getString(
                        R.string.saved_mapping,
                        KeyMappingStore.keyLabel(keyCode),
                        labels[which]
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ───────────────────────── 3) جدول التخصيصات ─────────────────────────

    private fun renderMappings() {
        mappingsContainer.removeAllViews()
        val mappings = KeyMappingStore.getAll(this)
        emptyText.visibility = if (mappings.isEmpty()) View.VISIBLE else View.GONE

        for ((keyCode, action) in mappings) {
            mappingsContainer.addView(buildRow(keyCode, action))
        }
    }

    private fun buildRow(keyCode: Int, action: RemoteAction): View {
        val row = layoutInflater.inflate(R.layout.item_mapping, mappingsContainer, false)
        row.findViewById<TextView>(R.id.keyName).text = KeyMappingStore.keyLabel(keyCode)
        row.findViewById<TextView>(R.id.actionName).setText(action.labelRes)
        row.findViewById<Button>(R.id.editButton).setOnClickListener {
            showActionChooser(keyCode)
        }
        row.findViewById<Button>(R.id.deleteButton).setOnClickListener {
            confirmDelete(keyCode, action)
        }
        return row
    }

    private fun confirmDelete(keyCode: Int, action: RemoteAction) {
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.delete)
            .setMessage(
                getString(
                    R.string.confirm_delete,
                    KeyMappingStore.keyLabel(keyCode),
                    getString(action.labelRes)
                )
            )
            .setPositiveButton(R.string.delete) { _, _ ->
                KeyMappingStore.remove(this, keyCode)
                renderMappings()
                addMappingButton.requestFocus()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private val DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert

        private val RESERVED_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER
        )
    }
}
