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
    private lateinit var shortcutsSummary: TextView
    private lateinit var groupsContainer: LinearLayout
    private lateinit var splitStatus: TextView
    private lateinit var freeformStatus: TextView

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

        shortcutsSummary = findViewById(R.id.shortcutsSummary)
        groupsContainer = findViewById(R.id.groupsContainer)
        splitStatus = findViewById(R.id.splitStatus)
        freeformStatus = findViewById(R.id.freeformStatus)
        findViewById<Button>(R.id.shortcutsButton).setOnClickListener { chooseShortcuts() }
        findViewById<Button>(R.id.addGroupButton).setOnClickListener { createGroup() }
        findViewById<Button>(R.id.enableMultiWindowButton).setOnClickListener { enableMultiWindow() }

        middleCheck.isChecked = Prefs.isMiddleBackEnabled(this)
        middleCheck.setOnCheckedChangeListener { _, checked ->
            Prefs.setMiddleBackEnabled(this, checked)
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
        renderShortcuts()
        renderGroups()
        updateMultiWindowStatus()
    }

    // ───────────────────────── اختصارات التطبيقات ─────────────────────────

    private fun renderShortcuts() {
        val list = Prefs.shortcuts(this).filter { AppRepository.isInstalled(this, it) }
        shortcutsSummary.text = if (list.isEmpty()) getString(R.string.shortcuts_none)
        else list.joinToString("  •  ") { AppRepository.label(this, it) }
    }

    private fun chooseShortcuts() {
        val apps = AppRepository.launchableApps(this)
        if (apps.isEmpty()) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_LONG).show()
            return
        }
        val current = Prefs.shortcuts(this)
        val checked = BooleanArray(apps.size) { apps[it].packageName in current }

        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.shortcuts_choose)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on ->
                checked[i] = on
            }
            .setPositiveButton(R.string.save) { _, _ ->
                val chosen = apps.indices.filter { checked[it] }.map { apps[it].packageName }
                // نحافظ على الترتيب القديم ونضيف الجديد في النهاية
                val ordered = current.filter { it in chosen } + chosen.filter { it !in current }
                Prefs.setShortcuts(this, ordered)
                renderShortcuts()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ───────────────────────── مجموعات التطبيقات ─────────────────────────

    private fun renderGroups() {
        groupsContainer.removeAllViews()
        for (group in Prefs.groups(this)) {
            val row = layoutInflater.inflate(R.layout.item_group, groupsContainer, false)
            row.findViewById<TextView>(R.id.groupName).text = group.name
            row.findViewById<TextView>(R.id.groupDetails).text = getString(
                R.string.group_details,
                getString(group.mode.labelRes),
                group.packages.joinToString(" + ") { AppRepository.label(this, it) }
            )
            row.findViewById<Button>(R.id.groupRun).setOnClickListener {
                MultiWindow.launchGroup(this, group)
            }
            row.findViewById<Button>(R.id.groupEdit).setOnClickListener { createGroup(group) }
            row.findViewById<Button>(R.id.groupDelete).setOnClickListener {
                AlertDialog.Builder(this, DIALOG_THEME)
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.group_delete_confirm, group.name))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        Prefs.setGroups(this, Prefs.groups(this).filter { it.id != group.id })
                        renderGroups()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            groupsContainer.addView(row)
        }
    }

    /** الخطوة 1: اختيار التطبيقات (2 إلى 4) */
    private fun createGroup(existing: AppGroup? = null) {
        val apps = AppRepository.launchableApps(this)
        if (apps.size < 2) {
            Toast.makeText(this, R.string.no_apps, Toast.LENGTH_LONG).show()
            return
        }
        val selected = (existing?.packages ?: emptyList()).filter { pkg -> apps.any { it.packageName == pkg } }
            .toMutableList()
        val checked = BooleanArray(apps.size) { apps[it].packageName in selected }

        val dialog = AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.group_pick_apps)
            .setMultiChoiceItems(apps.map { it.label }.toTypedArray(), checked) { _, i, on ->
                checked[i] = on
                val pkg = apps[i].packageName
                if (on) selected += pkg else selected -= pkg // ترتيب الاختيار = ترتيب الفتح
            }
            .setPositiveButton(R.string.next, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selected.size !in 2..4) {
                    Toast.makeText(this, R.string.group_count_error, Toast.LENGTH_SHORT).show()
                } else {
                    dialog.dismiss()
                    chooseGroupMode(existing, selected.toList())
                }
            }
        }
        dialog.show()
    }

    /** الخطوة 2: طريقة الفتح */
    private fun chooseGroupMode(existing: AppGroup?, packages: List<String>) {
        val modes = LaunchMode.values()
        val labels = modes.map { mode ->
            val support = when (mode) {
                LaunchMode.SEQUENTIAL -> ""
                LaunchMode.SPLIT ->
                    if (MultiWindow.splitSupported(this)) "" else getString(R.string.mode_unsupported_suffix)
                LaunchMode.WINDOWS ->
                    if (MultiWindow.freeformSupported(this)) "" else getString(R.string.mode_unsupported_suffix)
            }
            getString(mode.labelRes) + support
        }.toTypedArray()
        var choice = modes.indexOf(existing?.mode ?: LaunchMode.SPLIT)

        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.group_pick_mode)
            .setSingleChoiceItems(labels, choice) { _, i -> choice = i }
            .setPositiveButton(R.string.next) { _, _ -> nameGroup(existing, packages, modes[choice]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** الخطوة 3: الاسم ثم الحفظ */
    private fun nameGroup(existing: AppGroup?, packages: List<String>, mode: LaunchMode) {
        val input = android.widget.EditText(this).apply {
            setText(existing?.name ?: packages.joinToString(" + ") { AppRepository.label(this@MainActivity, it) })
            setSelectAllOnFocus(true)
            setSingleLine()
        }
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(R.string.group_name)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { getString(R.string.group_default_name) }
                val group = AppGroup(
                    id = existing?.id ?: System.currentTimeMillis().toString(),
                    name = name,
                    packages = packages,
                    mode = mode
                )
                val list = Prefs.groups(this).toMutableList()
                val index = list.indexOfFirst { it.id == group.id }
                if (index >= 0) list[index] = group else list += group
                Prefs.setGroups(this, list)
                renderGroups()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ───────────────────────── تقسيم الشاشة ─────────────────────────

    private fun updateMultiWindowStatus() {
        splitStatus.setText(
            if (MultiWindow.splitSupported(this)) R.string.mw_split_yes else R.string.mw_split_no
        )
        freeformStatus.setText(
            if (MultiWindow.freeformSupported(this)) R.string.mw_free_yes else R.string.mw_free_no
        )
    }

    private fun enableMultiWindow() {
        if (MultiWindow.developerFlagsEnabled(this)) {
            showInfo(R.string.mw_title, getString(R.string.mw_already))
            return
        }
        if (AccessibilityGuard.hasPermission(this) && MultiWindow.enableDeveloperFlags(this)) {
            showInfo(R.string.mw_title, getString(R.string.mw_enabled_reboot))
        } else {
            showInfo(R.string.mw_title, getString(R.string.adb_hint) + "\n\n" + getString(R.string.mw_adb_command))
        }
        updateMultiWindowStatus()
    }

    private fun showInfo(titleRes: Int, message: String) {
        AlertDialog.Builder(this, DIALOG_THEME)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
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
                    done(R.string.middle_result_floating_only)
                    true
                } else false
            }
            decor.setOnGenericMotionListener { _, e -> onMotion(e) }
            decor.setOnTouchListener { _, e -> onMotion(e) }
        }
        dialog.show()
    }

    companion object {
        private val DIALOG_THEME = android.R.style.Theme_Material_Dialog_Alert
    }
}
