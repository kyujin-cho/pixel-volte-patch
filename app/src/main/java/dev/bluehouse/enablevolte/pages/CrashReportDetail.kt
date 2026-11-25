package dev.bluehouse.enablevolte.pages

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.ConfirmDialog
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.components.exitKindLabel
import dev.bluehouse.enablevolte.components.reportSummary
import dev.bluehouse.enablevolte.diagnostics.CrashReport
import dev.bluehouse.enablevolte.diagnostics.CrashReports
import dev.bluehouse.enablevolte.diagnostics.ExitKind
import dev.bluehouse.enablevolte.diagnostics.ReportExporter
import dev.bluehouse.enablevolte.diagnostics.ReportSharing
import dev.bluehouse.enablevolte.diagnostics.SystemDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private const val DETAIL_TAG = "CrashReportDetail"

/** Outlives the page, for work that must finish after the user navigates away. */
private val detachedScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

/** Lines per preview item, so a multi-megabyte log is laid out lazily. */
private const val PREVIEW_CHUNK_LINES = 100

/** Preview of one zip entry; [chunks] is null for a binary file. */
private class PreviewFile(
    val file: ReportExporter.ExportedFile,
    val sizeBytes: Long,
    val chunks: List<String>?,
)

/**
 * One stored report: opt in to system diagnostics, prepare and preview the masked export, then
 * save or share the zip or open a prefilled GitHub issue. Opening the page acknowledges the report.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun CrashReportDetail(
    navController: NavController,
    reportId: String,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<CrashReport?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var includeSystem by rememberSaveable { mutableStateOf(false) }
    var includeTombstone by rememberSaveable { mutableStateOf(false) }
    var preparing by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<ReportSharing.Prepared?>(null) }
    var preview by remember { mutableStateOf<List<PreviewFile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var availability by remember { mutableStateOf(SystemDiagnostics.availability()) }
    val systemUsable = isUsable(availability)

    // Shizuku may have been started or granted while the user was away.
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) availability = SystemDiagnostics.availability()
    }

    LaunchedEffect(reportId) {
        report =
            withContext(Dispatchers.IO) {
                CrashReports.store(context).run { get(reportId)?.also { acknowledge(it.id) } }
            }
        loaded = true
    }

    val saveLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            val zip = prepared?.export?.zip
            // Not the composition scope: leaving the page must not cut the copy short.
            if (uri != null && zip != null) detachedScope.launch { saveZip(context.applicationContext, zip, uri) }
        }

    fun prepare() {
        preparing = true
        error = null
        scope.launch {
            try {
                val result = ReportSharing.prepare(context, reportId, includeSystem && systemUsable, includeTombstone)
                if (result == null) {
                    report = null
                } else {
                    preview = withContext(Dispatchers.IO) { result.export.files.map { loadPreview(it) } }
                    prepared = result
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(DETAIL_TAG, "Preparing report $reportId failed", e)
                error = e.message ?: e.javaClass.simpleName
            } finally {
                preparing = false
            }
        }
    }

    val current = report
    if (!loaded) return
    if (current == null) {
        Text(stringResource(R.string.report_not_found), modifier = Modifier.padding(16.dp))
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(exitKindLabel(current.kind), style = MaterialTheme.typography.titleLarge)
                    Text(reportSummary(current), color = MaterialTheme.colorScheme.outline)
                    current.details.appVersion?.let {
                        Text(stringResource(R.string.report_app_version, it), color = MaterialTheme.colorScheme.outline)
                    }
                    current.details.description?.let { Text(it, color = MaterialTheme.colorScheme.outline) }
                    TextButton(onClick = { confirmDelete = true }, enabled = !preparing, contentPadding = TextButtonPadding) {
                        Text(stringResource(R.string.delete))
                    }
                }
                HorizontalDivider()
                SystemDiagnosticsOption(
                    checked = includeSystem,
                    availability = availability,
                    enabled = !preparing,
                    onCheckedChange = {
                        includeSystem = it
                        // The preview no longer matches what would be exported.
                        prepared = null
                        preview = emptyList()
                    },
                )
                if (current.kind == ExitKind.NATIVE_CRASH && current.hasPayload) {
                    OptionSwitch(
                        title = stringResource(R.string.include_tombstone),
                        description = stringResource(R.string.include_tombstone_description),
                        checked = includeTombstone,
                        enabled = !preparing,
                        onCheckedChange = {
                            includeTombstone = it
                            prepared = null
                            preview = emptyList()
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.report_preview_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                if (prepared == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        Button(onClick = { prepare() }, enabled = !preparing) { Text(stringResource(R.string.prepare_report)) }
                        if (preparing) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 16.dp).size(24.dp))
                        }
                    }
                }
                error?.let {
                    Text(stringResource(R.string.report_prepare_failed, it), color = MaterialTheme.colorScheme.error)
                }
            }
            for (entry in preview) {
                item { PreviewHeader(context, entry) }
                entry.chunks?.let { chunks ->
                    items(chunks) { chunk ->
                        Text(text = chunk, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
        prepared?.let { ready ->
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { saveLauncher.launch(ready.export.zip.name) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.save_report), maxLines = 1)
                    }
                    OutlinedButton(onClick = {
                        val chooser = ReportSharing.shareIntent(context, ready, context.getString(R.string.share_report))
                        startActivitySafely(context, chooser)
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.share_report), maxLines = 1)
                    }
                    Button(onClick = {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.github_issue_attach_hint, ready.export.zip.name),
                                Toast.LENGTH_LONG,
                            ).show()
                        startActivitySafely(context, ReportSharing.gitHubIssueIntent(ready))
                    }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.open_github_issue), maxLines = 1)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            message = stringResource(R.string.delete_report_confirm),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    withContext(Dispatchers.IO) {
                        CrashReports.store(context).delete(reportId)
                        ReportSharing.clearExport(context)
                    }
                    navController.popBackStack()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private val TextButtonPadding = PaddingValues(0.dp)

/** Whether system diagnostics can be requested; a UserService that is still connecting is fine. */
private fun isUsable(availability: SystemDiagnostics.Availability): Boolean {
    val reason = (availability as? SystemDiagnostics.Availability.Unavailable)?.reason
    return reason == null || reason == SystemDiagnostics.UnavailableReason.SERVICE_NOT_CONNECTED
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun SystemDiagnosticsOption(
    checked: Boolean,
    availability: SystemDiagnostics.Availability,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val usable = isUsable(availability)
    OptionSwitch(
        title = stringResource(R.string.include_system_diagnostics),
        description =
            when ((availability as? SystemDiagnostics.Availability.Unavailable)?.reason) {
                SystemDiagnostics.UnavailableReason.SHIZUKU_NOT_RUNNING ->
                    stringResource(R.string.system_diagnostics_unavailable_not_running)
                SystemDiagnostics.UnavailableReason.PERMISSION_NOT_GRANTED ->
                    stringResource(R.string.system_diagnostics_unavailable_no_permission)
                else -> stringResource(R.string.include_system_diagnostics_description)
            },
        checked = checked && usable,
        enabled = enabled && usable,
        onCheckedChange = onCheckedChange,
    )
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun OptionSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PreviewHeader(
    context: Context,
    entry: PreviewFile,
) {
    val file = entry.file
    val title =
        when {
            !file.text -> stringResource(R.string.report_preview_binary, file.name, Formatter.formatShortFileSize(context, entry.sizeBytes))
            file.maskedCount > 0 -> stringResource(R.string.report_preview_masked, file.name, file.maskedCount)
            else -> file.name
        }
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        HorizontalDivider()
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

private fun loadPreview(file: ReportExporter.ExportedFile): PreviewFile {
    val chunks =
        if (file.text) {
            file.file.useLines { lines -> lines.chunked(PREVIEW_CHUNK_LINES).map { it.joinToString("\n") }.toList() }
        } else {
            null
        }
    return PreviewFile(file, file.file.length(), chunks)
}

private suspend fun saveZip(
    context: Context,
    zip: File,
    target: Uri,
) {
    val saved =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(target)?.use { out -> zip.inputStream().use { it.copyTo(out) } } != null
            } catch (e: IOException) {
                Log.w(DETAIL_TAG, "Saving the report failed", e)
                false
            } catch (e: SecurityException) {
                Log.w(DETAIL_TAG, "Saving the report failed", e)
                false
            }
        }
    Toast.makeText(context, if (saved) R.string.report_saved else R.string.report_save_failed, Toast.LENGTH_SHORT).show()
}

private fun startActivitySafely(
    context: Context,
    intent: Intent,
) {
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.no_app_to_open, Toast.LENGTH_SHORT).show()
    }
}
