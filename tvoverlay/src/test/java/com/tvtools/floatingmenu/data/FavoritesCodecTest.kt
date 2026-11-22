package com.tvtools.floatingmenu.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoritesCodecTest {

    @Test
    fun roundTripKeepsOrder() {
        val apps = listOf("com.netflix.ninja", "com.google.android.youtube.tv", "org.xbmc.kodi")
        assertEquals(apps, FavoritesCodec.decode(FavoritesCodec.encode(apps)))
    }

    @Test
    fun removesBlanksAndDuplicates() {
        assertEquals(listOf("a.b", "c.d"), FavoritesCodec.decode("a.b|| c.d |a.b|"))
        assertEquals("a.b|c.d", FavoritesCodec.encode(listOf("a.b", " ", "c.d", "a.b")))
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList<String>(), FavoritesCodec.decode(null))
        assertEquals(emptyList<String>(), FavoritesCodec.decode(""))
        assertEquals("", FavoritesCodec.encode(emptyList()))
    }
}
