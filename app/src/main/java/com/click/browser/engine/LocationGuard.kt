package com.click.browser.engine

/**
 * Website location is a per-site permission in Click: a secure site must ask
 * first, and Click shares the device location only with sites the user has
 * allowed (Site settings → Location). No substitute coordinates are ever sent.
 */
object LocationGuard {
    enum class LocationMode {
        BLOCK
    }

    fun websitesWillSee(): String =
        "Websites must ask first; your location is shared only with sites you allow in Site settings."
}
