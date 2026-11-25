package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTextTest {
    @Test
    fun `tail buffer keeps everything below capacity`() {
        val buffer = TailBuffer(8)
        buffer.write("abc".toByteArray())
        buffer.write("de".toByteArray())
        assertArrayEquals("abcde".toByteArray(), buffer.toByteArray())
        assertFalse(buffer.truncated)
        assertEquals(5, buffer.totalBytes)
    }

    @Test
    fun `tail buffer keeps only the last bytes across many writes`() {
        val buffer = TailBuffer(4)
        "abcdefghij".forEach { buffer.write(byteArrayOf(it.code.toByte())) }
        assertEquals("ghij", String(buffer.toByteArray()))
        assertTrue(buffer.truncated)
        assertEquals(10, buffer.totalBytes)
    }

    @Test
    fun `tail buffer handles a chunk larger than its capacity`() {
        val buffer = TailBuffer(4)
        buffer.write("xy".toByteArray())
        buffer.write("0123456789".toByteArray(), 1, 8)
        assertEquals("5678", String(buffer.toByteArray()))
        assertEquals(10, buffer.totalBytes)
    }

    @Test
    fun `partial first line is dropped`() {
        assertEquals("line2\nline3", DiagnosticsText.dropPartialFirstLine("ne1\nline2\nline3"))
        assertEquals("noNewline", DiagnosticsText.dropPartialFirstLine("noNewline"))
    }

    private val separator = "=".repeat(40)

    private fun entry(
        process: String,
        body: String,
    ) = "$separator\n2026-11-25 10:00:00 data_app_anr (text, 100 bytes)\nProcess: $process\nPID: 1\n$body\n"

    @Test
    fun `dropbox filter keeps only entries of the package and its processes`() {
        val text =
            "Drop box contents: 3 entries\nSearching for: data_app_anr\n\n" +
                entry("com.example.other", "other trace") +
                entry("dev.bluehouse.enablevolte", "our trace") +
                entry("dev.bluehouse.enablevolte:privileged", "service trace") +
                entry("dev.bluehouse.enablevolte.fake", "lookalike trace")
        val result = DiagnosticsText.filterDropboxEntries(text, "dev.bluehouse.enablevolte")
        assertEquals(4, result.totalEntries)
        assertEquals(2, result.keptEntries)
        assertTrue(result.text.startsWith("Drop box contents: 3 entries"))
        assertTrue(result.text.contains("our trace"))
        assertTrue(result.text.contains("service trace"))
        assertFalse(result.text.contains("other trace"))
        assertFalse(result.text.contains("lookalike trace"))
    }

    @Test
    fun `dropbox filter matches the Package header too`() {
        val text = "$separator\n2026-11-25 data_app_crash (text, 1 bytes)\nPackage: dev.bluehouse.enablevolte v18 (1.5.0b1)\ncrash\n"
        val result = DiagnosticsText.filterDropboxEntries(text, "dev.bluehouse.enablevolte")
        assertEquals(1, result.keptEntries)
        assertTrue(result.text.contains("crash"))
    }

    @Test
    fun `dropbox filter with no entries keeps the preamble`() {
        val result = DiagnosticsText.filterDropboxEntries("Drop box contents: 0 entries\n", "dev.bluehouse.enablevolte")
        assertEquals(0, result.totalEntries)
        assertEquals("Drop box contents: 0 entries\n", result.text)
    }
}
