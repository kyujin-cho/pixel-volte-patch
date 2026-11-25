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
}
