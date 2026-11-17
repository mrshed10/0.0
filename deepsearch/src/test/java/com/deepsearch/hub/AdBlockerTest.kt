package com.deepsearch.hub

import com.deepsearch.hub.browser.AdBlocker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    private val set = HashSet<String>().also {
        AdBlocker.parseInto(
            sequenceOf(
                "# comment",
                "0.0.0.0 doubleclick.net",
                "127.0.0.1 tracker.example.org # inline",
                "0.0.0.0 localhost",
                "plain-ads.com",
                "",
            ),
            it,
        )
    }

    @Test
    fun parsesHostsFormats() {
        assertTrue("doubleclick.net" in set)
        assertTrue("tracker.example.org" in set)
        assertTrue("plain-ads.com" in set)
        assertFalse("localhost" in set)
    }

    @Test
    fun matchesSubdomains() {
        assertTrue(AdBlocker.matches(set, "ad.doubleclick.net"))
        assertTrue(AdBlocker.matches(set, "A.B.DoubleClick.net"))
        assertFalse(AdBlocker.matches(set, "example.org"))
        assertFalse(AdBlocker.matches(set, "notdoubleclick.net"))
        assertFalse(AdBlocker.matches(set, null))
    }
}
