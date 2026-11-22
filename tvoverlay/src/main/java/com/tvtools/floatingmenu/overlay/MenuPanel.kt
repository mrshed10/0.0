package com.tvtools.floatingmenu.overlay

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvtools.floatingmenu.AppPickerActivity
import com.tvtools.floatingmenu.R
import com.tvtools.floatingmenu.data.AppEntry
import com.tvtools.floatingmenu.data.AppRepository
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.system.NetKind
import com.tvtools.floatingmenu.system.NetworkStatus
import com.tvtools.floatingmenu.system.SystemShortcuts
import com.tvtools.floatingmenu.system.VolumeController
import com.tvtools.floatingmenu.ui.FavoritesAdapter
import com.tvtools.floatingmenu.ui.FocusEffects

/**
 * لوحة القائمة العائمة. نافذتها قابلة للتركيز ليعمل التنقل بالأسهم/Enter/Esc،
 * وتغطي الشاشة بخلفية شبه شفافة: النقر عليها بالماوس يغلق القائمة.
 */
@SuppressLint("InflateParams")
class MenuPanel(
    private val context: Context,
    private val windowManager: WindowManager,
    private val prefs: Prefs,
    private val listener: Listener,
) : FavoritesAdapter.Listener {

    interface Listener {
        fun onMenuClosed()
        fun onStopRequested()
    }

    private val root = LayoutInflater.from(context).inflate(R.layout.overlay_menu, null) as KeyInterceptLayout
    private val panel: View = root.findViewById(R.id.panel)
    private val btnClose: ImageButton = root.findViewById(R.id.btn_close)
    private val btnVolDown: ImageButton = root.findViewById(R.id.btn_vol_down)
    private val btnVolUp: ImageButton = root.findViewById(R.id.btn_vol_up)
    private val seekVolume: SeekBar = root.findViewById(R.id.seek_volume)
    private val btnMute: Button = root.findViewById(R.id.btn_mute)
    private val txtVolume: TextView = root.findViewById(R.id.txt_volume)
    private val txtVolumeNote: TextView = root.findViewById(R.id.txt_volume_note)
    private val btnSettings: View = root.findViewById(R.id.btn_settings)
    private val btnWifi: View = root.findViewById(R.id.btn_wifi)
    private val imgWifi: ImageView = root.findViewById(R.id.img_wifi)
    private val txtWifiStatus: TextView = root.findViewById(R.id.txt_wifi_status)
    private val btnEdit: Button = root.findViewById(R.id.btn_edit)
    private val recyclerApps: RecyclerView = root.findViewById(R.id.recycler_apps)
    private val txtAppsEmpty: TextView = root.findViewById(R.id.txt_apps_empty)
    private val btnHideBubble: Button = root.findViewById(R.id.btn_hide_bubble)
    private val btnStop: Button = root.findViewById(R.id.btn_stop)

    private val volume = VolumeController(context)
    private val adapter = FavoritesAdapter(this)
    private val handler = Handler(Looper.getMainLooper())
    private val panelOffsetPx = 40f * context.resources.displayMetrics.density

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        OverlayWindows.type,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        title = "FloatingMenuPanel"
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
    }

    var isShowing = false
        private set

    private var receiverRegistered = false
    @Suppress("DEPRECATION")
    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                ACTION_VOLUME_CHANGED -> refreshVolume()
                // زر Home أو التطبيقات الأخيرة => أغلق القائمة
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    val reason = intent.getStringExtra("reason")
                    if (reason == "homekey" || reason == "recentapps") close()
                }
            }
        }
    }

    init {
        root.keyHandler = ::handleKey
        root.setOnClickListener { close() } // النقر على الخلفية المعتمة
        panel.setOnClickListener { /* يمنع وصول النقرة للخلفية */ }
        btnClose.setOnClickListener { close() }

        btnVolUp.setOnClickListener { volume.raise(); refreshVolumeSoon() }
        btnVolDown.setOnClickListener { volume.lower(); refreshVolumeSoon() }
        btnMute.setOnClickListener { volume.toggleMute(); refreshVolumeSoon() }
        seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    volume.setLevel(progress)
                    updateVolumeLabel()
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = refreshVolumeSoon()
        })
        // عجلة الماوس فوق عناصر الصوت ترفع/تخفض الصوت
        val wheel = View.OnGenericMotionListener { _, event ->
            if (event.isFromSource(InputDevice.SOURCE_CLASS_POINTER) &&
                event.actionMasked == MotionEvent.ACTION_SCROLL
            ) {
                val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (v > 0) volume.raise() else if (v < 0) volume.lower()
                refreshVolumeSoon()
                true
            } else {
                false
            }
        }
        listOf(seekVolume, btnVolUp, btnVolDown, btnMute, txtVolume).forEach { it.setOnGenericMotionListener(wheel) }

        btnSettings.setOnClickListener {
            if (!SystemShortcuts.openSettings(context)) toast(R.string.error_open_settings)
            close()
        }
        btnWifi.setOnClickListener {
            if (!SystemShortcuts.openWifiSettings(context)) toast(R.string.error_open_settings)
            close()
        }

        recyclerApps.layoutManager = GridLayoutManager(context, APP_COLUMNS)
        recyclerApps.adapter = adapter
        btnEdit.setOnClickListener {
            adapter.editMode = !adapter.editMode
            updateEditButton()
        }

        btnHideBubble.setOnClickListener {
            prefs.bubbleVisible = !prefs.bubbleVisible
            updateBubbleButton()
            if (!prefs.bubbleVisible) toast(R.string.bubble_hidden_toast)
        }
        btnStop.setOnClickListener { listener.onStopRequested() }

        FocusEffects.applyTree(panel)
    }

    /** يعرض القائمة في جهة الزر العائم. يعيد false إذا رفض النظام إظهار النافذة. */
    fun show(onLeftSide: Boolean): Boolean {
        adapter.editMode = false
        refreshAll()
        if (isShowing) return true

        (panel.layoutParams as FrameLayout.LayoutParams).gravity =
            (if (onLeftSide) Gravity.LEFT else Gravity.RIGHT) or Gravity.CENTER_VERTICAL
        try {
            windowManager.addView(root, params)
        } catch (e: RuntimeException) {
            toast(R.string.error_overlay)
            return false
        }
        isShowing = true
        registerReceiver()

        panel.alpha = 0f
        panel.translationX = if (onLeftSide) -panelOffsetPx else panelOffsetPx
        panel.animate().alpha(1f).translationX(0f).setDuration(180).start()
        root.post { btnVolUp.requestFocus() }
        return true
    }

    fun close() {
        if (!dismiss()) return
        listener.onMenuClosed()
    }

    /** إزالة النافذة بدون إشعار الخدمة (عند إيقافها). */
    fun dismiss(): Boolean {
        if (!isShowing) return false
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver()
        try {
            windowManager.removeView(root)
        } catch (e: RuntimeException) {
            // أزيلت مسبقاً
        }
        isShowing = false
        return true
    }

    // ---------- لوحة المفاتيح / الريموت ----------

    private fun handleKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                    if (adapter.editMode) {
                        adapter.editMode = false
                        updateEditButton()
                    } else {
                        close()
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_M, KeyEvent.KEYCODE_VOLUME_MUTE -> {
                if (down && event.repeatCount == 0) {
                    volume.toggleMute()
                    refreshVolumeSoon()
                }
                return true
            }
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_EQUALS,
            KeyEvent.KEYCODE_NUMPAD_ADD, KeyEvent.KEYCODE_VOLUME_UP -> {
                if (down) {
                    volume.raise()
                    refreshVolumeSoon()
                }
                return true
            }
            KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (down) {
                    volume.lower()
                    refreshVolumeSoon()
                }
                return true
            }
        }
        return false
    }

    // ---------- التحديث ----------

    private fun refreshAll() {
        refreshVolume()
        refreshNetwork()
        loadFavorites()
        updateBubbleButton()
        updateEditButton()
    }

    private fun refreshVolume() {
        seekVolume.max = volume.maxLevel
        seekVolume.progress = volume.currentLevel
        updateVolumeLabel()
        val muted = volume.isMuted
        btnMute.setText(if (muted) R.string.unmute else R.string.mute)
        btnMute.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (muted) R.drawable.ic_volume_up else R.drawable.ic_volume_off, 0, 0, 0,
        )
        txtVolumeNote.isVisible = volume.isFixed
    }

    /** بعض أجهزة التلفاز (HDMI-CEC) تحدّث الصوت بتأخير بسيط، لذا نعيد القراءة مرة أخرى. */
    private fun refreshVolumeSoon() {
        refreshVolume()
        handler.postDelayed({ if (isShowing) refreshVolume() }, 350)
    }

    private fun updateVolumeLabel() {
        txtVolume.text = if (volume.isMuted) {
            context.getString(R.string.muted_short)
        } else {
            context.getString(R.string.volume_percent, volume.percent)
        }
        txtVolume.setTextColor(context.getColor(if (volume.isMuted) R.color.danger else R.color.text_primary))
    }

    private fun refreshNetwork() {
        val state = NetworkStatus.read(context)
        var text = when (state.kind) {
            NetKind.WIFI -> state.signalBars?.let { context.getString(R.string.net_wifi_bars, it) }
                ?: context.getString(R.string.net_wifi)
            NetKind.ETHERNET -> context.getString(R.string.net_ethernet)
            NetKind.OTHER -> context.getString(R.string.net_other)
            NetKind.NONE -> context.getString(R.string.net_none)
        }
        val ok = state.kind != NetKind.NONE && state.hasInternet
        if (state.kind != NetKind.NONE && !state.hasInternet) {
            text += " • " + context.getString(R.string.net_no_internet)
        }
        txtWifiStatus.text = text
        txtWifiStatus.setTextColor(context.getColor(if (ok) R.color.success else R.color.danger))
        imgWifi.alpha = if (state.kind == NetKind.NONE) 0.4f else 1f
    }

    private fun loadFavorites() {
        val packages = prefs.favorites
        val entries = packages.mapNotNull { AppRepository.loadEntry(context, it) }
        if (entries.size != packages.size) {
            prefs.favorites = entries.map { it.packageName } // تنظيف التطبيقات المحذوفة من الجهاز
        }
        adapter.submit(entries)
        txtAppsEmpty.isVisible = entries.isEmpty()
    }

    private fun updateEditButton() {
        btnEdit.isVisible = adapter.appCount > 0
        btnEdit.setText(if (adapter.editMode) R.string.done else R.string.edit)
    }

    private fun updateBubbleButton() {
        btnHideBubble.setText(if (prefs.bubbleVisible) R.string.hide_bubble else R.string.show_bubble)
    }

    // ---------- FavoritesAdapter.Listener ----------

    override fun onAppClick(app: AppEntry) {
        if (!AppRepository.launch(context, app.packageName)) toast(R.string.error_launch)
        close()
    }

    override fun onAppRemove(app: AppEntry) {
        prefs.removeFavorite(app.packageName)
        adapter.remove(app)
        Toast.makeText(context, context.getString(R.string.app_removed, app.label), Toast.LENGTH_SHORT).show()
        txtAppsEmpty.isVisible = adapter.appCount == 0
        if (adapter.appCount == 0) adapter.editMode = false
        updateEditButton()
    }

    override fun onAddClick() {
        val intent = Intent(context, AppPickerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(AppPickerActivity.EXTRA_REOPEN_MENU, true)
        context.startActivity(intent)
        close()
    }

    override fun onRequestEditMode() {
        adapter.editMode = true
        updateEditButton()
    }

    // ---------- مساعدات ----------

    @Suppress("DEPRECATION")
    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_VOLUME_CHANGED)
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        }
        ContextCompat.registerReceiver(context, systemReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(systemReceiver)
        } catch (e: IllegalArgumentException) {
            // غير مسجل
        }
        receiverRegistered = false
    }

    private fun toast(res: Int) = Toast.makeText(context, res, Toast.LENGTH_SHORT).show()

    private companion object {
        const val APP_COLUMNS = 3
        const val ACTION_VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    }
}
