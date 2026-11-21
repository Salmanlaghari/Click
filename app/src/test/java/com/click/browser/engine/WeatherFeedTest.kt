package com.click.browser.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the Open-Meteo parsing and WMO weather-code labels behind the home
 * weather card (2026-11-21). The payload below mirrors the real forecast
 * API response shape for the fields WeatherFeed requests.
 */
class WeatherFeedTest {

    private val sampleBody = """
        {
          "latitude": 24.8608,
          "longitude": 67.0011,
          "timezone": "Asia/Karachi",
          "current": {
            "time": "2026-11-21T09:15",
            "temperature_2m": 31.4,
            "weather_code": 2
          },
          "daily": {
            "time": ["2026-11-21"],
            "temperature_2m_max": [34.1],
            "temperature_2m_min": [25.9]
          }
        }
    """.trimIndent()

    @Test
    fun parsesCurrentTemperatureConditionAndDailyRange() {
        val parsed = WeatherFeed.parseWeatherJson(sampleBody, "Karachi", fetchedAt = 123L)
        requireNotNull(parsed)
        assertEquals("Karachi", parsed.cityName)
        assertEquals(31.4, parsed.temperatureC, 0.001)
        assertEquals(2, parsed.conditionCode)
        assertEquals("Partly cloudy", parsed.conditionLabel)
        assertEquals(34.1, parsed.highC!!, 0.001)
        assertEquals(25.9, parsed.lowC!!, 0.001)
        assertEquals(123L, parsed.fetchedAt)
    }

    @Test
    fun missingDailyBlockStillParsesCurrent() {
        val body = """{"current":{"temperature_2m":20.0,"weather_code":0}}"""
        val parsed = WeatherFeed.parseWeatherJson(body, "Karachi", fetchedAt = 1L)
        requireNotNull(parsed)
        assertEquals("Clear sky", parsed.conditionLabel)
        assertNull(parsed.highC)
        assertNull(parsed.lowC)
    }

    @Test
    fun malformedOrIncompletePayloadsReturnNull() {
        assertNull(WeatherFeed.parseWeatherJson("not json", "Karachi", 1L))
        assertNull(WeatherFeed.parseWeatherJson("""{"current":{"weather_code":1}}""", "Karachi", 1L))
        assertNull(WeatherFeed.parseWeatherJson("""{"daily":{}}""", "Karachi", 1L))
    }

    @Test
    fun conditionLabelsCoverTheWmoCodeFamilies() {
        assertEquals("Clear sky", WeatherFeed.conditionLabel(0))
        assertEquals("Overcast", WeatherFeed.conditionLabel(3))
        assertEquals("Foggy", WeatherFeed.conditionLabel(48))
        assertEquals("Drizzle", WeatherFeed.conditionLabel(53))
        assertEquals("Heavy rain", WeatherFeed.conditionLabel(65))
        assertEquals("Snowfall", WeatherFeed.conditionLabel(71))
        assertEquals("Rain showers", WeatherFeed.conditionLabel(81))
        assertEquals("Thunderstorm", WeatherFeed.conditionLabel(95))
        assertEquals("Thunderstorm with hail", WeatherFeed.conditionLabel(99))
        assertEquals("Unknown", WeatherFeed.conditionLabel(12345))
    }

    @Test
    fun defaultLocationIsKarachi() {
        assertEquals(24.8608, WeatherFeed.DEFAULT_LOCATION.latitude, 0.0001)
        assertEquals(67.0011, WeatherFeed.DEFAULT_LOCATION.longitude, 0.0001)
        assertEquals("Karachi", WeatherFeed.DEFAULT_LOCATION.cityName)
    }
}
