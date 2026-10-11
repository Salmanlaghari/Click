package com.click.browser.engine

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Sends crash / OOM / feedback / bug reports to the admin team.
 *
 * Delivery is an email intent (ACTION_SENDTO mailto:) to all three admin
 * addresses — the user reviews the content in their own mail app before
 * anything is sent. Nothing is uploaded silently.
 */
object ReportMailer {

    /** Admin recipients for every report type. */
    val ADMIN_EMAILS = listOf(
        "laghariprince2@gmail.com",
        "lovefaillaghari@gmail.com",
        "farooquelaghari24@gmail.com"
    )

    private fun toAll(): String = ADMIN_EMAILS.joinToString(",")

    private fun send(context: Context, subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${toAll()}")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, "Send report").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (_: Exception) { /* no email app installed */ }
    }

    fun sendCrashReport(context: Context, report: CrashReporter.Report) {
        send(
            context,
            "Click Browser — Crash report (${report.title})",
            "Hi team,\n\nClick crashed. Details:\n\n${report.body}\n${CrashReporter.deviceBlock()}"
        )
    }

    fun sendOomReport(context: Context, report: CrashReporter.Report) {
        send(
            context,
            "Click Browser — OOM report (${report.title})",
            "Hi team,\n\nClick hit critical memory pressure. Details:\n\n${report.body}\n${CrashReporter.deviceBlock()}"
        )
    }

    fun sendFeedback(context: Context, message: String) {
        send(
            context,
            "Click Browser — Feedback",
            "Hi Prince,\n\n$message\n${CrashReporter.deviceBlock()}"
        )
    }

    fun sendBugReport(context: Context, summary: String, steps: String) {
        send(
            context,
            "Click Browser — Bug: ${summary.take(60)}",
            "Hi team,\n\nBug summary:\n$summary\n\nSteps to reproduce:\n$steps\n${CrashReporter.deviceBlock()}"
        )
    }
}
