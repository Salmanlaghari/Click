package com.click.browser

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Process
import android.os.ResultReceiver
import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.click.browser.engine.shouldOpenTargetProfile
import com.click.browser.engine.V9Engine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A permission-free, visible handoff between WebView profile processes.
 * This Activity runs without creating WebViews, so the previous browser can exit
 * while this screen remains in front and then launch MainActivity from a visible
 * user-facing window instead of relying on a delayed background alarm.
 */
class ModeSwitchActivity : ComponentActivity() {
    private var sourcePid = -1
    private var hackIntro = false
    private var advanceIntro = false
    private var bridgeReadyReceiver: ResultReceiver? = null
    private var bridgeWindowFocused = false
    private var sourceProcessExited = false
    private var targetActivityFocused = false
    private var canRetry = false
    private var statusText by mutableStateOf("Preparing the new browser profile…")
    private var handoffJob: Job? = null
    private var receiverRegistered = false

    private val handoffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_TARGET_PROFILE_FOCUSED -> {
                    targetActivityFocused = true
                    finishAndRemoveTask()
                }
                ACTION_CANCEL -> {
                    handoffJob?.cancel()
                    finishAndRemoveTask()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sourcePid = intent.getIntExtra(EXTRA_SOURCE_PID, -1)
        hackIntro = intent.getBooleanExtra(V9Engine.EXTRA_HACK_INTRO, false)
        advanceIntro = intent.getBooleanExtra(V9Engine.EXTRA_ADVANCE_INTRO, false)
        @Suppress("DEPRECATION")
        bridgeReadyReceiver = intent.getParcelableExtra(EXTRA_BRIDGE_READY_RECEIVER)

        val filter = IntentFilter().apply {
            addAction(ACTION_TARGET_PROFILE_FOCUSED)
            addAction(ACTION_CANCEL)
        }
        ContextCompat.registerReceiver(
            this,
            handoffReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!bridgeWindowFocused) {
                    statusText = "Your current browser is staying open while the handoff starts."
                } else if (sourceProcessExited || !isSourceProcessAlive()) {
                    sourceProcessExited = true
                    openTargetActivity()
                } else {
                    waitForSourceAndOpenTarget()
                }
            }
        })

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = "Switching browser profile",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        if (canRetry) {
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = ::retryHandoff) {
                                Text("Open the new profile")
                            }
                        }
                    }
                }
            }
        }

        if (sourcePid <= 0 || bridgeReadyReceiver == null) {
            statusText = "The profile switch could not start. Your current browser is staying open."
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || bridgeWindowFocused || sourcePid <= 0) return
        val readyReceiver = bridgeReadyReceiver ?: return

        bridgeWindowFocused = true
        readyReceiver.send(RESULT_BRIDGE_WINDOW_FOCUSED, null)
        waitForSourceAndOpenTarget()
    }

    private fun waitForSourceAndOpenTarget() {
        if (handoffJob?.isActive == true) return
        canRetry = false
        statusText = "Keeping Click open while the new profile starts…"
        handoffJob = lifecycleScope.launch {
            var exited = awaitSourceProcessExit(SOURCE_EXIT_GRACE_MS)
            if (!exited) {
                requestSourceProcessExitIfStillClickMain()
                exited = awaitSourceProcessExit(FORCED_EXIT_WAIT_MS)
            }
            if (!shouldOpenTargetProfile(sourceProcessExited = exited)) {
                statusText = "The previous profile is still closing. Click is keeping this screen open."
                canRetry = true
                return@launch
            }
            sourceProcessExited = true
            openTargetActivity()
        }
    }

    private suspend fun awaitSourceProcessExit(timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (!isSourceProcessAlive()) return true
            delay(PROCESS_POLL_INTERVAL_MS)
        }
        return !isSourceProcessAlive()
    }

    private fun isSourceProcessAlive(): Boolean {
        if (sourcePid <= 0 || sourcePid == Process.myPid()) return false
        return try {
            Os.kill(sourcePid, 0)
            true
        } catch (error: ErrnoException) {
            error.errno != OsConstants.ESRCH
        } catch (_: SecurityException) {
            true
        }
    }

    private fun requestSourceProcessExitIfStillClickMain() {
        if (sourcePid <= 0 || sourcePid == Process.myPid()) return
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        @Suppress("DEPRECATION")
        val source = manager.runningAppProcesses?.firstOrNull { it.pid == sourcePid } ?: return
        if (source.processName != packageName) return
        Log.w(TAG, "Mode-switch bridge is asking the original Click browser process to finish")
        Process.killProcess(sourcePid)
    }

    private fun retryHandoff() {
        if (handoffJob?.isActive == true) return
        if (shouldOpenTargetProfile(sourceProcessExited) || !isSourceProcessAlive()) {
            sourceProcessExited = true
            openTargetActivity()
        } else {
            waitForSourceAndOpenTarget()
        }
    }

    private fun openTargetActivity() {
        if (targetActivityFocused) return
        canRetry = false
        statusText = "Opening the new browser profile…"
        try {
            val target = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                if (hackIntro) putExtra(V9Engine.EXTRA_HACK_INTRO, true)
                if (advanceIntro) putExtra(V9Engine.EXTRA_ADVANCE_INTRO, true)
            }
            startActivity(target)
        } catch (error: Throwable) {
            Log.e(TAG, "Could not open Click in the target browser profile", error)
            canRetry = true
            statusText = "Click is still open. Tap below to retry the new profile."
            return
        }
        lifecycleScope.launch {
            delay(TARGET_FOCUS_TIMEOUT_MS)
            if (!targetActivityFocused) {
                canRetry = true
                statusText = "Click is still open. Tap below if the new profile has not appeared."
            }
        }
    }

    override fun onDestroy() {
        handoffJob?.cancel()
        if (receiverRegistered) {
            runCatching { unregisterReceiver(handoffReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    companion object {
        const val PROCESS_SUFFIX = ":mode_switch"
        const val EXTRA_SOURCE_PID = "click_mode_switch_source_pid"
        const val EXTRA_BRIDGE_READY_RECEIVER = "click_mode_switch_bridge_ready_receiver"
        const val ACTION_TARGET_PROFILE_FOCUSED = "com.click.browser.MODE_SWITCH_TARGET_FOCUSED"
        const val ACTION_CANCEL = "com.click.browser.MODE_SWITCH_CANCEL"
        const val RESULT_BRIDGE_WINDOW_FOCUSED = 1

        private const val TAG = "ModeSwitchActivity"
        private const val SOURCE_EXIT_GRACE_MS = 10_000L
        private const val FORCED_EXIT_WAIT_MS = 2_000L
        private const val PROCESS_POLL_INTERVAL_MS = 50L
        private const val TARGET_FOCUS_TIMEOUT_MS = 6_000L
    }
}
