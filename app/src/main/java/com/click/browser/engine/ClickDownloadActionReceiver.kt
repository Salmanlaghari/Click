package com.click.browser.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Pause / Resume / Cancel buttons on Click's download notifications.
 * Declared non-exported in the manifest; only our own PendingIntents (with
 * the record id + owning profile) can reach it.
 */
class ClickDownloadActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra(EXTRA_RECORD_ID)?.takeIf { it.isNotBlank() } ?: return
        val mode = runCatching {
            BrowserMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return
        val action = intent.action ?: return
        val pending = goAsync()
        scope.launch {
            try {
                val appContext = context.applicationContext
                when (action) {
                    ClickDownloadNotifications.ACTION_PAUSE ->
                        ClickDownloadEngine.pause(appContext, mode, recordId)
                    ClickDownloadNotifications.ACTION_RESUME ->
                        ClickDownloadEngine.resume(appContext, mode, recordId)
                    ClickDownloadNotifications.ACTION_CANCEL ->
                        ClickDownloadEngine.cancel(appContext, mode, recordId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_RECORD_ID = "com.click.browser.DOWNLOAD_RECORD_ID"
        const val EXTRA_MODE = "com.click.browser.DOWNLOAD_MODE"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
