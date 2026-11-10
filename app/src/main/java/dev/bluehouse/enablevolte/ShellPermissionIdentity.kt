package dev.bluehouse.enablevolte

import java.io.Closeable
import java.lang.reflect.InvocationTargetException

internal class ShellPermissionIdentity(
    private val activityManager: Any,
    activityManagerClass: Class<*>,
    private val uid: Int,
) : Closeable {
    // Resolve the framework interface before granting permissions. Some releases
    // require the delegated UID when stopping; older releases have no argument.
    private val stopMethod =
        try {
            activityManagerClass.getMethod("stopDelegateShellPermissionIdentity", Int::class.javaPrimitiveType)
        } catch (_: NoSuchMethodException) {
            activityManagerClass.getMethod("stopDelegateShellPermissionIdentity")
        }

    override fun close() {
        try {
            if (stopMethod.parameterTypes.isEmpty()) {
                stopMethod.invoke(activityManager)
            } else {
                stopMethod.invoke(activityManager, uid)
            }
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
