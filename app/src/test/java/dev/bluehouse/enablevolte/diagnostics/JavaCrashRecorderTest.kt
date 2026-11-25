package dev.bluehouse.enablevolte.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JavaCrashRecorderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val pendingDir get() = File(tmp.root, "pending")

    @Test
    fun `crash is written and chained to the previous handler`() {
        var chained: Throwable? = null
        val previous = Thread.UncaughtExceptionHandler { _, e -> chained = e }
        val recorder = JavaCrashRecorder(pendingDir, 42, "dev.bluehouse.enablevolte", "1.5.0 (18)", previous, clock = { 1_234L })
        val error = IllegalStateException("boom")

        recorder.uncaughtException(Thread.currentThread(), error)

        assertSame(error, chained)
        val pending = JavaCrashRecorder.pending(pendingDir).single()
        assertEquals(1_234L, pending.timestamp)
        assertEquals(42, pending.pid)
        val text = pending.file.readText()
        assertTrue(text.contains("thread: ${Thread.currentThread().name}"))
        assertTrue(text.contains("app version: 1.5.0 (18)"))
        assertTrue(text.contains("java.lang.IllegalStateException: boom"))
    }

    @Test
    fun `without a previous handler the process is terminated`() {
        var terminated = false
        val recorder = JavaCrashRecorder(pendingDir, 1, null, null, null, terminate = { terminated = true })
        recorder.uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(terminated)
    }

    @Test
    fun `a failing write still chains to the previous handler`() {
        // A file where the directory should be makes the write fail.
        val blocked = File(tmp.root, "blocked").apply { writeText("") }
        var chained = false
        val recorder = JavaCrashRecorder(blocked, 1, null, null, { _, _ -> chained = true })
        recorder.uncaughtException(Thread.currentThread(), RuntimeException())
        assertTrue(chained)
    }

    @Test
    fun `pending ignores unrelated files and sorts by time`() {
        pendingDir.mkdirs()
        File(pendingDir, "java-crash-200-1.txt").writeText("b")
        File(pendingDir, "java-crash-100-2.txt").writeText("a")
        File(pendingDir, "notes.txt").writeText("x")
        assertEquals(listOf(100L, 200L), JavaCrashRecorder.pending(pendingDir).map { it.timestamp })
    }
}
