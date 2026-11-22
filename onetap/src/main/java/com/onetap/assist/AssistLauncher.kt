package com.onetap.assist

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Fires the assist action. Tries the chosen system action first and, if the system refuses
 * (no handler, blocked, or restricted), falls back to the user's chosen app / shortcut / link.
 */
object AssistLauncher {

    const val GOOGLE_APP = "com.google.android.googlequicksearchbox"

    fun primaryIntent(action: TapAction): Intent? = when (action) {
        TapAction.ASSIST -> Intent(Intent.ACTION_ASSIST)
        TapAction.VOICE -> Intent(Intent.ACTION_VOICE_COMMAND)
        TapAction.SEARCH -> Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH)
        TapAction.FALLBACK_ONLY -> null
    }

    fun fallbackIntent(prefs: Prefs): Intent? =
        prefs.fallbackUri?.let { parseTarget(it) }

    /** Accepts an "intent:" URI or any plain deep link (treated as ACTION_VIEW). */
    fun parseTarget(uri: String): Intent? = runCatching {
        Intent.parseUri(uri.trim(), Intent.URI_INTENT_SCHEME).apply {
            // Never let a stored target escalate: drop selector and any grant flags.
            selector = null
            flags = 0
        }
    }.getOrNull()

    fun lensIntent(): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://lens")).setPackage(GOOGLE_APP)

    /** @return true if something was launched. */
    fun trigger(context: Context, prefs: Prefs = Prefs(context)): Boolean {
        val action = prefs.tapAction
        primaryIntent(action)?.let { if (launch(context, it)) return true }

        if (action == TapAction.FALLBACK_ONLY || prefs.autoFallback) {
            fallbackIntent(prefs)?.let { if (launch(context, it)) return true }
        }
        Toast.makeText(context, R.string.toast_launch_failed, Toast.LENGTH_LONG).show()
        return false
    }

    private fun launch(context: Context, intent: Intent): Boolean = try {
        context.startActivity(
            Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
}
