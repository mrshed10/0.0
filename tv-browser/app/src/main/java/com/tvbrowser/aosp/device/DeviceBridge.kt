package com.tvbrowser.aosp.device

import android.os.Handler
import android.os.Looper
import com.tvbrowser.aosp.util.Prefs
import org.json.JSONObject

/**
 * Native side of `window.tvDevice` (see assets/extensions/tvbridge/content.js).
 * Messages arrive from the built-in extension through GeckoView native messaging.
 *
 *   {action: "volumeUp" | "volumeDown" | "toggleMute" | "getVolume" | "setVolume", value?: 0..100}
 */
class DeviceBridge(private val volume: VolumeController, private val prefs: Prefs) {

    private val main = Handler(Looper.getMainLooper())

    fun handle(message: JSONObject): JSONObject {
        val action = message.optString("action")
        if (action != "getVolume" && !prefs.allowPageVolume) {
            return JSONObject().put("error", "volume control by web pages is disabled in settings")
        }
        // GeckoView delivers messages on the UI thread already; post anyway to be safe with listeners.
        val state = when (action) {
            "volumeUp" -> volume.up()
            "volumeDown" -> volume.down()
            "toggleMute" -> volume.toggleMute()
            "setVolume" -> volume.setPercent(message.optInt("value", volume.state().percent))
            "getVolume" -> volume.state()
            else -> return JSONObject().put("error", "unknown action: $action")
        }
        if (Looper.myLooper() != Looper.getMainLooper()) main.post { volume.listener?.invoke(state) }
        return state.toJson()
    }
}
