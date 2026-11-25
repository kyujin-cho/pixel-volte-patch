package dev.bluehouse.enablevolte

import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.ServiceManager
import android.util.Log
import dev.bluehouse.enablevolte.diagnostics.DiagnosticsProtocol
import dev.bluehouse.enablevolte.diagnostics.ShellDiagnostics
import kotlin.system.exitProcess

/**
 * Shizuku UserService. Shizuku starts it in its own process running as the shell uid (or root),
 * so binder calls made from here carry the shell identity without going through Shizuku.
 */
class PrivilegedService : IPrivilegedService.Stub() {
    @Suppress("ktlint:standard:property-naming")
    private val TAG = "PrivilegedService"

    override fun destroy() {
        exitProcess(0)
    }

    override fun exit() {
        this.destroy()
    }

    override fun startBrokerInstrumentation(
        component: ComponentName,
        arguments: Bundle,
        userId: Int,
        watcher: IBinder?,
    ): String? =
        try {
            val am = IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))
            // The connection lives in this process, so it is owned by shell. AMS only lets a
            // shell-owned connection delegate the shell permission identity to the instrumented app.
            val connection = UiAutomationConnection()
            val watcherInterface = watcher?.let { IInstrumentationWatcher.Stub.asInterface(it) }
            val started =
                startInstrumentationCompat(am, component, INSTR_FLAG_NO_RESTART, arguments, watcherInterface, connection, userId)
            if (started) null else "startInstrumentation returned false"
        } catch (e: Throwable) {
            // Never let a hidden-API mismatch kill the service process; the caller falls back.
            Log.e(TAG, "Failed to start the broker instrumentation", e)
            e.toString()
        }

    override fun collectDiagnostics(
        source: Int,
        output: ParcelFileDescriptor?,
    ): Bundle {
        val status = Bundle()
        try {
            if (output == null) {
                status.putString(DiagnosticsProtocol.KEY_ERROR, "No output file descriptor")
                return status
            }
            val spec = ShellDiagnostics.specFor(source)
            if (spec == null) {
                status.putString(DiagnosticsProtocol.KEY_ERROR, "Unknown diagnostics source $source")
                return status
            }
            val result = ShellDiagnostics.collect(spec, BuildConfig.APPLICATION_ID)
            status.putAll(result.status)
            ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(result.bytes) }
        } catch (e: Throwable) {
            // Never let a failing command kill the service process; the report proceeds without it.
            Log.e(TAG, "Failed to collect diagnostics source $source", e)
            status.putString(DiagnosticsProtocol.KEY_ERROR, e.toString())
        } finally {
            // Closing the write end is what signals end-of-stream to the caller.
            try {
                output?.close()
            } catch (_: Exception) {
            }
        }
        return status
    }
}
