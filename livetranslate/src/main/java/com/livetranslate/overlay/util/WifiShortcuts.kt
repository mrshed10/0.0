package com.livetranslate.overlay.util

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.provider.Settings

/**
 * اختصارات للتحكم بشبكة الواي فاي مباشرة.
 * منذ Android 10 لا يُسمح للتطبيقات بتشغيل/إيقاف الواي فاي برمجياً، لذا نفتح لوحة النظام السريعة
 * (تظهر فوق التطبيق: تشغيل/إيقاف واختيار الشبكة) أو صفحة إعدادات الواي فاي الكاملة.
 */
object WifiShortcuts {

    /** لوحة الواي فاي السريعة (Android 10+)، أو صفحة الإعدادات في الإصدارات الأقدم. */
    fun panelIntent(): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Intent(Settings.Panel.ACTION_WIFI)
        } else {
            Intent(Settings.ACTION_WIFI_SETTINGS)
        }

    /** صفحة إعدادات الواي فاي الكاملة (الشبكات المحفوظة، كلمات المرور، الخيارات المتقدمة). */
    fun settingsIntent(): Intent = Intent(Settings.ACTION_WIFI_SETTINGS)

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** يراقب حالة الاتصال بالواي فاي؛ يُرجع دالة لإلغاء المراقبة. */
    fun observe(context: Context, onChange: (Boolean) -> Unit): () -> Unit {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return {}
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onChange(isWifiConnected(context))
            override fun onLost(network: Network) = onChange(isWifiConnected(context))
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onChange(isWifiConnected(context))
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        return { runCatching { cm.unregisterNetworkCallback(callback) } }
    }
}
