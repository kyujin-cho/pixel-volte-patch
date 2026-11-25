package dev.bluehouse.enablevolte.diagnostics

import android.content.Context
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import dev.bluehouse.enablevolte.IPrivilegedService
import dev.bluehouse.enablevolte.PrivilegedServiceClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-side entry point for system diagnostics collected as shell through the Shizuku UserService
 * ([dev.bluehouse.enablevolte.PrivilegedService]).
 *
 * Only call [collect] after the user explicitly opted in to including system diagnostics. The
 * output is masked with [IdentifierMasker] before it touches disk, but masking is best effort:
 * show the text to the user for review before it is shared.
 */
object SystemDiagnostics {
    private const val TAG = "SystemDiagnostics"
    private const val OUTPUT_DIR = "system-diagnostics"
    private const val CONNECT_POLL_MS = 100L
    const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000L

    /** Hard limit on what the app reads from one pipe; the service already bounds its output. */
    private const val READ_LIMIT = 4 * 1024 * 1024

    enum class Source(
        internal val id: Int,
        /** File name of the masked output inside [outputDirectory]. */
        val fileName: String,
        /** The command the service runs, for display and for the file header. */
        val command: String,
    ) {
        LOGCAT(DiagnosticsProtocol.SOURCE_LOGCAT, "logcat.txt", "logcat -d -v threadtime -b main,system,radio"),
        DROPBOX_ANR(DiagnosticsProtocol.SOURCE_DROPBOX_ANR, "dropbox_data_app_anr.txt", "dumpsys dropbox --print data_app_anr"),
        DROPBOX_CRASH(DiagnosticsProtocol.SOURCE_DROPBOX_CRASH, "dropbox_data_app_crash.txt", "dumpsys dropbox --print data_app_crash"),
    }

    enum class UnavailableReason {
        SHIZUKU_NOT_RUNNING,
        PERMISSION_NOT_GRANTED,

        /** Shizuku is usable but the UserService did not connect (in time). */
        SERVICE_NOT_CONNECTED,
    }

    sealed interface Availability {
        object Available : Availability

        data class Unavailable(
            val reason: UnavailableReason,
        ) : Availability
    }

    /** One collected source. [file] is null when nothing could be collected; see [error]. */
    data class Section(
        val source: Source,
        val file: File?,
        val error: String?,
        /** The output exceeded its size limit; only the most recent part was kept. */
        val truncated: Boolean,
        /** The command hit its time limit; the output may be incomplete. */
        val timedOut: Boolean,
        /** Number of identifiers [IdentifierMasker] replaced. */
        val maskedCount: Int,
        /** Dropbox only: entries for the tag, and how many belong to this app (null for logcat). */
        val entriesTotal: Int?,
        val entriesKept: Int?,
    )

    sealed interface Result {
        data class Unavailable(
            val reason: UnavailableReason,
        ) : Result

        data class Collected(
            val sections: List<Section>,
        ) : Result
    }

    /**
     * Whether collection can run right now. Cheap and safe on the main thread; also starts binding
     * the UserService so a later [collect] does not have to wait as long.
     *
     * [UnavailableReason.SERVICE_NOT_CONNECTED] can be transient right after Shizuku became
     * available: [collect] waits for the connection before giving up.
     */
    fun availability(): Availability {
        unavailableReason()?.let { return Availability.Unavailable(it) }
        PrivilegedServiceClient.ensureBound()
        return if (PrivilegedServiceClient.service != null) {
            Availability.Available
        } else {
            Availability.Unavailable(UnavailableReason.SERVICE_NOT_CONNECTED)
        }
    }

    /** Directory holding the masked output files of the latest [collect]. */
    fun outputDirectory(context: Context): File = File(context.cacheDir, OUTPUT_DIR)

    /** Deletes the output of previous collections. */
    fun clear(context: Context) {
        outputDirectory(context).deleteRecursively()
    }

    /**
     * Collects [sources] as shell, masks them and writes one text file per source to
     * [outputDirectory] (replacing the previous output). Runs on [Dispatchers.IO]; each command is
     * bounded by time and size on the service side.
     *
     * Returns [Result.Unavailable] without throwing when Shizuku is not running, the permission is
     * not granted, or the UserService does not connect within [connectTimeoutMillis]. A failure of
     * a single source is reported in its [Section.error] and does not fail the others.
     */
    suspend fun collect(
        context: Context,
        sources: Collection<Source> = Source.entries,
        connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    ): Result =
        withContext(Dispatchers.IO) {
            unavailableReason()?.let { return@withContext Result.Unavailable(it) }
            val service =
                awaitService(connectTimeoutMillis)
                    ?: return@withContext Result.Unavailable(UnavailableReason.SERVICE_NOT_CONNECTED)

            val knownIdentifiers =
                try {
                    SubscriberIdentifiers(context.applicationContext).collect()
                } catch (e: Throwable) {
                    Log.w(TAG, "Could not look up subscriber identifiers: ${e.javaClass.simpleName}")
                    emptySet()
                }

            val directory = outputDirectory(context)
            directory.deleteRecursively()
            directory.mkdirs()
            Result.Collected(sources.map { collectSource(service, it, directory, knownIdentifiers) })
        }

    private fun unavailableReason(): UnavailableReason? =
        try {
            when {
                !Shizuku.pingBinder() -> UnavailableReason.SHIZUKU_NOT_RUNNING
                Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED ->
                    UnavailableReason.PERMISSION_NOT_GRANTED
                else -> null
            }
        } catch (_: RuntimeException) {
            UnavailableReason.SHIZUKU_NOT_RUNNING
        }

    private suspend fun awaitService(timeoutMillis: Long): IPrivilegedService? {
        withContext(Dispatchers.Main) { PrivilegedServiceClient.ensureBound() }
        var waited = 0L
        while (true) {
            PrivilegedServiceClient.service?.let { return it }
            if (waited >= timeoutMillis) return null
            delay(CONNECT_POLL_MS)
            waited += CONNECT_POLL_MS
        }
    }

    private suspend fun collectSource(
        service: IPrivilegedService,
        source: Source,
        directory: File,
        knownIdentifiers: Set<String>,
    ): Section {
        val (bytes, status) =
            try {
                runOverPipe(service, source)
            } catch (e: Exception) {
                Log.w(TAG, "Collecting ${source.name} failed", e)
                return Section(source, null, e.toString(), false, false, 0, null, null)
            }
        val error = status?.getString(DiagnosticsProtocol.KEY_ERROR) ?: if (status == null) "No status from the service" else null
        val truncated = status?.getBoolean(DiagnosticsProtocol.KEY_TRUNCATED) ?: false
        val timedOut = status?.getBoolean(DiagnosticsProtocol.KEY_TIMED_OUT) ?: false
        val entriesTotal =
            status
                ?.takeIf {
                    it.containsKey(
                        DiagnosticsProtocol.KEY_ENTRIES_TOTAL,
                    )
                }?.getInt(DiagnosticsProtocol.KEY_ENTRIES_TOTAL)
        val entriesKept =
            status
                ?.takeIf {
                    it.containsKey(
                        DiagnosticsProtocol.KEY_ENTRIES_KEPT,
                    )
                }?.getInt(DiagnosticsProtocol.KEY_ENTRIES_KEPT)
        if (bytes.isEmpty() && error != null) {
            return Section(source, null, error, truncated, timedOut, 0, entriesTotal, entriesKept)
        }

        val masked = IdentifierMasker.mask(String(bytes, Charsets.UTF_8), knownIdentifiers)
        val file = File(directory, source.fileName)
        file.writeText(header(source, masked.maskedCount, truncated, timedOut, entriesTotal, entriesKept) + masked.text)
        return Section(source, file, error, truncated, timedOut, masked.maskedCount, entriesTotal, entriesKept)
    }

    /**
     * Hands the service the write end of a pipe and drains the read end concurrently, so the
     * output streams through the file descriptor instead of a Binder parcel (see #488).
     */
    private suspend fun runOverPipe(
        service: IPrivilegedService,
        source: Source,
    ): Pair<ByteArray, Bundle?> =
        coroutineScope {
            val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
            val reader = async(Dispatchers.IO) { ParcelFileDescriptor.AutoCloseInputStream(readEnd).use { readCapped(it) } }
            val status =
                try {
                    service.collectDiagnostics(source.id, writeEnd)
                } finally {
                    // The service received its own copy; ours must be closed for the reader to see EOF.
                    writeEnd.close()
                }
            Pair(reader.await(), status)
        }

    /** Reads up to [READ_LIMIT] bytes and discards the rest, so the writer is never blocked. */
    private fun readCapped(input: InputStream): ByteArray {
        val tail = TailBuffer(READ_LIMIT)
        val chunk = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(chunk)
            if (read < 0) break
            tail.write(chunk, 0, read)
        }
        return tail.toByteArray()
    }

    private fun header(
        source: Source,
        maskedCount: Int,
        truncated: Boolean,
        timedOut: Boolean,
        entriesTotal: Int?,
        entriesKept: Int?,
    ): String =
        buildString {
            append("# ").append(source.command).append('\n')
            append("# Collected at ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())).append('\n')
            append("# Identifiers masked: ").append(maskedCount).append(" (best effort, review before sharing)\n")
            if (entriesTotal !=
                null
            ) {
                append("# Dropbox entries for this app: ")
                    .append(entriesKept ?: 0)
                    .append(" of ")
                    .append(entriesTotal)
                    .append('\n')
            }
            if (truncated) append("# Output truncated to its most recent part\n")
            if (timedOut) append("# Command timed out; output may be incomplete\n")
            append('\n')
        }
}
