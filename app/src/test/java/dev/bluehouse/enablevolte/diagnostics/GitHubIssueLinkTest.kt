package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class GitHubIssueLinkTest {
    private val environment =
        ReportEnvironment(
            manufacturer = "Google",
            model = "Pixel 8 Pro",
            device = "husky",
            androidRelease = "16",
            sdkInt = 36,
            securityPatch = "2025-11-05",
            buildId = "BP3A.251105.015",
            fingerprint = "google/husky/husky:16/BP3A.251105.015/14168014:user/release-keys",
            basebandVersion = "g5300q-250909-251024-B-14326967",
            appVersionName = "1.5.0",
            appVersionCode = 19,
            appBuildType = "release",
            installSource = "Google Play",
            shizukuGranted = true,
            carriers = listOf(ReportEnvironment.Carrier(1, "LG U+", "450-06", "kr"), ReportEnvironment.Carrier(2, "SKT", null, null)),
        )

    private val report =
        CrashReport(
            id = "anr-1764047000000-1234",
            details = ExitDetails(ExitKind.ANR, 1_764_047_000_000L, 1234, description = "Input dispatching timed out"),
            hasPayload = true,
            payloadTruncated = false,
            acknowledged = false,
            sizeBytes = 1000,
        )

    private fun query(url: String): Map<String, String> =
        URI(url).rawQuery.split("&").associate { pair ->
            val (key, value) = pair.split("=", limit = 2)
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun `targets the issue form and prefills its fields by id`() {
        val url = GitHubIssueLink.build(report, environment, "pixel-ims-report-anr-1764047000000-1234.zip")

        assertTrue(url.startsWith("https://github.com/kyujin-cho/pixel-volte-patch/issues/new?"))
        val params = query(url)
        assertEquals("bug_report.yml", params["template"])
        assertEquals("App stopped responding (ANR) on Pixel 8 Pro, Android 16", params["title"])
        assertEquals("Pixel 8 Pro (husky)", params["device-model"])
        assertEquals("Android 16 (API 36)", params["android-version"])
        assertEquals("2025-11-05", params["security-patch"])
        assertEquals("1.5.0 (19, release)", params["app-version"])
        assertEquals("SIM 1: LG U+ (450-06, kr); SIM 2: SKT", params["carrier"])
        assertTrue(params.getValue("logs").contains("pixel-ims-report-anr-1764047000000-1234.zip"))
    }

    @Test
    fun `url carries no trace and stays short`() {
        val url = GitHubIssueLink.build(report, environment, "pixel-ims-report-anr-1764047000000-1234.zip")

        assertFalse(url.contains("Input"))
        assertFalse(url.contains(" "))
        assertFalse(url.contains("+"))
        assertTrue("URL too long: ${url.length}", url.length < 2000)
    }

    @Test
    fun `fields are omitted when unknown`() {
        val url = GitHubIssueLink.build(report, environment.copy(carriers = null), "x.zip")

        assertFalse(query(url).containsKey("carrier"))
    }

    @Test
    fun `title names the crash kind`() {
        assertEquals("App crashed on Pixel 8 Pro, Android 16", GitHubIssueLink.title(ExitKind.JAVA_CRASH, environment))
        assertEquals("App crashed (native) on Pixel 8 Pro, Android 16", GitHubIssueLink.title(ExitKind.NATIVE_CRASH, environment))
    }

    @Test
    fun `environment text lists device, versions and carriers`() {
        val text = environment.format()

        assertTrue(text.contains("Device: Pixel 8 Pro (husky)\n"))
        assertTrue(text.contains("Security patch: 2025-11-05\n"))
        assertTrue(text.contains("App version: 1.5.0 (19, release)\n"))
        assertTrue(text.contains("Install source: Google Play\n"))
        assertTrue(text.contains("SIM 1: LG U+ (450-06, kr)\nSIM 2: SKT\n"))
        assertTrue(environment.copy(carriers = null).format().contains("SIMs: unavailable (needs Shizuku)"))
        assertTrue(environment.copy(carriers = emptyList()).format().contains("SIMs: none active"))
    }

    @Test
    fun `device description adds the manufacturer only when the model lacks it`() {
        assertEquals(
            "Samsung SM-S918N (dm3q)",
            environment.copy(manufacturer = "Samsung", model = "SM-S918N", device = "dm3q").deviceDescription,
        )
        assertEquals("Pixel 8 Pro (husky)", environment.deviceDescription)
    }

    @Test
    fun `installer is described`() {
        assertEquals("Google Play", ReportEnvironment.describeInstaller("com.android.vending"))
        assertEquals("com.google.android.packageinstaller", ReportEnvironment.describeInstaller("com.google.android.packageinstaller"))
        assertEquals(null, ReportEnvironment.describeInstaller(null))
    }
}
