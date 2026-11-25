package dev.bluehouse.enablevolte.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.bluehouse.enablevolte.ApplyResultTracker
import dev.bluehouse.enablevolte.ApplyState
import dev.bluehouse.enablevolte.ApplyStatus
import dev.bluehouse.enablevolte.ImsCheckState
import dev.bluehouse.enablevolte.R

/** Shows whether the latest carrier config change was applied and whether IMS registered after it. */
@Composable
fun ApplyResultView(status: ApplyStatus?) {
    if (status == null) return
    val timeoutSeconds = (ApplyResultTracker.IMS_POLL_TIMEOUT_MS / 1000).toInt()

    HeaderText(text = stringResource(R.string.apply_result))
    ClickablePropertyView(
        label = stringResource(R.string.apply_result_state),
        value =
            when (status.applyState) {
                ApplyState.APPLYING -> stringResource(R.string.apply_result_applying)
                ApplyState.APPLIED -> stringResource(R.string.apply_result_applied)
                ApplyState.FAILED -> stringResource(R.string.apply_result_failed, status.error ?: "")
            },
    )
    val imsState = status.imsState ?: return
    ClickablePropertyView(
        label = stringResource(R.string.apply_result_ims),
        value =
            when (imsState) {
                ImsCheckState.CHECKING -> stringResource(R.string.apply_result_ims_checking, timeoutSeconds)
                ImsCheckState.REGISTERED -> stringResource(R.string.registered)
                ImsCheckState.NOT_REGISTERED -> stringResource(R.string.apply_result_ims_not_registered, timeoutSeconds)
                ImsCheckState.UNKNOWN -> stringResource(R.string.unknown)
            },
    )
}
