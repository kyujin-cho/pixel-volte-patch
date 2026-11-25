package dev.bluehouse.enablevolte

import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.IUiAutomationConnection
import android.content.ComponentName
import android.os.Bundle
import java.lang.reflect.InvocationTargetException

/**
 * `ActivityManager.INSTR_FLAG_NO_RESTART` (API 31+): run the instrumentation inside the already
 * running target process instead of force-stopping and restarting it.
 */
const val INSTR_FLAG_NO_RESTART = 8

const val BROKER_INSTRUMENTATION_CLASS = "dev.bluehouse.enablevolte.BrokerInstrumentation"

/**
 * Calls the hidden `IActivityManager.startInstrumentation`.
 *
 * The compile-time signature (ComponentName, String, int, Bundle, IInstrumentationWatcher,
 * IUiAutomationConnection, int, String) has been stable since Android 12, but it is a hidden API,
 * so a missing overload falls back to matching any `startInstrumentation` by parameter type.
 */
fun startInstrumentationCompat(
    am: IActivityManager,
    component: ComponentName,
    flags: Int,
    arguments: Bundle,
    watcher: IInstrumentationWatcher?,
    connection: IUiAutomationConnection?,
    userId: Int,
): Boolean {
    try {
        return am.startInstrumentation(component, null, flags, arguments, watcher, connection, userId, null)
    } catch (e: NoSuchMethodError) {
        val method =
            am.javaClass.methods.firstOrNull { it.name == "startInstrumentation" }
                ?: throw e
        // The first int is the flags, the second one the user id.
        var intIndex = 0
        val params =
            method.parameterTypes
                .map { type ->
                    when {
                        type == ComponentName::class.java -> component
                        type == Bundle::class.java -> arguments
                        type == IInstrumentationWatcher::class.java -> watcher
                        type == IUiAutomationConnection::class.java -> connection
                        type == Int::class.javaPrimitiveType -> if (intIndex++ == 0) flags else userId
                        type == Boolean::class.javaPrimitiveType -> false
                        type == Long::class.javaPrimitiveType -> 0L
                        else -> null
                    }
                }.toTypedArray()
        return try {
            method.invoke(am, *params) as? Boolean ?: true
        } catch (ite: InvocationTargetException) {
            throw ite.cause ?: ite
        }
    }
}
