package dev.bluehouse.enablevolte.diagnostics

import android.os.Bundle
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Contract of `IPrivilegedService.collectDiagnostics` shared by the app and the UserService.
 *
 * The caller only picks one of the fixed [SOURCE_LOGCAT] / [SOURCE_DROPBOX_ANR] /
 * [SOURCE_DROPBOX_CRASH] ids; the service never runs a caller-supplied command line.
 */
object DiagnosticsProtocol {
    const val SOURCE_LOGCAT = 1
    const val SOURCE_DROPBOX_ANR = 2
    const val SOURCE_DROPBOX_CRASH = 3

    /** String: why the section could not be collected (absent on success). */
    const val KEY_ERROR = "error"

    /** Boolean: the output was cut to the size limit (only the most recent part is kept). */
    const val KEY_TRUNCATED = "truncated"

    /** Boolean: the command hit its time limit and was killed; the output may be incomplete. */
    const val KEY_TIMED_OUT = "timedOut"

    /** Long: bytes the command printed before any filtering or truncation. */
    const val KEY_RAW_BYTES = "rawBytes"

    /** Int: dropbox entries found for the tag, and how many of them belong to this app. */
    const val KEY_ENTRIES_TOTAL = "entriesTotal"
    const val KEY_ENTRIES_KEPT = "entriesKept"
}

/**
 * Runs the diagnostic commands. Executes inside [dev.bluehouse.enablevolte.PrivilegedService],
 * i.e. in the Shizuku UserService process running as the shell uid, which can read the radio log
 * buffer and the system's dropbox.
 */
internal object ShellDiagnostics {
    private const val MIB = 1024 * 1024
    private const val READ_CHUNK = 64 * 1024
    private const val READER_JOIN_MS = 2_000L
    private const val DENIAL_SCAN_CHARS = 1024

    class Spec(
        val command: List<String>,
        val timeoutMillis: Long,
        /** Bytes of raw output kept (the tail), before filtering. */
        val rawLimit: Int,
        /** Bytes of the final output kept (the tail), after filtering. */
        val outputLimit: Int,
        val dropboxFilter: Boolean,
    )

    fun specFor(source: Int): Spec? =
        when (source) {
            DiagnosticsProtocol.SOURCE_LOGCAT ->
                Spec(
                    listOf("logcat", "-d", "-v", "threadtime", "-b", "main,system,radio"),
                    timeoutMillis = 15_000,
                    rawLimit = 2 * MIB,
                    outputLimit = 2 * MIB,
                    dropboxFilter = false,
                )
            DiagnosticsProtocol.SOURCE_DROPBOX_ANR -> dropboxSpec("data_app_anr")
            DiagnosticsProtocol.SOURCE_DROPBOX_CRASH -> dropboxSpec("data_app_crash")
            else -> null
        }

    private fun dropboxSpec(tag: String) =
        Spec(
            listOf("dumpsys", "dropbox", "--print", tag),
            timeoutMillis = 15_000,
            rawLimit = 8 * MIB,
            outputLimit = 1 * MIB,
            dropboxFilter = true,
        )

    class Output(
        val bytes: ByteArray,
        val status: Bundle,
    )

    fun collect(
        spec: Spec,
        packageName: String,
    ): Output {
        val status = Bundle()
        val process = ProcessBuilder(spec.command).redirectErrorStream(true).start()
        process.outputStream.close()
        val raw = TailBuffer(spec.rawLimit)
        val reader =
            thread(name = "diagnostics-reader", isDaemon = true) {
                try {
                    process.inputStream.use { input ->
                        val chunk = ByteArray(READ_CHUNK)
                        while (true) {
                            val read = input.read(chunk)
                            if (read < 0) break
                            synchronized(raw) { raw.write(chunk, 0, read) }
                        }
                    }
                } catch (_: Exception) {
                    // The stream is closed when the process is killed on timeout.
                }
            }
        val finished = process.waitFor(spec.timeoutMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            status.putBoolean(DiagnosticsProtocol.KEY_TIMED_OUT, true)
        }
        reader.join(READER_JOIN_MS)

        val rawBytes: ByteArray
        synchronized(raw) {
            rawBytes = raw.toByteArray()
            status.putLong(DiagnosticsProtocol.KEY_RAW_BYTES, raw.totalBytes)
        }
        var text = String(rawBytes, Charsets.UTF_8)
        var truncated = rawBytes.size.toLong() < status.getLong(DiagnosticsProtocol.KEY_RAW_BYTES)
        if (truncated) text = DiagnosticsText.dropPartialFirstLine(text)

        if (spec.dropboxFilter) {
            // dumpsys prints a denial as its only output and still exits 0. Only the head is
            // checked: entries of other apps may legitimately contain the same words.
            text.take(DENIAL_SCAN_CHARS).lineSequence().firstOrNull { it.contains("Permission Denial") }?.let {
                status.putString(DiagnosticsProtocol.KEY_ERROR, it.trim())
                return Output(ByteArray(0), status)
            }
            val filtered = DiagnosticsText.filterDropboxEntries(text, packageName)
            status.putInt(DiagnosticsProtocol.KEY_ENTRIES_TOTAL, filtered.totalEntries)
            status.putInt(DiagnosticsProtocol.KEY_ENTRIES_KEPT, filtered.keptEntries)
            text = filtered.text
        }
        var bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > spec.outputLimit) {
            val tail = TailBuffer(spec.outputLimit)
            tail.write(bytes)
            bytes = DiagnosticsText.dropPartialFirstLine(String(tail.toByteArray(), Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
            truncated = true
        }
        if (finished && process.exitValue() != 0 && bytes.isEmpty()) {
            status.putString(DiagnosticsProtocol.KEY_ERROR, "${spec.command.first()} exited with ${process.exitValue()}")
        }
        status.putBoolean(DiagnosticsProtocol.KEY_TRUNCATED, truncated)
        return Output(bytes, status)
    }
}
