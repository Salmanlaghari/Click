package com.click.browser.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pause / Resume / Cancel buttons on the progress notification of a
 * response-body download. Declared non-exported in the manifest; only our
 * own PendingIntents (carrying the registry id) can reach it. The actions
 * drive the live stream's gate in [ActiveResponseDownloads] — the same
 * control entry points the Downloads screen buttons use.
 */
class ResponseDownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val registryId = intent.getLongExtra(EXTRA_RESPONSE_ID, -1L)
        if (registryId < 0L) return
        val action = intent.action ?: return
        val appContext = context.applicationContext
        when (action) {
            ClickDownloadNotifications.ACTION_PAUSE -> {
                ActiveResponseDownloads.pause(registryId)
                refreshNotification(appContext, registryId)
            }
            ClickDownloadNotifications.ACTION_RESUME -> {
                ActiveResponseDownloads.resume(registryId)
                refreshNotification(appContext, registryId)
            }
            ClickDownloadNotifications.ACTION_CANCEL -> {
                ActiveResponseDownloads.cancel(registryId)
                ClickDownloadNotifications.cancelResponseProgress(appContext, registryId)
            }
        }
    }

    /** Re-posts the progress notification so it reflects the new state. */
    private fun refreshNotification(appContext: Context, registryId: Long) {
        val item = ActiveResponseDownloads.active.value.firstOrNull { it.id == registryId } ?: return
        scope.launch { ClickDownloadNotifications.showResponseProgress(appContext, item) }
    }

    companion object {
        const val EXTRA_RESPONSE_ID = "com.click.browser.RESPONSE_DOWNLOAD_ID"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
