package com.livetranslate.overlay

import android.app.Application
import com.livetranslate.overlay.translate.OcrPackStore

class LiveTranslateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        OcrPackStore.init(this)
    }
}
