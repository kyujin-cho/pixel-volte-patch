package dev.bluehouse.enablevolte.diagnostics

/**
 * Masks subscriber and device identifiers (IMSI, ICCID, IMEI/MEID, MSISDN and other phone
 * numbers) in diagnostic text before it is shown in a preview or exported.
 *
 * Pure and Android-free so it can be unit tested on the JVM. Masking is best effort and errs on
 * the side of over-masking: any long run of digits is masked even when it is not an identifier
 * (for example an epoch timestamp in milliseconds). Users must still review the preview.
 */
object IdentifierMasker {
    const val MASK_ID = "[ID]"
    const val MASK_IMSI = "[IMSI]"
    const val MASK_ICCID = "[ICCID]"
    const val MASK_IMEI = "[IMEI]"
    const val MASK_PHONE = "[PHONE]"
    const val MASK_NUMBER = "[NUMBER]"

    /** Known identifiers shorter than this (in digits) are ignored to avoid masking unrelated text. */
    private const val MIN_LITERAL_DIGITS = 6

    /** Keyed values and URI user parts are masked only when they contain at least this many digits. */
    private const val MIN_VALUE_DIGITS = 5

    data class Result(
        val text: String,
        val maskedCount: Int,
    )

    private class Rule(
        val regex: Regex,
        val replace: (MatchResult) -> String?,
    )

    // key=value / key: value pairs whose key names a subscriber or device identifier.
    private val keyedValue =
        Regex(
            "(?i)(?<![A-Za-z0-9_])(m?(?:imsi|iccid|icc_?id|imeisv|imei|meid|msisdn|mdn|subscriber_?id|" +
                "line1_?number|phone_?number|impi|impu|number))(\\s*[:=]\\s*[\"']?)([^\\s,;}\\])\"'>]+)",
        )

    // SIP / tel URIs: the user part can be an MSISDN or an IMSI-based private identity.
    private val uriUserPart = Regex("(?i)(?<![A-Za-z0-9])(sips?|tel):(\\+?[0-9A-Za-z.()\\-]+)")

    private val rules =
        listOf(
            Rule(keyedValue) { match ->
                val value = match.groupValues[3]
                if (digitCount(value) < MIN_VALUE_DIGITS) {
                    null
                } else {
                    match.groupValues[1] + match.groupValues[2] + maskForKey(match.groupValues[1])
                }
            },
            Rule(uriUserPart) { match ->
                val user = match.groupValues[2]
                if (digitCount(user) < MIN_VALUE_DIGITS) {
                    null
                } else {
                    val mask = if (user.startsWith("+") || digitCount(user) < 14) MASK_PHONE else MASK_ID
                    match.groupValues[1] + ":" + mask
                }
            },
            // ICCID: 89 + 16..20 digits, sometimes with a trailing F padding nibble.
            Rule(Regex("(?<![0-9A-Za-z])89\\d{16,20}[Ff]?(?![0-9A-Za-z])")) { MASK_ICCID },
            // IMEI written with separators: 35-209900-176148-1
            Rule(Regex("(?<![0-9A-Za-z\\-])\\d{2}-\\d{6}-\\d{6}-\\d{1,2}(?![0-9A-Za-z\\-])")) { MASK_IMEI },
            // International numbers with separators: +82 10-1234-5678, +1 (555) 123-4567
            Rule(Regex("(?<![\\w+])\\+\\d{1,3}[ \\-]?\\(?\\d{1,4}\\)?[ \\-]\\d{3,4}[ \\-]\\d{4}(?![\\w\\-])")) { MASK_PHONE },
            // E.164 without separators: +821012345678
            Rule(Regex("(?<![\\w+])\\+\\d{7,15}(?!\\d)")) { MASK_PHONE },
            // National numbers with separators: 010-1234-5678, 555-123-4567, (555) 123-4567
            Rule(Regex("(?<![\\w.:\\-])(?:\\d{2,3}|\\(\\d{3}\\))[ \\-]?\\d{3,4}-\\d{4}(?![\\w\\-])")) { MASK_PHONE },
            // Any remaining run of 10+ digits: bare IMSI, IMEI, ICCID, MSISDN.
            Rule(Regex("(?<![0-9A-Za-z_.])\\d{10,}(?![0-9A-Za-z_])")) { MASK_NUMBER },
        )

    /**
     * Returns [text] with identifiers masked.
     *
     * [knownIdentifiers] are the device's own identifiers when they could be obtained (IMSI,
     * ICCID, IMEI, phone numbers); they are masked as literals wherever they appear, in addition
     * to the generic patterns.
     */
    fun mask(
        text: String,
        knownIdentifiers: Collection<String> = emptyList(),
    ): Result {
        var count = 0
        var current = text
        for (literal in literalVariants(knownIdentifiers)) {
            val regex = Regex(Regex.escape(literal), RegexOption.IGNORE_CASE)
            current =
                regex.replace(current) {
                    count++
                    MASK_ID
                }
        }
        for (rule in rules) {
            current =
                rule.regex.replace(current) { match ->
                    val replacement = rule.replace(match)
                    if (replacement == null) {
                        match.value
                    } else {
                        count++
                        replacement
                    }
                }
        }
        return Result(current, count)
    }

    /**
     * Expands known identifiers into the literal forms they may appear in (as given, digits only,
     * without the ICCID padding nibble), longest first so a longer form is masked before its parts.
     */
    internal fun literalVariants(knownIdentifiers: Collection<String>): List<String> {
        val variants = linkedSetOf<String>()
        for (raw in knownIdentifiers) {
            val trimmed = raw.trim()
            if (digitCount(trimmed) < MIN_LITERAL_DIGITS) continue
            variants += trimmed
            val digits = trimmed.filter { it.isDigit() }
            variants += digits
            if (trimmed.endsWith("F") || trimmed.endsWith("f")) variants += trimmed.dropLast(1)
        }
        return variants.filter { digitCount(it) >= MIN_LITERAL_DIGITS }.sortedByDescending { it.length }
    }

    private fun maskForKey(key: String): String {
        val lower = key.lowercase().replace("_", "")
        // Strip a field-name prefix (mImsi, mIccId) but not the m of msisdn, mdn or meid.
        val normalized = if (lower.startsWith("m") && lower.drop(1) in PREFIXABLE_KEYS) lower.drop(1) else lower
        return when (normalized) {
            "imsi", "impi", "subscriberid" -> MASK_IMSI
            "iccid" -> MASK_ICCID
            "imei", "imeisv", "meid" -> MASK_IMEI
            else -> MASK_PHONE
        }
    }

    private val PREFIXABLE_KEYS =
        setOf("imsi", "iccid", "imeisv", "imei", "impi", "impu", "subscriberid", "line1number", "phonenumber", "number")

    private fun digitCount(value: String): Int = value.count { it.isDigit() }
}
