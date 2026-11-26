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
fun latestStableTag(releases: List<ReleaseInfo>): String? = releases.firstOrNull { !it.prerelease && !it.draft }?.tagName?.trim()

/**
 * Which GitHub releases the update check offers.
 */
enum class UpdateChannel(
    val prefValue: String,
) {
    /** Published, non-pre-release entries only. */
    STABLE("stable"),

    /** Pre-releases as well; a newer stable release is still offered because it sorts above its betas. */
    BETA("beta"),
    ;

    companion object {
        fun fromPrefValue(value: String?): UpdateChannel? = entries.firstOrNull { it.prefValue == value }
    }
}

/**
 * Returns the tag of the newest release the [channel] offers, ignoring drafts.
 *
 * The beta channel picks the highest version rather than the first entry: GitHub orders releases by creation
 * date, so a stable hotfix on an older line (e.g. 1.4.1 published after 1.5.0b3) must not hide a newer beta.
 * Tags that are not versions are skipped. The returned tag is trimmed, the same normalization
 * [parseAppVersion] applies, so the version comparison and the release link use the same string.
 */
fun latestTag(
    releases: List<ReleaseInfo>,
    channel: UpdateChannel,
): String? =
    when (channel) {
        UpdateChannel.STABLE -> latestStableTag(releases)
        UpdateChannel.BETA ->
            releases
                .filter { !it.draft }
                .mapNotNull { release -> parseAppVersion(release.tagName)?.let { release.tagName.trim() to it } }
                .maxByOrNull { it.second }
                ?.first
    }

/**
 * Channel used until the user picks one: beta for a beta build (so existing testers keep getting beta
 * prompts), stable otherwise.
 */
fun defaultUpdateChannel(versionName: String): UpdateChannel =
    if (parseAppVersion(versionName)?.preRelease != null) UpdateChannel.BETA else UpdateChannel.STABLE

/**
 * The channel to use given the persisted value (null or unknown when never chosen) and the installed version.
 * An explicit choice always wins over the default.
 */
fun resolveUpdateChannel(
    stored: String?,
    versionName: String,
): UpdateChannel = UpdateChannel.fromPrefValue(stored) ?: defaultUpdateChannel(versionName)

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
