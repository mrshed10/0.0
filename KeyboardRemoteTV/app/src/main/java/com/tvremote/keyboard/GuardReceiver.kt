package com.tvremote.keyboard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * يستقبل: منبّه الحارس الدوري، وتشغيل الجهاز (BOOT_COMPLETED)، وتحديث التطبيق.
 * يفحص الخدمة في خيط خلفي ويعيد تفعيلها إن لزم.
 */
class GuardReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        ServiceGuard.schedulePeriodic(app) // نعيد الجدولة دائماً (بعد الإقلاع تُمسح المنبّهات)

        val pending = goAsync()
        Thread {
            try {
                ServiceGuard.repairIfNeeded(app)
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_CHECK = "com.tvremote.keyboard.GUARD_CHECK"
    }
}
