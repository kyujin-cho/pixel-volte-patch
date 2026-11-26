package dev.bluehouse.enablevolte.pages

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.REPORTS_ROUTE
import dev.bluehouse.enablevolte.ShizukuStatus
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.UpdateChannel
import dev.bluehouse.enablevolte.checkShizukuPermission
import dev.bluehouse.enablevolte.components.BooleanPropertyView
import dev.bluehouse.enablevolte.components.ClickablePropertyView
import dev.bluehouse.enablevolte.components.CrashReportCard
import dev.bluehouse.enablevolte.components.HeaderText
import dev.bluehouse.enablevolte.components.RadioSelectPropertyView
import dev.bluehouse.enablevolte.components.StringPropertyView
import dev.bluehouse.enablevolte.diagnostics.CrashReport
import dev.bluehouse.enablevolte.diagnostics.CrashReports
import dev.bluehouse.enablevolte.diagnostics.ReportEnvironment
import dev.bluehouse.enablevolte.diagnostics.ReportEnvironmentReader
import dev.bluehouse.enablevolte.getLatestAppVersion
import dev.bluehouse.enablevolte.getUpdateChannel
import dev.bluehouse.enablevolte.isNewerVersion
import dev.bluehouse.enablevolte.parseAppVersion
import dev.bluehouse.enablevolte.reportRoute
import dev.bluehouse.enablevolte.setUpdateChannel
import dev.bluehouse.enablevolte.uniqueName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread
import kotlin.coroutines.resume

const val TAG = "HomeActivity:Home"

// Exhaustive on purpose: adding a channel without a label fails to compile instead of shifting the labels.
@StringRes
private fun UpdateChannel.labelRes(): Int =
    when (this) {
        UpdateChannel.STABLE -> R.string.update_channel_stable
        UpdateChannel.BETA -> R.string.update_channel_beta
    }

@Suppress("ktlint:standard:function-naming")
@Composable
fun Home(navController: NavController) {
    val carrierModer = CarrierModer(LocalContext.current)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var subscriptions by remember { mutableStateOf(listOf<SubscriptionInfo>()) }
    var deviceIMSEnabled by rememberSaveable { mutableStateOf(false) }

    var isIMSRegistered by remember { mutableStateOf(listOf<Boolean?>()) }
    var newerVersion by rememberSaveable { mutableStateOf("") }
    var updateChannel by remember { mutableStateOf(getUpdateChannel(context)) }
    val showUpdateChannel = remember { !ReportEnvironment.isPlayStoreInstaller(ReportEnvironmentReader.installerPackage(context)) }

    // Crash/ANR reports: checked once this launch's collection of previous exits has finished.
    val reportsCollected by CrashReports.collected
    val reportsRevision by CrashReports.revision
    var storedReports by remember { mutableStateOf<List<CrashReport>?>(null) }
    val unsentReports = storedReports.orEmpty().filter { !it.acknowledged }

    LaunchedEffect(reportsCollected, reportsRevision) {
        if (reportsCollected) {
            storedReports = withContext(Dispatchers.IO) { CrashReports.store(context).list() }
        }
    }

    // A plain thread, not a composition scope: "Send report" navigates away right after this.
    fun acknowledge(reports: List<CrashReport>) {
        val appContext = context.applicationContext
        thread(name = "CrashReportAcknowledge") {
            val store = CrashReports.store(appContext)
            reports.forEach { store.acknowledge(it.id) }
        }
    }

    fun loadFlags() {
        shizukuGranted = true
        subscriptions = carrierModer.subscriptions
        deviceIMSEnabled = carrierModer.deviceSupportsIMS

        if (subscriptions.isNotEmpty() && deviceIMSEnabled) {
            isIMSRegistered = subscriptions.map { SubscriptionModer(context, it.subscriptionId).isIMSRegistered }
        }
    }

    LaunchedEffect(Unit) {
        try {
            when (checkShizukuPermission(0)) {
                ShizukuStatus.GRANTED -> {
                    shizukuEnabled = true
                    loadFlags()
                }
                ShizukuStatus.NOT_GRANTED -> {
                    shizukuEnabled = true
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            loadFlags()
                        }
                    }
                }
                else -> {
                    shizukuEnabled = false
                }
            }
        } catch (e: IllegalStateException) {
            shizukuEnabled = false
        }
    }

    // Re-runs the update check whenever the channel changes. Changing the channel cancels this effect, so a
    // response for the previous channel that arrives late is dropped instead of overwriting the newer result.
    LaunchedEffect(updateChannel) {
        val channel = updateChannel
        val latest =
            suspendCancellableCoroutine { continuation ->
                getLatestAppVersion(channel) { if (continuation.isActive) continuation.resume(it) }
            }
        Log.d(TAG, "Fetched version $latest for channel $channel")
        newerVersion = if (isNewerVersion(latest, BuildConfig.VERSION_NAME)) latest else ""
    }

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        unsentReports.firstOrNull()?.let { newest ->
            CrashReportCard(
                newest = newest,
                unsentCount = unsentReports.size,
                onSend = {
                    acknowledge(unsentReports)
                    navController.navigate(if (unsentReports.size > 1) REPORTS_ROUTE else reportRoute(newest.id))
                },
                onDismiss = { acknowledge(unsentReports) },
            )
        }
        HeaderText(text = stringResource(R.string.version))
        if (newerVersion.isNotEmpty()) {
            ClickablePropertyView(
                label = BuildConfig.VERSION_NAME,
                value =
                    stringResource(
                        if (parseAppVersion(newerVersion)?.preRelease != null) {
                            R.string.newer_beta_version_available
                        } else {
                            R.string.newer_version_available
                        },
                        newerVersion,
                    ),
            ) {
                val url = "https://github.com/kyujin-cho/pixel-volte-patch/releases/tag/${Uri.encode(newerVersion)}"
                val i = Intent(Intent.ACTION_VIEW)
                i.data = url.toUri()
                context.startActivity(i, null)
            }
        } else {
            StringPropertyView(label = BuildConfig.VERSION_NAME, value = stringResource(R.string.running_latest_version))
        }
        if (showUpdateChannel) {
            RadioSelectPropertyView(
                label = stringResource(R.string.update_channel),
                values = UpdateChannel.entries.map { stringResource(it.labelRes()) }.toTypedArray(),
                selectedIndex = UpdateChannel.entries.indexOf(updateChannel),
                onUpdate = {
                    val selected = UpdateChannel.entries[it]
                    if (selected != updateChannel) {
                        // Hide the previous channel's prompt until the new check finishes.
                        newerVersion = ""
                        updateChannel = selected
                    }
                    setUpdateChannel(context, selected)
                },
            )
            Text(
                text = stringResource(R.string.update_channel_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = Dp(8f)),
            )
        }
        HeaderText(text = stringResource(R.string.permissions_capabilities))
        BooleanPropertyView(label = stringResource(R.string.shizuku_service_running), toggled = shizukuEnabled)
        BooleanPropertyView(label = stringResource(R.string.shizuku_permission_granted), toggled = shizukuGranted)
        BooleanPropertyView(label = stringResource(R.string.sim_detected), toggled = subscriptions.isNotEmpty())
        BooleanPropertyView(label = stringResource(R.string.volte_supported_by_device), toggled = deviceIMSEnabled)

        for (idx in subscriptions.indices) {
            // null (not loaded yet, or no IMS check variant works on this build) renders as "Unknown"
            val isRegistered = isIMSRegistered.getOrNull(idx)
            HeaderText(text = stringResource(R.string.ims_status_for, subscriptions[idx].uniqueName))
            BooleanPropertyView(
                label = stringResource(R.string.ims_status),
                toggled = isRegistered,
                trueLabel = stringResource(R.string.registered),
                falseLabel = stringResource(R.string.unregistered),
            )
        }

        HeaderText(text = stringResource(R.string.diagnostics))
        ClickablePropertyView(
            label = stringResource(R.string.crash_reports),
            value = storedReports?.let { stringResource(R.string.crash_reports_stored, it.size) } ?: stringResource(R.string.loading),
        ) {
            navController.navigate(REPORTS_ROUTE)
        }
    }
}
