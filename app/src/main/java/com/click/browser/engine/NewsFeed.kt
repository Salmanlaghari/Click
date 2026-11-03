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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.net.URI
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
    RESEARCH("Research"),
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

private data class FeedFetchResult(
    val articles: List<NewsArticle>,
    val succeeded: Boolean
)

internal data class NewsImageCandidate(
    val url: String,
    val width: Int = 0,
    val height: Int = 0,
    val sourcePriority: Int = 0
) {
    val pixelArea: Long
        get() = if (width > 0 && height > 0) width.toLong() * height.toLong() else 0L
}

internal fun selectBestNewsImageCandidate(candidates: List<NewsImageCandidate>): NewsImageCandidate? =
    candidates.asSequence()
        .filter { it.url.isNotBlank() }
        .maxWithOrNull(compareBy<NewsImageCandidate> { it.pixelArea }.thenBy { it.sourcePriority })

object NewsFeed {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val feeds = listOf(
        FeedDef("https://feeds.bbci.co.uk/news/rss.xml", "BBC News", NewsCategory.NEWS),
        // NOTE: https://www.dawn.com/feed 301-redirects to http:// (cleartext),
        // which Android 9+ blocks. Use the direct HTTPS URL instead.
        FeedDef("https://www.dawn.com/feeds/home", "Dawn", NewsCategory.NEWS),
        FeedDef("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en", "Google News", NewsCategory.NEWS),
        FeedDef("https://feeds.bbci.co.uk/news/technology/rss.xml", "BBC Tech", NewsCategory.TECH),
        FeedDef("https://techcrunch.com/feed/", "TechCrunch", NewsCategory.TECH),
        FeedDef("https://news.google.com/rss/search?q=technology&hl=en-US&gl=US&ceid=US:en", "Google Tech", NewsCategory.TECH),
        FeedDef("https://techcrunch.com/category/artificial-intelligence/feed/", "TechCrunch AI", NewsCategory.AI),
        FeedDef("https://news.google.com/rss/search?q=artificial%20intelligence&hl=en-US&gl=US&ceid=US:en", "Google AI", NewsCategory.AI),
        FeedDef("https://export.arxiv.org/api/query?search_query=cat%3Acs.AI%20OR%20cat%3Acs.LG&start=0&max_results=40&sortBy=submittedDate&sortOrder=descending", "arXiv AI/ML", NewsCategory.RESEARCH),
        FeedDef("https://feeds.bbci.co.uk/sport/rss.xml", "BBC Sport", NewsCategory.SPORTS),
        FeedDef("https://news.google.com/rss/search?q=sports&hl=en-US&gl=US&ceid=US:en", "Google Sports", NewsCategory.SPORTS)
    )

    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    internal const val MAX_RESEARCH_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val ARXIV_CACHE_TTL_MS = 6L * 60 * 60 * 1000
    private const val ARXIV_EMPTY_CACHE_TTL_MS = 15L * 60 * 1000
    private const val ARXIV_MIN_INTERVAL_MS = 3_000L
    private val memoryCache = mutableMapOf<NewsCategory, Pair<Long, List<NewsArticle>>>()
    private val cacheLock = Any()
    private val arxivMutex = Mutex()
    private var arxivCache: Pair<Long, List<NewsArticle>>? = null

    internal fun isFreshResearchArticle(article: NewsArticle, now: Long): Boolean {
        if (article.category != NewsCategory.RESEARCH) return true
        val age = now - article.publishedAt
        return article.publishedAt > 0L && age >= 0L - 24L * 60 * 60 * 1000 && age <= MAX_RESEARCH_AGE_MS
    }

    private fun filterFreshResearchArticles(articles: List<NewsArticle>, now: Long = System.currentTimeMillis()): List<NewsArticle> =
        articles.filter { isFreshResearchArticle(it, now) }

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

    suspend fun invalidateResearchCache(context: Context) = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { memoryCache.remove(NewsCategory.RESEARCH) }
        arxivMutex.withLock {
            arxivCache = null
            if (hasInternetConnection(context)) {
                runCatching { cacheFile(context, NewsCategory.RESEARCH).delete() }
            }
        }
    }

    suspend fun getArticles(context: Context, category: NewsCategory): NewsResult =
        withContext(Dispatchers.IO) {
            // 1. Memory cache
            synchronized(cacheLock) {
                val cached = memoryCache[category]
                if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                    return@withContext NewsResult(filterFreshResearchArticles(cached.second), NewsStatus.OK)
                }
            }
            // 2. Disk cache (survives process death)
            val diskCached = readDiskCache(context, category)

            // 3. Genuine connectivity check BEFORE any network attempt.
            if (!hasInternetConnection(context)) {
                val fallback = diskCached?.takeIf {
                    System.currentTimeMillis() - it.first < 24 * 60 * 60 * 1000L
                }?.second.orEmpty().let(::filterFreshResearchArticles)
                return@withContext NewsResult(fallback, NewsStatus.OFFLINE)
            }

            // 4. Network (device is online — a total failure here is ERROR, not OFFLINE)
            val wanted = feeds.filter { category == NewsCategory.ALL || it.category == category }
            val fetchedRaw = try {
                coroutineScope {
                    wanted.map { feed ->
                        async {
                            try {
                                if (feed.category == NewsCategory.RESEARCH) fetchArxivFeed(context, feed)
                                else fetchFeed(feed)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll().flatten()
                }
            } catch (_: Exception) {
                emptyList()
            }
            val fetched = filterFreshResearchArticles(fetchedRaw)

            return@withContext if (fetched.isNotEmpty()) {
                val sorted = fetched.sortedByDescending { it.publishedAt }.take(20)
                synchronized(cacheLock) { memoryCache[category] = System.currentTimeMillis() to sorted }
                writeDiskCache(context, category, sorted)
                val freshResearch = fetched.asSequence()
                    .filter { it.category == NewsCategory.RESEARCH }
                    .sortedByDescending { it.publishedAt }
                    .take(20)
                    .toList()
                if (freshResearch.isNotEmpty()) writeDiskCache(context, NewsCategory.RESEARCH, freshResearch)
                else if (fetchedRaw.any { it.category == NewsCategory.RESEARCH }) writeDiskCache(context, NewsCategory.RESEARCH, emptyList())
                NewsResult(sorted, NewsStatus.OK)
            } else if (category == NewsCategory.RESEARCH && fetchedRaw.any { it.category == NewsCategory.RESEARCH }) {
                synchronized(cacheLock) { memoryCache[category] = System.currentTimeMillis() to emptyList() }
                writeDiskCache(context, category, emptyList())
                NewsResult(emptyList(), NewsStatus.OK)
            } else {
                // Online, but every feed failed (server errors, timeouts, blocks):
                // fall back to disk cache if fresh enough (< 24h), marked ERROR.
                val fallback = diskCached?.takeIf {
                    System.currentTimeMillis() - it.first < 24 * 60 * 60 * 1000L
                }?.second.orEmpty().let(::filterFreshResearchArticles)
                NewsResult(fallback, NewsStatus.ERROR)
            }
        }

    /** Honest app identifier for feed/image requests; does not impersonate a device or browser. */
    internal val FEED_USER_AGENT =
        "ClickBrowser/${com.click.browser.BuildConfig.VERSION_NAME} (Android)"

    private suspend fun fetchArxivFeed(context: Context, feed: FeedDef): List<NewsArticle> = arxivMutex.withLock {
        val now = System.currentTimeMillis()
        val cached = arxivCache
        if (cached != null) {
            val ttl = if (cached.second.isEmpty()) ARXIV_EMPTY_CACHE_TTL_MS else ARXIV_CACHE_TTL_MS
            if (now - cached.first < ttl) return@withLock cached.second
        }

        val diskCached = readDiskCache(context, NewsCategory.RESEARCH)
        if (diskCached != null) {
            val ttl = if (diskCached.second.isEmpty()) ARXIV_EMPTY_CACHE_TTL_MS else ARXIV_CACHE_TTL_MS
            if (now - diskCached.first < ttl) {
                arxivCache = diskCached
                return@withLock diskCached.second
            }
        }

        val throttleFile = File(File(context.cacheDir, "news").apply { mkdirs() }, "arxiv_last_request_ms.txt")
        val lastRequest = runCatching { throttleFile.readText().trim().toLongOrNull() ?: 0L }.getOrDefault(0L)
        val waitMs = (ARXIV_MIN_INTERVAL_MS - (now - lastRequest)).coerceAtLeast(0L)
        if (waitMs > 0L) delay(waitMs)
        val requestAt = System.currentTimeMillis()
        runCatching { throttleFile.writeText(requestAt.toString()) }

        val fetched = fetchFeedWithStatus(feed)
        if (fetched.succeeded) arxivCache = requestAt to fetched.articles
        fetched.articles
    }

    private fun fetchFeed(feed: FeedDef): List<NewsArticle> = fetchFeedWithStatus(feed).articles

    private fun fetchFeedWithStatus(feed: FeedDef): FeedFetchResult {
        val request = Request.Builder()
            .url(feed.url)
            .header("User-Agent", FEED_USER_AGENT)
            .build()
        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("NewsFeed", "${feed.source}: HTTP ${resp.code}")
                    return FeedFetchResult(emptyList(), succeeded = false)
                }
                val body = resp.body?.string() ?: return FeedFetchResult(emptyList(), succeeded = false)
                val articles = parseRss(body, feed)
                if (articles.isEmpty()) {
                    android.util.Log.w("NewsFeed", "${feed.source}: parsed 0 articles (${body.length} chars)")
                }
                return FeedFetchResult(articles, succeeded = body.isNotBlank())
            }
        } catch (e: Exception) {
            android.util.Log.w("NewsFeed", "${feed.source}: ${e.javaClass.simpleName}: ${e.message}")
            return FeedFetchResult(emptyList(), succeeded = false)
        }
    }

    internal fun parseDate(raw: String): Long {
        val value = raw.trim()
        if (value.isEmpty()) return 0L
        val patterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss z",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            try {
                val format = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                    if (pattern.endsWith("'Z'")) timeZone = TimeZone.getTimeZone("UTC")
                }
                val position = ParsePosition(0)
                val parsed = format.parse(value, position)
                if (parsed != null && position.index == value.length) return parsed.time
            } catch (_: Exception) { /* try the next supported date format */ }
        }
        return 0L
    }

    private fun normalizedImageUrl(raw: String?, baseUrl: String): String? = runCatching {
        val candidate = raw?.trim()?.replace("&amp;", "&")?.takeIf { it.isNotEmpty() } ?: return null
        val uri = URI(baseUrl).resolve(candidate).normalize()
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank() && uri.userInfo == null) {
            uri.toASCIIString()
        } else null
    }.getOrNull()

    private fun parseRss(xml: String, feed: FeedDef): List<NewsArticle> {
        val articles = mutableListOf<NewsArticle>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(xml.reader())
            var event = parser.eventType
            var inItem = false
            var title = ""; var link = ""; var itemId = ""; var pubDate = ""
            var imageCandidates = mutableListOf<NewsImageCandidate>()
            var description = ""
            fun addImageCandidate(raw: String?, priority: Int, width: Int = 0, height: Int = 0) {
                normalizedImageUrl(raw, feed.url)?.let { imageCandidates += NewsImageCandidate(it, width, height, priority) }
            }
            // Track the current open tag: parser.name is null during TEXT events,
            // so we must remember it from the last START_TAG (root cause of the
            // "news never loads" bug — text was silently dropped for every feed).
            var currentTag = ""
            while (event != XmlPullParser.END_DOCUMENT) {
                val ns = parser.namespace ?: ""
                val name = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        currentTag = name
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            inItem = true
                            title = ""; link = ""; itemId = ""; pubDate = ""
                            imageCandidates = mutableListOf(); description = ""
                            currentTag = ""
                        } else if (inItem) {
                            when {
                                name.equals("thumbnail", ignoreCase = true) &&
                                    (ns.contains("media") || parser.getAttributeValue(null, "url") != null) ->
                                    addImageCandidate(
                                        parser.getAttributeValue(null, "url"), 2,
                                        parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
                                        parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                    )
                                name.equals("content", ignoreCase = true) && ns.contains("media") ->
                                    run {
                                        val url = parser.getAttributeValue(null, "url")
                                        val type = parser.getAttributeValue(null, "type").orEmpty()
                                        if (type.startsWith("image/", ignoreCase = true) || isImageUrl(url)) {
                                            addImageCandidate(
                                                url, 3,
                                                parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
                                                parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                            )
                                        }
                                    }
                                name.equals("enclosure", ignoreCase = true) -> {
                                    val type = parser.getAttributeValue(null, "type").orEmpty()
                                    if (type.startsWith("image/")) {
                                        addImageCandidate(
                                            parser.getAttributeValue(null, "url"), 1,
                                            parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0,
                                            parser.getAttributeValue(null, "height")?.toIntOrNull() ?: 0
                                        )
                                    }
                                }
                                name.equals("link", ignoreCase = true) -> {
                                    val href = parser.getAttributeValue(null, "href")
                                    val rel = parser.getAttributeValue(null, "rel").orEmpty()
                                    if (link.isBlank() && !href.isNullOrBlank() && (rel.isBlank() || rel == "alternate")) {
                                        link = href.trim().replaceFirst(
                                            Regex("^http://arxiv\\.org/abs/", RegexOption.IGNORE_CASE),
                                            "https://arxiv.org/abs/"
                                        )
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inItem) {
                            val text = parser.text.orEmpty()
                            when {
                                currentTag.equals("title", ignoreCase = true) -> title += text
                                currentTag.equals("link", ignoreCase = true) ->
                                    if (link.isBlank()) link = text.trim()
                                currentTag.equals("id", ignoreCase = true) -> if (itemId.isBlank()) itemId = text.trim()
                                currentTag.equals("pubDate", ignoreCase = true) || currentTag.equals("published", ignoreCase = true) ||
                                    currentTag.equals("updated", ignoreCase = true) -> if (pubDate.isBlank()) pubDate = text.trim()
                                currentTag.equals("description", ignoreCase = true) || currentTag.equals("summary", ignoreCase = true) ->
                                    description += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("item", ignoreCase = true) || name.equals("entry", ignoreCase = true)) {
                            inItem = false
                            if (link.isBlank()) link = itemId.trim()
                                .replaceFirst(Regex("^http://arxiv\\.org/abs/", RegexOption.IGNORE_CASE), "https://arxiv.org/abs/")
                            val descriptionImage = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                                .find(description)?.groupValues?.getOrNull(1)
                            addImageCandidate(descriptionImage, 0)
                            val thumb = selectBestNewsImageCandidate(imageCandidates)?.url
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
                        currentTag = ""
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // Malformed feed: return what we got.
        }
        return articles
    }

    fun timeAgo(ts: Long): String {
        if (ts <= 0L) return "date unavailable"
        val diff = System.currentTimeMillis() - ts
        val mins = diff / 60_000
        return when {
            mins < 1 -> "just now"
            mins < 60 -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }

    private fun isImageUrl(url: String?): Boolean =
        url?.matches(Regex(".*\\.(jpg|jpeg|png|webp|avif)(\\?.*)?", RegexOption.IGNORE_CASE)) == true

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
    private val memCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount.coerceAtLeast(value.byteCount)
    }
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
                val body = resp.body ?: return@withContext null
                if (body.contentLength() > 8 * 1024 * 1024) return@withContext null // sanity cap
                val bytes = body.bytes()
                if (bytes.size > 8 * 1024 * 1024) return@withContext null
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
            val target = 1600
            while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= target) sample *= 2
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (_: Exception) { null }
    }
}
