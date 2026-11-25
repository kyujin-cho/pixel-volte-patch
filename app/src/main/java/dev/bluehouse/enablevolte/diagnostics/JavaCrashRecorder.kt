package dev.bluehouse.enablevolte.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean

/** A stack trace written by [JavaCrashRecorder] that has not been turned into a report yet. */
data class PendingJavaCrash(
    val file: File,
    val timestamp: Long,
    val pid: Int,
)

/**
 * Default uncaught exception handler that writes the stack trace of a Java crash to [pendingDir],
 * then chains to the handler that was installed before it (the system one shows the crash dialog
 * and reports to Play).
 *
 * The handler only formats the trace and does one synchronous file write: no Binder calls, no
 * locks shared with app code. On the next launch [ExitReportCollector] matches the file with the
 * `REASON_CRASH` exit record and moves it into the [CrashReportStore].
 */
class JavaCrashRecorder(
    private val pendingDir: File,
    private val pid: Int,
    private val processName: String?,
    private val appVersion: String?,
    private val previous: Thread.UncaughtExceptionHandler?,
    private val clock: () -> Long = System::currentTimeMillis,
    private val terminate: () -> Unit = {},
) : Thread.UncaughtExceptionHandler {
    private val handling = AtomicBoolean(false)

    override fun uncaughtException(
        thread: Thread,
        throwable: Throwable,
    ) {
        // A second crash while recording (or on another thread) must not record over the first.
        if (handling.compareAndSet(false, true)) {
            try {
                record(thread, throwable)
            } catch (_: Throwable) {
            }
        }
        if (previous != null) {
            previous.uncaughtException(thread, throwable)
        } else {
            terminate()
        }
    }

    /** Writes the crash file and returns it. Visible for tests. */
    fun record(
        thread: Thread,
        throwable: Throwable,
    ): File {
        val timestamp = clock()
        val text = format(thread, throwable, timestamp)
        pendingDir.mkdirs()
        val name = "$FILE_PREFIX$timestamp-$pid$FILE_SUFFIX"
        val tmp = File(pendingDir, "$name.tmp")
        tmp.writeBytes(text.toByteArray())
        val target = File(pendingDir, name)
        if (!tmp.renameTo(target)) tmp.copyTo(target, overwrite = true).also { tmp.delete() }
        return target
    }

    private fun format(
        thread: Thread,
        throwable: Throwable,
        timestamp: Long,
    ): String {
        val trace = StringWriter()
        PrintWriter(trace).use { throwable.printStackTrace(it) }
        var stack = trace.toString()
        if (stack.length > MAX_TRACE_CHARS) {
            val marker = "\n--- truncated by Pixel IMS: stack trace exceeded $MAX_TRACE_CHARS characters ---\n"
            stack = stack.substring(0, MAX_TRACE_CHARS) + marker
        }
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
        format.timeZone = TimeZone.getDefault()
        val time = format.format(Date(timestamp))
        return buildString {
            append("Pixel IMS uncaught exception\n")
            append("timestamp: $timestamp ($time)\n")
            append("thread: ${thread.name} (id ${thread.id})\n")
            append("process: ${processName ?: "unknown"} (pid $pid)\n")
            append("app version: ${appVersion ?: "unknown"}\n\n")
            append(stack)
        }
    }

    companion object {
        private const val FILE_PREFIX = "java-crash-"
        private const val FILE_SUFFIX = ".txt"
        private const val MAX_TRACE_CHARS = 256 * 1024
        private const val STALE_TMP_MS = 60_000L
        private val FILE_PATTERN = Regex("""java-crash-(\d+)-(\d+)\.txt""")

        /** Crash files waiting in [pendingDir], oldest first. Stale temp files are removed. */
        fun pending(pendingDir: File): List<PendingJavaCrash> {
            val files = pendingDir.listFiles() ?: return emptyList()
            return files
                .mapNotNull { file ->
                    val match = FILE_PATTERN.matchEntire(file.name)
                    if (match == null) {
                        // A temp file left by a crash during the write; never one being written now.
                        val stale = System.currentTimeMillis() - file.lastModified() > STALE_TMP_MS
                        if (file.name.endsWith(".tmp") && stale) file.delete()
                        return@mapNotNull null
                    }
                    val timestamp = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                    val pid = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                    PendingJavaCrash(file, timestamp, pid)
                }.sortedBy { it.timestamp }
        }
    }
}
