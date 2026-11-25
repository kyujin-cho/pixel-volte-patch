package dev.bluehouse.enablevolte.diagnostics

/** Why a previous process of the app died, for the exits that become stored reports. */
enum class ExitKind(
    /** Stable token used in report ids and metadata. */
    val token: String,
    /** File name the payload should get when it is exported (for example inside a zip). */
    val payloadName: String,
    /** Whether the payload is text. Binary payloads are never given a truncation marker. */
    val textPayload: Boolean,
) {
    /** `ApplicationExitInfo.REASON_ANR`; the payload is the system ANR trace. */
    ANR("anr", "anr-trace.txt", true),

    /** `ApplicationExitInfo.REASON_CRASH`; the payload is the stack trace our handler wrote. */
    JAVA_CRASH("crash", "java-crash.txt", true),

    /** `ApplicationExitInfo.REASON_CRASH_NATIVE`; the payload is the raw tombstone protobuf. */
    NATIVE_CRASH("native", "tombstone.pb", false),
    ;

    companion object {
        fun fromToken(token: String?): ExitKind? = entries.firstOrNull { it.token == token }
    }
}

/**
 * What is known about one process exit. Fields other than [kind], [timestamp] and [pid] are
 * optional because a Java crash can be stored without a matching system exit record.
 */
data class ExitDetails(
    val kind: ExitKind,
    /** Wall-clock time of the exit in milliseconds since the epoch. */
    val timestamp: Long,
    val pid: Int,
    val processName: String? = null,
    /** `ApplicationExitInfo.getDescription()`. */
    val description: String? = null,
    /** `ApplicationExitInfo.getImportance()`. */
    val importance: Int? = null,
    /** Last known PSS in kB. */
    val pssKb: Long? = null,
    /** Last known RSS in kB. */
    val rssKb: Long? = null,
    /** `ApplicationExitInfo.getStatus()` (signal number for native crashes). */
    val status: Int? = null,
    /** Version name of the app that recorded the report. */
    val appVersion: String? = null,
) {
    /** Deterministic id, so the same exit can never be stored twice. */
    val reportId: String get() = "${kind.token}-$timestamp-$pid"
}

/** A stored crash or ANR report, as listed by [CrashReportStore]. */
data class CrashReport(
    /** Directory name of the report; pass it to the [CrashReportStore] methods. */
    val id: String,
    val details: ExitDetails,
    /** Whether a payload (trace, stack trace or tombstone) was stored. */
    val hasPayload: Boolean,
    /** Whether the payload was cut at [CrashReportStore.maxPayloadBytes]. */
    val payloadTruncated: Boolean,
    /** Whether the user has already handled (shared or dismissed) this report. */
    val acknowledged: Boolean,
    /** Size of the report on disk, in bytes. */
    val sizeBytes: Long,
) {
    val kind: ExitKind get() = details.kind
    val timestamp: Long get() = details.timestamp

    /** File name to use for the payload when it is exported. */
    val payloadName: String get() = details.kind.payloadName
}
