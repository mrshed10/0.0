package com.athar.app.widget

import com.athar.app.core.designsystem.component.parseHex
import com.athar.app.core.notify.Tones
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ElementStyleTest {
    @Test
    fun eachElementKeepsItsOwnStyle() {
        val spec = CustomSpec(
            listOf(
                CustomElement(ElementKind.HijriDate, sizeSp = 18, fontId = "arefruqaa", italic = true, align = 2, color = 0xFF3D8BFF.toInt()),
                CustomElement(ElementKind.Salary, sizeSp = 12, fontId = "poppins", underline = true, opacity = 60),
                CustomElement(ElementKind.Text, text = "أثر"),
            ),
            align = 1,
        )
        val back = CustomSpecJson.decode(CustomSpecJson.encode(spec))
        assertEquals(spec, back)
        assertEquals("arefruqaa", back.elements[0].fontId)
        assertEquals("poppins", back.elements[1].fontId)
        assertNull(back.elements[2].fontId)
        assertNull(back.elements[2].align)
    }

    @Test
    fun oldElementsWithoutNewFieldsStillLoad() {
        val raw = """{"align":0,"elements":[{"kind":"Time","text":"","size":40,"color":null,"accent":false,"bold":true,"dots":false,"ref":-1}]}"""
        val e = CustomSpecJson.decode(raw).elements.single()
        assertEquals(ElementKind.Time, e.kind)
        assertNull(e.fontId)
        assertEquals(100, e.opacity)
    }

    @Test
    fun dotColoursSurviveJsonAndFallBack() {
        val style = WidgetPresets.glow.copy(dotOn = 0xFF3D8BFF.toInt(), dotOff = 0x553D8BFF)
        val back = WidgetJson.decode(WidgetJson.encode(WidgetConfig(WidgetContent(WidgetType.YearDots), style)))!!.style
        assertEquals(0xFF3D8BFF.toInt(), back.litDots())
        assertEquals(0x553D8BFF, back.unlitDots())
        val plain = WidgetStyle(textColor = 0xFFFFFFFF.toInt())
        assertEquals(0xFFFFFFFF.toInt(), plain.litDots())
        assertEquals(0x2EFFFFFF, plain.unlitDots())
    }

    @Test
    fun hexColours() {
        assertEquals(0xFF3D8BFF.toInt(), parseHex("#3D8BFF"))
        assertEquals(0x553D8BFF, parseHex("553D8BFF"))
        assertEquals(0xFFFFFFFF.toInt(), parseHex("#fff"))
        assertNull(parseHex("#12"))
        assertNull(parseHex("blue"))
    }

    @Test
    fun importedSoundsNeverOverwriteEachOther() {
        val dir: File = Files.createTempDirectory("tones").toFile()
        File(dir, "adhan.mp3").writeText("a")
        assertEquals("adhan (2).mp3", Tones.uniqueFile(dir, "adhan.mp3").name)
        File(dir, "adhan (2).mp3").writeText("b")
        assertEquals("adhan (3).mp3", Tones.uniqueFile(dir, "adhan.mp3").name)
        assertEquals("new.mp3", Tones.uniqueFile(dir, "new.mp3").name)
        dir.deleteRecursively()
    }
}
