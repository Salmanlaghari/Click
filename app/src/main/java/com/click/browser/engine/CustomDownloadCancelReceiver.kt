package com.click.browser.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.click.browser.data.BrowserMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Handles the explicit Cancel action on a custom-download progress notification. */
class CustomDownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordId = intent.getStringExtra(CustomDownloadScheduler.EXTRA_RECORD_ID) ?: return
        val attemptId = intent.getStringExtra(CustomDownloadScheduler.EXTRA_ATTEMPT_ID) ?: return
        val mode = runCatching {
            BrowserMode.valueOf(intent.getStringExtra(CustomDownloadScheduler.EXTRA_MODE).orEmpty())
        }.getOrNull() ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                CustomDownloadScheduler.cancelById(context.applicationContext, mode, recordId, attemptId)
            } catch (_: Exception) {
                // The in-app record remains available if Android has already stopped the backend.
            } finally {
                pending.finish()
            }
        }
    }
}
