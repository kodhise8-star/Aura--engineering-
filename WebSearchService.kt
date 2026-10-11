package com.example.personalassistant

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URLEncoder

/** API-key-free web search using DuckDuckGo's public HTML results page. */
object WebSearchService {
    suspend fun search(query: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val q = URLEncoder.encode(query.take(180), "UTF-8")
            val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=$q")
                .userAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                .timeout(12_000)
                .get()
            val results = doc.select(".result").take(4).mapNotNull { el ->
                val link = el.selectFirst("a.result__a") ?: return@mapNotNull null
                val snippet = el.selectFirst(".result__snippet")?.text().orEmpty()
                val title = link.text().trim()
                val url = link.absUrl("href")
                if (title.isBlank() || url.isBlank()) null else "- $title\n  $snippet\n  $url"
            }
            results.joinToString("\n").ifBlank { "No web results were returned. The search engine may have blocked automated requests." }
        }
    }
}
