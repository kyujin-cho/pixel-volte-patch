package dev.bluehouse.enablevolte.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.components.ConfirmDialog
import dev.bluehouse.enablevolte.components.exitKindLabel
import dev.bluehouse.enablevolte.components.reportSummary
import dev.bluehouse.enablevolte.diagnostics.CrashReport
import dev.bluehouse.enablevolte.diagnostics.CrashReports
import dev.bluehouse.enablevolte.diagnostics.ReportSharing
import dev.bluehouse.enablevolte.reportRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lists the stored crash and ANR reports; opens one, deletes one, or deletes all. */
@Suppress("ktlint:standard:function-naming")
@Composable
fun CrashReportList(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by CrashReports.revision
    var reports by remember { mutableStateOf<List<CrashReport>?>(null) }
    var pendingDelete by remember { mutableStateOf<CrashReport?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    LaunchedEffect(revision) {
        reports = withContext(Dispatchers.IO) { CrashReports.store(context).list() }
    }

    fun delete(report: CrashReport?) {
        scope.launch(Dispatchers.IO) {
            val store = CrashReports.store(context)
            if (report == null) store.deleteAll() else store.delete(report.id)
            ReportSharing.clearExport(context)
        }
    }

    val current = reports
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (current != null && current.isEmpty()) {
            Text(
                text = stringResource(R.string.crash_reports_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        if (!current.isNullOrEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDeleteAll = true }) { Text(stringResource(R.string.delete_all)) }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(current, key = { it.id }) { report ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { navController.navigate(reportRoute(report.id)) }
                                .padding(vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = exitKindLabel(report.kind), style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = reportSummary(report),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                            )
                        }
                        IconButton(onClick = { pendingDelete = report }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    pendingDelete?.let { report ->
        ConfirmDialog(
            message = stringResource(R.string.delete_report_confirm),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { delete(report) },
            onDismiss = { pendingDelete = null },
        )
    }
    if (confirmDeleteAll) {
        ConfirmDialog(
            message = stringResource(R.string.delete_all_reports_confirm),
            confirmLabel = stringResource(R.string.delete_all),
            onConfirm = { delete(null) },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}
