package dev.bluehouse.enablevolte.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.isShizukuPermissionGranted

/** Reads the [ReportEnvironment] of this device (kept apart so the data class stays JVM-testable). */
object ReportEnvironmentReader {
    private const val TAG = "ReportEnvironment"

    /**
     * Reads the environment of this device. Lists the SIMs through Shizuku when the permission
     * is granted (Binder calls: call off the main thread).
     */
    fun collect(context: Context): ReportEnvironment {
        val shizukuGranted = isShizukuPermissionGranted()
        return ReportEnvironment(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            buildId = Build.DISPLAY.orEmpty(),
            fingerprint = Build.FINGERPRINT.orEmpty(),
            basebandVersion = Build.getRadioVersion()?.takeIf { it.isNotBlank() },
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE,
            appBuildType = BuildConfig.BUILD_TYPE,
            installSource = installSource(context),
            shizukuGranted = shizukuGranted,
            carriers = if (shizukuGranted) carriers(context) else null,
        )
    }

    private fun installSource(context: Context): String? =
        ReportEnvironment.describeInstaller(installerPackage(context)) ?: "other (not installed from a store)"

    /** The package that installed this app, or null when unknown. */
    fun installerPackage(context: Context): String? =
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(context.packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: RuntimeException) {
            null
        }

    private fun carriers(context: Context): List<ReportEnvironment.Carrier>? =
        try {
            CarrierModer(context).subscriptions.sortedBy { it.simSlotIndex }.map { info ->
                ReportEnvironment.Carrier(
                    slot = info.simSlotIndex + 1,
                    name = info.carrierName?.toString(),
                    mccMnc =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && info.mccString != null && info.mncString != null) {
                            "${info.mccString}-${info.mncString}"
                        } else {
                            null
                        },
                    countryIso = info.countryIso,
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Could not list SIMs: ${e.javaClass.simpleName}")
            null
        }
}
