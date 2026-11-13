package com.livetranslate.overlay.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PatchGeometryTest {

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
