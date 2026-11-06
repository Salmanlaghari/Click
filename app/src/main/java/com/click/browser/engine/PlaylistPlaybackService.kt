package com.click.browser.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.click.browser.MainActivity
import com.click.browser.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Playlist background playback — a generic media player, Play-policy-safe.
 *
 * - Plays ONLY direct media URLs from the user's Playlist (streamed via
 *   MediaPlayer, never downloaded). No YouTube handling anywhere: the
 *   Playlist refuses YouTube URLs at add time, so none can reach here.
 * - Runs as a foreground service (type mediaPlayback) with a playback
 *   notification, so audio keeps playing when the app is backgrounded.
 * - Never marketed for any specific site/service — UI calls this
 *   "Playlist" / "Background audio", nothing more.
 */
class PlaylistPlaybackService : Service() {

    enum class Status { IDLE, PREPARING, PLAYING, PAUSED, ERROR }

    data class PlayerState(
        val status: Status = Status.IDLE,
        val currentTitle: String? = null,
        val currentUrl: String? = null,
        val errorMessage: String? = null
    )

    companion object {
        private const val TAG = "PlaylistPlayback"
        private const val NOTIF_ID = 4402
        private const val CHANNEL_ID = "playlist_playback"
        private const val ACTION_BASE = "com.teampkai.clickbrowser.engine.PlaylistPlaybackService"

        const val ACTION_PLAY = "$ACTION_BASE.PLAY"
        const val ACTION_PAUSE = "$ACTION_BASE.PAUSE"
        const val ACTION_RESUME = "$ACTION_BASE.RESUME"
        const val ACTION_NEXT = "$ACTION_BASE.NEXT"
        const val ACTION_PREV = "$ACTION_BASE.PREV"
        const val ACTION_STOP = "$ACTION_BASE.STOP"
        const val EXTRA_ITEM_ID = "item_id"

        private val _state = MutableStateFlow(PlayerState())
        val state: StateFlow<PlayerState> = _state.asStateFlow()

        private fun intent(context: Context, action: String, itemId: String? = null): Intent =
            Intent(context, PlaylistPlaybackService::class.java).setAction(action).apply {
                if (itemId != null) putExtra(EXTRA_ITEM_ID, itemId)
            }

        /** Start playing [item]; the queue is the current playlist snapshot. */
        fun play(context: Context, item: PlaylistItem) {
            context.startForegroundService(intent(context, ACTION_PLAY, item.id))
        }

        fun pause(context: Context) = context.startService(intent(context, ACTION_PAUSE))
        fun resume(context: Context) = context.startService(intent(context, ACTION_RESUME))
        fun next(context: Context) = context.startService(intent(context, ACTION_NEXT))
        fun prev(context: Context) = context.startService(intent(context, ACTION_PREV))
        fun stop(context: Context) = context.startService(intent(context, ACTION_STOP))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var queue: List<PlaylistItem> = emptyList()
    private var index: Int = -1
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (_state.value.status == Status.PLAYING) doPause()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        // API 33+: an explicit exported flag is required for registerReceiver.
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        }
        ensureChannel()
    }

    override fun onDestroy() {
        try { unregisterReceiver(noisyReceiver) } catch (_: Exception) { }
        releasePlayer()
        abandonFocus()
        scope.cancel()
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                // Promote to foreground IMMEDIATELY (the system gives ~5s
                // after startForegroundService) — the queue loads async below.
                updateState(Status.PREPARING)
                promoteToForeground()
                scope.launch { startPlaying(itemId) }
            }
            ACTION_PAUSE -> doPause()
            ACTION_RESUME -> doResume()
            ACTION_NEXT -> step(1)
            ACTION_PREV -> step(-1)
            ACTION_STOP -> doStop()
        }
        return START_NOT_STICKY
    }

    // ---------- queue / playback ----------

    private suspend fun startPlaying(itemId: String?) {
        val items = try {
            PlaylistManager(applicationContext).itemsFlow.first()
        } catch (e: Exception) {
            Log.w(TAG, "playlist read failed", e)
            emptyList()
        }
        if (items.isEmpty()) {
            doStop()
            return
        }
        queue = items
        index = itemId?.let { id -> items.indexOfFirst { it.id == id } }.takeIf { it != null && it >= 0 } ?: 0
        playAt(index)
    }

    private fun playAt(i: Int) {
        if (i !in queue.indices) {
            doStop()
            return
        }
        index = i
        val item = queue[i]
        updateState(Status.PREPARING, item)
        releasePlayer()
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(item.url)
                setOnPreparedListener {
                    if (!requestFocus()) {
                        updateState(Status.ERROR, item, "Could not get audio focus.")
                        return@setOnPreparedListener
                    }
                    start()
                    updateState(Status.PLAYING, item)
                    refreshNotification()
                }
                setOnCompletionListener { step(1) }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "player error what=$what extra=$extra url=${item.url}")
                    abandonFocus()
                    updateState(
                        Status.ERROR, item,
                        "Couldn't play this link. It may not be a direct audio/video file."
                    )
                    refreshNotification()
                    true
                }
            }
            player = mp
            mp.prepareAsync()
            promoteToForeground()
        } catch (e: Exception) {
            Log.w(TAG, "playAt failed", e)
            updateState(Status.ERROR, item, "Couldn't play this link.")
            refreshNotification()
        }
    }

    private fun doPause() {
        val mp = player ?: return
        if (_state.value.status != Status.PLAYING) return
        try { mp.pause() } catch (_: Exception) { }
        updateState(Status.PAUSED)
        refreshNotification()
    }

    private fun doResume() {
        val st = _state.value
        when (st.status) {
            Status.PAUSED -> {
                if (!requestFocus()) return
                try {
                    player?.start()
                    updateState(Status.PLAYING)
                    refreshNotification()
                } catch (_: Exception) {
                    updateState(Status.ERROR, errorMessage = "Couldn't resume playback.")
                    refreshNotification()
                }
            }
            // Retry the current item after a playback error.
            Status.ERROR -> if (index in queue.indices) playAt(index)
            else -> { }
        }
    }

    private fun step(delta: Int) {
        val next = index + delta
        if (next !in queue.indices) {
            // End of queue: stop cleanly instead of looping silently.
            doStop()
            return
        }
        playAt(next)
    }

    private fun doStop() {
        releasePlayer()
        abandonFocus()
        queue = emptyList()
        index = -1
        updateState(Status.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releasePlayer() {
        try { player?.reset() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
    }

    private fun updateState(status: Status, item: PlaylistItem? = null, errorMessage: String? = null) {
        val cur = _state.value
        _state.value = PlayerState(
            status = status,
            currentTitle = item?.title ?: cur.currentTitle.takeIf { status != Status.IDLE },
            currentUrl = item?.url ?: cur.currentUrl.takeIf { status != Status.IDLE },
            errorMessage = errorMessage
        )
    }

    // ---------- audio focus ----------

    private fun requestFocus(): Boolean {
        val am = audioManager ?: return true
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        resumeOnFocusGain =
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT &&
                                _state.value.status == Status.PLAYING
                        if (_state.value.status == Status.PLAYING) doPause()
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (resumeOnFocusGain) {
                            resumeOnFocusGain = false
                            doResume()
                        }
                    }
                }
            }
            .build()
        focusRequest = req
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    // ---------- notification ----------

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Playlist playback",
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Controls for playlist audio playing in the background." }
            )
        }
    }

    private fun actionIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, PlaylistPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun buildNotification(): Notification {
        val st = _state.value
        val isPlaying = st.status == Status.PLAYING
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(st.currentTitle ?: "Playlist")
            .setContentText(
                when (st.status) {
                    Status.PREPARING -> "Loading…"
                    Status.PLAYING -> "Playing in background"
                    Status.PAUSED -> "Paused"
                    Status.ERROR -> st.errorMessage ?: "Playback error"
                    Status.IDLE -> "Playlist"
                }
            )
            .setContentIntent(openApp)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
        if (index > 0) builder.addAction(
            android.R.drawable.ic_media_previous, "Previous", actionIntent(ACTION_PREV)
        )
        builder.addAction(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            actionIntent(if (isPlaying) ACTION_PAUSE else ACTION_RESUME)
        )
        if (index in 0 until queue.size - 1) builder.addAction(
            android.R.drawable.ic_media_next, "Next", actionIntent(ACTION_NEXT)
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", actionIntent(ACTION_STOP))
        return builder.build()
    }

    private fun promoteToForeground() {
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun refreshNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (_state.value.status == Status.IDLE) return
        nm.notify(NOTIF_ID, buildNotification())
    }
}
