package com.click.browser.engine

/** Visual identity for the search provider currently used by the address bar. */
data class SearchEngineBrand(
    val monogram: String,
    val shortName: String,
    val foregroundArgb: Int,
    val backgroundArgb: Int
)

object SearchEngineBrands {
    fun forEngine(engine: String): SearchEngineBrand = when (engine.trim().lowercase()) {
        "google" -> SearchEngineBrand("G", "Google", 0xFF4285F4.toInt(), 0xFFEAF2FF.toInt())
        "bing" -> SearchEngineBrand("b", "Bing", 0xFF008373.toInt(), 0xFFE7F7F3.toInt())
        "yahoo" -> SearchEngineBrand("Y!", "Yahoo", 0xFF6001D2.toInt(), 0xFFF3E9FF.toInt())
        "duckduckgo" -> SearchEngineBrand("D", "DuckDuckGo", 0xFFDE5833.toInt(), 0xFFFFEEE7.toInt())
        "yandex" -> SearchEngineBrand("Я", "Yandex", 0xFFFC3F1D.toInt(), 0xFFFFEEE9.toInt())
        "baidu" -> SearchEngineBrand("百", "Baidu", 0xFF2932E1.toInt(), 0xFFECEEFF.toInt())
        "brave search" -> SearchEngineBrand("B", "Brave", 0xFFFB542B.toInt(), 0xFFFFEEE8.toInt())
        "startpage" -> SearchEngineBrand("S", "Startpage", 0xFF1F5AA6.toInt(), 0xFFEAF2FF.toInt())
        "perplexity" -> SearchEngineBrand("P", "Perplexity", 0xFF176B64.toInt(), 0xFFE8F5F2.toInt())
        else -> {
            val clean = engine.trim().ifEmpty { "Search" }
            SearchEngineBrand(
                monogram = clean.first().uppercaseChar().toString(),
                shortName = clean,
                foregroundArgb = 0xFF66533F.toInt(),
                backgroundArgb = 0xFFF2EBDD.toInt()
            )
        }
    }
}
