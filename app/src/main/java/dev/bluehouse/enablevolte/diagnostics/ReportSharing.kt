package dev.bluehouse.enablevolte.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dev.bluehouse.enablevolte.isShizukuPermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android side of exporting a crash or ANR report: gathers the environment, the device's own
 * identifiers (for masking) and, after the user opted in, the system diagnostics; builds the zip
 * with [ReportExporter]; and creates the share and GitHub issue intents.
 */
object ReportSharing {
    private const val TAG = "ReportSharing"

    /** Directory served by the FileProvider (see `res/xml/report_export_paths.xml`). */
    private const val EXPORT_DIR = "report-export"

    data class Prepared(
        val export: ReportExporter.Export,
        val environment: ReportEnvironment,
    )

    fun authority(context: Context) = "${context.packageName}.reports"

    fun exporter(context: Context) = ReportExporter(CrashReports.store(context), File(context.cacheDir, EXPORT_DIR))

    /**
     * Builds the export of report [reportId]; collects system diagnostics only when
     * [includeSystemDiagnostics] is true, and the unmaskable native tombstone only when
     * [includeBinaryPayload] is true. Returns null if the report no longer exists. Runs on
     * [Dispatchers.IO]; throws [java.io.IOException] when the zip cannot be written.
     */
    suspend fun prepare(
        context: Context,
        reportId: String,
        includeSystemDiagnostics: Boolean,
        includeBinaryPayload: Boolean,
    ): Prepared? =
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val report = CrashReports.store(appContext).get(reportId) ?: return@withContext null
            val environment = ReportEnvironmentReader.collect(appContext)
            val knownIdentifiers =
                if (isShizukuPermissionGranted()) {
                    try {
                        SubscriberIdentifiers(appContext).collect()
                    } catch (e: Throwable) {
                        Log.w(TAG, "Could not look up subscriber identifiers: ${e.javaClass.simpleName}")
                        emptySet()
                    }
                } else {
                    emptySet()
                }
            val system =
                if (!includeSystemDiagnostics) {
                    ReportExporter.SystemPart.NotRequested
                } else {
                    when (val result = SystemDiagnostics.collect(appContext)) {
                        is SystemDiagnostics.Result.Unavailable -> ReportExporter.SystemPart.Unavailable(result.reason.name)
                        is SystemDiagnostics.Result.Collected -> ReportExporter.SystemPart.Collected(result.sections)
                    }
                }
            try {
                Prepared(
                    exporter(appContext).prepare(report, environment.format(), knownIdentifiers, system, includeBinaryPayload),
                    environment,
                )
            } finally {
                // The masked files were copied into the export; do not keep a second copy around.
                if (includeSystemDiagnostics) SystemDiagnostics.clear(appContext)
            }
        }

    /** Deletes the current export (zip and staged files). Does file I/O. */
    fun clearExport(context: Context) {
        exporter(context.applicationContext).clear()
    }

    /**
     * A chooser for sending the zip. The content URI is readable only by the app the user picks:
     * the read grant travels with the intent's ClipData and is not given to anyone else.
     */
    fun shareIntent(
        context: Context,
        prepared: Prepared,
        chooserTitle: CharSequence,
    ): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), prepared.export.zip)
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, GitHubIssueLink.title(prepared.export.report.kind, prepared.environment))
                clipData = ClipData.newRawUri(prepared.export.zip.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(send, chooserTitle)
    }

    /** Opens the prefilled bug report form in the browser. */
    fun gitHubIssueIntent(prepared: Prepared): Intent {
        val url = GitHubIssueLink.build(prepared.export.report, prepared.environment, prepared.export.zip.name)
        return Intent(Intent.ACTION_VIEW, url.toUri())
    }
}
