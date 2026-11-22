package com.tvtools.floatingmenu

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tvtools.floatingmenu.data.Prefs
import com.tvtools.floatingmenu.system.OverlayPermission

/** يشغّل الخدمة تلقائياً عند تشغيل التلفاز أو بعد تحديث التطبيق. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Prefs(context)
        if (prefs.startOnBoot && prefs.serviceEnabled && OverlayPermission.isGranted(context)) {
            try {
                OverlayService.start(context)
            } catch (e: IllegalStateException) {
                // بعض الأنظمة تمنع بدء الخدمة في هذه اللحظة؛ ستبدأ عند فتح التطبيق.
            }
        }
    }
}
