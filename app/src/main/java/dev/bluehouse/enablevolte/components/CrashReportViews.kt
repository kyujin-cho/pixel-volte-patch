package dev.bluehouse.enablevolte.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.diagnostics.CrashReport
import dev.bluehouse.enablevolte.diagnostics.ExitDetails
import dev.bluehouse.enablevolte.diagnostics.ExitKind
import java.text.DateFormat
import java.util.Date

@Composable
fun exitKindLabel(kind: ExitKind): String =
    when (kind) {
        ExitKind.ANR -> stringResource(R.string.report_kind_anr)
        ExitKind.JAVA_CRASH -> stringResource(R.string.report_kind_crash)
        ExitKind.NATIVE_CRASH -> stringResource(R.string.report_kind_native_crash)
    }

/** Localized date and time of a report, for example `Nov 25, 2026, 2:03 PM`. */
fun formatReportTime(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

/** `Nov 25, 2026, 2:03 PM · 120 kB`. */
@Composable
fun reportSummary(report: CrashReport): String =
    formatReportTime(report.timestamp) + " · " + Formatter.formatShortFileSize(LocalContext.current, report.sizeBytes)

/**
 * Home page card shown after the app crashed or stopped responding: offers to send the newest
 * unacknowledged report. [unsentCount] is the number of reports the card stands for.
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun CrashReportCard(
    newest: CrashReport,
    unsentCount: Int,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)) {
            Text(
                text =
                    stringResource(
                        if (newest.kind == ExitKind.ANR) R.string.crash_report_card_anr else R.string.crash_report_card_crash,
                    ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 8.dp),
            )
            Text(
                text =
                    if (unsentCount > 1) {
                        stringResource(R.string.crash_report_card_unsent, unsentCount)
                    } else {
                        reportSummary(newest)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                TextButton(onClick = onSend) { Text(stringResource(R.string.send_report)) }
            }
        }
    }
}

/** Yes/no confirmation for destructive actions. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun ConfirmDialog(
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Suppress("ktlint:standard:function-naming")
@Preview
@Composable
fun CrashReportCardPreview() {
    val report =
        CrashReport(
            id = "anr-1764047000000-1234",
            details = ExitDetails(ExitKind.ANR, 1764047000000L, 1234),
            hasPayload = true,
            payloadTruncated = false,
            acknowledged = false,
            sizeBytes = 120_000,
        )
    CrashReportCard(report, 1, {}, {})
}
