package com.click.browser.engine

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import com.click.browser.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash + OOM reporting.
 *
 * - Installs a default uncaught-exception handler that writes the crash to
 *   the app's files dir, then chains to the previous handler (the app still
 *   dies normally — we never swallow crashes).
 * - Registers ComponentCallbacks2: TRIM_MEMORY_RUNNING_CRITICAL /
 *   TRIM_MEMORY_COMPLETE events are logged as OOM-risk reports.
 * - Reports are listed/sent/discarded from the Reports screen — nothing is
 *   uploaded automatically. The user taps "Send" and an email goes to the
 *   admin addresses via [ReportMailer].
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR = "reports"
    private const val PREFIX_CRASH = "crash_"
    private const val PREFIX_OOM = "oom_"
    private const val MAX_FILES = 20

    data class Report(
        val fileName: String,
        val kind: String, // "crash" | "oom"
        val timestamp: Long,
        val title: String,
        val body: String
    )

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(
                    app, PREFIX_CRASH,
                    "Crash in ${thread.name}",
                    formatThrowable(throwable)
                )
            } catch (e: Exception) {
                Log.w(TAG, "failed to write crash report", e)
            }
            previous?.uncaughtException(thread, throwable)
        }
        app.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            override fun onLowMemory() = onTrim(ComponentCallbacks2.TRIM_MEMORY_COMPLETE, app)
            override fun onTrimMemory(level: Int) = onTrim(level, app)
            private fun onTrim(level: Int, context: Context) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                    try {
                        writeReport(
                            context, PREFIX_OOM,
                            "Low-memory warning (level=$level)",
                            "The system reported critical memory pressure while Click was running.\n" +
                                "Foreground tab count and heavy pages at that moment are unknown — " +
                                "this is a best-effort signal, not a diagnosis."
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "failed to write OOM report", e)
                    }
                }
            }
        })
    }

    fun listReports(context: Context): List<Report> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && (it.name.startsWith(PREFIX_CRASH) || it.name.startsWith(PREFIX_OOM)) }
            ?.mapNotNull { parseFile(it) }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()
    }

    fun deleteReport(context: Context, fileName: String): Boolean {
        val safe = fileName.replace(Regex("[^a-zA-Z0-9_.-]"), "")
        return File(File(context.filesDir, DIR), safe).delete()
    }

    fun clearAll(context: Context) {
        listReports(context).forEach { deleteReport(context, it.fileName) }
    }

    private fun parseFile(file: File): Report? {
        return try {
            val o = JSONObject(file.readText())
            val kind = if (file.name.startsWith(PREFIX_CRASH)) "crash" else "oom"
            Report(
                fileName = file.name,
                kind = kind,
                timestamp = o.optLong("timestamp", file.lastModified()),
                title = o.optString("title").ifBlank { file.name },
                body = o.optString("body")
            )
        } catch (e: Exception) {
            Log.w(TAG, "parse failed: ${file.name}", e); null
        }
    }

    private fun writeReport(context: Context, prefix: String, title: String, body: String) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        prune(dir)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "$prefix$stamp.json")
        val o = JSONObject()
            .put("timestamp", System.currentTimeMillis())
            .put("title", title)
            .put("body", body)
            .put("appVersion", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("android", "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        file.writeText(o.toString())
    }

    private fun prune(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.isFile && (it.name.startsWith(PREFIX_CRASH) || it.name.startsWith(PREFIX_OOM)) }
            ?.sortedBy { it.lastModified() } ?: return
        if (files.size >= MAX_FILES) {
            files.take(files.size - MAX_FILES + 1).forEach { it.delete() }
        }
    }

    private fun formatThrowable(t: Throwable): String {
        val sb = StringBuilder()
        sb.append(t.javaClass.name).append(": ").append(t.message).append("\n")
        val trace = t.stackTrace.take(40).joinToString("\n") { "    at $it" }
        sb.append(trace)
        t.cause?.let { sb.append("\nCaused by: ").append(it.javaClass.name).append(": ").append(it.message) }
        return sb.toString().take(8000)
    }

    /** Device/app block appended to every outgoing report email. */
    fun deviceBlock(): String = """
        ----
        App: Click Browser ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
        Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})
    """.trimIndent()

    /** Test hook: writes a fake crash report (used from the Reports screen). */
    fun writeTestCrash(context: Context) {
        writeReport(context, PREFIX_CRASH, "Test crash (manual)", "This is a manually generated test report.")
    }
}
