package com.tvremote.keyboard

/**
 * الوظائف التي يمكن ربطها بأي زر في الكيبورد.
 * [repeatable] = هل تتكرر الوظيفة عند الضغط المطوّل على الزر.
 */
enum class RemoteAction(val labelRes: Int, val repeatable: Boolean = false) {
    VOLUME_UP(R.string.act_volume_up, repeatable = true),
    VOLUME_DOWN(R.string.act_volume_down, repeatable = true),
    MUTE(R.string.act_mute),
    HOME(R.string.act_home),
    BACK(R.string.act_back),
    RECENTS(R.string.act_recents),
    PLAY_PAUSE(R.string.act_play_pause),
    MEDIA_NEXT(R.string.act_media_next),
    MEDIA_PREVIOUS(R.string.act_media_previous),
    NOTIFICATIONS(R.string.act_notifications);

    companion object {
        fun fromName(name: String?): RemoteAction? = values().firstOrNull { it.name == name }
    }
}
