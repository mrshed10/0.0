package com.tvtools.floatingmenu.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager

enum class NetKind { WIFI, ETHERNET, OTHER, NONE }

data class NetState(
    val kind: NetKind,
    val hasInternet: Boolean,
    /** قوة إشارة الواي فاي من 0 إلى 4، أو null إذا لم تتوفر. */
    val signalBars: Int?,
)

object NetworkStatus {

    fun read(context: Context): NetState {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return NetState(NetKind.NONE, hasInternet = false, signalBars = null)
        val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetState(NetKind.WIFI, hasInternet, wifiBars(context))
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                NetState(NetKind.ETHERNET, hasInternet, null)
            else -> NetState(NetKind.OTHER, hasInternet, null)
        }
    }

    @Suppress("DEPRECATION") // connectionInfo ما زال يعيد RSSI بدون صلاحية الموقع
    private fun wifiBars(context: Context): Int? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return try {
            signalBars(wifi.connectionInfo?.rssi ?: return null)
        } catch (e: SecurityException) {
            null
        }
    }

    /** تحويل RSSI (dBm) إلى أعمدة إشارة 0..4. */
    fun signalBars(rssi: Int): Int? = when {
        rssi <= -127 || rssi >= 0 -> null // قيمة غير صالحة
        rssi >= -55 -> 4
        rssi >= -66 -> 3
        rssi >= -77 -> 2
        rssi >= -88 -> 1
        else -> 0
    }
}
