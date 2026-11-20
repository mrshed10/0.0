package com.localcam.surveillance

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** مساعد إرشادي لضبط البطارية على «غير مقيدة» حتى لا يوقف النظام التسجيل الطويل. */
object BatteryHelper {

    fun isUnrestricted(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** يُعرض مرة واحدة عند أول تشغيل إن لم يكن التطبيق مستثنى بعد. */
    fun showTipIfNeeded(activity: AppCompatActivity) {
        val settings = AppSettings(activity)
        if (!settings.batteryTipShown && !isUnrestricted(activity)) {
            settings.batteryTipShown = true
            showDialog(activity)
        }
    }

    fun showDialog(activity: AppCompatActivity) {
        MaterialAlertDialogBuilder(activity)
            .setTitle("مهم للتسجيل لساعات وأيام")
            .setMessage(
                "حتى لا يوقف نظام أندرويد التسجيل لتوفير البطارية:\n\n" +
                    "1) اضغط «السماح بلا قيود» ثم وافق.\n" +
                    "2) أو افتح «إعدادات التطبيق» ← البطارية ← اختر «غير مقيدة / Unrestricted».\n" +
                    "3) في أجهزة سامسونج وشاومي وهواوي: فعّل «التشغيل التلقائي» وأزل التطبيق من «التطبيقات النائمة».\n\n" +
                    "ويُفضّل إبقاء الجوال موصولاً بالشاحن أثناء المراقبة الطويلة."
            )
            .setPositiveButton("السماح بلا قيود") { _, _ -> requestUnrestricted(activity) }
            .setNeutralButton("إعدادات التطبيق") { _, _ -> openAppSettings(activity) }
            .setNegativeButton("لاحقاً", null)
            .show()
    }

    /** نافذة النظام لطلب استثناء التطبيق من تحسين البطارية. */
    @SuppressLint("BatteryLife")
    fun requestUnrestricted(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /** صفحة معلومات التطبيق (منها: البطارية ← غير مقيدة). */
    fun openAppSettings(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }
}
