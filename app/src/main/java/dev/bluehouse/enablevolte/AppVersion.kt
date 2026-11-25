package dev.bluehouse.enablevolte

import net.swiftzer.semver.SemVer

/**
 * A GitHub release as far as the update check cares about it.
 */
data class ReleaseInfo(
    val tagName: String,
    val prerelease: Boolean,
    val draft: Boolean,
)

// Beta tags such as "1.5.0b1" are not valid SemVer; read them as "1.5.0-b1" so they sort below "1.5.0".
private val SHORT_PRERELEASE = Regex("""^(\d+\.\d+\.\d+)([A-Za-z]+\d*)$""")

/**
 * Parses an app version or release tag, accepting a leading "v" and short pre-release
 * suffixes like "1.5.0b1". Returns null instead of throwing when the string is not a version.
 */
fun parseAppVersion(version: String): SemVer? {
    val trimmed = version.trim().removePrefix("v")
    runCatching { return SemVer.parse(trimmed) }
    val match = SHORT_PRERELEASE.matchEntire(trimmed) ?: return null
    return runCatching { SemVer.parse("${match.groupValues[1]}-${match.groupValues[2]}") }.getOrNull()
}

/**
 * Returns the tag of the first published, non-pre-release entry. GitHub lists releases newest
 * first, and beta releases must not prompt users of stable builds to update.
 */
fun latestStableTag(releases: List<ReleaseInfo>): String? = releases.firstOrNull { !it.prerelease && !it.draft }?.tagName

/**
 * True when [latest] is a newer version than [current]. Unparseable input never counts as newer.
 */
fun isNewerVersion(
    latest: String,
    current: String,
): Boolean {
    val latestVersion = parseAppVersion(latest) ?: return false
    val currentVersion = parseAppVersion(current) ?: return false
    return latestVersion > currentVersion
}
