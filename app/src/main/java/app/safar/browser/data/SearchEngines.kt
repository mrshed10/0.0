package app.safar.browser.data

data class SearchEngine(val id: String, val name: String, val template: String, val privateByDesign: Boolean)

object SearchEngines {
    val all = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", true),
        SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s", true),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", true),
        SearchEngine("google", "Google", "https://www.google.com/search?q=%s", false),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s", false),
        SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", false),
    )

    fun byId(id: String): SearchEngine = all.firstOrNull { it.id == id } ?: all.first()
}
