package com.tvbrowser.aosp.browser

import android.os.Handler
import android.os.Looper
import com.tvbrowser.aosp.util.safe
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

/**
 * Attaches a session to a GeckoView.
 *
 * Sessions handed to Gecko from onNewSession / onOpenPopup are opened by Gecko itself a
 * moment later. We only WAIT for that — never open them ourselves (opening twice crashed).
 * Covers the view with the dark background until the first paint: no white flash.
 */
object SessionAttacher {
    private val main = Handler(Looper.getMainLooper())

    fun attach(
        view: GeckoView,
        session: GeckoSession,
        coverColor: Int,
        stillWanted: () -> Boolean = { true },
        attempt: Int = 0,
    ) {
        if (!stillWanted()) return
        if (session.isOpen) {
            safe("SessionAttacher") {
                if (view.session !== session) {
                    view.releaseSession()
                    view.setSession(session)
                }
                view.coverUntilFirstPaint(coverColor)
            }
            return
        }
        if (attempt >= 60) return // ~3s: Gecko refused it; leave the view as is
        main.postDelayed({ attach(view, session, coverColor, stillWanted, attempt + 1) }, 50)
    }
}
