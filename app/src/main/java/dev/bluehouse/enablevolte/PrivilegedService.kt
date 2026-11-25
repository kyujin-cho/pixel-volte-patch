package dev.bluehouse.enablevolte

import android.app.IActivityManager
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.ServiceManager
import android.util.Log
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
    ): String? =
        try {
            val am = IActivityManager.Stub.asInterface(ServiceManager.getService(Context.ACTIVITY_SERVICE))
            // The connection lives in this process, so it is owned by shell. AMS only lets a
            // shell-owned connection delegate the shell permission identity to the instrumented app.
            val connection = UiAutomationConnection()
            val started =
                startInstrumentationCompat(am, component, INSTR_FLAG_NO_RESTART, arguments, null, connection, userId)
            if (started) null else "startInstrumentation returned false"
        } catch (e: Throwable) {
            // Never let a hidden-API mismatch kill the service process; the caller falls back.
            Log.e(TAG, "Failed to start the broker instrumentation", e)
            e.toString()
        }
}
