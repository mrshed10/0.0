package com.tvremote.floating

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var overlayStatus: TextView
    private lateinit var overlayButton: Button
    private lateinit var a11yStatus: TextView
    private lateinit var a11yButton: Button
    private lateinit var toggleButton: Button
    private lateinit var sizeLabel: TextView
    private lateinit var sizeSeek: SeekBar
    private lateinit var alphaLabel: TextView
    private lateinit var alphaSeek: SeekBar
    private lateinit var actionsContainer: LinearLayout
    private lateinit var middleCheck: CheckBox
    private lateinit var middleTestButton: Button
    private lateinit var middleStatus: TextView
    private lateinit var guardStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.overlayStatus)
        overlayButton = findViewById(R.id.overlayButton)
        a11yStatus = findViewById(R.id.a11yStatus)
        a11yButton = findViewById(R.id.a11yButton)
        toggleButton = findViewById(R.id.toggleButton)
        sizeLabel = findViewById(R.id.sizeLabel)
        sizeSeek = findViewById(R.id.sizeSeek)
        alphaLabel = findViewById(R.id.alphaLabel)
        alphaSeek = findViewById(R.id.alphaSeek)
        actionsContainer = findViewById(R.id.actionsContainer)
        middleCheck = findViewById(R.id.middleCheck)
        middleTestButton = findViewById(R.id.middleTestButton)
        middleStatus = findViewById(R.id.middleStatus)
        guardStatus = findViewById(R.id.guardStatus)

        overlayButton.setOnClickListener { openOverlaySettings() }
        a11yButton.setOnClickListener { openAccessibilitySettings() }
        toggleButton.setOnClickListener { toggleFloating() }

        setupSeekBars()
        buildActionCheckboxes()

        middleCheck.isChecked = Prefs.isMiddleBackEnabled(this)
        middleCheck.setOnCheckedChangeListener { _, checked ->
            Prefs.setMiddleBackEnabled(this, checked)
            ControlAccessibilityService.instance?.configureMouseObserver()
        }
        middleTestButton.setOnClickListener { showMiddleTest() }

        // Android 13+: إذن الإشعارات حتى يظهر الإشعار الدائم
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        toggleButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        // إن كان المستخدم قد فعّل القائمة سابقاً نتأكد أنها تعمل
        if (Prefs.isFloatingEnabled(this) && FloatingService.canDrawOverlays(this) &&
            FloatingService.instance == null
        ) {
            FloatingService.start(this)
        }
        AccessibilityGuard.repairAsync(this)
    }

    // ───────────────────────── الحالة ─────────────────────────

    private fun refreshStatus() {
        val overlayOk = FloatingService.canDrawOverlays(this)
        overlayStatus.setText(if (overlayOk) R.string.overlay_ok else R.string.overlay_missing)
        overlayStatus.setBackgroundResource(if (overlayOk) R.drawable.status_ok_bg else R.drawable.status_bad_bg)

        val a11yOk = ControlAccessibilityService.isEnabled(this)
        a11yStatus.setText(if (a11yOk) R.string.a11y_ok else R.string.a11y_missing)
        a11yStatus.setBackgroundResource(if (a11yOk) R.drawable.status_ok_bg else R.drawable.status_bad_bg)

        val running = Prefs.isFloatingEnabled(this)
        toggleButton.setText(if (running) R.string.toggle_stop else R.string.toggle_start)
        toggleButton.setBackgroundResource(
            if (running) R.drawable.button_stop_bg else R.drawable.button_primary_bg
        )

        guardStatus.setText(
            if (AccessibilityGuard.hasPermission(this)) R.string.guard_ok else R.string.guard_missing
        )
        updateMiddleStatus()
    }

    private fun toggleFloating() {
        if (Prefs.isFloatingEnabled(this)) {
            FloatingService.stop(this)
        } else {
            if (!FloatingService.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.need_overlay, Toast.LENGTH_LONG).show()
                openOverlaySettings()
                return
            }
            FloatingService.start(this)
        }
        refreshStatus()
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, R.string.overlay_ok, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            showAdbDialog(R.string.overlay_adb_title, R.string.overlay_adb_command)
        }
    }

    private fun openAccessibilitySettings() {
        for (intent in listOf(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: ActivityNotFoundException) {
                // جرّب التالي
            }
        }
        showAdbDialog(R.string.a11y_adb_title, R.string.a11y_adb_command)
    }

    private fun showAdbDialog(titleRes: Int, commandRes: Int) {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(titleRes)
            .setMessage(getString(R.string.adb_hint) + "\n\n" + getString(commandRes))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    // ───────────────────────── الحجم والشفافية ─────────────────────────

    private fun setupSeekBars() {
        sizeSeek.max = Prefs.SIZE_MAX_DP - Prefs.SIZE_MIN_DP
        sizeSeek.progress = Prefs.bubbleSizeDp(this) - Prefs.SIZE_MIN_DP
        alphaSeek.max = Prefs.ALPHA_MAX - Prefs.ALPHA_MIN
        alphaSeek.progress = Prefs.bubbleAlpha(this) - Prefs.ALPHA_MIN
        updateSeekLabels()

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (seekBar === sizeSeek) {
                    Prefs.setBubbleSizeDp(this@MainActivity, progress + Prefs.SIZE_MIN_DP)
                } else {
                    Prefs.setBubbleAlpha(this@MainActivity, progress + Prefs.ALPHA_MIN)
                }
                updateSeekLabels()
                FloatingService.instance?.applyAppearance() // معاينة فورية على الأيقونة
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }
        sizeSeek.setOnSeekBarChangeListener(listener)
        alphaSeek.setOnSeekBarChangeListener(listener)
    }

    private fun updateSeekLabels() {
        sizeLabel.text = getString(R.string.size_label, Prefs.bubbleSizeDp(this))
        alphaLabel.text = getString(R.string.alpha_label, Prefs.bubbleAlpha(this))
    }

    // ───────────────────────── اختيار الأزرار ─────────────────────────

    private fun buildActionCheckboxes() {
        actionsContainer.removeAllViews()
        for (action in FloatAction.values()) {
            val box = layoutInflater.inflate(R.layout.item_action_check, actionsContainer, false) as CheckBox
            box.setText(action.labelRes)
            box.isChecked = Prefs.isActionVisible(this, action)
            box.setOnCheckedChangeListener { _, checked ->
                Prefs.setActionVisible(this, action, checked)
            }
            actionsContainer.addView(box)
        }
    }

    // ───────────────────────── اختبار الزر الأوسط ─────────────────────────

    private fun updateMiddleStatus() {
        val text = when {
            Prefs.middleKeyCode(this) != -1 -> getString(R.string.middle_status_key)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                getString(R.string.middle_status_android15)
            else -> getString(R.string.middle_status_floating_only)
        }
        middleStatus.text = text
    }

    private fun showMiddleTest() {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.middle_test_title)
            .setMessage(R.string.middle_test_message)
            .setNegativeButton(R.string.cancel, null)
            .create()

        fun done(messageRes: Int) {
            dialog.dismiss()
            updateMiddleStatus()
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(R.string.middle_test_title)
                .setMessage(messageRes)
                .setPositiveButton(R.string.ok, null)
                .show()
        }

        // الحالة 1: الجهاز يرسل الزر الأوسط كزر (KeyEvent)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN || !event.isFromSource(InputDevice.SOURCE_MOUSE)) {
                return@setOnKeyListener false
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                done(R.string.middle_result_native_back)
            } else {
                Prefs.setMiddleKeyCode(this, keyCode)
                done(R.string.middle_result_key)
            }
            true
        }

        dialog.setOnShowListener {
            // الحالة 2: الجهاز يرسله كنقرة ماوس عادية (BUTTON_TERTIARY)
            val decor = dialog.window?.decorView ?: return@setOnShowListener
            val onMotion: (MotionEvent) -> Boolean = { e ->
                if (e.buttonState and MotionEvent.BUTTON_TERTIARY != 0) {
                    done(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
                            R.string.middle_result_android15
                        else R.string.middle_result_floating_only
                    )
                    true
                } else false
            }
            decor.setOnGenericMotionListener { _, e -> onMotion(e) }
            decor.setOnTouchListener { _, e -> onMotion(e) }
        }
        dialog.show()
    }

    companion object {
        @Suppress("unused")
        private const val TAG = "MainActivity"
    }
}
