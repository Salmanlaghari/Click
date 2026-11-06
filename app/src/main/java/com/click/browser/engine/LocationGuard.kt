package com.click.browser.engine

/**
 * Click does not request Android device-location permission. Web geolocation
 * requests are therefore denied; no substitute coordinates are ever sent.
 */
object LocationGuard {
    enum class LocationMode {
        BLOCK
    }

    fun websitesWillSee(): String =
        "Click blocks website location requests and does not access device location."
}
