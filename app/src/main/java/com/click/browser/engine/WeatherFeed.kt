package com.click.browser.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Weather for the Click home dashboards, fetched directly from the
 * Open-Meteo forecast API — no API key, no account, no backend server.
 *
 * Follows the same shape as [NewsFeed]: OkHttp with the shared honest user
 * agent, a memory + disk cache (30-minute freshness), and an honest status —
 * genuinely offline (verified via [NewsFeed.hasInternetConnection]) is
 * [WeatherStatus.OFFLINE]; online but the fetch failed is
 * [WeatherStatus.ERROR]. On either failure a cached reading younger than
 * 24 hours is still returned so the card can show the last known weather
 * instead of going blank.
 */
data class WeatherData(
    val cityName: String,
    val temperatureC: Double,
    val conditionCode: Int,
    val conditionLabel: String,
    val highC: Double?,
    val lowC: Double?,
    val fetchedAt: Long
)

enum class WeatherStatus { OK, OFFLINE, ERROR }

data class WeatherResult(
    val weather: WeatherData?,
    val status: WeatherStatus
)

/** A place to fetch weather for; [cityName] is what the card displays. */
data class WeatherLocation(
    val latitude: Double,
    val longitude: Double,
    val cityName: String
)

object WeatherFeed {

    /** Fallback when no device location is available: Karachi, Pakistan. */
    val DEFAULT_LOCATION = WeatherLocation(24.8608, 67.0011, "Karachi")

    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    private const val FALLBACK_MAX_AGE_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val cacheLock = Any()
    private var memoryCache: Pair<String, WeatherData>? = null

    /**
     * Resolves where to show weather for, WITHOUT ever requesting a
     * permission: the device's last known location is used only when
     * ACCESS_FINE_LOCATION has already been granted; otherwise (or when no
     * last known fix exists) the default city is returned, so the card
     * always has somewhere sensible to show.
     */
    fun resolveLocation(context: Context): WeatherLocation {
        val granted = try {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            false
        }
        if (!granted) return DEFAULT_LOCATION
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return DEFAULT_LOCATION
            var best: android.location.Location? = null
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                val loc = try {
                    lm.getLastKnownLocation(provider)
                } catch (_: Exception) {
                    null
                }
                if (loc != null && (best == null || loc.time > best.time)) best = loc
            }
            val fix = best ?: return DEFAULT_LOCATION
            WeatherLocation(fix.latitude, fix.longitude, "Current location")
        } catch (_: Exception) {
            DEFAULT_LOCATION
        }
    }

    suspend fun getWeather(context: Context, location: WeatherLocation): WeatherResult =
        withContext(Dispatchers.IO) {
            val key = cacheKey(location)
            val now = System.currentTimeMillis()

            // 1. Memory cache
            synchronized(cacheLock) {
                val mem = memoryCache
                if (mem != null && mem.first == key && now - mem.second.fetchedAt < CACHE_TTL_MS) {
                    return@withContext WeatherResult(mem.second, WeatherStatus.OK)
                }
            }

            // 2. Disk cache (survives process death)
            val disk = readDiskCache(context, key)
            if (disk != null && now - disk.fetchedAt < CACHE_TTL_MS) {
                synchronized(cacheLock) { memoryCache = key to disk }
                return@withContext WeatherResult(disk, WeatherStatus.OK)
            }

            // 3. Genuine connectivity check BEFORE any network attempt.
            if (!NewsFeed.hasInternetConnection(context)) {
                val fallback = disk?.takeIf { now - it.fetchedAt < FALLBACK_MAX_AGE_MS }
                return@withContext WeatherResult(fallback, WeatherStatus.OFFLINE)
            }

            // 4. Network (device is online — a failure here is ERROR, not OFFLINE)
            val fetched = fetchWeather(location)
            if (fetched != null) {
                synchronized(cacheLock) { memoryCache = key to fetched.data }
                writeDiskCache(context, key, fetched.data, fetched.rawBody)
                return@withContext WeatherResult(fetched.data, WeatherStatus.OK)
            }
            val fallback = disk?.takeIf { now - it.fetchedAt < FALLBACK_MAX_AGE_MS }
            WeatherResult(fallback, WeatherStatus.ERROR)
        }

    private data class FetchOutcome(val data: WeatherData, val rawBody: String)

    private fun fetchWeather(location: WeatherLocation): FetchOutcome? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${location.latitude}&longitude=${location.longitude}" +
            "&current=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NewsFeed.FEED_USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("WeatherFeed", "HTTP ${resp.code}")
                    return null
                }
                val body = resp.body?.string() ?: return null
                val parsed = parseWeatherJson(body, location.cityName, System.currentTimeMillis())
                    ?: return null
                FetchOutcome(parsed, body)
            }
        } catch (e: Exception) {
            android.util.Log.w("WeatherFeed", "${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    /**
     * Parses an Open-Meteo forecast response. Returns null on any malformed
     * or incomplete payload instead of guessing values.
     */
    internal fun parseWeatherJson(body: String, cityName: String, fetchedAt: Long): WeatherData? {
        return try {
            val root = JSONObject(body)
            val current = root.optJSONObject("current") ?: return null
            if (!current.has("temperature_2m") || !current.has("weather_code")) return null
            val temperature = current.getDouble("temperature_2m")
            val code = current.getInt("weather_code")
            var high: Double? = null
            var low: Double? = null
            val daily = root.optJSONObject("daily")
            if (daily != null) {
                high = runCatching {
                    daily.optJSONArray("temperature_2m_max")
                        ?.takeIf { it.length() > 0 }?.getDouble(0)
                }.getOrNull()
                low = runCatching {
                    daily.optJSONArray("temperature_2m_min")
                        ?.takeIf { it.length() > 0 }?.getDouble(0)
                }.getOrNull()
            }
            WeatherData(
                cityName = cityName,
                temperatureC = temperature,
                conditionCode = code,
                conditionLabel = conditionLabel(code),
                highC = high,
                lowC = low,
                fetchedAt = fetchedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    /** WMO weather-code table (the codes the forecast API returns). */
    internal fun conditionLabel(code: Int): String = when (code) {
        0 -> "Clear sky"
        1 -> "Mostly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61 -> "Light rain"
        63 -> "Rain"
        65 -> "Heavy rain"
        66, 67 -> "Freezing rain"
        71, 73, 75 -> "Snowfall"
        77 -> "Snow grains"
        80 -> "Light showers"
        81 -> "Rain showers"
        82 -> "Heavy showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Unknown"
    }

    private fun cacheKey(location: WeatherLocation): String {
        val lat = (location.latitude * 100).roundToInt()
        val lon = (location.longitude * 100).roundToInt()
        return "${lat}_$lon"
    }

    // ---- Disk cache (raw API payload + metadata, one file per location) ----
    private fun cacheFile(context: Context, key: String): File {
        val dir = File(context.cacheDir, "weather").apply { mkdirs() }
        return File(dir, "weather_$key.json")
    }

    private fun readDiskCache(context: Context, key: String): WeatherData? {
        return try {
            val file = cacheFile(context, key)
            if (!file.exists()) return null
            val stored = JSONObject(file.readText())
            val fetchedAt = stored.optLong("fetchedAt", 0L)
            if (fetchedAt <= 0L) return null
            val cityName = stored.optString("cityName").ifBlank { return null }
            val body = stored.optString("body").ifBlank { return null }
            parseWeatherJson(body, cityName, fetchedAt)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeDiskCache(context: Context, key: String, data: WeatherData, rawBody: String) {
        try {
            val stored = JSONObject()
                .put("fetchedAt", data.fetchedAt)
                .put("cityName", data.cityName)
                .put("body", rawBody)
            cacheFile(context, key).writeText(stored.toString())
        } catch (_: Exception) { /* cache is best-effort */ }
    }
}
