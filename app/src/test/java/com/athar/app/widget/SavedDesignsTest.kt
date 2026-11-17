package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedDesignsTest {
    @Test
    fun savedDesignsRoundTrip() {
        val spec = CustomSpec(
            listOf(
                CustomElement(ElementKind.Time, sizeSp = 40, bold = true),
                CustomElement(ElementKind.IqamaCountdown, accent = true),
                CustomElement(ElementKind.DaysLeftInMonth),
            ),
        )
        val config = WidgetConfig(WidgetContent(WidgetType.Custom, extra = CustomSpecJson.encode(spec)), WidgetPresets.glow)
        val list = listOf(SavedDesign("d1", "My clock", config, wide = true))
        val back = SavedDesignsJson.decode(SavedDesignsJson.encode(list))
        assertEquals(1, back.size)
        assertEquals("My clock", back[0].name)
        assertEquals(BackgroundKind.GlowDots, back[0].config.style.kind)
        assertEquals(spec, CustomSpecJson.decode(back[0].config.content.extra))
        assertTrue(back[0].config.needsMinuteTick()) // time + iqama countdown tick
    }

    @Test
    fun brokenDataGivesEmptyList() {
        assertEquals(emptyList<SavedDesign>(), SavedDesignsJson.decode("not json"))
        assertEquals(emptyList<SavedDesign>(), SavedDesignsJson.decode(null))
    }
}
