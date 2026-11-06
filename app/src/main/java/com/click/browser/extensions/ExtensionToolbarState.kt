package com.click.browser.extensions

import android.content.Context
import org.mozilla.geckoview.WebExtension

/** A toolbar action is exposed only for at least one enabled, policy-approved add-on. */
internal enum class ExtensionToolbarAction {
    HIDDEN,
    OPEN_EXTENSION_MANAGER
}

internal data class ExtensionToolbarCandidate(
    val id: String,
    val enabled: Boolean,
    val policyApproved: Boolean
)

internal object ExtensionToolbarPolicy {
    fun enabledExtensionIds(candidates: Iterable<ExtensionToolbarCandidate>): Set<String> =
        candidates.asSequence()
            .filter { it.id.isNotBlank() && it.enabled && it.policyApproved }
            .map { it.id }
            .toSet()

    fun actionFor(enabledExtensionIds: Iterable<String>): ExtensionToolbarAction =
        if (enabledExtensionIds.any(String::isNotBlank)) {
            ExtensionToolbarAction.OPEN_EXTENSION_MANAGER
        } else {
            ExtensionToolbarAction.HIDDEN
        }
}

/**
 * Caches eligible extension IDs for manager/UI synchronization. This state does
 * not make add-ons executable in WebView; normal-tab actions are routed only
 * through GeckoView sessions.
 */
internal object ExtensionToolbarState {
    private const val PREFERENCES = "extension_toolbar_state"
    private const val KEY_ENABLED_IDS = "enabled_supported_extension_ids"
    private const val KEY_INITIALIZED = "extension_list_verified"

    fun currentAction(context: Context): ExtensionToolbarAction {
        val preferences = preferences(context)
        if (!preferences.getBoolean(KEY_INITIALIZED, false)) return ExtensionToolbarAction.HIDDEN
        return ExtensionToolbarPolicy.actionFor(preferences.getStringSet(KEY_ENABLED_IDS, emptySet()).orEmpty())
    }

    fun isInitialized(context: Context): Boolean =
        preferences(context).getBoolean(KEY_INITIALIZED, false)

    fun update(context: Context, enabledIds: Set<String>) {
        val ids = enabledIds.filter(String::isNotBlank).toSet()
        preferences(context).edit()
            .putStringSet(KEY_ENABLED_IDS, ids)
            .putBoolean(KEY_INITIALIZED, true)
            .apply()
    }

    fun invalidate(context: Context) {
        preferences(context).edit()
            .remove(KEY_ENABLED_IDS)
            .putBoolean(KEY_INITIALIZED, false)
            .apply()
    }

    /** One-time discovery also covers add-ons installed by the previously shipped extension flow. */
    fun synchronizeIfNeeded(context: Context, onUpdated: (ExtensionToolbarAction) -> Unit) {
        val appContext = context.applicationContext
        if (isInitialized(appContext)) {
            onUpdated(currentAction(appContext))
            return
        }

        ExtensionBrowserRuntime.get(appContext).webExtensionController.list().accept(
            { extensions ->
                updateFromExtensions(appContext, extensions.orEmpty())
                onUpdated(currentAction(appContext))
            },
            {
                // Do not surface a stale action when GeckoView could not verify the installed list.
                onUpdated(ExtensionToolbarAction.HIDDEN)
            }
        )
    }

    fun updateFromExtensions(context: Context, extensions: Iterable<WebExtension>) {
        val candidates = extensions.asSequence()
            .filterNot { it.isBuiltIn }
            .map { extension ->
                ExtensionToolbarCandidate(
                    id = extension.id,
                    enabled = extension.metaData.enabled,
                    policyApproved = extensionPolicyBlockers(extension).isEmpty()
                )
            }
            .toList()
        update(context, ExtensionToolbarPolicy.enabledExtensionIds(candidates))
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}
