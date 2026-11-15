package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDesignTest {
    @Test
    fun dotMatrixCoversClockText() {
        assertTrue(DotMatrix.supports("12:34"))
        assertTrue(DotMatrix.supports("SAT 15 NOV"))
        assertTrue(DotMatrix.supports("87%"))
        assertTrue(DotMatrix.supports("١٢:٣٤"))
        assertFalse(DotMatrix.supports("العصر"))
        // "1" (5) + gap + ":" (1) + gap + "2" (5)
        assertEquals(13, DotMatrix.columns("1:2"))
    }

    @Test
    fun customSpecRoundTripAndTick() {
        val spec = CustomSpec(
            listOf(
                CustomElement(ElementKind.Text, text = "مرحبا", sizeSp = 20, color = 0xFFD71921.toInt(), bold = true),
                CustomElement(ElementKind.Time, sizeSp = 48, dots = true),
                CustomElement(ElementKind.Counter, ref = 7, accent = true),
            ),
            align = 0,
        )
        assertEquals(spec, CustomSpecJson.decode(CustomSpecJson.encode(spec)))
        val config = WidgetConfig(WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(spec)))
        assertTrue(config.needsMinuteTick())
        val noTime = WidgetConfig(WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(CustomSpec(listOf(CustomElement(ElementKind.HijriDate))))))
        assertFalse(noTime.needsMinuteTick())
        assertTrue(WidgetConfig(WidgetContent(WidgetType.NothingClock)).needsMinuteTick())
    }

    @Test
    fun badCustomSpecFallsBackToStarter() {
        assertEquals(CustomSpec.starter(), CustomSpecJson.decode("garbage"))
        assertEquals(CustomSpec.starter(), CustomSpecJson.decode(null))
    }
}
