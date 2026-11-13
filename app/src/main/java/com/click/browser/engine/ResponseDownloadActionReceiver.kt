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
        // goAsync: the notification refresh suspends (it reads the user's
        // notification toggle first), and without a pending result the
        // framework may reclaim the process before the refresh lands —
        // the same pattern ClickDownloadActionReceiver uses.
        val pending = goAsync()
        scope.launch {
            try {
                when (action) {
                    ClickDownloadNotifications.ACTION_PAUSE -> {
                        ActiveResponseDownloads.pause(registryId)
                        postRefreshed(appContext, registryId)
                    }
                    ClickDownloadNotifications.ACTION_RESUME -> {
                        ActiveResponseDownloads.resume(registryId)
                        postRefreshed(appContext, registryId)
                    }
                    ClickDownloadNotifications.ACTION_CANCEL -> {
                        ActiveResponseDownloads.cancel(registryId)
                        ClickDownloadNotifications.cancelResponseProgress(appContext, registryId)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** Re-posts the progress notification so it reflects the new state. */
    private suspend fun postRefreshed(appContext: Context, registryId: Long) {
        val item = ActiveResponseDownloads.active.value.firstOrNull { it.id == registryId } ?: return
        ClickDownloadNotifications.showResponseProgress(appContext, item)
    }

    companion object {
        const val EXTRA_RESPONSE_ID = "com.click.browser.RESPONSE_DOWNLOAD_ID"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
