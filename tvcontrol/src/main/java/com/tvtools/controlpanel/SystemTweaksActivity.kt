package com.tvtools.controlpanel

import android.app.Activity
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * تعديل إعدادات النظام مباشرة عبر Settings.Global — بدون الحاجة لواجهة خيارات المطور.
 *
 * القراءة متاحة دائمًا. الكتابة تحتاج WRITE_SECURE_SETTINGS؛ إن لم تكن ممنوحة
 * تُعرض بطاقة تحذير فيها أمر ADB الجاهز، وتُعطَّل عناصر التحكم.
 */
@Suppress("DEPRECATION") // android.widget.Switch مناسب هنا: لا اعتماديات AndroidX في التطبيق.
class SystemTweaksActivity : Activity() {

    private class ScaleRow(val key: String, val title: Int, val view: View)

    private lateinit var permissionCard: View
    private lateinit var permissionTitle: TextView
    private lateinit var grantCommand: TextView
    private lateinit var switchDev: Switch
    private lateinit var switchAdb: Switch
    private lateinit var switchWifiAdb: Switch
    private val scaleRows = mutableListOf<ScaleRow>()
    private val scaleValues = listOf(0f, 0.5f, 1f)

    /** لمنع المستمعين من الكتابة أثناء تحديث الواجهة برمجيًا. */
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tweaks)

        permissionCard = findViewById(R.id.permission_card)
        permissionTitle = findViewById(R.id.permission_title)
        grantCommand = findViewById(R.id.grant_command)
        switchDev = findViewById(R.id.switch_dev)
        switchAdb = findViewById(R.id.switch_adb)
        switchWifiAdb = findViewById(R.id.switch_wifi_adb)

        grantCommand.text = AdbHelper.grantCommand(this)
        findViewById<Button>(R.id.btn_recheck).setOnClickListener {
            refresh()
            val res = if (AdbHelper.canWriteSecureSettings(this)) R.string.perm_granted_toast else R.string.perm_missing_toast
            Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
        }

        bindSwitch(switchDev) { AdbHelper.setDeveloperOptions(this, it) }
        bindSwitch(switchAdb) { AdbHelper.setUsbDebugging(this, it) }
        if (AdbHelper.supportsWirelessDebugging) {
            bindSwitch(switchWifiAdb) { AdbHelper.setWirelessDebugging(this, it) }
        } else {
            switchWifiAdb.visibility = View.GONE
        }

        buildAnimationRows()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun bindSwitch(switch: Switch, write: (Boolean) -> Boolean) {
        switch.setOnCheckedChangeListener { button: CompoundButton, checked: Boolean ->
            if (binding) return@setOnCheckedChangeListener
            if (!write(checked)) {
                Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_LONG).show()
            }
            // نعيد القراءة من النظام لنعرض القيمة الحقيقية (قد ترفضها الشركة المصنّعة).
            refresh()
            button.requestFocus()
        }
    }

    private fun buildAnimationRows() {
        val container = findViewById<LinearLayout>(R.id.animation_rows)
        val inflater = LayoutInflater.from(this)

        val keys = listOf(
            Settings.Global.WINDOW_ANIMATION_SCALE to R.string.anim_window,
            Settings.Global.TRANSITION_ANIMATION_SCALE to R.string.anim_transition,
            Settings.Global.ANIMATOR_DURATION_SCALE to R.string.anim_animator,
        )
        // صف "الكل" أولًا: أسرع طريقة لتسريع الشاشة بضغطة واحدة.
        val allRow = inflateScaleRow(inflater, container, R.string.anim_all) { value ->
            AdbHelper.animationScaleKeys.all { AdbHelper.setAnimationScale(this, it, value) }
        }
        scaleRows += ScaleRow(key = "", title = R.string.anim_all, view = allRow)

        for ((key, title) in keys) {
            val row = inflateScaleRow(inflater, container, title) { value ->
                AdbHelper.setAnimationScale(this, key, value)
            }
            scaleRows += ScaleRow(key, title, row)
        }
    }

    private fun inflateScaleRow(
        inflater: LayoutInflater,
        container: LinearLayout,
        title: Int,
        write: (Float) -> Boolean,
    ): View {
        val row = inflater.inflate(R.layout.item_scale_row, container, false)
        row.findViewById<TextView>(R.id.title).setText(title)
        val buttons = row.findViewById<LinearLayout>(R.id.buttons)
        for (value in scaleValues) {
            val button = inflater.inflate(R.layout.item_chip, buttons, false) as Button
            button.text = formatScale(value)
            button.tag = value
            button.setOnClickListener {
                if (!write(value)) {
                    Toast.makeText(this, R.string.toggle_failed, Toast.LENGTH_LONG).show()
                }
                refresh()
            }
            buttons.addView(button)
        }
        container.addView(row)
        return row
    }

    private fun formatScale(value: Float): String = when (value) {
        0f -> getString(R.string.anim_off)
        else -> "${value}x"
    }

    private fun refresh() {
        binding = true
        val canWrite = AdbHelper.canWriteSecureSettings(this)

        // بطاقة الصلاحية: تحذير بالأمر المطلوب، أو تأكيد أن التحكم المباشر متاح.
        grantCommand.visibility = if (canWrite) View.GONE else View.VISIBLE
        findViewById<View>(R.id.grant_help).visibility = if (canWrite) View.GONE else View.VISIBLE
        permissionTitle.setText(if (canWrite) R.string.perm_granted_title else R.string.perm_missing_title)
        permissionCard.setBackgroundResource(if (canWrite) R.drawable.card_ok else R.drawable.card_warning)

        switchDev.isChecked = AdbHelper.isDeveloperOptionsEnabled(this)
        switchAdb.isChecked = AdbHelper.isUsbDebuggingEnabled(this)
        switchWifiAdb.isChecked = AdbHelper.isWirelessDebuggingEnabled(this)
        for (s in listOf(switchDev, switchAdb, switchWifiAdb)) s.isEnabled = canWrite

        for (row in scaleRows) {
            val current: Float? = if (row.key.isEmpty()) {
                // "الكل": نعرض القيمة فقط إذا كانت الثلاثة متساوية.
                AdbHelper.animationScaleKeys.map { AdbHelper.getAnimationScale(this, it) }.distinct().singleOrNull()
            } else {
                AdbHelper.getAnimationScale(this, row.key)
            }
            row.view.findViewById<TextView>(R.id.value).text =
                current?.let { getString(R.string.anim_current, formatScale(it)) }
                    ?: getString(R.string.anim_mixed)
            val buttons = row.view.findViewById<LinearLayout>(R.id.buttons)
            for (i in 0 until buttons.childCount) {
                val b = buttons.getChildAt(i)
                b.isSelected = current != null && b.tag == current
                b.isEnabled = canWrite
            }
        }
        binding = false
    }
}
