package dev.bluehouse.enablevolte.diagnostics

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Properties
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * App-private storage of crash and ANR reports: one directory per report under [rootDir], holding
 * `report.properties` (metadata) and an optional gzip-compressed `payload.gz`.
 *
 * Reports are bounded by [maxReports] and [maxTotalBytes] (on-disk size); the oldest reports are
 * evicted first. A single payload is cut after [maxPayloadBytes] uncompressed bytes.
 *
 * Every method does file I/O: call them off the main thread. The class is thread-safe.
 */
class CrashReportStore(
    private val rootDir: File,
    val maxReports: Int = DEFAULT_MAX_REPORTS,
    val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
    val maxPayloadBytes: Long = DEFAULT_MAX_PAYLOAD_BYTES,
) {
    private val lock = Any()

    /** Called (on the calling thread) after any change to the stored reports. */
    @Volatile
    var onChange: (() -> Unit)? = null

    /** All stored reports, newest first. */
    fun list(): List<CrashReport> =
        synchronized(lock) {
            reportDirs().mapNotNull { readReport(it) }.sortedByDescending { it.timestamp }
        }

    fun get(id: String): CrashReport? = synchronized(lock) { reportDir(id)?.let { readReport(it) } }

    fun contains(id: String): Boolean = synchronized(lock) { reportDir(id)?.let { File(it, META_FILE).isFile } == true }

    /** Reports the user has not handled yet, newest first. */
    fun unacknowledged(): List<CrashReport> = list().filter { !it.acknowledged }

    fun hasUnacknowledged(): Boolean = unacknowledged().isNotEmpty()

    /**
     * Opens the decompressed payload of a report, or returns null if it has none. The caller
     * closes the stream.
     */
    fun openPayload(id: String): InputStream? {
        val file = synchronized(lock) { reportDir(id)?.let { File(it, PAYLOAD_FILE) } } ?: return null
        return try {
            GZIPInputStream(file.inputStream().buffered())
        } catch (_: IOException) {
            null
        }
    }

    /** Marks a report as handled, so it no longer counts as unacknowledged. */
    fun acknowledge(id: String): Boolean {
        val changed =
            synchronized(lock) {
                val dir = reportDir(id) ?: return false
                val props = loadProperties(File(dir, META_FILE)) ?: return false
                if (props.getProperty(KEY_ACKNOWLEDGED) == "true") return true
                props.setProperty(KEY_ACKNOWLEDGED, "true")
                writePropertiesAtomically(dir, props)
            }
        if (changed) notifyChange()
        return changed
    }

    fun delete(id: String): Boolean {
        val deleted = synchronized(lock) { reportDir(id)?.deleteRecursively() == true }
        if (deleted) notifyChange()
        return deleted
    }

    fun deleteAll() {
        synchronized(lock) { rootDir.listFiles()?.forEach { it.deleteRecursively() } }
        notifyChange()
    }

    /**
     * Stores a report for [details] with an optional [payload], which is read to the end (or to
     * [maxPayloadBytes]) and closed. Returns the stored report, or the existing one if a report
     * with the same id is already stored. Returns null if the report could not be written.
     */
    fun save(
        details: ExitDetails,
        payload: InputStream?,
    ): CrashReport? {
        val id = details.reportId
        val saved =
            synchronized(lock) {
                val target = File(rootDir, id)
                if (File(target, META_FILE).isFile) {
                    payload?.closeQuietly()
                    return readReport(target)
                }
                rootDir.listFiles()?.filter { it.name.startsWith(TMP_PREFIX) }?.forEach { it.deleteRecursively() }
                val tmp = File(rootDir, TMP_PREFIX + id)
                try {
                    tmp.deleteRecursively()
                    if (!tmp.mkdirs()) throw IOException("cannot create $tmp")
                    val truncated = payload?.use { writePayload(File(tmp, PAYLOAD_FILE), it, details.kind.textPayload) }
                    val props = detailsToProperties(details)
                    if (truncated == true) props.setProperty(KEY_TRUNCATED, "true")
                    File(tmp, META_FILE).outputStream().use { props.store(it, null) }
                    target.deleteRecursively()
                    if (!tmp.renameTo(target)) throw IOException("cannot rename $tmp")
                } catch (_: IOException) {
                    payload?.closeQuietly()
                    tmp.deleteRecursively()
                    return null
                }
                enforceLimits(keepId = id)
                readReport(target)
            }
        notifyChange()
        return saved
    }

    private fun notifyChange() {
        onChange?.invoke()
    }

    /** Evicts the oldest reports until both limits hold; the report [keepId] is evicted last. */
    private fun enforceLimits(keepId: String) {
        val reports = reportDirs().mapNotNull { readReport(it) }.sortedBy { it.timestamp }.toMutableList()
        var total = reports.sumOf { it.sizeBytes }
        while (reports.size > 1 && (reports.size > maxReports || total > maxTotalBytes)) {
            val victim = reports.firstOrNull { it.id != keepId } ?: break
            File(rootDir, victim.id).deleteRecursively()
            reports.remove(victim)
            total -= victim.sizeBytes
        }
    }

    /** Copies at most [maxPayloadBytes] bytes, gzip-compressed. Returns whether input was cut. */
    private fun writePayload(
        file: File,
        input: InputStream,
        text: Boolean,
    ): Boolean {
        var truncated = false
        GZIPOutputStream(file.outputStream().buffered()).use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            var remaining = maxPayloadBytes
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            if (remaining <= 0 && input.read() >= 0) {
                truncated = true
                if (text) out.write(truncationMarker(maxPayloadBytes).toByteArray())
            }
        }
        return truncated
    }

    private fun reportDirs(): List<File> = rootDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(TMP_PREFIX) } ?: emptyList()

    /** Resolves an id to its directory, rejecting anything that is not a plain report id. */
    private fun reportDir(id: String): File? = if (ID_PATTERN.matches(id)) File(rootDir, id).takeIf { it.isDirectory } else null

    private fun readReport(dir: File): CrashReport? {
        val props = loadProperties(File(dir, META_FILE)) ?: return null
        val details = propertiesToDetails(props) ?: return null
        return CrashReport(
            id = dir.name,
            details = details,
            hasPayload = File(dir, PAYLOAD_FILE).isFile,
            payloadTruncated = props.getProperty(KEY_TRUNCATED) == "true",
            acknowledged = props.getProperty(KEY_ACKNOWLEDGED) == "true",
            sizeBytes = dir.listFiles()?.sumOf { it.length() } ?: 0L,
        )
    }

    private fun writePropertiesAtomically(
        dir: File,
        props: Properties,
    ): Boolean {
        val tmp = File(dir, "$META_FILE.tmp")
        return try {
            tmp.outputStream().use { props.store(it, null) }
            tmp.renameTo(File(dir, META_FILE))
        } catch (_: IOException) {
            tmp.delete()
            false
        }
    }

    companion object {
        const val DEFAULT_MAX_REPORTS = 5
        const val DEFAULT_MAX_TOTAL_BYTES = 4L * 1024 * 1024
        const val DEFAULT_MAX_PAYLOAD_BYTES = 4L * 1024 * 1024

        private const val META_FILE = "report.properties"
        private const val PAYLOAD_FILE = "payload.gz"
        private const val TMP_PREFIX = ".tmp-"
        private const val BUFFER_SIZE = 16 * 1024
        private val ID_PATTERN = Regex("[A-Za-z0-9_-]+")

        private const val KEY_KIND = "kind"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_PID = "pid"
        private const val KEY_PROCESS = "processName"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_IMPORTANCE = "importance"
        private const val KEY_PSS = "pssKb"
        private const val KEY_RSS = "rssKb"
        private const val KEY_STATUS = "status"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_TRUNCATED = "payloadTruncated"
        private const val KEY_ACKNOWLEDGED = "acknowledged"

        fun truncationMarker(limit: Long) = "\n\n--- truncated by Pixel IMS: payload exceeded $limit bytes ---\n"

        private fun detailsToProperties(details: ExitDetails) =
            Properties().apply {
                setProperty(KEY_KIND, details.kind.token)
                setProperty(KEY_TIMESTAMP, details.timestamp.toString())
                setProperty(KEY_PID, details.pid.toString())
                details.processName?.let { setProperty(KEY_PROCESS, it) }
                details.description?.let { setProperty(KEY_DESCRIPTION, it) }
                details.importance?.let { setProperty(KEY_IMPORTANCE, it.toString()) }
                details.pssKb?.let { setProperty(KEY_PSS, it.toString()) }
                details.rssKb?.let { setProperty(KEY_RSS, it.toString()) }
                details.status?.let { setProperty(KEY_STATUS, it.toString()) }
                details.appVersion?.let { setProperty(KEY_APP_VERSION, it) }
            }

        private fun propertiesToDetails(props: Properties): ExitDetails? {
            val kind = ExitKind.fromToken(props.getProperty(KEY_KIND)) ?: return null
            val timestamp = props.getProperty(KEY_TIMESTAMP)?.toLongOrNull() ?: return null
            val pid = props.getProperty(KEY_PID)?.toIntOrNull() ?: return null
            return ExitDetails(
                kind = kind,
                timestamp = timestamp,
                pid = pid,
                processName = props.getProperty(KEY_PROCESS),
                description = props.getProperty(KEY_DESCRIPTION),
                importance = props.getProperty(KEY_IMPORTANCE)?.toIntOrNull(),
                pssKb = props.getProperty(KEY_PSS)?.toLongOrNull(),
                rssKb = props.getProperty(KEY_RSS)?.toLongOrNull(),
                status = props.getProperty(KEY_STATUS)?.toIntOrNull(),
                appVersion = props.getProperty(KEY_APP_VERSION),
            )
        }

        private fun loadProperties(file: File): Properties? =
            try {
                if (file.isFile) Properties().apply { file.inputStream().use { load(it) } } else null
            } catch (_: IOException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }

        private fun InputStream.closeQuietly() {
            try {
                close()
            } catch (_: IOException) {
            }
        }
    }
}
