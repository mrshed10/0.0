package com.tvremote.floating

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** يعيد تشغيل القائمة العائمة تلقائياً بعد إقلاع الجهاز أو تحديث التطبيق */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.isFloatingEnabled(context) && FloatingService.canDrawOverlays(context)) {
            FloatingService.start(context)
        }
        AccessibilityGuard.repairAsync(context)
    }
}
