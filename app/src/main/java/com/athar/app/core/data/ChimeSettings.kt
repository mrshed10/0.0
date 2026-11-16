package com.athar.app.core.data

/** Sound of the hourly chime. */
enum class ChimeSound { Classic, Single, Soft }

/**
 * Hourly chime settings. [quietFrom]/[quietTo] are hours (0–23); the window may
 * cross midnight (22 → 7). [volume] is 0–100.
 */
data class ChimeSettings(
    val enabled: Boolean = false,
    val sound: ChimeSound = ChimeSound.Classic,
    val volume: Int = 45,
    val quietHours: Boolean = true,
    val quietFrom: Int = 22,
    val quietTo: Int = 7,
    val respectSilent: Boolean = true,
) {
    fun normalized() = copy(volume = volume.coerceIn(5, 100), quietFrom = quietFrom.mod(24), quietTo = quietTo.mod(24))

    /** True if the chime should stay quiet at [hour]. */
    fun isQuiet(hour: Int): Boolean {
        if (!quietHours || quietFrom == quietTo) return false
        return if (quietFrom < quietTo) hour in quietFrom until quietTo else hour >= quietFrom || hour < quietTo
    }
}
