package com.click.browser.engine

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import com.click.browser.MainActivity
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.first
import kotlin.coroutines.resume

/**
 * Google Play in-app updates (Prince: "User ko notification bhi jaye ke
 * update available hai Play Store par — ye mandatory hai").
 *
 * Uses ONLY the official Play Core AppUpdateManager — no version files on a
 * server, no hard-coded "latest version" checks. Behaviour:
 * - On app start (throttled to once per day) Click asks Play whether an
 *   update is available. If yes: a system notification is posted (once per
 *   version) AND the flexible update flow is offered in-app.
 * - Tapping the notification reopens Click with [EXTRA_OPEN_UPDATE], which
 *   starts the flexible flow (Play's own download UI; the user confirms).
 * - Settings → About → "Check for updates" runs the same check manually and
 *   reports the result honestly (including "check unavailable" on devices
 *   without Play services — e.g. sideloaded debug builds report no update).
 */
object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    const val EXTRA_OPEN_UPDATE = "com.click.browser.EXTRA_OPEN_UPDATE"
    const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 0x5501
    const val REQUEST_CODE_FLEXIBLE = 4815

    /** Pure policy: notify at most once per available version, and only
     *  when Play actually reports an update for a newer versionCode. */
    fun shouldNotifyUpdate(
        updateAvailable: Boolean,
        availableVersionCode: Int,
        installedVersionCode: Int,
        lastNotifiedVersionCode: Int
    ): Boolean = updateAvailable &&
        availableVersionCode > installedVersionCode &&
        availableVersionCode != lastNotifiedVersionCode

    /** Pure policy: automatic checks are throttled to once per 24 hours. */
    fun shouldRunAutomaticCheck(lastCheckMs: Long, nowMs: Long): Boolean =
        lastCheckMs <= 0L || nowMs - lastCheckMs >= CHECK_INTERVAL_MS

    private const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

    private fun manager(context: Context): AppUpdateManager =
        AppUpdateManagerFactory.create(context.applicationContext)

    /** Automatic check, called from MainActivity.onCreate. Fire-and-forget. */
    fun checkOnStart(activity: Activity) {
        val appContext = activity.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val prefs = appContext.dataStore.data.first()
                val lastCheck = prefs[AppSettings.UPDATE_LAST_CHECK_MS] ?: 0L
                if (!shouldRunAutomaticCheck(lastCheck, System.currentTimeMillis())) return@launch
                appContext.dataStore.edit { it[AppSettings.UPDATE_LAST_CHECK_MS] = System.currentTimeMillis() }
                val info = awaitUpdateInfo(appContext) ?: return@launch
                val available = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                if (!available) return@launch
                val lastNotified = prefs[AppSettings.UPDATE_NOTIFIED_VERSION] ?: 0
                if (shouldNotifyUpdate(
                        updateAvailable = true,
                        availableVersionCode = info.availableVersionCode(),
                        installedVersionCode = com.click.browser.BuildConfig.VERSION_CODE,
                        lastNotifiedVersionCode = lastNotified
                    )
                ) {
                    appContext.dataStore.edit {
                        it[AppSettings.UPDATE_NOTIFIED_VERSION] = info.availableVersionCode()
                    }
                    showUpdateNotification(appContext)
                }
                // Also complete a previously-downloaded flexible update.
                if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    runCatching { manager(appContext).completeUpdate() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "automatic update check failed", e)
            }
        }
    }

    /** Starts Play's flexible update flow when an update is available. */
    fun startFlexibleFlow(activity: Activity) {
        val appContext = activity.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                val info = awaitUpdateInfo(appContext) ?: run {
                    android.widget.Toast.makeText(
                        activity,
                        "Update check is unavailable on this device.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                when {
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        manager(appContext).completeUpdate()
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        manager(appContext).startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            REQUEST_CODE_FLEXIBLE
                        )
                    }
                    else -> {
                        android.widget.Toast.makeText(
                            activity,
                            "Click is up to date.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "startFlexibleFlow failed", e)
                android.widget.Toast.makeText(
                    activity,
                    "Update check is unavailable on this device.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Manual check for Settings → About. Reports through [onResult]. */
    fun manualCheck(activity: Activity, onResult: (String) -> Unit) {
        val appContext = activity.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                appContext.dataStore.edit {
                    it[AppSettings.UPDATE_LAST_CHECK_MS] = System.currentTimeMillis()
                }
                val info = awaitUpdateInfo(appContext)
                when {
                    info == null -> onResult("Update check is unavailable on this device.")
                    info.installStatus() == InstallStatus.DOWNLOADED -> {
                        manager(appContext).completeUpdate()
                        onResult("Update downloaded — restarting to finish installing.")
                    }
                    info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> {
                        onResult("Update available — opening Google Play update…")
                        manager(appContext).startUpdateFlowForResult(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                            REQUEST_CODE_FLEXIBLE
                        )
                    }
                    else -> onResult("Click is up to date (version ${com.click.browser.BuildConfig.VERSION_NAME}).")
                }
            } catch (e: Exception) {
                Log.w(TAG, "manualCheck failed", e)
                onResult("Update check is unavailable on this device.")
            }
        }
    }

    private suspend fun awaitUpdateInfo(context: Context): AppUpdateInfo? =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            try {
                manager(context).appUpdateInfo
                    .addOnSuccessListener { info ->
                        if (continuation.isActive) continuation.resume(info) { }
                    }
                    .addOnFailureListener { error ->
                        Log.w(TAG, "appUpdateInfo failed", error)
                        if (continuation.isActive) continuation.resume(null) { }
                    }
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resume(null) { }
            }
        }

    private fun showUpdateNotification(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(
                            CHANNEL_ID,
                            "App updates",
                            NotificationManager.IMPORTANCE_DEFAULT
                        ).apply { description = "A new version of Click is available on Google Play." }
                    )
                }
            }
            if (!ClickDownloadNotifications.hasRuntimePermission(context)) return
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = "com.click.browser.OPEN_APP_UPDATE"
                putExtra(EXTRA_OPEN_UPDATE, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Click update available")
                .setContentText("A new version of Click is available on Google Play. Tap to update.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "showUpdateNotification failed", e)
        }
    }
}
