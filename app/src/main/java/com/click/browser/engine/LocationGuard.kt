package com.click.browser.engine

import android.net.Uri

/**
 * Location privacy guard — lets the user hide their location while surfing.
 *
 * Three modes (global default, overridable per site):
 * - [ASK]: show an in-app dialog whenever a website requests geolocation.
 * - [BLOCK]: websites always get PERMISSION_DENIED (no dialog, no location).
 * - [SPOOF]: websites get fixed spoofed coordinates via a JS override of
 *   `navigator.geolocation` (no dialog, no real location).
 *
 * Honest limits (documented in UI):
 * - This controls the *browser geolocation API* only. IP-based geolocation
 *   still reveals the user's approximate location (needs a VPN to change).
 * - No Android location permission is requested: BLOCK and SPOOF never touch
 *   the device GPS. There is no "real location" mode for this reason.
 */
object LocationGuard {

    /** Stored as lowercase string in DataStore ("ask" | "block" | "spoof"). */
    enum class LocationMode(val key: String) {
        ASK("ask"),
        BLOCK("block"),
        SPOOF("spoof");

        companion object {
            fun fromKey(key: String?): LocationMode =
                values().firstOrNull { it.key == key } ?: ASK
        }
    }

    /** A named spoof target shown in the picker. */
    data class SpoofPreset(val label: String, val lat: Double, val lng: Double)

    /** Built-in presets — major cities across regions. */
    val PRESETS: List<SpoofPreset> = listOf(
        SpoofPreset("New York, USA", 40.7128, -74.0060),
        SpoofPreset("Los Angeles, USA", 34.0522, -118.2437),
        SpoofPreset("London, UK", 51.5074, -0.1278),
        SpoofPreset("Paris, France", 48.8566, 2.3522),
        SpoofPreset("Berlin, Germany", 52.5200, 13.4050),
        SpoofPreset("Dubai, UAE", 25.2048, 55.2708),
        SpoofPreset("Tokyo, Japan", 35.6762, 139.6503),
        SpoofPreset("Singapore", 1.3521, 103.8198),
        SpoofPreset("Sydney, Australia", -33.8688, 151.2093),
        SpoofPreset("Toronto, Canada", 43.6532, -79.3832)
    )

    /** Default spoof target when the user never picked one. */
    val DEFAULT_PRESET: SpoofPreset = PRESETS[0]

    /**
     * JS that replaces `navigator.geolocation` with a stub returning the
     * spoofed coordinates. Injected at page start (before page scripts run)
     * so sites calling getCurrentPosition()/watchPosition() never reach the
     * native prompt. Uses Object.defineProperty with a fallback to direct
     * method assignment for older WebViews.
     */
    fun geolocationSpoofJs(lat: Double, lng: Double): String = """
        (function() {
          var __lat = $lat, __lng = $lng;
          function __pos() {
            return {
              coords: {
                latitude: __lat, longitude: __lng, accuracy: 20,
                altitude: null, altitudeAccuracy: null, heading: null, speed: null
              },
              timestamp: Date.now()
            };
          }
          function __get(success) {
            if (typeof success === 'function') {
              setTimeout(function() { try { success(__pos()); } catch (e) {} }, 0);
            }
          }
          function __watch(success) {
            if (typeof success === 'function') {
              setTimeout(function() { try { success(__pos()); } catch (e) {} }, 0);
            }
            return 1;
          }
          var __stub = { getCurrentPosition: __get, watchPosition: __watch, clearWatch: function() {} };
          try {
            Object.defineProperty(navigator, 'geolocation', {
              value: __stub, configurable: true, writable: true
            });
          } catch (e) {
            try {
              navigator.geolocation.getCurrentPosition = __get;
              navigator.geolocation.watchPosition = __watch;
              navigator.geolocation.clearWatch = function() {};
            } catch (e2) {}
          }
        })();
    """.trimIndent()

    /** Extracts the host from a URL, or null when unparseable. */
    fun hostFromUrl(url: String?): String? = try {
        Uri.parse(url)?.host?.lowercase()
    } catch (_: Exception) {
        null
    }

    /**
     * Human-readable summary of what websites will see, for honest UI labels.
     * e.g. "Denied" or "New York, USA".
     */
    fun websitesWillSee(mode: LocationMode, spoofLabel: String): String = when (mode) {
        LocationMode.ASK -> "Ask every time"
        LocationMode.BLOCK -> "Denied"
        LocationMode.SPOOF -> spoofLabel
    }
}
