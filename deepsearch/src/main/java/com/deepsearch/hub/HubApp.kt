package com.deepsearch.hub

import android.app.Application
import com.deepsearch.hub.browser.AdBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HubApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        appScope.launch { AdBlocker.init(this@HubApp) }
    }
}
