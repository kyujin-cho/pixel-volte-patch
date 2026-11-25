package dev.bluehouse.enablevolte.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Device and app facts written to `environment.txt` in an exported report and used to prefill the
 * GitHub issue form.
 *
 * It deliberately holds no subscriber or device identifiers: carriers are described by name,
 * MCC/MNC and country only (no subscription ids, ICCIDs, numbers or user-editable SIM names).
 */
data class ReportEnvironment(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildId: String,
    val fingerprint: String,
    val basebandVersion: String?,
    val appVersionName: String,
    val appVersionCode: Int,
    val appBuildType: String,
    val installSource: String?,
    val shizukuGranted: Boolean,
    /** Null when the SIM list could not be read (Shizuku not available). */
    val carriers: List<Carrier>?,
) {
    data class Carrier(
        /** 1-based SIM slot. */
        val slot: Int,
        val name: String?,
        val mccMnc: String?,
        val countryIso: String?,
    ) {
        /** Short form, for example `LG U+ (450-06, kr)`. */
        fun describe(): String {
            val details = listOfNotNull(mccMnc, countryIso?.takeIf { it.isNotBlank() }).joinToString(", ")
            val label = name?.takeIf { it.isNotBlank() } ?: UNKNOWN
            return if (details.isEmpty()) label else "$label ($details)"
        }
    }

    /** `Pixel 8 Pro (husky)` style device description. */
    val deviceDescription: String
        get() =
            buildString {
                if (!model.startsWith(manufacturer, ignoreCase = true) && !model.startsWith("Pixel")) {
                    append(manufacturer).append(' ')
                }
                append(model)
                if (device.isNotBlank()) append(" (").append(device).append(')')
            }

    val androidDescription: String get() = "Android $androidRelease (API $sdkInt)"

    val appVersionDescription: String get() = "$appVersionName ($appVersionCode, $appBuildType)"

    /** One line per SIM, `SIM 1: LG U+ (450-06, kr)`; null when the SIM list is unavailable. */
    val carrierSummary: String?
        get() =
            carriers?.let { list ->
                if (list.isEmpty()) "No active SIM" else list.joinToString("; ") { "SIM ${it.slot}: ${it.describe()}" }
            }

    /** The `environment.txt` text (English, independent of the app language). */
    fun format(): String =
        buildString {
            appendLine("Device: $deviceDescription")
            appendLine("Android: $androidDescription")
            appendLine("Security patch: $securityPatch")
            appendLine("Build: $buildId")
            appendLine("Fingerprint: $fingerprint")
            appendLine("Baseband: ${basebandVersion ?: UNKNOWN}")
            appendLine("App version: $appVersionDescription")
            appendLine("Install source: ${installSource ?: UNKNOWN}")
            appendLine("Shizuku permission granted: ${if (shizukuGranted) "yes" else "no"}")
            if (carriers == null) {
                appendLine("SIMs: unavailable (needs Shizuku)")
            } else if (carriers.isEmpty()) {
                appendLine("SIMs: none active")
            } else {
                carriers.forEach { appendLine("SIM ${it.slot}: ${it.describe()}") }
            }
        }

    companion object {
        private const val UNKNOWN = "unknown"
        private const val PLAY_STORE = "com.android.vending"

        /** "Google Play" for the Play Store, the installer package otherwise, null if unknown. */
        internal fun describeInstaller(installer: String?): String? =
            when {
                installer.isNullOrBlank() -> null
                installer == PLAY_STORE -> "Google Play"
                else -> installer
            }

        /** `2026-11-25 14:03:12 +0900` in the device time zone. */
        fun formatTimestamp(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date(millis))
    }
}
