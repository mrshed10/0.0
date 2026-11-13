package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineSplitterTest {

    @Test
    fun singleLineKeepsWholeText() {
        assertEquals(listOf("مرحبا بالعالم"), LineSplitter.distribute(" مرحبا بالعالم ", listOf(300f), true))
    }

    @Test
    fun splitsProportionallyToLineWidths() {
        val parts = LineSplitter.distribute(
            "هذه هي منتجات العناية بالبشرة الكورية المفضلة لدي هذا الشتاء",
            listOf(600f, 300f),
            spaced = true,
        )
        assertEquals(2, parts.size)
        assertTrue(parts[0].length > parts[1].length)
        assertEquals(
            "هذه هي منتجات العناية بالبشرة الكورية المفضلة لدي هذا الشتاء",
            parts.joinToString(" "),
        )
    }

    @Test
    fun everyLineGetsTextWhenPossible() {
        val parts = LineSplitter.distribute("واحد اثنان ثلاثة", listOf(100f, 100f, 100f), true)
        assertEquals(listOf("واحد", "اثنان", "ثلاثة"), parts)
    }

    @Test
    fun fewerWordsThanLinesLeavesTrailingLinesEmpty() {
        val parts = LineSplitter.distribute("مرحبا", listOf(100f, 100f, 100f), true)
        assertEquals(listOf("مرحبا", "", ""), parts)
    }

    @Test
    fun unspacedTargetsSplitByCharacter() {
        val parts = LineSplitter.distribute("今天天气很好", listOf(100f, 100f), spaced = false)
        assertEquals("今天天气很好", parts.joinToString(""))
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.isNotEmpty() })
    }
}
