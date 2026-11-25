package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ExitReportProcessorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: CrashReportStore
    private lateinit var pendingDir: File
    private val cursor =
        object : ExitCursor {
            var value = 0L

            override fun get() = value

            override fun set(timestamp: Long) {
                value = timestamp
            }
        }

    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        store = CrashReportStore(File(tmp.root, "reports"))
        pendingDir = File(tmp.root, "pending")
    }

    private fun processor(processStart: Long = now) =
        ExitReportProcessor(store, cursor, { JavaCrashRecorder.pending(pendingDir) }, now, processStart)

    private fun record(
        timestamp: Long,
        kind: ExitKind?,
        pid: Int = 100,
        trace: String? = null,
    ) = ExitRecord(timestamp, kind?.let { ExitDetails(it, timestamp, pid) }) { trace?.byteInputStream() }

    private fun crashFile(
        timestamp: Long,
        pid: Int,
        text: String,
    ): File {
        pendingDir.mkdirs()
        return File(pendingDir, "java-crash-$timestamp-$pid.txt").apply { writeText(text) }
    }

    private fun payload(report: CrashReport) = store.openPayload(report.id)?.use { it.readBytes().decodeToString() }

    @Test
    fun `ANR and native exits store the system trace`() {
        val stored =
            processor().process(
                listOf(record(now - 2_000, ExitKind.ANR, trace = "anr"), record(now - 1_000, ExitKind.NATIVE_CRASH, trace = "tomb")),
            )
        assertEquals(2, stored.size)
        assertEquals("anr", payload(store.get("anr-${now - 2_000}-100")!!))
        assertEquals("tomb", payload(store.get("native-${now - 1_000}-100")!!))
        assertEquals(now - 1_000, cursor.value)
    }

    @Test
    fun `other exit reasons only advance the cursor`() {
        val stored = processor().process(listOf(record(now - 1_000, null)))
        assertTrue(stored.isEmpty())
        assertEquals(now - 1_000, cursor.value)
    }

    @Test
    fun `each exit is reported once across launches`() {
        val records = listOf(record(now - 1_000, ExitKind.ANR, trace = "anr"))
        assertEquals(1, processor().process(records).size)
        assertTrue(processor().process(records).isEmpty())

        // Even if the cursor was lost after storing, the report id prevents a duplicate.
        cursor.value = 0
        assertTrue(processor().process(records).isEmpty())
        assertEquals(1, store.list().size)
    }

    @Test
    fun `deleted reports are not recreated`() {
        val records = listOf(record(now - 1_000, ExitKind.ANR))
        val report = processor().process(records).single()
        store.delete(report.id)
        assertTrue(processor().process(records).isEmpty())
    }

    @Test
    fun `java crash is matched with its crash file by pid`() {
        val exitTime = now - 5_000
        crashFile(exitTime - 200, 77, "other process")
        val own = crashFile(exitTime - 300, 42, "own trace")
        val stored = processor().process(listOf(record(exitTime, ExitKind.JAVA_CRASH, pid = 42)))
        assertEquals("own trace", payload(stored.single { it.timestamp == exitTime }))
        assertFalse(own.exists())
    }

    @Test
    fun `java crash falls back to the nearest crash file in time`() {
        val exitTime = now - 5_000
        crashFile(exitTime - 50_000, 1, "far")
        crashFile(exitTime - 100, 2, "near")
        val stored = processor().process(listOf(record(exitTime, ExitKind.JAVA_CRASH, pid = 3)))
        assertEquals("near", payload(stored.first { it.details.pid == 3 }))
    }

    @Test
    fun `java crash without a crash file is stored without payload`() {
        crashFile(now - 500_000, 42, "too far away")
        val stored = processor().process(listOf(record(now - 1_000, ExitKind.JAVA_CRASH, pid = 42)))
        val exitReport = stored.first { it.timestamp == now - 1_000 }
        assertFalse(exitReport.hasPayload)
    }

    @Test
    fun `orphan crash files from earlier processes become reports`() {
        val orphan = crashFile(now - 1_000, 42, "orphan")
        val current = crashFile(now + 1_000, 43, "current process")
        val stored = processor(processStart = now).process(emptyList())
        val report = stored.single()
        assertEquals(ExitKind.JAVA_CRASH, report.kind)
        assertEquals(ExitReportProcessor.ORPHAN_DESCRIPTION, report.details.description)
        assertEquals("orphan", payload(report))
        assertFalse(orphan.exists())
        assertTrue(current.exists())
    }

    @Test
    fun `exits older than the age limit are skipped and their crash files removed`() {
        val old = now - ExitReportProcessor.DEFAULT_MAX_AGE_MS - 10
        val file = crashFile(old, 42, "old")
        val stored = processor().process(listOf(record(old, ExitKind.JAVA_CRASH, pid = 42), record(old + 1, ExitKind.ANR)))
        assertTrue(stored.isEmpty())
        assertFalse(file.exists())
        assertEquals(old + 1, cursor.value)
    }

    @Test
    fun `a failing trace stream still stores the report`() {
        val record = ExitRecord(now - 1_000, ExitDetails(ExitKind.ANR, now - 1_000, 100)) { throw IOException("gone") }
        val report = processor().process(listOf(record)).single()
        assertFalse(report.hasPayload)
        assertNull(payload(report))
    }
}
