package dev.bluehouse.enablevolte

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Keeps the Shizuku UserService ([PrivilegedService]) bound for as long as the app process lives.
 *
 * The service is not a daemon, so Shizuku stops it when the app process dies. While the app is
 * alive the binding is never released, which keeps the shell-owned UiAutomationConnection of a
 * running broker instrumentation alive until the instrumentation finishes.
 *
 * Binding is asynchronous and callers run on the main thread, so [service] is only non-null once
 * the connection has been established; callers must fall back when it is still null.
 */
object PrivilegedServiceClient {
    private const val TAG = "PrivilegedServiceClient"

    @Volatile
    var service: IPrivilegedService? = null
        private set

    @Volatile
    private var bindRequested = false

    private val userServiceArgs by lazy {
        Shizuku
            .UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, PrivilegedService::class.java.name))
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                binder: IBinder?,
            ) {
                if (binder == null || !binder.pingBinder()) {
                    Log.w(TAG, "Received a dead UserService binder")
                    bindRequested = false
                    return
                }
                try {
                    binder.linkToDeath({
                        Log.w(TAG, "UserService died")
                        service = null
                        bindRequested = false
                    }, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to link to UserService death", e)
                }
                service = IPrivilegedService.Stub.asInterface(binder)
                Log.i(TAG, "UserService connected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.w(TAG, "UserService disconnected")
                service = null
                bindRequested = false
            }
        }

    /**
     * Requests the UserService binding if it is not bound or being bound yet. Cheap and idempotent;
     * does nothing when Shizuku is unavailable or the permission is not granted.
     */
    fun ensureBound() {
        if (service != null || bindRequested) return
        if (!isShizukuPermissionGranted()) return
        synchronized(this) {
            if (service != null || bindRequested) return
            try {
                Shizuku.bindUserService(userServiceArgs, connection)
                bindRequested = true
            } catch (e: Exception) {
                Log.w(TAG, "Failed to bind the Shizuku UserService", e)
            }
        }
    }
}
