package com.onetap.assist.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class PositioningTest {
    private val screenW = 1080
    private val screenH = 2400
    private val size = 150
    private val area = Positioning.area(screenW, screenH, insetTop = 100, insetBottom = 50, size = size, margin = 10)

    @Test fun areaRespectsInsetsAndMargins() {
        assertEquals(10, area.left)
        assertEquals(110, area.top)
        assertEquals(1080 - 150 - 10, area.right)
        assertEquals(2400 - 50 - 150 - 10, area.bottom)
    }

    @Test fun snapsToNearestEdge() {
        assertEquals(area.left, Positioning.snapX(100, size, screenW, area))
        assertEquals(area.right, Positioning.snapX(700, size, screenW, area))
    }

    @Test fun clampsOutOfBounds() {
        assertEquals(area.left, Positioning.clampX(-500, area))
        assertEquals(area.bottom, Positioning.clampY(99999, area))
    }

    @Test fun fractionRoundTrip() {
        val y = 1000
        val f = Positioning.toFracY(y, area)
        assertEquals(y, Positioning.fromFracY(f, area))
        assertEquals(area.right, Positioning.fromFracX(1f, area))
        assertEquals(area.left, Positioning.fromFracX(0f, area))
    }

    @Test fun degenerateScreenDoesNotCrash() {
        val tiny = Positioning.area(100, 100, 0, 0, size = 150, margin = 0)
        assertEquals(0f, Positioning.toFracX(50, tiny))
        assertEquals(tiny.left, Positioning.fromFracX(0.5f, tiny))
    }
}
