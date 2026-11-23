package app.safar.browser

import app.safar.browser.translate.ScriptDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptDetectorTest {
    @Test fun japanese() = assertEquals("ja", ScriptDetector.guess("なにをしているんだ!?"))
    @Test fun japaneseWithKanji() = assertEquals("ja", ScriptDetector.guess("俺は海賊王になる男だ"))
    @Test fun korean() = assertEquals("ko", ScriptDetector.guess("뭐 하는 거야?!"))
    @Test fun chinese() = assertEquals("zh", ScriptDetector.guess("你在做什么"))
    @Test fun english() = assertEquals("en", ScriptDetector.guess("WHAT ARE YOU DOING?"))
    @Test fun arabic() = assertEquals("ar", ScriptDetector.guess("ماذا تفعل؟"))
    @Test fun tooShort() = assertNull(ScriptDetector.guess("!?"))
    @Test fun koreanRecognizerWinsOnHangul() =
        assertTrue(ScriptDetector.nativeScore("뭐 하는 거야", "ko") > ScriptDetector.nativeScore("뭐 하는 거야", "ja"))
}
