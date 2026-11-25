package dev.bluehouse.enablevolte.diagnostics

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import dev.bluehouse.enablevolte.BuildConfig
import java.io.File
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/**
 * Entry point of crash and ANR diagnostics.
 *
 * - [installCrashHandler] records Java crashes of the app process (call it as early as possible).
 * - [collectPreviousExits] turns the previous processes' ANR, Java crash and native crash exits
 *   into stored reports, on a background thread, on API 30+ only.
 * - [store] lists, reads, acknowledges and deletes the stored reports.
 * - [revision] and [collected] let Compose UI refresh when reports change.
 */
object CrashReports {
    private const val TAG = "CrashReports"
    private const val REPORTS_DIR = "reports"
    private const val PENDING_DIR = "crash-pending"
    private const val PREFS = "crash_reports"
    private const val KEY_LAST_EXIT = "last_processed_exit_timestamp"

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var storeInstance: CrashReportStore? = null

    @Volatile
    private var collectionStarted = false

    private val revisionState = mutableIntStateOf(0)
    private val collectedState = mutableStateOf(false)

    /** Incremented (on the main thread) whenever stored reports change. Use as an effect key. */
    val revision: State<Int> get() = revisionState

    /**
     * True once this launch's processing of previous exits has finished (immediately on API < 30).
     * Check for unacknowledged reports after this turns true.
     */
    val collected: State<Boolean> get() = collectedState

    /** The report store. Its methods do file I/O: call them off the main thread. */
    fun store(context: Context): CrashReportStore =
        storeInstance ?: synchronized(this) {
            storeInstance ?: CrashReportStore(File(context.applicationContext.filesDir, REPORTS_DIR)).also {
                it.onChange = { bumpRevision() }
                storeInstance = it
            }
        }

    /**
     * Convenience for the "the app died last time" check: whether a stored report has not been
     * acknowledged yet. Does file I/O: call it off the main thread.
     */
    fun hasUnacknowledgedReports(context: Context): Boolean = store(context).hasUnacknowledged()

    /** Installs [JavaCrashRecorder] as the default uncaught exception handler (API 30+). */
    fun installCrashHandler(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is JavaCrashRecorder) return
        Thread.setDefaultUncaughtExceptionHandler(
            JavaCrashRecorder(
                pendingDir = File(context.filesDir, PENDING_DIR),
                pid = Process.myPid(),
                processName = Application.getProcessName(),
                appVersion = appVersion(),
                previous = previous,
                terminate = {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                },
            ),
        )
    }

    /** Processes the previous processes' exits on a background thread, once per process. */
    fun collectPreviousExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            mainHandler.post { collectedState.value = true }
            return
        }
        synchronized(this) {
            if (collectionStarted) return
            collectionStarted = true
        }
        val appContext = context.applicationContext
        val processStart = System.currentTimeMillis()
        thread(name = "CrashReportCollector", isDaemon = true, priority = Thread.MIN_PRIORITY) {
            try {
                val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val cursor =
                    object : ExitCursor {
                        override fun get() = prefs.getLong(KEY_LAST_EXIT, 0L)

                        override fun set(timestamp: Long) {
                            prefs.edit().putLong(KEY_LAST_EXIT, timestamp).commit()
                        }
                    }
                val pendingDir = File(appContext.filesDir, PENDING_DIR)
                val processor =
                    ExitReportProcessor(
                        store = store(appContext),
                        cursor = cursor,
                        pendingCrashes = { JavaCrashRecorder.pending(pendingDir) },
                        now = System.currentTimeMillis(),
                        processStart = processStart,
                    )
                val stored = ExitReportCollector.collect(appContext, processor, appVersion())
                if (stored.isNotEmpty()) Log.i(TAG, "Stored ${stored.size} report(s): ${stored.map { it.id }}")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to collect previous process exits", e)
            } finally {
                mainHandler.post { collectedState.value = true }
            }
        }
    }

    private fun appVersion() = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    private fun bumpRevision() {
        mainHandler.post { revisionState.intValue += 1 }
    }
}
