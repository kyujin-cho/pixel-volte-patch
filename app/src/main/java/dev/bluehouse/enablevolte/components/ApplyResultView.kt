package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ApplyResultTracker
import dev.bluehouse.enablevolte.ApplyState
import dev.bluehouse.enablevolte.ApplyStatus
import dev.bluehouse.enablevolte.ImsCheckState
import dev.bluehouse.enablevolte.R

// Shown while the IMS check has not started yet, so the bar keeps both rows in every state.
private const val NO_VALUE = "—"

/**
 * Bar showing whether the latest carrier config change was applied and whether IMS registered
 * after it.
 *
 * Meant to be placed below the scrolling toggle list, not inside it. Every state renders the same
 * three single-line rows (long failure reasons are ellipsized; the full reason is in the Toast), so
 * the bar's height never changes while the status updates.
 */
@Composable
fun ApplyResultView(status: ApplyStatus?) {
    if (status == null) return
    val timeoutSeconds = (ApplyResultTracker.IMS_POLL_TIMEOUT_MS / 1000).toInt()

    Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        Column {
            HorizontalDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.apply_result),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusRow(
                    label = stringResource(R.string.apply_result_state),
                    value =
                        when (status.applyState) {
                            ApplyState.APPLYING -> stringResource(R.string.apply_result_applying)
                            ApplyState.APPLIED -> stringResource(R.string.apply_result_applied)
                            ApplyState.FAILED -> stringResource(R.string.apply_result_failed, status.error ?: "")
                        },
                    valueColor =
                        if (status.applyState == ApplyState.FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                )
                StatusRow(
                    label = stringResource(R.string.apply_result_ims),
                    value =
                        when (status.imsState) {
                            null -> NO_VALUE
                            ImsCheckState.CHECKING -> stringResource(R.string.apply_result_ims_checking, timeoutSeconds)
                            ImsCheckState.REGISTERED -> stringResource(R.string.registered)
                            ImsCheckState.NOT_REGISTERED -> stringResource(R.string.apply_result_ims_not_registered, timeoutSeconds)
                            ImsCheckState.UNKNOWN -> stringResource(R.string.unknown)
                        },
                    valueColor = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
