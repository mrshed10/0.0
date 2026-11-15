package com.athar.app.feature.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdhkarTest {
    @Test
    fun defaultsHaveUniqueIdsAndCounts() {
        val d = AdhkarLists.defaults()
        val ids = (d.morning + d.evening).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(d.morning.isNotEmpty() && d.evening.isNotEmpty())
        assertTrue((d.morning + d.evening).all { it.count >= 1 })
    }

    @Test
    fun jsonRoundTripWithEdits() {
        val d = AdhkarLists.defaults()
        val edited = d.replace(false, d.morning.drop(1) + UserDhikr(d.nextId, "سبحان الله العظيم", 33))
        assertEquals(edited, AdhkarJson.decode(AdhkarJson.encode(edited)))
    }

    @Test
    fun nextIdIsAboveAll() {
        val d = AdhkarLists.defaults()
        assertTrue((d.morning + d.evening).all { it.id < d.nextId })
    }
}
