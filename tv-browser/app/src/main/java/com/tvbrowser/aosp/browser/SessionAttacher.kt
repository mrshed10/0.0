package com.tvbrowser.aosp.browser

import android.os.Handler
import android.os.Looper
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Attaches a session to a GeckoView. Sessions handed to Gecko from onNewSession / onOpenPopup
 * are opened by Gecko itself a moment later, so we wait for that instead of opening twice.
 */
object SessionAttacher {
    private val main = Handler(Looper.getMainLooper())

    fun attach(
        view: GeckoView,
        session: GeckoSession,
        runtime: GeckoRuntime,
        stillWanted: () -> Boolean = { true },
        attempt: Int = 0,
    ) {
        if (!stillWanted()) return
        if (session.isOpen || attempt >= 30) {
            if (!session.isOpen) session.open(runtime)
            if (view.session !== session) {
                view.releaseSession()
                view.setSession(session)
            }
            return
        }
        main.postDelayed({ attach(view, session, runtime, stillWanted, attempt + 1) }, 50)
    }
}
