package com.athar.app.feature.prayer

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

/** Gets an approximate position without Google Play services. Caller checks permission. */
object LocationHelper {
    @SuppressLint("MissingPermission")
    fun current(context: Context, onResult: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < 6 * 60 * 60 * 1000L) {
            onResult(last)
            return
        }
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER }
        if (provider == null) {
            onResult(last)
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { onResult(it ?: last) }
            } else {
                @Suppress("DEPRECATION")
                lm.requestSingleUpdate(provider, { onResult(it) }, Looper.getMainLooper())
            }
        } catch (e: Exception) {
            onResult(last)
        }
    }
}
