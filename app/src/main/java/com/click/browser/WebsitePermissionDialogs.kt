package com.click.browser

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The seven website device-permission dialogs (consent + Android-permission
 * recovery for microphone, camera, combined media, and location).
 *
 * Extracted from MainActivity's `setContent` lambda: the generated method
 * for that lambda had grown past the JVM 64 KB method limit. The dialogs
 * are unchanged — they read the same handler state and call the same
 * callbacks; only the receiver moved.
 */
@Composable
internal fun WebsitePermissionDialogs(handler: WebsiteDevicePermissionHandler) {
    handler.microphoneConsent?.let { consent ->
        AlertDialog(
            onDismissRequest = consent.onDismiss,
            title = { Text("Allow microphone for ${consent.host}?") },
            text = {
                Text(
                    "Only this secure site can receive microphone audio after you allow it here and Android grants Click's RECORD_AUDIO permission. " +
                        if (consent.privateTab) {
                            "This choice lasts only in this private tab. Website notifications stay blocked."
                        } else {
                            "This choice is saved only in the current browser profile. Website notifications stay blocked."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = consent.onAllow) { Text("Allow microphone") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = consent.onBlock) { Text("Block site") }
                    TextButton(onClick = consent.onDismiss) { Text("Not now") }
                }
            }
        )
    }
    handler.microphoneRecovery?.let { recovery ->
        AlertDialog(
            onDismissRequest = { handler.microphoneRecovery = null },
            title = { Text("Android microphone permission is off") },
            text = {
                Text(
                    "Microphone is allowed for ${recovery.origin} in " +
                        if (recovery.privateTab) {
                            "this private tab, but Android denied Click's RECORD_AUDIO permission. Enable microphone access in Android settings, then return to the site and try again."
                        } else {
                            "this browser profile, but Android denied Click's RECORD_AUDIO permission. Enable microphone access in Android settings, then return to the site and try again."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    handler.microphoneRecovery = null
                    handler.openWebsiteMicrophoneAndroidSettings()
                }) { Text("Open Android settings") }
            },
            dismissButton = {
                TextButton(onClick = { handler.microphoneRecovery = null }) {
                    Text("Not now")
                }
            }
        )
    }
    handler.cameraConsent?.let { consent ->
        AlertDialog(
            onDismissRequest = consent.onDismiss,
            title = { Text("Allow camera for ${consent.host}?") },
            text = {
                Text(
                    "Only this secure site can receive camera video after you allow it here and Android grants Click's CAMERA permission. " +
                        if (consent.privateTab) {
                            "This choice lasts only in this private tab. Website notifications stay blocked."
                        } else {
                            "This choice is saved only in the current browser profile. Website notifications stay blocked."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = consent.onAllow) { Text("Allow camera") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = consent.onBlock) { Text("Block site") }
                    TextButton(onClick = consent.onDismiss) { Text("Not now") }
                }
            }
        )
    }
    handler.cameraRecovery?.let { recovery ->
        AlertDialog(
            onDismissRequest = { handler.cameraRecovery = null },
            title = { Text("Android camera permission is off") },
            text = {
                Text(
                    "Camera is allowed for ${recovery.origin} in " +
                        if (recovery.privateTab) {
                            "this private tab, but Android denied Click's CAMERA permission. Enable camera access in Android settings, then return to the site and try again."
                        } else {
                            "this browser profile, but Android denied Click's CAMERA permission. Enable camera access in Android settings, then return to the site and try again."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    handler.cameraRecovery = null
                    handler.openWebsiteMicrophoneAndroidSettings()
                }) { Text("Open Android settings") }
            },
            dismissButton = {
                TextButton(onClick = { handler.cameraRecovery = null }) {
                    Text("Not now")
                }
            }
        )
    }
    handler.mediaConsent?.let { consent ->
        AlertDialog(
            onDismissRequest = consent.onDismiss,
            title = { Text("Allow camera and microphone for ${consent.host}?") },
            text = {
                Text(
                    "Only this secure site can receive camera video and microphone audio after you allow it here and Android grants Click's CAMERA and RECORD_AUDIO permissions. " +
                        if (consent.privateTab) {
                            "This choice lasts only in this private tab. Website notifications stay blocked."
                        } else {
                            "This choice is saved only in the current browser profile. Website notifications stay blocked."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = consent.onAllow) { Text("Allow both") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = consent.onBlock) { Text("Block site") }
                    TextButton(onClick = consent.onDismiss) { Text("Not now") }
                }
            }
        )
    }
    handler.locationConsent?.let { consent ->
        AlertDialog(
            onDismissRequest = consent.onDismiss,
            title = { Text("Allow location for ${consent.host}?") },
            text = {
                Text(
                    "Only this secure site can receive your device location after you allow it here and Android grants Click's location permission. Click never sends substitute coordinates. " +
                        if (consent.privateTab) {
                            "This choice lasts only in this private tab."
                        } else {
                            "This choice is saved only in the current browser profile."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = consent.onAllow) { Text("Allow location") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = consent.onBlock) { Text("Block site") }
                    TextButton(onClick = consent.onDismiss) { Text("Not now") }
                }
            }
        )
    }
    handler.locationRecovery?.let { recovery ->
        AlertDialog(
            onDismissRequest = { handler.locationRecovery = null },
            title = { Text("Android location permission is off") },
            text = {
                Text(
                    "Location is allowed for ${recovery.origin} in " +
                        if (recovery.privateTab) {
                            "this private tab, but Android denied Click's location permission. Enable location access in Android settings, then return to the site and try again."
                        } else {
                            "this browser profile, but Android denied Click's location permission. Enable location access in Android settings, then return to the site and try again."
                        }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    handler.locationRecovery = null
                    handler.openWebsiteMicrophoneAndroidSettings()
                }) { Text("Open Android settings") }
            },
            dismissButton = {
                TextButton(onClick = { handler.locationRecovery = null }) {
                    Text("Not now")
                }
            }
        )
    }
}
