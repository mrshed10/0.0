package com.deepsearch.hub

import com.deepsearch.hub.search.SearchEngines
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEnginesTest {

    @Test
    fun submitPageContainsFormForEveryEngine() {
        val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
        SearchEngines.all().forEach { e ->
            val html = SearchEngines.buildSubmitPage(e, jpeg, "Uploading")
            assertTrue(e.id, html.contains("enctype=\"multipart/form-data\""))
            assertTrue(e.id, html.contains("name=\"${e.field}\""))
            assertTrue(e.id, html.contains("/9j/")) // بداية JPEG بترميز Base64
        }
    }

    @Test
    fun unknownIdsFallBackToAllEngines() {
        assertTrue(SearchEngines.byIds(listOf("nope")).size == SearchEngines.all().size)
    }
}
