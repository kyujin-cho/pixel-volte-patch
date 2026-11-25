package dev.bluehouse.enablevolte.diagnostics

import java.net.URLEncoder

/**
 * Builds the "new issue" URL that opens the repository's bug report issue form
 * (`.github/ISSUE_TEMPLATE/bug_report.yml`) with fields prefilled.
 *
 * Only short, non-identifying values go into the URL: title, device, Android version, security
 * patch, app version, carrier names and a note about the zip to attach. Traces and logs never do
 * (URLs have length limits and end up in browser history); the user attaches the zip instead.
 */
object GitHubIssueLink {
    const val NEW_ISSUE_URL = "https://github.com/kyujin-cho/pixel-volte-patch/issues/new"
    const val TEMPLATE = "bug_report.yml"

    /**
     * Ids of the issue form fields. The form must keep these ids: changing one silently breaks
     * prefilling from released app versions.
     */
    object Field {
        const val DEVICE_MODEL = "device-model"
        const val ANDROID_VERSION = "android-version"
        const val SECURITY_PATCH = "security-patch"
        const val APP_VERSION = "app-version"
        const val CARRIER = "carrier"
        const val LOGS = "logs"
    }

    /** Values are cut to this many characters so the URL stays short. */
    private const val MAX_VALUE_LENGTH = 200

    fun build(
        report: CrashReport,
        environment: ReportEnvironment,
        zipName: String,
    ): String {
        val params =
            linkedMapOf(
                "template" to TEMPLATE,
                "title" to title(report.kind, environment),
                Field.DEVICE_MODEL to environment.deviceDescription,
                Field.ANDROID_VERSION to environment.androidDescription,
                Field.SECURITY_PATCH to environment.securityPatch,
                Field.APP_VERSION to environment.appVersionDescription,
                Field.CARRIER to environment.carrierSummary.orEmpty(),
                Field.LOGS to logsNote(report, zipName),
            )
        return NEW_ISSUE_URL + "?" +
            params.entries
                .filter { it.value.isNotBlank() }
                .joinToString("&") { (key, value) -> "${encode(key)}=${encode(value.take(MAX_VALUE_LENGTH))}" }
    }

    fun title(
        kind: ExitKind,
        environment: ReportEnvironment,
    ): String {
        val what =
            when (kind) {
                ExitKind.ANR -> "App stopped responding (ANR)"
                ExitKind.JAVA_CRASH -> "App crashed"
                ExitKind.NATIVE_CRASH -> "App crashed (native)"
            }
        return "$what on ${environment.model}, Android ${environment.androidRelease}"
    }

    private fun logsNote(
        report: CrashReport,
        zipName: String,
    ): String =
        "Pixel IMS ${report.kind.name} report from ${ReportEnvironment.formatTimestamp(report.timestamp)}.\n" +
            "Attach $zipName here (drag the file into this box)."

    /** Percent-encoding for a query component; spaces become `%20` rather than `+`. */
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
