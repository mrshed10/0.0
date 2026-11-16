package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerPulseTest {
    @Test
    fun litDotsFollowProgress() {
        assertEquals(1, PrayerPulse.litCount(0f, 48))
        assertEquals(24, PrayerPulse.litCount(0.5f, 48))
        assertEquals(48, PrayerPulse.litCount(1f, 48))
    }

    @Test
    fun shimmerTravelsAcrossFrames() {
        val lit = 30
        val peaks = (0 until PrayerPulse.FRAMES).map { f -> (0 until lit).maxByOrNull { PrayerPulse.shimmer(it, lit, f) }!! }
        assertTrue(peaks.zipWithNext().all { (a, b) -> b > a })
        (0 until PrayerPulse.FRAMES).forEach { f -> (0 until lit).forEach { assertTrue(PrayerPulse.shimmer(it, lit, f) in 0f..1f) } }
    }

    @Test
    fun glowDotsStyleSurvivesJson() {
        val config = WidgetConfig(WidgetContent(WidgetType.NextPrayer), WidgetPresets.glow.copy(variant = PrayerPulse.RING))
        val back = WidgetJson.decode(WidgetJson.encode(config))!!
        assertEquals(BackgroundKind.GlowDots, back.style.kind)
        assertEquals(PrayerPulse.RING, back.style.variant)
        assertTrue(back.needsMinuteTick())
    }
}
