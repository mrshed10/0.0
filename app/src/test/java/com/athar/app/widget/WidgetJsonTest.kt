package com.athar.app.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetJsonTest {
    @Test
    fun roundTripFullStyle() {
        val config = WidgetConfig(
            WidgetContent(WidgetType.Counter, 42),
            WidgetStyle(
                kind = BackgroundKind.Image, color1 = 0x80112233.toInt(), color2 = 0xFFABCDEF.toInt(), angle = 45,
                opacity = 0, imagePath = "/data/bg.jpg", blur = 20, dim = 10, cornerDp = 32, border = false,
                textColor = 0xFF000000.toInt(), accentColor = 0xFFE6C27A.toInt(), fontId = "amiri",
            ),
        )
        assertEquals(config, WidgetJson.decode(WidgetJson.encode(config)))
    }

    @Test
    fun defaultsAndPresetsRoundTrip() {
        WidgetPresets.all.forEach { style ->
            val config = WidgetConfig(WidgetContent(WidgetType.NextPrayer), style)
            assertEquals(config, WidgetJson.decode(WidgetJson.encode(config)))
        }
    }

    @Test
    fun outOfRangeValuesAreClamped() {
        val raw = """{"type":"Tasbih","item":-1,"style":{"opacity":250,"blur":99,"corner":-5}}"""
        val style = WidgetJson.decode(raw)!!.style
        assertEquals(100, style.opacity)
        assertEquals(25, style.blur)
        assertEquals(0, style.cornerDp)
    }

    @Test
    fun invalidInputGivesNull() {
        assertNull(WidgetJson.decode(null))
        assertNull(WidgetJson.decode("nope"))
        assertNull(WidgetJson.decode("""{"type":"Unknown"}"""))
    }
}
