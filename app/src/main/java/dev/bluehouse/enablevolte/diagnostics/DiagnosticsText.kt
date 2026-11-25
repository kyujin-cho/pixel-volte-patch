package dev.bluehouse.enablevolte.diagnostics

/**
 * Keeps only the last [capacity] bytes written to it, while counting every byte. Used to bound
 * command output to its most recent part (the end of a log is the part closest to the failure).
 */
class TailBuffer(
    private val capacity: Int,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val buffer = ByteArray(capacity)

    /** Number of bytes written in total, including the ones that were dropped. */
    var totalBytes: Long = 0
        private set

    val truncated: Boolean
        get() = totalBytes > capacity

    fun write(
        source: ByteArray,
        offset: Int = 0,
        length: Int = source.size,
    ) {
        var srcOffset = offset
        var remaining = length
        if (remaining > capacity) {
            // Only the last `capacity` bytes of this chunk can survive.
            val skipped = remaining - capacity
            totalBytes += skipped
            srcOffset += skipped
            remaining = capacity
        }
        while (remaining > 0) {
            val position = (totalBytes % capacity).toInt()
            val chunk = minOf(remaining, capacity - position)
            System.arraycopy(source, srcOffset, buffer, position, chunk)
            totalBytes += chunk
            srcOffset += chunk
            remaining -= chunk
        }
    }

    fun toByteArray(): ByteArray {
        if (!truncated) return buffer.copyOf(totalBytes.toInt())
        val position = (totalBytes % capacity).toInt()
        val result = ByteArray(capacity)
        System.arraycopy(buffer, position, result, 0, capacity - position)
        System.arraycopy(buffer, 0, result, capacity - position, position)
        return result
    }
}

object DiagnosticsText {
    /** Lines of `=` that `dumpsys dropbox --print` writes before every entry. */
    private val dropboxSeparator = Regex("^={20,}\\s*$")

    data class DropboxFilterResult(
        val text: String,
        val totalEntries: Int,
        val keptEntries: Int,
    )

    /**
     * Drops the (possibly partial) first line of a tail-truncated log so the text starts at a line
     * boundary. Returns [text] unchanged when it contains no line break.
     */
    fun dropPartialFirstLine(text: String): String {
        val newline = text.indexOf('\n')
        return if (newline < 0) text else text.substring(newline + 1)
    }

    /**
     * Keeps the preamble of `dumpsys dropbox --print <tag>` output plus only the entries that
     * belong to [packageName] (a `Process:` or `Package:` header naming the package or one of its
     * processes, such as `pkg:privileged`). Entries of other apps are removed entirely.
     */
    fun filterDropboxEntries(
        text: String,
        packageName: String,
    ): DropboxFilterResult {
        val ownerLine = Regex("^(?:Process|Package): " + Regex.escape(packageName) + "(?:[:\\s].*)?$")
        val preamble = StringBuilder()
        val kept = StringBuilder()
        var entry: StringBuilder? = null
        var entryMatches = false
        var total = 0
        var keptCount = 0

        fun finishEntry() {
            val current = entry ?: return
            if (entryMatches) {
                kept.append(current)
                keptCount++
            }
        }

        for (line in text.lineSequence()) {
            if (dropboxSeparator.matches(line)) {
                finishEntry()
                entry = StringBuilder()
                entryMatches = false
                total++
            }
            val target = entry ?: preamble
            target.append(line).append('\n')
            if (entry != null && !entryMatches && ownerLine.matches(line)) entryMatches = true
        }
        finishEntry()
        return DropboxFilterResult(preamble.toString().trimEnd() + "\n" + kept.toString(), total, keptCount)
    }
}
