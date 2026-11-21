package com.click.browser.engine

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * External app handoff policy (Prince's 1.8.5 device test, 2026-11-21):
 * "Maine TikTok KO sign in Karna Chaha, TikTok app mare pass hey likan
 * Click ne support Nahin Kiya."
 *
 * The bug: a growing number of sign-in flows (TikTok's "continue in app"
 * above all) leave the web at the last step — the page navigates to a
 * custom scheme (`tiktok://`, `snssdk…`, …) or an `intent://` URI that is
 * meant to open the site's installed app. Click's navigation layer only
 * knew two answers for a navigation — "load this URL" or "block" — so
 * every such navigation was denied and the sign-in died on the login
 * screen with nothing but a toast.
 *
 * This policy supplies the missing third answer — "hand it to the app" —
 * as pure, testable classification/parsing. It decides:
 *  - which navigations are ordinary web loads Click renders itself,
 *  - which are Click-internal destinations (its own pages, add-on pages),
 *  - which may be handed to an installed Android app, and
 *  - how an `intent://` URI's fallback URL / target package / scheme are
 *    read.
 *
 * The Android side (intent building, resolution, launching, the
 * confirmation prompt) lives in MainActivity; this file never touches it.
 *
 * Safety shape:
 *  - Web pages and Click's internal pages are NEVER handed off — the
 *    2026-11-10 rule that sign-in/OAuth web pages stay inside the Click
 *    tab is untouched; only non-web destinations that used to be dead
 *    ends can leave.
 *  - Schemes that must never reach another app (`file:`, `javascript:`,
 *    `data:`, …) classify as [NavigationSchemeKind.OTHER], keeping the
 *    old deny behaviour.
 *  - A handoff that follows an active user gesture may launch at once;
 *    one a page triggered on its own must ask first
 *    (see [launchDecision]), so a page cannot fling the user into apps
 *    unasked.
 */

/** Who should handle a main-frame navigation, by URL alone. */
enum class NavigationSchemeKind {
    /** Ordinary web load Click renders itself (http/https, about:blank). */
    WEB,

    /** Click-internal destination (click:// pages, verified add-on pages). */
    INTERNAL,

    /** Non-web destination an installed Android app may handle. */
    EXTERNAL_APP,

    /**
     * Anything else — unparseable, or a scheme that must never be handed
     * off. The existing navigation policy decides (in practice: deny).
     */
    OTHER
}

/** Whether a resolved app handoff may launch immediately or must ask. */
enum class ExternalLaunchDecision {
    /** The user just tapped: launch the app straight away. */
    LAUNCH_NOW,

    /** No user gesture behind this navigation: ask the user first. */
    ASK_FIRST
}

/** Outcome of an external-scheme handoff attempt, consumed by the engines. */
sealed interface ExternalHandoffResult {
    /** The handoff was taken care of (app launched, or prompt shown). */
    object Handled : ExternalHandoffResult

    /** No app could take the link, but the link names a web fallback: load it. */
    data class LoadFallback(val url: String) : ExternalHandoffResult

    /** Nobody could take this destination: keep the previous behaviour. */
    object NotHandled : ExternalHandoffResult
}

object ExternalSchemePolicy {

    /**
     * Schemes a web page must never be able to push into another app (or
     * anywhere else) through the handoff path. They keep the pre-handoff
     * behaviour: the navigation policy denies them.
     */
    private val NEVER_HAND_OFF_SCHEMES = setOf(
        "file", "content", "javascript", "data", "blob",
        "view-source", "chrome", "android-app"
    )

    /** The URL's scheme, lowercased, or null when there is none. */
    fun schemeOf(rawUrl: String): String? =
        runCatching { URI(rawUrl).scheme?.lowercase() }.getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Classify a navigation URL by who should handle it. */
    fun kindOf(rawUrl: String): NavigationSchemeKind {
        val scheme = schemeOf(rawUrl) ?: return NavigationSchemeKind.OTHER
        return when {
            scheme == "http" || scheme == "https" -> NavigationSchemeKind.WEB
            scheme == "about" ->
                if (rawUrl.equals("about:blank", ignoreCase = true)) {
                    NavigationSchemeKind.WEB
                } else {
                    NavigationSchemeKind.OTHER
                }
            scheme == "click" || scheme == "moz-extension" ->
                NavigationSchemeKind.INTERNAL
            scheme in NEVER_HAND_OFF_SCHEMES -> NavigationSchemeKind.OTHER
            else -> NavigationSchemeKind.EXTERNAL_APP
        }
    }

    /**
     * True when a redirect hop to [rawUrl] must pass through exactly as
     * the server sent it — no rewriting, no restarting (the Facebook
     * "Too many redirects" fix). Only ordinary web hops qualify; internal
     * and external destinations keep their own handling mid-chain, and
     * anything else keeps the existing policy.
     */
    fun shouldLetRedirectPassThrough(rawUrl: String): Boolean =
        kindOf(rawUrl) == NavigationSchemeKind.WEB

    /** True for `intent://` URIs (the Android intent-URI format). */
    fun isIntentUri(rawUrl: String): Boolean = schemeOf(rawUrl) == "intent"

    /**
     * The web fallback an `intent://` URI carries in its
     * `S.browser_fallback_url` extra — where the user should land when no
     * app can take the intent. Null for non-intent URIs and when absent.
     */
    fun fallbackUrlOf(rawUrl: String): String? {
        if (!isIntentUri(rawUrl)) return null
        return intentFragmentParams(rawUrl)["S.browser_fallback_url"]
            ?.takeIf { it.isNotBlank() }
    }

    /** The package an `intent://` URI names (`package=`), when present. */
    fun targetPackageOf(rawUrl: String): String? {
        if (!isIntentUri(rawUrl)) return null
        return intentFragmentParams(rawUrl)["package"]?.takeIf { it.isNotBlank() }
    }

    /**
     * The real scheme an `intent://` URI wraps (`scheme=` in its
     * fragment), e.g. `zxing` for `intent://scan/#Intent;scheme=zxing;…`.
     * For plain custom-scheme URIs this is simply the URL's own scheme.
     */
    fun wrappedSchemeOf(rawUrl: String): String? {
        if (!isIntentUri(rawUrl)) return schemeOf(rawUrl)
        return intentFragmentParams(rawUrl)["scheme"]?.lowercase()
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Gesture rule: a navigation the user actively triggered (tap) may
     * open the app immediately; one that arrived without a gesture (a
     * page redirecting on its own) must be confirmed by the user first.
     */
    fun launchDecision(hasUserGesture: Boolean): ExternalLaunchDecision =
        if (hasUserGesture) {
            ExternalLaunchDecision.LAUNCH_NOW
        } else {
            ExternalLaunchDecision.ASK_FIRST
        }

    /**
     * Parameters of an intent URI's `#Intent;…;end` fragment as a map.
     * Values are URL-decoded; undecodable entries are dropped.
     */
    private fun intentFragmentParams(rawUrl: String): Map<String, String> {
        val fragment = runCatching { URI(rawUrl).rawFragment }.getOrNull()
            ?: return emptyMap()
        if (!fragment.startsWith("Intent;")) return emptyMap()
        val result = LinkedHashMap<String, String>()
        for (part in fragment.removePrefix("Intent;").split(';')) {
            if (part == "end") break
            val separator = part.indexOf('=')
            if (separator <= 0) continue
            val value = runCatching {
                URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8.name())
            }.getOrNull() ?: continue
            result[part.substring(0, separator)] = value
        }
        return result
    }
}
