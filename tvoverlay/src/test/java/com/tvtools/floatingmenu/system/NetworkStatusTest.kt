package com.tvtools.floatingmenu.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetworkStatusTest {

    @Test
    fun rssiToBars() {
        assertEquals(4, NetworkStatus.signalBars(-40))
        assertEquals(3, NetworkStatus.signalBars(-60))
        assertEquals(2, NetworkStatus.signalBars(-70))
        assertEquals(1, NetworkStatus.signalBars(-85))
        assertEquals(0, NetworkStatus.signalBars(-95))
    }

    @Test
    fun invalidRssi() {
        assertNull(NetworkStatus.signalBars(-127))
        assertNull(NetworkStatus.signalBars(0))
    }
}
