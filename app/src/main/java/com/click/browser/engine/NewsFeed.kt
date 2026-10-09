package com.click.browser.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Client-side RSS news feed for the Click home screen (Phase 1 Premium UX).
 *
 * No backend server: feeds are fetched directly from reputable publishers
 * with OkHttp, parsed with XmlPullParser, and cached (memory + disk).
 * Connectivity is verified via ConnectivityManager: genuinely offline ->
 * [NewsStatus.OFFLINE]; online but all feeds failed -> [NewsStatus.ERROR]
 * (never misreported as offline).
 */
enum class NewsCategory(val label: String) {
    ALL("All"),
    NEWS("News"),
    TECH("Tech"),
    AI("AI"),
    SPORTS("Sports")
}

data class NewsArticle(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: Long,
    val thumbnailUrl: String?,
    val category: NewsCategory
)

data class NewsResult(
    val articles: List<NewsArticle>,
    val status: NewsStatus
)

/**
 * News load outcome. OFFLINE means the device genuinely has no internet
 * (verified via ConnectivityManager). ERROR means the device IS online but
 * every feed fetch failed (server errors, timeouts, blocks) — the UI must
 * NOT claim the user is offline in that case.
 */
enum class NewsStatus { OK, OFFLINE, ERROR }

private data class FeedDef(
    val url: String,
    val source: String,
    val category: NewsCategory
)

object NewsFeed {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val feeds = listOf(
        FeedDef("https://feeds.bbci.co.uk/news/rss.xml", "BBC News", NewsCategory.NEWS),
        FeedDef("https://www.dawn.com/feed", "Dawn", NewsCategory.NEWS),
        FeedDef("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News", NewsCategory.NEWS),
        FeedDef("https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Tech", NewsCategory.TECH),
        FeedDef("https://techcrunch.com/feed/", "TechCrunch", NewsCategory.TECH),
        FeedDef("https://news.google.com/rss/search?q=technology&hl=en-US&gl=US&ceid=US:en", "Google Tech", NewsCategory.TECH),
        FeedDef("https://techcrunch.com/category/artificial-intelligence/feed/", "TechCrunch AI", NewsCategory.AI),
        FeedDef("https://news.google.com/rss/search?q=artificial%20intelligence&hl=en-US&gl=US&ceid=US:en", "Google AI", NewsCategory.AI),
        FeedDef("https://feeds.bbci.co.uk/sport/rss.xml", "BBC Sport", NewsCategory.SPORTS),
        FeedDef("https://news.google.com/rss/search?q=sports&hl=en-US&gl=US&ceid=US:en", "Google Sports", NewsCategory.SPORTS)
    )

    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    private val memoryCache = mutableMapOf<NewsCategory, Pair<Long, List<NewsArticle>>>()
    private val cacheLock = Any()

    /** Real connectivity check: any active network (Wi-Fi, mobile data, …) with internet capability. */
    fun hasInternetConnection(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            // If we can't determine, assume online and let the fetch decide —
            // never falsely claim "offline".
            true
        }
    }

    suspend fun getArticles(context: Context, category: NewsCategory): NewsResult =
        withContext(Dispatchers.IO) {
            // 1. Memory cache
            synchronized(cacheLock) {
                val cached = memoryCache[category]
                if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                    return@withContext NewsResult(cached.second, NewsStatus.OK)
                }
            }
            // 2. Disk cache (survives process death)
            val diskCached = readDiskCache(context, category)

            // 3. Genuine connectivity check BEFORE any network attempt.
            if (!hasInternetConnection(context)) {
                val fallback = diskCached?.takeIf {
                    System.currentTimeMillis() - it.first < 24 * 60 * 60 * 1000L
                }?.second.orEmpty()
                return@withContext NewsResult(fallback, NewsStatus.OFFLINE)
            }

            // 4. Network (device is online — a total failure here is ERROR, not OFFLINE)
            val wanted = feeds.filter { category == NewsCategory.ALL || it.category == category }
            val fetched = try {
                coroutineScope {
                    wanted.map { feed ->
                        async {
                            try {
                                fetchFeed(feed)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
            } catch (_: Exception) {
                emptyList()
            }

            return@withContext if (fetched.isNotEmpty()) {
                val sorted = fetched.sortedByDescending { it.publishedAt }.take(20)
                synchronized(cacheLock) { memoryCache[category] = System.currentTimeMillis() to sorted }
                writeDiskCache(context, category, sorted)
                NewsResult(sorted, NewsStatus.OK)
            } else {
                // Online, but every feed failed (server errors, timeouts, blocks):
                // fall back to disk cache if fresh enough (< 24h), marked ERROR.
                val fallback = diskCached?.takeIf {
                    System.currentTimeMillis() - it.first < 24 * 60 * 60 * 1000L
                }?.second.orEmpty()
                NewsResult(fallback, NewsStatus.ERROR)
            }
        }

    /** Browser-like UA: some publishers (Cloudflare-protected) reject unknown
     * bot-style agents such as "ClickBrowser/1.0". */
    internal const val FEED_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private fun fetchFeed(feed: FeedDef): List<NewsArticle> {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", FEED_USER_AGENT)
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string() ?: return emptyList()
            return parseRss(body, feed)
        }
    }

    private fun parseRss(xml: String, feed: FeedDef): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.reader())
            var event = parser.eventType
            var inItem = false
            var title = ""; var link = ""; var pubDate = ""; var thumb: String? = null
            var description = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                val ns = parser.namespace ?: ""
                val name = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            inItem = true
                            title = ""; link = ""; pubDate = ""; thumb = null; description = ""
                        } else if (inItem) {
                            when {
                                name.equals("thumbnail", ignoreCase = true) &&
                                    (ns.contains("media") || parser.getAttributeValue(null, "url") != null) ->
                                    thumb = thumb ?: parser.getAttributeValue(null, "url")?.takeIf { it.isNotBlank() }
                                name.equals("content", ignoreCase = true) && ns.contains("media") ->
                                    thumb = thumb ?: parser.getAttributeValue(null, "url")
                                        ?.takeIf { it.isNotBlank() && it.matches(Regex(".*\\.(jpg|jpeg|png|webp)(\\?.*)?", RegexOption.IGNORE_CASE)) }
                                name.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (type.startsWith("image/")) {
                                        thumb = thumb ?: parser.getAttributeValue(null, "url")?.takeIf { it.isNotBlank() }
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty()
                            when {
                                name.equals("title", ignoreCase = true) -> title += text
                                name.equals("link", ignoreCase = true) ->
                                    if (link.isBlank()) link = text.trim()
                                name.equals("pubDate", ignoreCase = true) || name.equals("published", ignoreCase = true) ||
                                    name.equals("updated", ignoreCase = true) -> if (pubDate.isBlank()) pubDate = text.trim()
                                name.equals("description", ignoreCase = true) || name.equals("summary", ignoreCase = true) ->
                                    description += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if ((name.equals("link", ignoreCase = true)) && inItem && link.isBlank()) {
                            // Atom-style <link href="..."/>
                            link = parser.getAttributeValue(null, "href")?.trim().orEmpty()
                        }
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            inItem = false
                            if (thumb == null) {
                                // Fallback: first <img> in the description HTML.
                                thumb = Regex("<img[^>]+src=[\"']([^\"']+)[\"']")
                                    .find(description)?.groupValues?.getOrNull(1)
                                    ?.takeIf { it.startsWith("http") }
                            }
                            if (title.isNotBlank() && link.isNotBlank()) {
                                articles.add(
                                    NewsArticle(
                                        title = title.trim().replace(Regex("\\s+"), " "),
                                        link = link,
                                        source = feed.source,
                                        publishedAt = parseDate(pubDate),
                                        thumbnailUrl = thumb,
                                        category = feed.category
                                    )
                                )
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Malformed feed: return what we got.
        }
        return articles
    }

    private val dateFormats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
    )

    private fun parseDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        for (f in dateFormats) {
            try {
                return f.parse(raw)?.time ?: continue
            } catch (_: Exception) { /* try next */ }
        }
        return System.currentTimeMillis()
    }

    fun timeAgo(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        val mins = diff / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }

    // ---- Disk cache (raw article list, pipe-separated) ----
    private fun cacheFile(context: Context, category: NewsCategory): File {
        val dir = File(context.cacheDir, "news").apply { mkdirs() }
        return File(dir, "feed_${category.name}.txt")
    }

    private fun readDiskCache(context: Context, category: NewsCategory): Pair<Long, List<NewsArticle>>? {
        return try {
            val file = cacheFile(context, category)
            if (!file.exists()) return null
            val lines = file.readLines()
            if (lines.isEmpty()) return null
            val ts = lines.first().toLongOrNull() ?: return null
            val articles = lines.drop(1).mapNotNull { line ->
                val p = line.split("\u001F")
                if (p.size >= 6) NewsArticle(p[0], p[1], p[2], p[3].toLongOrNull() ?: 0L, p[4].ifBlank { null }, NewsCategory.valueOf(p[5]))
                else null
            }
            ts to articles
        } catch (_: Exception) { null }
    }

    private fun writeDiskCache(context: Context, category: NewsCategory, articles: List<NewsArticle>) {
        try {
            val sb = StringBuilder().append(System.currentTimeMillis()).append('\n')
            for (a in articles) {
                fun esc(s: String) = s.replace("\u001F", " ").replace("\n", " ")
                sb.append(esc(a.title)).append('\u001F')
                    .append(esc(a.link)).append('\u001F')
                    .append(esc(a.source)).append('\u001F')
                    .append(a.publishedAt).append('\u001F')
                    .append(esc(a.thumbnailUrl.orEmpty())).append('\u001F')
                    .append(a.category.name).append('\n')
            }
            cacheFile(context, category).writeText(sb.toString())
        } catch (_: Exception) { /* cache is best-effort */ }
    }
}

/**
 * Tiny image loader for news thumbnails (no Coil dependency):
 * memory LRU + disk cache in cacheDir, OkHttp download on IO.
 */
object NewsImageCache {
    private val memCache = LruCache<String, Bitmap>(40)
    private val lock = Any()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    suspend fun get(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        synchronized(lock) { memCache.get(url) }?.let { return@withContext it }
        val file = diskFile(context, url)
        if (file.exists()) {
            decode(file)?.let {
                synchronized(lock) { memCache.put(url, it) }
                return@withContext it
            }
        }
        try {
            val req = Request.Builder().url(url)
                .header("User-Agent", NewsFeed.FEED_USER_AGENT).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                if (bytes.size > 4 * 1024 * 1024) return@withContext null // sanity cap
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                decode(file)?.let {
                    synchronized(lock) { memCache.put(url, it) }
                    return@withContext it
                }
            }
        } catch (_: Exception) { /* fall through */ }
        null
    }

    private fun diskFile(context: Context, url: String): File {
        val dir = File(context.cacheDir, "news_img").apply { mkdirs() }
        return File(dir, url.hashCode().toString() + ".img")
    }

    private fun decode(file: File): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            var sample = 1
            val target = 480
            while (opts.outWidth / (sample * 2) >= target && opts.outHeight / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }
}
