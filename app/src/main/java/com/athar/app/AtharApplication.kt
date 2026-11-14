package com.athar.app

import android.app.Application
import com.athar.app.core.data.AppContainer

class AtharApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
