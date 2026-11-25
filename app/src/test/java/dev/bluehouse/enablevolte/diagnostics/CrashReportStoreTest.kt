package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.random.Random

class CrashReportStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun details(
        timestamp: Long,
        kind: ExitKind = ExitKind.ANR,
        pid: Int = 100,
    ) = ExitDetails(
        kind = kind,
        timestamp = timestamp,
        pid = pid,
        processName = "dev.bluehouse.enablevolte",
        description = "desc $timestamp",
    )

    private fun store(
        maxReports: Int = 5,
        maxTotalBytes: Long = 1024L * 1024,
        maxPayloadBytes: Long = 64L * 1024,
    ) = CrashReportStore(File(tmp.root, "reports"), maxReports, maxTotalBytes, maxPayloadBytes)

    private fun CrashReportStore.payloadText(id: String) = openPayload(id)!!.use { it.readBytes().decodeToString() }

    @Test
    fun `saved report round-trips metadata and payload`() {
        val store = store()
        val saved =
            store.save(
                details(1_000).copy(importance = 100, pssKb = 12, rssKb = 34, status = 6, appVersion = "1.5.0"),
                "trace".byteInputStream(),
            )
        assertNotNull(saved)
        val report = store.get(saved!!.id)!!
        assertEquals("anr-1000-100", report.id)
        assertEquals(100, report.details.importance)
        assertEquals(12L, report.details.pssKb)
        assertEquals(34L, report.details.rssKb)
        assertEquals(6, report.details.status)
        assertEquals("1.5.0", report.details.appVersion)
        assertEquals("anr-trace.txt", report.payloadName)
        assertTrue(report.hasPayload)
        assertFalse(report.payloadTruncated)
        assertFalse(report.acknowledged)
        assertEquals("trace", store.payloadText(report.id))
    }

    @Test
    fun `report without payload has no payload stream`() {
        val store = store()
        val saved = store.save(details(1_000, ExitKind.JAVA_CRASH), null)!!
        assertFalse(saved.hasPayload)
        assertNull(store.openPayload(saved.id))
    }

    @Test
    fun `saving the same exit twice keeps one report`() {
        val store = store()
        store.save(details(1_000), "first".byteInputStream())
        store.save(details(1_000), "second".byteInputStream())
        assertEquals(1, store.list().size)
        assertEquals("first", store.payloadText("anr-1000-100"))
    }

    @Test
    fun `list is newest first and count cap evicts the oldest`() {
        val store = store(maxReports = 3)
        listOf(3_000L, 1_000L, 5_000L, 2_000L, 4_000L).forEach { store.save(details(it), "x".byteInputStream()) }
        assertEquals(listOf(5_000L, 4_000L, 3_000L), store.list().map { it.timestamp })
    }

    @Test
    fun `size cap evicts the oldest but keeps the newest report`() {
        val store = store(maxTotalBytes = 3_000, maxPayloadBytes = 10_000)
        // Random bytes do not compress, so each report is a little over 1 kB on disk.
        repeat(4) { store.save(details(1_000L + it), Random(it).nextBytes(1_000).inputStream()) }
        val reports = store.list()
        assertTrue(reports.sumOf { it.sizeBytes } <= 3_000)
        assertEquals(1_003L, reports.first().timestamp)
        assertTrue(reports.size < 4)
    }

    @Test
    fun `text payload over the limit is cut with a marker`() {
        val store = store(maxPayloadBytes = 10)
        val saved = store.save(details(1_000), "0123456789ABCDEF".byteInputStream())!!
        assertTrue(saved.payloadTruncated)
        assertEquals("0123456789" + CrashReportStore.truncationMarker(10), store.payloadText(saved.id))
    }

    @Test
    fun `binary payload over the limit is cut without a marker`() {
        val store = store(maxPayloadBytes = 4)
        val saved = store.save(details(1_000, ExitKind.NATIVE_CRASH), byteArrayOf(1, 2, 3, 4, 5).inputStream())!!
        assertTrue(saved.payloadTruncated)
        assertEquals(listOf<Byte>(1, 2, 3, 4), store.openPayload(saved.id)!!.use { it.readBytes() }.toList())
    }

    @Test
    fun `payload exactly at the limit is not truncated`() {
        val store = store(maxPayloadBytes = 4)
        val saved = store.save(details(1_000), "abcd".byteInputStream())!!
        assertFalse(saved.payloadTruncated)
        assertEquals("abcd", store.payloadText(saved.id))
    }

    @Test
    fun `acknowledge and delete`() {
        val store = store()
        val a = store.save(details(1_000), null)!!
        val b = store.save(details(2_000), null)!!
        assertTrue(store.hasUnacknowledged())
        assertTrue(store.acknowledge(a.id))
        assertEquals(listOf(b.id), store.unacknowledged().map { it.id })
        assertTrue(store.get(a.id)!!.acknowledged)
        assertTrue(store.delete(b.id))
        assertFalse(store.hasUnacknowledged())
        store.deleteAll()
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun `ids that are not plain report ids are rejected`() {
        val store = store()
        File(tmp.root, "outside").mkdirs()
        assertFalse(store.delete("../outside"))
        assertNull(store.get("../outside"))
        assertTrue(File(tmp.root, "outside").exists())
    }

    @Test
    fun `incomplete writes are ignored`() {
        val store = store()
        File(tmp.root, "reports/.tmp-anr-1-1").mkdirs()
        File(tmp.root, "reports/anr-2-2").mkdirs()
        assertTrue(store.list().isEmpty())
        assertFalse(store.contains("anr-2-2"))
    }

    @Test
    fun `changes notify the listener`() {
        val store = store()
        var changes = 0
        store.onChange = { changes++ }
        val saved = store.save(details(1_000), null)!!
        store.acknowledge(saved.id)
        store.delete(saved.id)
        assertEquals(3, changes)
    }
}
