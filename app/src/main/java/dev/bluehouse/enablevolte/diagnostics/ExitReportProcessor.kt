package dev.bluehouse.enablevolte.diagnostics

import java.io.InputStream
import kotlin.math.abs

/**
 * One historical process exit, decoupled from `ApplicationExitInfo` so the processing logic can
 * be unit tested. [details] is null for exit reasons that do not become reports; such exits only
 * advance the cursor.
 */
data class ExitRecord(
    val timestamp: Long,
    val details: ExitDetails?,
    /** Opens the system trace (ANR trace or tombstone); may return null or throw. */
    val openTrace: () -> InputStream? = { null },
)

/** Persists the timestamp of the newest exit that has been processed. */
interface ExitCursor {
    fun get(): Long

    fun set(timestamp: Long)
}

/**
 * Turns historical process exits into stored reports, each exit at most once.
 *
 * Exits newer than the [cursor] are processed oldest first, and the cursor is advanced after
 * each one, so an exit is never processed again on a later launch. The deterministic report id
 * additionally guards against a crash between storing a report and advancing the cursor.
 *
 * A `REASON_CRASH` exit is matched with the stack trace file that [JavaCrashRecorder] wrote
 * just before the process died: same pid within [MATCH_WINDOW_MS], or failing that the nearest
 * file in time within the window. Crash files that match no exit and were written before
 * [processStart] (so not by the current process) become reports on their own.
 */
class ExitReportProcessor(
    private val store: CrashReportStore,
    private val cursor: ExitCursor,
    private val pendingCrashes: () -> List<PendingJavaCrash>,
    private val now: Long,
    private val processStart: Long,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS,
) {
    /** Processes [records] (any order) and returns the reports that were stored. */
    fun process(records: List<ExitRecord>): List<CrashReport> {
        val stored = mutableListOf<CrashReport>()
        val pending = pendingCrashes().toMutableList()
        val last = cursor.get()
        for (record in records.filter { it.timestamp > last }.sortedBy { it.timestamp }) {
            val details = record.details
            if (details != null && isRecent(record.timestamp) && !store.contains(details.reportId)) {
                val payload =
                    if (details.kind == ExitKind.JAVA_CRASH) {
                        matchCrashFile(pending, details)?.let { match ->
                            pending.remove(match)
                            openOrNull { match.file.inputStream() }.also { match.file.delete() }
                        }
                    } else {
                        openOrNull(record.openTrace)
                    }
                store.save(details, payload)?.let { stored += it }
            } else if (details?.kind == ExitKind.JAVA_CRASH) {
                // Already stored or too old: its crash file must not resurface as an orphan.
                matchCrashFile(pending, details)?.let { match ->
                    pending.remove(match)
                    match.file.delete()
                }
            }
            cursor.set(record.timestamp)
        }
        for (orphan in pending.filter { it.timestamp < processStart }) {
            if (isRecent(orphan.timestamp)) {
                val details =
                    ExitDetails(
                        kind = ExitKind.JAVA_CRASH,
                        timestamp = orphan.timestamp,
                        pid = orphan.pid,
                        description = ORPHAN_DESCRIPTION,
                    )
                store.save(details, openOrNull { orphan.file.inputStream() })?.let { stored += it }
            }
            orphan.file.delete()
        }
        return stored
    }

    private fun isRecent(timestamp: Long) = now - timestamp <= maxAgeMs

    private fun matchCrashFile(
        pending: List<PendingJavaCrash>,
        details: ExitDetails,
    ): PendingJavaCrash? {
        val inWindow = pending.filter { abs(details.timestamp - it.timestamp) <= MATCH_WINDOW_MS }
        return inWindow.firstOrNull { it.pid == details.pid }
            ?: inWindow.minByOrNull { abs(details.timestamp - it.timestamp) }
    }

    private fun openOrNull(open: () -> InputStream?): InputStream? =
        try {
            open()
        } catch (_: Exception) {
            null
        }

    companion object {
        /** Exits older than this are skipped (for example on the first launch after an update). */
        const val DEFAULT_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** How far apart a crash file and its exit record may be. */
        const val MATCH_WINDOW_MS = 60_000L

        const val ORPHAN_DESCRIPTION = "Uncaught exception without a matching system exit record"
    }
}
