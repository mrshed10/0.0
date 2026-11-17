package com.tvbrowser.aosp.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.tvbrowser.aosp.TvBrowserApp
import com.tvbrowser.aosp.browser.PromptHandler
import com.tvbrowser.aosp.browser.SessionAttacher
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

/** Shows an extension's toolbar popup (e.g. uBlock Origin's panel) with its own cursor. */
class PopupHost(private val activity: Activity) {

    fun open(extension: WebExtension): GeckoResult<GeckoSession> {
        val app = activity.application as TvBrowserApp
        val session = GeckoSession(GeckoSessionSettings.Builder().build())
        session.promptDelegate = PromptHandler(activity)
        session.contentDelegate = object : GeckoSession.ContentDelegate {}

        val gecko = GeckoView(activity)
        val cursorView = CursorView(activity)
        val root = FrameLayout(activity).apply {
            setBackgroundColor(Color.WHITE)
            addView(gecko, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(cursorView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val cursor = CursorController(gecko, cursorView, app.prefs, { session })

        val dialog = Dialog(activity, android.R.style.Theme_Material_Dialog_NoActionBar)
        dialog.setTitle(extension.metaData.name)
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        val dm = activity.resources.displayMetrics
        dialog.window?.setLayout((dm.widthPixels * 0.45f).toInt(), (dm.heightPixels * 0.85f).toInt())
        dialog.setOnKeyListener { _, _, ev ->
            if (ev.keyCode == KeyEvent.KEYCODE_BACK) false else cursor.handleKey(ev)
        }
        dialog.setOnDismissListener {
            gecko.releaseSession()
            if (session.isOpen) session.close()
        }
        dialog.show()
        gecko.requestFocus()
        SessionAttacher.attach(gecko, session, app.runtime, { dialog.isShowing })
        return GeckoResult.fromValue(session)
    }
}
