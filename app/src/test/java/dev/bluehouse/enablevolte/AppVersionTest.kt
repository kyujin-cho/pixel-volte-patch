package dev.bluehouse.enablevolte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun `short beta suffix parses as a pre-release`() {
        assertEquals("1.5.0-b1", parseAppVersion("1.5.0b1").toString())
    }

    @Test
    fun `leading v and plain SemVer are accepted`() {
        assertEquals("1.4.0", parseAppVersion("v1.4.0").toString())
        assertEquals("1.5.0-beta.1", parseAppVersion("1.5.0-beta.1").toString())
    }

    @Test
    fun `unparseable versions return null instead of throwing`() {
        assertNull(parseAppVersion("nightly"))
        assertNull(parseAppVersion(""))
    }

    @Test
    fun `beta sorts between the previous and the final release`() {
        assertTrue(isNewerVersion("1.5.0b1", "1.4.0"))
        assertTrue(isNewerVersion("1.5.0", "1.5.0b1"))
        assertTrue(isNewerVersion("1.5.0b2", "1.5.0b1"))
        assertFalse(isNewerVersion("1.4.0", "1.5.0b1"))
    }

    @Test
    fun `unparseable versions never count as newer`() {
        assertFalse(isNewerVersion("garbage", "1.4.0"))
        assertFalse(isNewerVersion("1.5.0", "garbage"))
        assertFalse(isNewerVersion("0.0.0", "1.5.0b1"))
    }

    @Test
    fun `latest stable tag skips pre-releases and drafts`() {
        val releases =
            listOf(
                ReleaseInfo("1.5.0b1", prerelease = true, draft = false),
                ReleaseInfo("1.5.0", prerelease = false, draft = true),
                ReleaseInfo("1.4.0", prerelease = false, draft = false),
                ReleaseInfo("1.3.2", prerelease = false, draft = false),
            )
        assertEquals("1.4.0", latestStableTag(releases))
        assertNull(latestStableTag(listOf(ReleaseInfo("1.5.0b1", prerelease = true, draft = false))))
    }

    private val mixedReleases =
        listOf(
            ReleaseInfo("1.5.1b1", prerelease = true, draft = true),
            ReleaseInfo("1.5.0b3", prerelease = true, draft = false),
            ReleaseInfo("1.5.0b2", prerelease = true, draft = false),
            ReleaseInfo("1.4.0", prerelease = false, draft = false),
        )

    @Test
    fun `stable channel matches latestStableTag`() {
        assertEquals(latestStableTag(mixedReleases), latestTag(mixedReleases, UpdateChannel.STABLE))
        assertEquals("1.4.0", latestTag(mixedReleases, UpdateChannel.STABLE))
    }

    @Test
    fun `beta channel offers the newest pre-release but ignores drafts`() {
        assertEquals("1.5.0b3", latestTag(mixedReleases, UpdateChannel.BETA))
        assertNull(latestTag(listOf(ReleaseInfo("1.5.0b1", prerelease = true, draft = true)), UpdateChannel.BETA))
        assertNull(latestTag(emptyList(), UpdateChannel.BETA))
    }

    @Test
    fun `beta channel still offers a newer stable release`() {
        val releases = listOf(ReleaseInfo("1.5.0", prerelease = false, draft = false)) + mixedReleases
        val latest = latestTag(releases, UpdateChannel.BETA)!!
        assertEquals("1.5.0", latest)
        assertTrue(isNewerVersion(latest, "1.5.0b3"))
    }

    @Test
    fun `beta tester is offered the next beta`() {
        assertTrue(isNewerVersion(latestTag(mixedReleases, UpdateChannel.BETA)!!, "1.5.0b2"))
        assertFalse(isNewerVersion(latestTag(mixedReleases, UpdateChannel.STABLE)!!, "1.5.0b2"))
    }

    @Test
    fun `beta channel picks the highest version, not the newest created release`() {
        // A stable hotfix on the old line, published after the beta, is listed first by GitHub.
        val releases =
            listOf(
                ReleaseInfo("1.4.1", prerelease = false, draft = false),
                ReleaseInfo("not-a-version", prerelease = true, draft = false),
            ) + mixedReleases
        assertEquals("1.5.0b3", latestTag(releases, UpdateChannel.BETA))
        assertTrue(isNewerVersion(latestTag(releases, UpdateChannel.BETA)!!, "1.5.0b2"))
        assertNull(latestTag(listOf(ReleaseInfo("not-a-version", prerelease = true, draft = false)), UpdateChannel.BETA))
    }

    @Test
    fun `default channel follows the installed version`() {
        assertEquals(UpdateChannel.STABLE, defaultUpdateChannel("1.4.0"))
        assertEquals(UpdateChannel.BETA, defaultUpdateChannel("1.5.0b2"))
        assertEquals(UpdateChannel.BETA, defaultUpdateChannel("1.5.0-beta.1"))
        assertEquals(UpdateChannel.STABLE, defaultUpdateChannel("garbage"))
    }

    @Test
    fun `explicit choice wins over the default`() {
        assertEquals(UpdateChannel.STABLE, resolveUpdateChannel("stable", "1.5.0b2"))
        assertEquals(UpdateChannel.BETA, resolveUpdateChannel("beta", "1.4.0"))
        assertEquals(UpdateChannel.BETA, resolveUpdateChannel(null, "1.5.0b2"))
        assertEquals(UpdateChannel.STABLE, resolveUpdateChannel("unknown-value", "1.4.0"))
    }

    @Test
    fun `returned tags are trimmed so the link matches the parsed version`() {
        val releases =
            listOf(
                ReleaseInfo(" 1.5.0b3 ", prerelease = true, draft = false),
                ReleaseInfo("1.4.0\n", prerelease = false, draft = false),
            )
        assertEquals("1.5.0b3", latestTag(releases, UpdateChannel.BETA))
        assertEquals("1.4.0", latestTag(releases, UpdateChannel.STABLE))
    }
}
