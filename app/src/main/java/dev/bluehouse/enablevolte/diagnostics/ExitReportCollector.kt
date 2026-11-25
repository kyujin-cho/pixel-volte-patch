package dev.bluehouse.enablevolte.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Reads `ApplicationExitInfo` records (API 30+) of the app's previous processes and feeds them to
 * [ExitReportProcessor]. Runs on the calling thread; [CrashReports] calls it from a background
 * thread.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal object ExitReportCollector {
    /** How many historical exits to ask the system for. */
    private const val MAX_EXITS = 16

    fun collect(
        context: Context,
        processor: ExitReportProcessor,
        appVersion: String?,
    ): List<CrashReport> {
        val activityManager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        val exits = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS)
        return processor.process(exits.map { it.toRecord(appVersion) })
    }

    private fun ApplicationExitInfo.toRecord(appVersion: String?): ExitRecord {
        val kind =
            when (reason) {
                ApplicationExitInfo.REASON_ANR -> ExitKind.ANR
                ApplicationExitInfo.REASON_CRASH -> ExitKind.JAVA_CRASH
                ApplicationExitInfo.REASON_CRASH_NATIVE -> ExitKind.NATIVE_CRASH
                else -> null
            }
        val details =
            kind?.let {
                ExitDetails(
                    kind = it,
                    timestamp = timestamp,
                    pid = pid,
                    processName = processName,
                    description = description,
                    importance = importance,
                    pssKb = pss,
                    rssKb = rss,
                    status = status,
                    appVersion = appVersion,
                )
            }
        val info = this
        // The system provides a trace for ANRs and (API 31+) native crashes only.
        val hasTrace =
            kind == ExitKind.ANR ||
                (kind == ExitKind.NATIVE_CRASH && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        return ExitRecord(timestamp, details) { if (hasTrace) info.traceInputStream else null }
    }
}
