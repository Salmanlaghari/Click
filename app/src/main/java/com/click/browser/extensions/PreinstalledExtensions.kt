package com.click.browser.extensions

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * One extension that ships inside the APK and is installed on first run, so
 * it is already there — with its icon — when the user opens the extensions
 * panel. [folderName] is the folder under `assets/preinstalled_extensions/`
 * holding the *extracted* official XPI (GeckoView's built-in mechanism,
 * [WebExtensionController.ensureBuiltIn], installs from a folder, not from
 * an .xpi file). [iconAssetPath] is the extension's own PNG icon exactly as
 * declared in its manifest (the `icons` entry, or the browser action's
 * `default_icon` when the manifest icon is an SVG, which Android cannot
 * decode), relative to the APK assets root.
 */
internal data class PreinstalledExtension(
    val id: String,
    val folderName: String,
    val displayName: String,
    val version: String,
    val iconAssetPath: String,
    /** Official AMO listing the pinned package came from (provenance). */
    val amoListingUrl: String,
    /** Versioned official AMO package URL the bundle was extracted from. */
    val xpiDownloadUrl: String,
    /** SHA-256 of that official XPI, as published by the AMO API. */
    val xpiSha256: String
) {
    val location: String
        get() = "resource://android/assets/preinstalled_extensions/$folderName/"
}

/**
 * Extensions pre-installed with Click (Prince's order, 2026-11-21: the
 * essential ones must already be installed, with their icons visible in the
 * extensions panel, ready to use).
 *
 * Every entry is the unmodified, Mozilla-signed package from the official
 * Mozilla Add-ons listing, pinned to an exact version and verified against
 * the SHA-256 the AMO API publishes for that file (see [PreinstalledExtension]
 * fields; `PreinstalledExtensionsTest` re-checks the bundled manifests
 * against this registry on every build). The packages were verified before
 * bundling to (a) declare Android compatibility on AMO, (b) request no
 * capability Click's add-on policy treats as unsupported
 * ([ExtensionInstallPolicy]), and (c) be part of the add-ons that run on
 * Firefox for Android's GeckoView — uBlock Origin's action popup is even
 * the Fenix-specific `popup-fenix.html`.
 *
 * Provenance note: these are installed through GeckoView's built-in
 * mechanism from the APK itself, so their trust anchor is this pinned,
 * hash-verified bundle — not a live AMO install. That is why the panel and
 * the action router judge them with [preinstalledPolicyBlockers]
 * (capability checks only) instead of the AMO-install trust check in
 * [extensionPolicyBlockers].
 */
internal object PreinstalledExtensions {
    private const val TAG = "PreinstalledExt"
    private const val PREFERENCES = "preinstalled_extensions"
    private const val KEY_FIRST_ENABLE_DONE = "first_enable_done"

    val entries: List<PreinstalledExtension> = listOf(
        PreinstalledExtension(
            id = "uBlock0@raymondhill.net",
            folderName = "ublock_origin",
            displayName = "uBlock Origin",
            version = "1.76.6",
            // Manifest "icons" are SVG; this is the browser action's PNG default_icon.
            iconAssetPath = "preinstalled_extensions/ublock_origin/img/icon_64.png",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/ublock-origin/",
            xpiDownloadUrl = "https://addons.mozilla.org/firefox/downloads/file/5282683/ublock_origin-1.76.6.xpi",
            xpiSha256 = "debffadf7d5117bd2d4e9218e27bfdf3112544fd2e87f14882d274a11dc95375"
        ),
        PreinstalledExtension(
            id = "addon@darkreader.org",
            folderName = "dark_reader",
            displayName = "Dark Reader",
            version = "4.9.135",
            iconAssetPath = "preinstalled_extensions/dark_reader/icons/dr_128.png",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/darkreader/",
            xpiDownloadUrl = "https://addons.mozilla.org/firefox/downloads/file/5216111/darkreader-4.9.135.xpi",
            xpiSha256 = "bfed4591d6cc69857f5ed33a2d3cbfbc5811d5b92c4323d0735d2f1757e2dce4"
        ),
        PreinstalledExtension(
            id = "jid1-MnnxcxisBPnSXQ@jetpack",
            folderName = "privacy_badger",
            displayName = "Privacy Badger",
            version = "2026.9.15",
            iconAssetPath = "preinstalled_extensions/privacy_badger/icons/badger-128.png",
            amoListingUrl = "https://addons.mozilla.org/en-US/android/addon/privacy-badger17/",
            xpiDownloadUrl = "https://addons.mozilla.org/firefox/downloads/file/5032646/privacy_badger17-2026.9.15.xpi",
            xpiSha256 = "97cd8911e3486da34a532a7c7f2806cd6d0cfa117a1fbe5e8747042a94442d62"
        )
    )

    private val byId: Map<String, PreinstalledExtension> = entries.associateBy { it.id }

    fun isPreinstalled(extensionId: String?): Boolean = extensionId != null && extensionId in byId

    fun entry(extensionId: String?): PreinstalledExtension? = extensionId?.let { byId[it] }

    // --- Install-on-first-run -------------------------------------------------
    // ensureInstalled() coalesces concurrent callers: the app-start call and
    // the extensions panel's refresh can race, and GeckoView must not get
    // duplicate ensureBuiltIn calls for the same extension.
    private val stateLock = Any()
    private var ensureInFlight = false
    private val pendingCallbacks = mutableListOf<() -> Unit>()

    /**
     * Makes sure every bundled extension is installed in the shared runtime
     * (and enabled, on its first install only — a later user disable is a
     * USER-source state that this pass never overrides). An ID the user
     * already installed themselves from the add-ons store is left alone:
     * their own install wins. [onComplete] runs once this pass settles —
     * including any first-install enable — whether individual installs
     * succeeded or not.
     */
    fun ensureInstalled(context: Context, onComplete: () -> Unit = {}) {
        synchronized(stateLock) {
            pendingCallbacks += onComplete
            if (ensureInFlight) return
            ensureInFlight = true
        }
        val appContext = context.applicationContext
        val runtime = try {
            ExtensionBrowserRuntime.get(appContext)
        } catch (error: Throwable) {
            Log.w(TAG, "Gecko runtime unavailable for pre-installed extensions", error)
            finishEnsure()
            return
        }
        val controller = runtime.webExtensionController
        try {
            controller.list().accept(
                { installed ->
                    try {
                        val userInstalledIds = installed.orEmpty()
                            .filterNot { it.isBuiltIn }
                            .mapTo(mutableSetOf()) { it.id }
                        val pending = entries.filter { it.id !in userInstalledIds }
                        if (pending.isEmpty()) {
                            finishEnsure()
                            return@accept
                        }
                        val remaining = AtomicInteger(pending.size)
                        pending.forEach { entry ->
                            val settled = java.util.concurrent.atomic.AtomicBoolean(false)
                            val settle = {
                                if (settled.compareAndSet(false, true) &&
                                    remaining.decrementAndGet() == 0
                                ) {
                                    finishEnsure()
                                }
                            }
                            try {
                                controller.ensureBuiltIn(entry.location, entry.id).accept(
                                    { extension ->
                                        if (extension != null) {
                                            try {
                                                enableOnFirstInstall(
                                                    appContext, controller, entry, extension, settle
                                                )
                                            } catch (error: Throwable) {
                                                Log.w(TAG, "First-install enable failed for ${entry.id}", error)
                                                settle()
                                            }
                                        } else {
                                            Log.w(TAG, "ensureBuiltIn returned no extension for ${entry.id}")
                                            settle()
                                        }
                                    },
                                    { error ->
                                        Log.w(TAG, "Could not install pre-installed extension ${entry.id}", error)
                                        settle()
                                    }
                                )
                            } catch (error: Throwable) {
                                Log.w(TAG, "Could not start install of pre-installed extension ${entry.id}", error)
                                settle()
                            }
                        }
                    } catch (error: Throwable) {
                        Log.w(TAG, "Pre-installed extension pass failed", error)
                        finishEnsure()
                    }
                },
                { error ->
                    Log.w(TAG, "Could not list extensions before pre-install pass", error)
                    finishEnsure()
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Could not start the pre-installed extension pass", error)
            finishEnsure()
        }
    }

    private fun finishEnsure() {
        val callbacks = synchronized(stateLock) {
            ensureInFlight = false
            pendingCallbacks.toList().also { pendingCallbacks.clear() }
        }
        callbacks.forEach { callback ->
            try {
                callback()
            } catch (error: Throwable) {
                Log.w(TAG, "Pre-installed extension completion callback failed", error)
            }
        }
    }

    /**
     * Built-ins come up enabled in GeckoView; if one ever comes up disabled
     * on its very first install, enable it once as the app. The flag is per
     * extension — deliberately NOT per bundled version: once it is set,
     * this code never touches the enable state again, not even when a later
     * app update bundles a newer version of the extension, so the user's
     * own enable/disable choice in the panel is final. [onSettled] runs on
     * every path so the caller's completion tally cannot strand.
     */
    private fun enableOnFirstInstall(
        context: Context,
        controller: WebExtensionController,
        entry: PreinstalledExtension,
        extension: WebExtension,
        onSettled: () -> Unit
    ) {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val key = "$KEY_FIRST_ENABLE_DONE:${entry.id}"
        if (preferences.getBoolean(key, false)) {
            onSettled()
            return
        }
        if (extension.metaData.enabled) {
            preferences.edit().putBoolean(key, true).apply()
            onSettled()
            return
        }
        try {
            controller.enable(extension, WebExtensionController.EnableSource.APP).accept(
                {
                    preferences.edit().putBoolean(key, true).apply()
                    onSettled()
                },
                { error ->
                    Log.w(TAG, "Could not enable pre-installed extension ${entry.id}", error)
                    onSettled()
                }
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Could not enable pre-installed extension ${entry.id}", error)
            onSettled()
        }
    }
}

/**
 * Capability policy for the pre-installed extensions. Unlike
 * [extensionPolicyBlockers] this deliberately skips the "Mozilla-signed
 * official AMO install" provenance check: a built-in's provenance is the
 * pinned, hash-verified bundle inside the APK (see [PreinstalledExtensions]),
 * and its metadata carries no AMO listing/download URL to check. What must
 * still hold — and is checked live from the installed metadata — is that
 * Mozilla has not blocklisted the add-on, that it uses no capability Click
 * cannot support, and that it requests no unreviewable site-access pattern.
 */
internal fun preinstalledPolicyBlockers(extension: WebExtension): List<String> {
    val metadata = extension.metaData
    val currentPermissions = metadata.requiredPermissions.orEmpty().asList() +
        metadata.grantedOptionalPermissions.orEmpty().asList()
    val currentOrigins = metadata.requiredOrigins.orEmpty().asList() +
        metadata.grantedOptionalOrigins.orEmpty().asList()
    return buildList {
        if (metadata.blocklistState != WebExtension.BlocklistStateFlags.NOT_BLOCKED) {
            add("Blocklisted by Mozilla")
        }
        addAll(ExtensionInstallPolicy.unsupportedPermissionNames(currentPermissions).map { "Unsupported capability: $it" })
        addAll(ExtensionInstallPolicy.blockedOriginPatterns(currentOrigins).map { "Unreviewable site access pattern: $it" })
    }.distinct()
}

/** The policy that applies to [extension]: built-in pre-installs are judged
 *  by [preinstalledPolicyBlockers], everything else by [extensionPolicyBlockers]. */
internal fun effectivePolicyBlockers(extension: WebExtension): List<String> =
    if (PreinstalledExtensions.isPreinstalled(extension.id)) {
        preinstalledPolicyBlockers(extension)
    } else {
        extensionPolicyBlockers(extension)
    }

/**
 * True when the extensions panel and the normal-tab action router may treat
 * [extension] as a usable add-on: user-installed add-ons always qualify;
 * built-ins qualify only when they are one of the verified pre-installed
 * extensions (Click's own internal built-ins, e.g. the WebRTC guard, stay
 * hidden and inert in add-on UI).
 */
internal fun canExposeExtensionActions(extension: WebExtension): Boolean =
    !extension.isBuiltIn || PreinstalledExtensions.isPreinstalled(extension.id)
