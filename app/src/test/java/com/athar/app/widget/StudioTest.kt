package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioTest {
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

    @Test
    fun exportImportDesigns() {
        val a = SavedDesign("1", "Clock", WidgetConfig(WidgetContent(WidgetType.NothingClock), WidgetTemplates.nothingDark), wide = false, createdAt = 1)
        val b = SavedDesign(
            "2", "Photo",
            WidgetConfig(WidgetContent(WidgetType.Date), WidgetStyle(kind = BackgroundKind.Image, imagePath = "/data/x.jpg")),
            wide = true, createdAt = 2,
        )
        val single = DesignCodec.import(DesignCodec.export(listOf(a)))
        assertEquals(1, single.size)
        assertEquals(a.config, single[0].config)
        assertEquals("Clock", single[0].name)
        assertTrue(single[0].id != a.id)

        val pack = DesignCodec.import(DesignCodec.export(listOf(a, b)))
        assertEquals(2, pack.size)
        // Local photos are not shared.
        assertNull(pack[1].config.style.imagePath)
        assertEquals(BackgroundKind.Gradient, pack[1].config.style.kind)
    }

    @Test(expected = IllegalArgumentException::class)
    fun importRejectsOtherJson() {
        DesignCodec.import("""{"format":"something-else"}""")
    }

    @Test
    fun libraryListRoundTrip() {
        val list = listOf(SavedDesign("x", "A", WidgetConfig(WidgetContent(WidgetType.Verse)), true, 5))
        assertEquals(list, DesignCodec.decodeList(DesignCodec.encodeList(list)))
    }
}
