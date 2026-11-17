package com.athar.app

import kotlinx.coroutines.launch

import android.app.Application
import com.athar.app.core.data.AppContainer
import com.athar.app.core.data.CrashLog
import com.athar.app.core.data.keepWidgetsInSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AtharApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        container.keepAlarmsInSync(appScope)
        container.keepWidgetsInSync(this, appScope)
        // Imported sounds stay playable as notification sounds (grants reset on reboot).
        appScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.athar.app.core.notify.Tones.grantAll(this@AtharApplication) }
    }
}
