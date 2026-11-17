package com.athar.app.core.notify

import android.content.Context
import com.athar.app.R

/** The sentences spoken for prayer alerts. */
object PrayerSpeech {
    /** "Dhuhr prayer is near. 10 minutes left." */
    fun pre(context: Context, prayer: String, minutes: Int): String =
        context.getString(R.string.voice_pre, prayer, context.resources.getQuantityString(R.plurals.voice_minutes, minutes, minutes))

    /** "Allahu Akbar, Allahu Akbar. It is now time for Dhuhr prayer." */
    fun adhan(context: Context, prayer: String): String = context.getString(R.string.voice_adhan, prayer)

    /** "Hayya 'ala as-salah. It is time for the iqama of Dhuhr." */
    fun iqama(context: Context, prayer: String): String = context.getString(R.string.voice_iqama, prayer)
}
