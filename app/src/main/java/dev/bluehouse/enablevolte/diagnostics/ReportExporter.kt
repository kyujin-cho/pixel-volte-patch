package dev.bluehouse.enablevolte.diagnostics

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the shareable zip of one stored crash or ANR report.
 *
 * Every text file is masked with [IdentifierMasker] and staged under [exportRoot] before it is
 * zipped, so the preview shows exactly the bytes that are shared. The binary native tombstone
 * cannot be masked or previewed and is copied as is.
 *
 * Only one export exists at a time: [prepare] replaces the previous one. Does file I/O: call off
 * the main thread. Android-free so it can be unit tested on the JVM.
 */
class ReportExporter(
    private val store: CrashReportStore,
    private val exportRoot: File,
) {
    /** One file inside the zip. [text] files can be previewed; [file] is the staged copy. */
    data class ExportedFile(
        val name: String,
        val file: File,
        val text: Boolean,
        val maskedCount: Int,
    )

    data class Export(
        val report: CrashReport,
        val zip: File,
        val files: List<ExportedFile>,
    )

    /** System diagnostics the user opted in to, or why they are missing. */
    sealed interface SystemPart {
        object NotRequested : SystemPart

        data class Unavailable(
            val reason: String,
        ) : SystemPart

        data class Collected(
            val sections: List<SystemDiagnostics.Section>,
        ) : SystemPart
    }

    /**
     * Stages and zips [report]: `environment.txt` ([environmentText] plus the exit details and the
     * system diagnostics status), the masked exit payload, and the already masked system
     * diagnostics files under `system/`. Throws [IOException] when a file cannot be written.
     */
    fun prepare(
        report: CrashReport,
        environmentText: String,
        knownIdentifiers: Set<String>,
        system: SystemPart,
    ): Export {
        clear()
        val staging = File(exportRoot, STAGING_DIR)
        if (!staging.mkdirs()) throw IOException("cannot create $staging")

        val files = mutableListOf<ExportedFile>()
        val environment = buildEnvironment(report, environmentText, system)
        files += writeMasked(staging, ENVIRONMENT_FILE, environment, knownIdentifiers)

        if (report.hasPayload) {
            val input = store.openPayload(report.id) ?: throw IOException("cannot read the payload of ${report.id}")
            if (report.kind.textPayload) {
                val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
                files += writeMasked(staging, report.payloadName, text, knownIdentifiers)
            } else {
                val target = File(staging, report.payloadName)
                input.use { source -> target.outputStream().use { source.copyTo(it) } }
                files += ExportedFile(report.payloadName, target, text = false, maskedCount = 0)
            }
        }

        if (system is SystemPart.Collected) {
            val systemDir = File(staging, SYSTEM_DIR).apply { mkdirs() }
            for (section in system.sections) {
                val source = section.file ?: continue
                val target = File(systemDir, section.source.fileName)
                source.copyTo(target, overwrite = true)
                files += ExportedFile("$SYSTEM_DIR/${section.source.fileName}", target, text = true, maskedCount = section.maskedCount)
            }
        }

        val zip = File(exportRoot, zipName(report))
        writeZip(zip, files)
        return Export(report, zip, files)
    }

    /** Deletes the current export, if any. */
    fun clear() {
        exportRoot.deleteRecursively()
        exportRoot.mkdirs()
    }

    private fun writeMasked(
        dir: File,
        name: String,
        text: String,
        knownIdentifiers: Set<String>,
    ): ExportedFile {
        val masked = IdentifierMasker.mask(text, knownIdentifiers)
        val file = File(dir, name)
        file.writeText(masked.text)
        return ExportedFile(name, file, text = true, maskedCount = masked.maskedCount)
    }

    private fun writeZip(
        zip: File,
        files: List<ExportedFile>,
    ) {
        val tmp = File(zip.parentFile, zip.name + ".tmp")
        ZipOutputStream(tmp.outputStream().buffered()).use { out ->
            for (exported in files) {
                out.putNextEntry(ZipEntry(exported.name))
                exported.file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        if (!tmp.renameTo(zip)) throw IOException("cannot create $zip")
    }

    companion object {
        const val ENVIRONMENT_FILE = "environment.txt"
        const val SYSTEM_DIR = "system"
        private const val STAGING_DIR = "staging"

        fun zipName(report: CrashReport) = "pixel-ims-report-${report.id}.zip"

        internal fun buildEnvironment(
            report: CrashReport,
            environmentText: String,
            system: SystemPart,
        ): String =
            buildString {
                appendLine("# Pixel IMS crash/ANR report")
                appendLine("Masking of subscriber identifiers is best effort; review before sharing.")
                appendLine()
                appendLine("## Exit")
                val details = report.details
                appendLine("Kind: ${report.kind.name}")
                appendLine("Time: ${ReportEnvironment.formatTimestamp(report.timestamp)}")
                details.appVersion?.let { appendLine("App version at exit: $it") }
                details.processName?.let { appendLine("Process: $it") }
                details.description?.let { appendLine("Description: $it") }
                details.importance?.let { appendLine("Importance: $it") }
                details.status?.let { appendLine("Status: $it") }
                details.pssKb?.let { appendLine("PSS: $it kB") }
                details.rssKb?.let { appendLine("RSS: $it kB") }
                val payload =
                    when {
                        !report.hasPayload -> "none"
                        report.payloadTruncated -> "${report.payloadName} (truncated)"
                        else -> report.payloadName
                    }
                appendLine("Payload: $payload")
                appendLine()
                appendLine("## Environment")
                append(environmentText.trimEnd()).append('\n')
                appendLine()
                appendLine("## System diagnostics")
                when (system) {
                    SystemPart.NotRequested -> appendLine("Not included")
                    is SystemPart.Unavailable -> appendLine("Requested but unavailable: ${system.reason}")
                    is SystemPart.Collected ->
                        system.sections.forEach { section ->
                            val status =
                                buildList {
                                    if (section.file == null) add("not collected") else add("$SYSTEM_DIR/${section.source.fileName}")
                                    section.error?.let { add("error: $it") }
                                    if (section.truncated) add("truncated")
                                    if (section.timedOut) add("timed out")
                                }.joinToString(", ")
                            appendLine("${section.source.command}: $status")
                        }
                }
            }
    }
}
