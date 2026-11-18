package com.click.browser

import android.Manifest
import android.net.Uri
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.click.browser.data.BrowserRepository
import com.click.browser.engine.PERMISSION_AUTO_REMOVE_WINDOW_MS
import com.click.browser.engine.WebsitePermissionGlobalDefault
import com.click.browser.engine.canonicalWebsiteMicrophoneOrigin
import com.click.browser.engine.permissionOriginsToRevoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

internal data class WebsiteMicrophoneConsentUiState(
    val requestId: String,
    val tabId: String,
    val host: String,
    val privateTab: Boolean,
    val onAllow: () -> Unit,
    val onBlock: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class WebsiteMicrophoneRecoveryUiState(
    val origin: String,
    val tabId: String,
    val privateTab: Boolean
)

internal data class PendingWebsiteMicrophoneAndroidResult(
    val requestId: String,
    val tabId: String,
    val onResult: (Boolean) -> Unit,
    val onCancel: () -> Unit
)

internal data class WebsiteCameraConsentUiState(
    val requestId: String,
    val tabId: String,
    val host: String,
    val privateTab: Boolean,
    val onAllow: () -> Unit,
    val onBlock: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class WebsiteCameraRecoveryUiState(
    val origin: String,
    val tabId: String,
    val privateTab: Boolean
)

internal data class PendingWebsiteCameraAndroidResult(
    val requestId: String,
    val tabId: String,
    val onResult: (Boolean) -> Unit,
    val onCancel: () -> Unit
)

/** Combined camera + microphone request: one dialog decides both. */
internal data class WebsiteMediaConsentUiState(
    val requestId: String,
    val tabId: String,
    val host: String,
    val privateTab: Boolean,
    val onAllow: () -> Unit,
    val onBlock: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class PendingWebsiteMediaAndroidResult(
    val requestId: String,
    val tabId: String,
    val onResult: (Map<String, Boolean>) -> Unit,
    val onCancel: () -> Unit
)

internal data class WebsiteLocationConsentUiState(
    val requestId: String,
    val tabId: String,
    val host: String,
    val privateTab: Boolean,
    val onAllow: () -> Unit,
    val onBlock: () -> Unit,
    val onDismiss: () -> Unit
)

internal data class WebsiteLocationRecoveryUiState(
    val origin: String,
    val tabId: String,
    val privateTab: Boolean
)

internal data class PendingWebsiteLocationAndroidResult(
    val requestId: String,
    val tabId: String,
    val onResult: (Boolean) -> Unit,
    val onCancel: () -> Unit
)

/**
 * Owns every website device-permission flow (microphone, camera, combined
 * camera+microphone, location) plus the "Automatically remove permissions"
 * sweep and the tab-close cleanup for pending requests.
 *
 * This used to live as local functions inside MainActivity's `setContent`
 * lambda; the generated method for that lambda grew past the JVM 64 KB
 * method limit (compile error "Method too large"). Moving the flows here —
 * with the exact same behaviour — keeps the composition lambda small.
 *
 * The activity supplies everything the flows need but cannot own: the tab
 * list, the coroutine scope, the repository, the latest permission states,
 * an Android-permission check, the app-settings opener, and the four
 * permission launchers (assigned after the activity registers them, because
 * the launchers need this handler for their result callbacks and the handler
 * needs the launchers to ask Android — the vars start as no-ops and are
 * assigned before any site can request a permission).
 */
internal class WebsiteDevicePermissionHandler(
    private val repository: BrowserRepository,
    private val scope: CoroutineScope,
    private val tabs: SnapshotStateList<TabItem>,
    private val isPermissionGranted: (String) -> Boolean,
    private val openAppSettings: () -> Unit,
    private val isAutoRemoveEnabled: suspend () -> Boolean,
    private val latestMicrophonePermissions: State<Map<String, Boolean>>,
    private val latestCameraPermissions: State<Map<String, Boolean>>,
    private val latestLocationPermissions: State<Map<String, Boolean>>,
    private val latestMicrophoneDefault: State<WebsitePermissionGlobalDefault>,
    private val latestCameraDefault: State<WebsitePermissionGlobalDefault>,
    private val latestLocationDefault: State<WebsitePermissionGlobalDefault>
) {
    var microphoneConsent by mutableStateOf<WebsiteMicrophoneConsentUiState?>(null)
    var microphoneRecovery by mutableStateOf<WebsiteMicrophoneRecoveryUiState?>(null)
    var pendingMicrophoneAndroidResult by mutableStateOf<PendingWebsiteMicrophoneAndroidResult?>(null)
    var cameraConsent by mutableStateOf<WebsiteCameraConsentUiState?>(null)
    var cameraRecovery by mutableStateOf<WebsiteCameraRecoveryUiState?>(null)
    var pendingCameraAndroidResult by mutableStateOf<PendingWebsiteCameraAndroidResult?>(null)
    var mediaConsent by mutableStateOf<WebsiteMediaConsentUiState?>(null)
    var pendingMediaAndroidResult by mutableStateOf<PendingWebsiteMediaAndroidResult?>(null)
    var locationConsent by mutableStateOf<WebsiteLocationConsentUiState?>(null)
    var locationRecovery by mutableStateOf<WebsiteLocationRecoveryUiState?>(null)
    var pendingLocationAndroidResult by mutableStateOf<PendingWebsiteLocationAndroidResult?>(null)

    /** Assigned by the activity after registering the permission launchers. */
    var launchMicrophonePermission: () -> Unit = {}
    var launchCameraPermission: () -> Unit = {}
    var launchMediaPermissions: () -> Unit = {}
    var launchLocationPermission: () -> Unit = {}

    fun onMicrophonePermissionResult(granted: Boolean) {
        val pendingResult = pendingMicrophoneAndroidResult
        pendingMicrophoneAndroidResult = null
        pendingResult?.onResult?.invoke(granted)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        val pendingResult = pendingCameraAndroidResult
        pendingCameraAndroidResult = null
        pendingResult?.onResult?.invoke(granted)
    }

    fun onMediaPermissionResult(results: Map<String, Boolean>) {
        val pendingResult = pendingMediaAndroidResult
        pendingMediaAndroidResult = null
        pendingResult?.onResult?.invoke(results)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        val pendingResult = pendingLocationAndroidResult
        pendingLocationAndroidResult = null
        pendingResult?.onResult?.invoke(granted)
    }

    fun openWebsiteMicrophoneAndroidSettings() {
        openAppSettings()
    }

    fun handleWebsiteMicrophoneRequest(
        tab: TabItem?,
        requestedOrigin: String,
        grant: () -> Unit,
        reject: () -> Unit,
        requestId: String = UUID.randomUUID().toString()
    ) {
        val origin = canonicalWebsiteMicrophoneOrigin(requestedOrigin)
        if (origin == null || tab == null || !tabs.contains(tab)) {
            reject()
            return
        }
        val privateTab = tab.isIncognito
        val decision = if (privateTab) {
            tab.privateWebsiteMicrophonePermissions[origin]
        } else {
            latestMicrophonePermissions.value[origin]
        }

        fun requestAndroidPermissionAfterConsent() {
            if (!tabs.contains(tab)) {
                reject()
                return
            }
            val alreadyGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
            if (alreadyGranted) {
                runCatching { grant() }.onFailure { runCatching { reject() } }
                return
            }
            if (pendingMicrophoneAndroidResult != null) {
                reject()
                return
            }
            pendingMicrophoneAndroidResult = PendingWebsiteMicrophoneAndroidResult(
                requestId = requestId,
                tabId = tab.id,
                onResult = { granted ->
                    if (!tabs.contains(tab)) {
                        runCatching { reject() }
                    } else if (granted) {
                        runCatching { grant() }.onFailure { runCatching { reject() } }
                    } else {
                        runCatching { reject() }
                        microphoneRecovery = WebsiteMicrophoneRecoveryUiState(
                            origin = origin,
                            tabId = tab.id,
                            privateTab = privateTab
                        )
                    }
                },
                onCancel = { runCatching { reject() } }
            )
            try {
                launchMicrophonePermission()
            } catch (_: Exception) {
                if (pendingMicrophoneAndroidResult?.requestId == requestId) {
                    pendingMicrophoneAndroidResult = null
                }
                runCatching { reject() }
                microphoneRecovery = WebsiteMicrophoneRecoveryUiState(
                    origin = origin,
                    tabId = tab.id,
                    privateTab = privateTab
                )
            }
        }

        fun saveDecision(allowed: Boolean) {
            if (privateTab) {
                tab.privateWebsiteMicrophonePermissions[origin] = allowed
            } else {
                scope.launch {
                    repository.setWebsiteMicrophonePermission(origin, allowed)
                }
            }
        }

        // No saved choice: the profile default decides — "allow" skips
        // the dialog (Android permission still applies), "block" denies
        // silently, "ask" shows the consent dialog. The default itself
        // is never saved as a per-site choice.
        val effectiveDecision = decision ?: when (latestMicrophoneDefault.value) {
            WebsitePermissionGlobalDefault.ALLOW -> true
            WebsitePermissionGlobalDefault.BLOCK -> false
            WebsitePermissionGlobalDefault.ASK -> null
        }

        when (effectiveDecision) {
            false -> reject()
            true -> requestAndroidPermissionAfterConsent()
            null -> {
                if (microphoneConsent != null) {
                    reject()
                    return
                }
                val host = Uri.parse(origin).host ?: origin
                microphoneConsent = WebsiteMicrophoneConsentUiState(
                    requestId = requestId,
                    tabId = tab.id,
                    host = host,
                    privateTab = privateTab,
                    onAllow = {
                        if (microphoneConsent?.requestId == requestId) {
                            microphoneConsent = null
                            if (tabs.contains(tab)) {
                                saveDecision(true)
                                requestAndroidPermissionAfterConsent()
                            } else {
                                runCatching { reject() }
                            }
                        }
                    },
                    onBlock = {
                        if (microphoneConsent?.requestId == requestId) {
                            microphoneConsent = null
                            saveDecision(false)
                        }
                        runCatching { reject() }
                    },
                    onDismiss = {
                        if (microphoneConsent?.requestId == requestId) {
                            microphoneConsent = null
                        }
                        runCatching { reject() }
                    }
                )
            }
        }
    }

    fun cancelWebsiteMicrophoneRequest(requestId: String) {
        if (microphoneConsent?.requestId == requestId) {
            microphoneConsent = null
        }
        val pending = pendingMicrophoneAndroidResult
        if (pending?.requestId == requestId) {
            pendingMicrophoneAndroidResult = null
        }
    }

    // Website camera consent flow: a direct mirror of the microphone
    // flow above, backed by the camera decision store and the Android
    // CAMERA permission.
    fun handleWebsiteCameraRequest(
        tab: TabItem?,
        requestedOrigin: String,
        grant: () -> Unit,
        reject: () -> Unit,
        requestId: String = UUID.randomUUID().toString()
    ) {
        val origin = canonicalWebsiteMicrophoneOrigin(requestedOrigin)
        if (origin == null || tab == null || !tabs.contains(tab)) {
            reject()
            return
        }
        val privateTab = tab.isIncognito
        val decision = if (privateTab) {
            tab.privateWebsiteCameraPermissions[origin]
        } else {
            latestCameraPermissions.value[origin]
        }

        fun requestAndroidPermissionAfterConsent() {
            if (!tabs.contains(tab)) {
                reject()
                return
            }
            val alreadyGranted = isPermissionGranted(Manifest.permission.CAMERA)
            if (alreadyGranted) {
                runCatching { grant() }.onFailure { runCatching { reject() } }
                return
            }
            if (pendingCameraAndroidResult != null) {
                reject()
                return
            }
            pendingCameraAndroidResult = PendingWebsiteCameraAndroidResult(
                requestId = requestId,
                tabId = tab.id,
                onResult = { granted ->
                    if (!tabs.contains(tab)) {
                        runCatching { reject() }
                    } else if (granted) {
                        runCatching { grant() }.onFailure { runCatching { reject() } }
                    } else {
                        runCatching { reject() }
                        cameraRecovery = WebsiteCameraRecoveryUiState(
                            origin = origin,
                            tabId = tab.id,
                            privateTab = privateTab
                        )
                    }
                },
                onCancel = { runCatching { reject() } }
            )
            try {
                launchCameraPermission()
            } catch (_: Exception) {
                if (pendingCameraAndroidResult?.requestId == requestId) {
                    pendingCameraAndroidResult = null
                }
                runCatching { reject() }
                cameraRecovery = WebsiteCameraRecoveryUiState(
                    origin = origin,
                    tabId = tab.id,
                    privateTab = privateTab
                )
            }
        }

        fun saveDecision(allowed: Boolean) {
            if (privateTab) {
                tab.privateWebsiteCameraPermissions[origin] = allowed
            } else {
                scope.launch {
                    repository.setWebsiteCameraPermission(origin, allowed)
                }
            }
        }

        // Same default handling as the microphone flow: the profile
        // default applies without being saved as a per-site choice.
        val effectiveDecision = decision ?: when (latestCameraDefault.value) {
            WebsitePermissionGlobalDefault.ALLOW -> true
            WebsitePermissionGlobalDefault.BLOCK -> false
            WebsitePermissionGlobalDefault.ASK -> null
        }

        when (effectiveDecision) {
            false -> reject()
            true -> requestAndroidPermissionAfterConsent()
            null -> {
                if (cameraConsent != null) {
                    reject()
                    return
                }
                val host = Uri.parse(origin).host ?: origin
                cameraConsent = WebsiteCameraConsentUiState(
                    requestId = requestId,
                    tabId = tab.id,
                    host = host,
                    privateTab = privateTab,
                    onAllow = {
                        if (cameraConsent?.requestId == requestId) {
                            cameraConsent = null
                            if (tabs.contains(tab)) {
                                saveDecision(true)
                                requestAndroidPermissionAfterConsent()
                            } else {
                                runCatching { reject() }
                            }
                        }
                    },
                    onBlock = {
                        if (cameraConsent?.requestId == requestId) {
                            cameraConsent = null
                            saveDecision(false)
                        }
                        runCatching { reject() }
                    },
                    onDismiss = {
                        if (cameraConsent?.requestId == requestId) {
                            cameraConsent = null
                        }
                        runCatching { reject() }
                    }
                )
            }
        }
    }

    fun cancelWebsiteCameraRequest(requestId: String) {
        if (cameraConsent?.requestId == requestId) {
            cameraConsent = null
        }
        val pending = pendingCameraAndroidResult
        if (pending?.requestId == requestId) {
            pendingCameraAndroidResult = null
        }
    }

    // Combined camera + microphone request (getUserMedia with both):
    // a saved/defaulted block on either capability denies the whole
    // request; both allowed goes straight to the Android permissions;
    // anything undecided gets ONE combined consent dialog that saves
    // both decisions, after which only the Android-granted subset is
    // granted to the site.
    fun handleWebsiteMediaRequest(
        tab: TabItem?,
        requestedOrigin: String,
        grant: (Array<String>) -> Unit,
        reject: () -> Unit,
        requestId: String = UUID.randomUUID().toString()
    ) {
        val origin = canonicalWebsiteMicrophoneOrigin(requestedOrigin)
        if (origin == null || tab == null || !tabs.contains(tab)) {
            reject()
            return
        }
        val privateTab = tab.isIncognito

        fun savedDecision(camera: Boolean): Boolean? = if (privateTab) {
            if (camera) tab.privateWebsiteCameraPermissions[origin]
            else tab.privateWebsiteMicrophonePermissions[origin]
        } else {
            if (camera) latestCameraPermissions.value[origin]
            else latestMicrophonePermissions.value[origin]
        }

        fun effective(
            decision: Boolean?,
            globalDefault: WebsitePermissionGlobalDefault
        ): Boolean? = decision ?: when (globalDefault) {
            WebsitePermissionGlobalDefault.ALLOW -> true
            WebsitePermissionGlobalDefault.BLOCK -> false
            WebsitePermissionGlobalDefault.ASK -> null
        }

        val cameraEffective = effective(savedDecision(true), latestCameraDefault.value)
        val microphoneEffective =
            effective(savedDecision(false), latestMicrophoneDefault.value)
        if (cameraEffective == false || microphoneEffective == false) {
            reject()
            return
        }

        fun saveDecision(camera: Boolean, allowed: Boolean) {
            if (privateTab) {
                if (camera) tab.privateWebsiteCameraPermissions[origin] = allowed
                else tab.privateWebsiteMicrophonePermissions[origin] = allowed
            } else {
                scope.launch {
                    if (camera) repository.setWebsiteCameraPermission(origin, allowed)
                    else repository.setWebsiteMicrophonePermission(origin, allowed)
                }
            }
        }

        fun grantAllowedSubset(cameraGranted: Boolean, microphoneGranted: Boolean) {
            val grantedResources = mutableListOf<String>()
            if (cameraGranted) {
                grantedResources += PermissionRequest.RESOURCE_VIDEO_CAPTURE
            }
            if (microphoneGranted) {
                grantedResources += PermissionRequest.RESOURCE_AUDIO_CAPTURE
            }
            if (grantedResources.isEmpty()) {
                runCatching { reject() }
                if (!cameraGranted) {
                    cameraRecovery = WebsiteCameraRecoveryUiState(
                        origin = origin,
                        tabId = tab.id,
                        privateTab = privateTab
                    )
                } else {
                    microphoneRecovery = WebsiteMicrophoneRecoveryUiState(
                        origin = origin,
                        tabId = tab.id,
                        privateTab = privateTab
                    )
                }
            } else {
                runCatching { grant(grantedResources.toTypedArray()) }
                    .onFailure { runCatching { reject() } }
            }
        }

        fun requestAndroidPermissionsAfterConsent() {
            if (!tabs.contains(tab)) {
                reject()
                return
            }
            val cameraGranted = isPermissionGranted(Manifest.permission.CAMERA)
            val microphoneGranted = isPermissionGranted(Manifest.permission.RECORD_AUDIO)
            if (cameraGranted && microphoneGranted) {
                grantAllowedSubset(cameraGranted = true, microphoneGranted = true)
                return
            }
            if (pendingMediaAndroidResult != null) {
                reject()
                return
            }
            pendingMediaAndroidResult = PendingWebsiteMediaAndroidResult(
                requestId = requestId,
                tabId = tab.id,
                onResult = { results ->
                    if (!tabs.contains(tab)) {
                        runCatching { reject() }
                    } else {
                        grantAllowedSubset(
                            cameraGranted = results[Manifest.permission.CAMERA]
                                ?: isPermissionGranted(Manifest.permission.CAMERA),
                            microphoneGranted = results[Manifest.permission.RECORD_AUDIO]
                                ?: isPermissionGranted(Manifest.permission.RECORD_AUDIO)
                        )
                    }
                },
                onCancel = { runCatching { reject() } }
            )
            try {
                launchMediaPermissions()
            } catch (_: Exception) {
                if (pendingMediaAndroidResult?.requestId == requestId) {
                    pendingMediaAndroidResult = null
                }
                runCatching { reject() }
                cameraRecovery = WebsiteCameraRecoveryUiState(
                    origin = origin,
                    tabId = tab.id,
                    privateTab = privateTab
                )
            }
        }

        if (cameraEffective == true && microphoneEffective == true) {
            requestAndroidPermissionsAfterConsent()
            return
        }
        if (mediaConsent != null) {
            reject()
            return
        }
        val host = Uri.parse(origin).host ?: origin
        mediaConsent = WebsiteMediaConsentUiState(
            requestId = requestId,
            tabId = tab.id,
            host = host,
            privateTab = privateTab,
            onAllow = {
                if (mediaConsent?.requestId == requestId) {
                    mediaConsent = null
                    if (tabs.contains(tab)) {
                        if (savedDecision(true) == null) saveDecision(true, true)
                        if (savedDecision(false) == null) saveDecision(false, true)
                        requestAndroidPermissionsAfterConsent()
                    } else {
                        runCatching { reject() }
                    }
                }
            },
            onBlock = {
                if (mediaConsent?.requestId == requestId) {
                    mediaConsent = null
                    if (savedDecision(true) == null) saveDecision(true, false)
                    if (savedDecision(false) == null) saveDecision(false, false)
                }
                runCatching { reject() }
            },
            onDismiss = {
                if (mediaConsent?.requestId == requestId) {
                    mediaConsent = null
                }
                runCatching { reject() }
            }
        )
    }

    fun cancelWebsiteMediaRequest(requestId: String) {
        if (mediaConsent?.requestId == requestId) {
            mediaConsent = null
        }
        val pending = pendingMediaAndroidResult
        if (pending?.requestId == requestId) {
            pendingMediaAndroidResult = null
        }
    }

    // Website location flow: the GeolocationPermissions callback is
    // held by exactly one state holder at a time (consent dialog or
    // pending Android result) and invoked exactly once on every path,
    // always with retain = false.
    fun handleWebsiteLocationRequest(
        tab: TabItem?,
        rawOrigin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (callback == null) return
        val origin = rawOrigin?.let(::canonicalWebsiteMicrophoneOrigin)
        if (origin == null || tab == null || !tabs.contains(tab)) {
            callback.invoke(rawOrigin, false, false)
            return
        }
        val requestId = UUID.randomUUID().toString()
        val privateTab = tab.isIncognito
        val decision = if (privateTab) {
            tab.privateWebsiteLocationPermissions[origin]
        } else {
            latestLocationPermissions.value[origin]
        }
        val effectiveDecision = decision ?: when (latestLocationDefault.value) {
            WebsitePermissionGlobalDefault.ALLOW -> true
            WebsitePermissionGlobalDefault.BLOCK -> false
            WebsitePermissionGlobalDefault.ASK -> null
        }

        fun saveDecision(allowed: Boolean) {
            if (privateTab) {
                tab.privateWebsiteLocationPermissions[origin] = allowed
            } else {
                scope.launch {
                    repository.setWebsiteLocationPermission(origin, allowed)
                }
            }
        }

        fun requestAndroidPermissionAfterConsent() {
            if (!tabs.contains(tab)) {
                callback.invoke(origin, false, false)
                return
            }
            val alreadyGranted = isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)
            if (alreadyGranted) {
                callback.invoke(origin, true, false)
                return
            }
            if (pendingLocationAndroidResult != null) {
                callback.invoke(origin, false, false)
                return
            }
            pendingLocationAndroidResult = PendingWebsiteLocationAndroidResult(
                requestId = requestId,
                tabId = tab.id,
                onResult = { granted ->
                    if (!tabs.contains(tab)) {
                        callback.invoke(origin, false, false)
                    } else if (granted) {
                        callback.invoke(origin, true, false)
                    } else {
                        callback.invoke(origin, false, false)
                        locationRecovery = WebsiteLocationRecoveryUiState(
                            origin = origin,
                            tabId = tab.id,
                            privateTab = privateTab
                        )
                    }
                },
                onCancel = { callback.invoke(origin, false, false) }
            )
            try {
                launchLocationPermission()
            } catch (_: Exception) {
                if (pendingLocationAndroidResult?.requestId == requestId) {
                    pendingLocationAndroidResult = null
                }
                callback.invoke(origin, false, false)
                locationRecovery = WebsiteLocationRecoveryUiState(
                    origin = origin,
                    tabId = tab.id,
                    privateTab = privateTab
                )
            }
        }

        when (effectiveDecision) {
            false -> callback.invoke(origin, false, false)
            true -> requestAndroidPermissionAfterConsent()
            null -> {
                if (locationConsent != null) {
                    callback.invoke(origin, false, false)
                    return
                }
                val host = Uri.parse(origin).host ?: origin
                locationConsent = WebsiteLocationConsentUiState(
                    requestId = requestId,
                    tabId = tab.id,
                    host = host,
                    privateTab = privateTab,
                    onAllow = {
                        if (locationConsent?.requestId == requestId) {
                            locationConsent = null
                            if (tabs.contains(tab)) {
                                saveDecision(true)
                                requestAndroidPermissionAfterConsent()
                            } else {
                                callback.invoke(origin, false, false)
                            }
                        }
                    },
                    onBlock = {
                        if (locationConsent?.requestId == requestId) {
                            locationConsent = null
                            saveDecision(false)
                        }
                        callback.invoke(origin, false, false)
                    },
                    onDismiss = {
                        if (locationConsent?.requestId == requestId) {
                            locationConsent = null
                        }
                        callback.invoke(origin, false, false)
                    }
                )
            }
        }
    }

    /**
     * "Automatically remove permissions" sweep: once per app launch, drop
     * saved camera / microphone / location choices whose origin has a
     * last-visit stamp older than 30 days. The stamp store is the revoke
     * trigger — stamped when a choice is saved, refreshed on page loads —
     * NOT browsing history, which is capped at 100 entries and can silently
     * lose a recent visit. Recent history only acts as a secondary
     * keep-signal. Fail-safe: the toggle defaults off, a missing stamp
     * never revokes, and any failure revokes nothing.
     *
     * All permission state for the sweep comes from ONE repository snapshot
     * read (a single DataStore snapshot), never from several separate
     * `.first()` calls on cold flows, so the decisions cannot mix snapshots
     * taken at different moments.
     */
    suspend fun runAutoRemoveSweep() {
        try {
            if (!isAutoRemoveEnabled()) return
            val now = System.currentTimeMillis()
            val snapshot = repository.websitePermissionAutoRemoveSnapshot(
                now - PERMISSION_AUTO_REMOVE_WINDOW_MS
            )
            permissionOriginsToRevoke(
                snapshot.cameraDecisions,
                snapshot.lastVisitByOriginMs,
                snapshot.recentHistoryHosts,
                now
            ).forEach { repository.clearWebsiteCameraPermission(it) }
            permissionOriginsToRevoke(
                snapshot.microphoneDecisions,
                snapshot.lastVisitByOriginMs,
                snapshot.recentHistoryHosts,
                now
            ).forEach { repository.clearWebsiteMicrophonePermission(it) }
            permissionOriginsToRevoke(
                snapshot.locationDecisions,
                snapshot.lastVisitByOriginMs,
                snapshot.recentHistoryHosts,
                now
            ).forEach { repository.clearWebsiteLocationPermission(it) }
        } catch (_: Exception) {
            // A failed sweep must never break browsing or revoke blindly.
        }
    }

    /**
     * Tab-close cleanup: when the tab behind a consent dialog, a pending
     * Android result, or a recovery notice is gone, clear that state and
     * invoke the pending callback exactly once (deny), so no request is
     * left hanging.
     */
    fun clearStaleRequests(currentTabs: List<TabItem>) {
        val consent = microphoneConsent
        if (consent != null && currentTabs.none { it.id == consent.tabId }) {
            microphoneConsent = null
            runCatching { consent.onDismiss() }
        }
        val pending = pendingMicrophoneAndroidResult
        if (pending != null && currentTabs.none { it.id == pending.tabId }) {
            pendingMicrophoneAndroidResult = null
            runCatching { pending.onCancel() }
        }
        val recovery = microphoneRecovery
        if (recovery != null && currentTabs.none { it.id == recovery.tabId }) {
            microphoneRecovery = null
        }
        val cameraConsentValue = cameraConsent
        if (cameraConsentValue != null && currentTabs.none { it.id == cameraConsentValue.tabId }) {
            cameraConsent = null
            runCatching { cameraConsentValue.onDismiss() }
        }
        val cameraPending = pendingCameraAndroidResult
        if (cameraPending != null && currentTabs.none { it.id == cameraPending.tabId }) {
            pendingCameraAndroidResult = null
            runCatching { cameraPending.onCancel() }
        }
        val cameraRecoveryValue = cameraRecovery
        if (cameraRecoveryValue != null && currentTabs.none { it.id == cameraRecoveryValue.tabId }) {
            cameraRecovery = null
        }
        val mediaConsentValue = mediaConsent
        if (mediaConsentValue != null && currentTabs.none { it.id == mediaConsentValue.tabId }) {
            mediaConsent = null
            runCatching { mediaConsentValue.onDismiss() }
        }
        val mediaPending = pendingMediaAndroidResult
        if (mediaPending != null && currentTabs.none { it.id == mediaPending.tabId }) {
            pendingMediaAndroidResult = null
            runCatching { mediaPending.onCancel() }
        }
        val locationConsentValue = locationConsent
        if (locationConsentValue != null && currentTabs.none { it.id == locationConsentValue.tabId }) {
            locationConsent = null
            runCatching { locationConsentValue.onDismiss() }
        }
        val locationPending = pendingLocationAndroidResult
        if (locationPending != null && currentTabs.none { it.id == locationPending.tabId }) {
            pendingLocationAndroidResult = null
            runCatching { locationPending.onCancel() }
        }
        val locationRecoveryValue = locationRecovery
        if (locationRecoveryValue != null && currentTabs.none { it.id == locationRecoveryValue.tabId }) {
            locationRecovery = null
        }
    }
}
