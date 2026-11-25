package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class ReportExporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { CrashReportStore(File(tmp.root, "reports")) }
    private val exportRoot by lazy { File(tmp.root, "report-export") }
    private val exporter by lazy { ReportExporter(store, exportRoot) }

    private val environmentText = "Device: Pixel 8 Pro (husky)\nSIM 1: LG U+ (450-06, kr)\n"

    private fun save(
        kind: ExitKind,
        payload: ByteArray?,
    ): CrashReport =
        store.save(
            ExitDetails(
                kind,
                1_764_047_000_000L,
                1234,
                processName = "dev.bluehouse.enablevolte",
                description = "user request after error",
            ),
            payload?.inputStream(),
        )!!

    private fun zipEntries(zip: File): Map<String, ByteArray> =
        ZipFile(zip).use { file -> file.entries().toList().associate { it.name to file.getInputStream(it).readBytes() } }

    @Test
    fun `text payload is masked and the zip matches the staged preview`() {
        val trace = "\"main\" prio=5 tid=1 Blocked\n  imsi=450061234567890\n  line1Number=+821012345678\n"
        val report = save(ExitKind.ANR, trace.toByteArray())

        val export = exporter.prepare(report, environmentText, setOf("8982061234567890123"), ReportExporter.SystemPart.NotRequested)

        assertEquals("pixel-ims-report-${report.id}.zip", export.zip.name)
        assertEquals(listOf("environment.txt", "anr-trace.txt"), export.files.map { it.name })
        val entries = zipEntries(export.zip)
        assertEquals(export.files.map { it.name }.toSet(), entries.keys)
        val exportedTrace = entries.getValue("anr-trace.txt").decodeToString()
        assertFalse(exportedTrace.contains("450061234567890"))
        assertFalse(exportedTrace.contains("821012345678"))
        assertTrue(exportedTrace.contains("\"main\" prio=5 tid=1 Blocked"))
        // The preview reads the staged files, which must be byte-identical to the zip entries.
        export.files.forEach { assertTrue(it.file.readBytes().contentEquals(entries.getValue(it.name))) }
        assertEquals(2, export.files.single { it.name == "anr-trace.txt" }.maskedCount)
    }

    @Test
    fun `environment file carries exit details and environment`() {
        val report = save(ExitKind.JAVA_CRASH, "java.lang.IllegalStateException".toByteArray())

        val export = exporter.prepare(report, environmentText, emptySet(), ReportExporter.SystemPart.NotRequested)
        val environment = zipEntries(export.zip).getValue("environment.txt").decodeToString()

        assertTrue(environment.contains("Kind: JAVA_CRASH"))
        assertTrue(environment.contains("Process: dev.bluehouse.enablevolte"))
        assertTrue(environment.contains("Payload: java-crash.txt"))
        assertTrue(environment.contains("Device: Pixel 8 Pro (husky)"))
        assertTrue(environment.contains("SIM 1: LG U+ (450-06, kr)"))
        assertTrue(environment.contains("## System diagnostics\nNot included"))
    }

    @Test
    fun `binary tombstone is copied unchanged`() {
        val tombstone = ByteArray(4096) { (it % 251).toByte() }
        val report = save(ExitKind.NATIVE_CRASH, tombstone)

        val export = exporter.prepare(report, environmentText, emptySet(), ReportExporter.SystemPart.NotRequested)

        val file = export.files.single { it.name == "tombstone.pb" }
        assertFalse(file.text)
        assertTrue(tombstone.contentEquals(zipEntries(export.zip).getValue("tombstone.pb")))
    }

    @Test
    fun `system diagnostics files go under system and failures are listed`() {
        val report = save(ExitKind.ANR, "trace".toByteArray())
        val logcat = File(tmp.root, "logcat.txt").apply { writeText("# logcat\nmasked [IMSI]\n") }
        val sections =
            listOf(
                SystemDiagnostics.Section(SystemDiagnostics.Source.LOGCAT, logcat, null, true, false, 3, null, null),
                SystemDiagnostics.Section(SystemDiagnostics.Source.DROPBOX_ANR, null, "Permission Denial", false, false, 0, null, null),
            )

        val export = exporter.prepare(report, environmentText, emptySet(), ReportExporter.SystemPart.Collected(sections))
        val entries = zipEntries(export.zip)

        assertEquals("# logcat\nmasked [IMSI]\n", entries.getValue("system/logcat.txt").decodeToString())
        assertFalse(entries.containsKey("system/dropbox_data_app_anr.txt"))
        val environment = entries.getValue("environment.txt").decodeToString()
        assertTrue(environment.contains("logcat -d -v threadtime -b main,system,radio: system/logcat.txt, truncated"))
        assertTrue(environment.contains("dumpsys dropbox --print data_app_anr: not collected, error: Permission Denial"))
    }

    @Test
    fun `unavailable system diagnostics are noted`() {
        val report = save(ExitKind.ANR, null)

        val export = exporter.prepare(report, environmentText, emptySet(), ReportExporter.SystemPart.Unavailable("SHIZUKU_NOT_RUNNING"))
        val environment = zipEntries(export.zip).getValue("environment.txt").decodeToString()

        assertEquals(listOf("environment.txt"), export.files.map { it.name })
        assertTrue(environment.contains("Payload: none"))
        assertTrue(environment.contains("Requested but unavailable: SHIZUKU_NOT_RUNNING"))
    }

    @Test
    fun `a new export replaces the previous one`() {
        val first = save(ExitKind.ANR, "one".toByteArray())
        val second =
            store.save(ExitDetails(ExitKind.JAVA_CRASH, 1_764_047_100_000L, 99), "two".byteInputStream())!!

        val old = exporter.prepare(first, environmentText, emptySet(), ReportExporter.SystemPart.NotRequested)
        val new = exporter.prepare(second, environmentText, emptySet(), ReportExporter.SystemPart.NotRequested)

        assertFalse(old.zip.exists())
        assertTrue(new.zip.exists())
        assertEquals(listOf(new.zip.name, "staging"), exportRoot.list()!!.sorted())

        exporter.clear()
        assertTrue(exportRoot.list()!!.isEmpty())
    }
}
