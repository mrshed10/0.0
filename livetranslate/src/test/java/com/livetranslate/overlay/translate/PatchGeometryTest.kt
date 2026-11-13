package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchGeometryTest {

    @Test
    fun widensVerticalColumnAroundItsCenter() {
        val column = RectF4(480f, 100f, 520f, 500f)
        val wide = PatchGeometry.widenVertical(column, emptyList(), 1080f, 540f, 8f)
        assertEquals(500f, wide.centerX, 0.01f)
        assertEquals(300f, wide.width, 0.01f) // 75% من الارتفاع
        assertEquals(column.top, wide.top, 0.01f)
        assertEquals(column.bottom, wide.bottom, 0.01f)
    }

    @Test
    fun neverOverlapsNeighbouringColumns() {
        val column = RectF4(480f, 100f, 520f, 500f)
        val left = RectF4(380f, 150f, 420f, 450f)
        val right = RectF4(560f, 120f, 600f, 480f)
        val wide = PatchGeometry.widenVertical(column, listOf(left, right), 1080f, 540f, 8f)
        assertTrue(wide.left >= left.right + 8f - 0.01f)
        assertTrue(wide.right <= right.left - 8f + 0.01f)
        // ويبقى يغطي العمود الأصلي بالكامل
        assertTrue(wide.left <= column.left && wide.right >= column.right)
    }

    @Test
    fun staysInsideScreen() {
        val column = RectF4(1040f, 100f, 1075f, 600f)
        val wide = PatchGeometry.widenVertical(column, emptyList(), 1080f, 540f, 8f)
        assertTrue(wide.right <= 1080f)
        assertTrue(wide.left <= column.left)
    }

    @Test
    fun steepAnglesAreDrawnUpright() {
        assertEquals(20f, PatchGeometry.drawAngle(20f), 0.01f)
        assertEquals(-30f, PatchGeometry.drawAngle(-30f), 0.01f)
        assertEquals(0f, PatchGeometry.drawAngle(90f), 0.01f)
        assertEquals(0f, PatchGeometry.drawAngle(-88f), 0.01f)
        assertEquals(0f, PatchGeometry.drawAngle(180f), 0.01f)
        assertEquals(0f, PatchGeometry.drawAngle(1f), 0.01f)
    }

    @Test
    fun detectsSidewaysColumns() {
        assertTrue(PatchGeometry.isUprightVertical(90f, 40f, 300f))
        assertTrue(PatchGeometry.isUprightVertical(-90f, 40f, 300f))
        assertFalse(PatchGeometry.isUprightVertical(0f, 300f, 40f))
        assertFalse(PatchGeometry.isUprightVertical(30f, 40f, 300f))
    }
}
