package dev.bluehouse.enablevolte

import java.lang.reflect.Modifier

/**
 * Resolves IMS registration state through a chain of hidden-API variants so that a missing or
 * changed `ITelephony` signature degrades to "unknown" instead of crashing the caller.
 */
internal object ImsRegistrationCheck {
    /**
     * A single way of asking for the IMS registration state.
     *
     * [query] returns the state, or null when this variant is not applicable (e.g. the method does
     * not exist on this build). Throwing one of the hidden-API drift errors also moves on to the next
     * step.
     */
    class Step(
        val name: String,
        val query: () -> Boolean?,
    )

    /**
     * Returns the result of the first step that yields a value, or null ("unknown") when every
     * step is unavailable or fails.
     */
    fun resolve(
        steps: List<Step>,
        onStepFailed: (String, Throwable) -> Unit = { _, _ -> },
    ): Boolean? {
        for (step in steps) {
            try {
                step.query()?.let { return it }
            } catch (e: LinkageError) {
                // NoSuchMethodError / AbstractMethodError / IncompatibleClassChangeError on signature drift
                onStepFailed(step.name, e)
            } catch (e: Exception) {
                if (!isRecoverable(e)) throw e
                onStepFailed(step.name, e)
            }
        }
        return null
    }

    private fun isRecoverable(e: Exception): Boolean =
        e is ReflectiveOperationException ||
            e is SecurityException ||
            e is IllegalArgumentException ||
            e is UnsupportedOperationException ||
            isRemoteException(e)

    // android.os.RemoteException is matched by name so this helper stays loadable in JVM unit tests,
    // where the stub android.jar classes cannot be loaded.
    private fun isRemoteException(e: Exception): Boolean =
        generateSequence<Class<*>>(e.javaClass) { it.superclass }.any { it.name == "android.os.RemoteException" }

    /**
     * Reflectively invokes `boolean [methodName](int)` on [target].
     *
     * Returns null when no such public method exists. Errors raised by the invocation itself are
     * propagated (wrapped in [java.lang.reflect.InvocationTargetException]) so [resolve] can log
     * them and move on.
     */
    fun invokeIntToBoolean(
        target: Any,
        methodName: String,
        arg: Int,
    ): Boolean? {
        val method =
            target.javaClass.methods.firstOrNull {
                it.name == methodName &&
                    !Modifier.isStatic(it.modifiers) &&
                    it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) &&
                    (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.javaObjectType)
            } ?: return null
        return method.invoke(target, arg) as Boolean?
    }
}
