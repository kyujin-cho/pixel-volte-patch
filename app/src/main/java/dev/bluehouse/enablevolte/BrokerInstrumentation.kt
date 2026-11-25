package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

const val TAG = "BrokerInstrumentation"

private const val UI_AUTOMATION_ATTEMPTS = 5
private const val UI_AUTOMATION_RETRY_DELAY_MS = 500L

/**
 * Legacy shell identity source, used only when the instrumentation was started from the app
 * process (UserService unavailable) or when UiAutomation adoption fails.
 *
 * It borrows the shell identity through the hidden IActivityManager delegate API reached via
 * Shizuku. Android 17 removed the no-arg stop; AMS also clears the delegation when the
 * instrumentation finishes, so a missing stop method must not kill the process.
 */
private object LegacyShellPermissionDelegate {
    fun <T> run(block: () -> T): T {
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))
        try {
            am.startDelegateShellPermissionIdentity(Os.getuid(), null)
            return block()
        } finally {
            stop(am)
        }
    }

    private fun stop(am: IActivityManager) {
        try {
            am.stopDelegateShellPermissionIdentity()
        } catch (e: LinkageError) {
            Log.i(TAG, "stopDelegateShellPermissionIdentity is unavailable on this platform: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop shell permission identity delegate", e)
        }
    }
}

class BrokerInstrumentation : Instrumentation() {
    private var arguments: Bundle? = null

    private fun obtainUiAutomation(): UiAutomation? {
        // Keep the user's accessibility services running, and skip the accessibility connection
        // entirely where the platform allows it (API 33+); only the permission identity is needed.
        var flags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags = flags or UiAutomation.FLAG_DONT_USE_ACCESSIBILITY
        }
        // Connecting can be flaky right after the instrumentation starts.
        for (attempt in 1..UI_AUTOMATION_ATTEMPTS) {
            try {
                getUiAutomation(flags)?.let { return it }
                Log.w(TAG, "getUiAutomation returned null (attempt $attempt)")
            } catch (e: RuntimeException) {
                Log.w(TAG, "getUiAutomation failed (attempt $attempt)", e)
            }
            if (attempt < UI_AUTOMATION_ATTEMPTS) {
                Thread.sleep(UI_AUTOMATION_RETRY_DELAY_MS)
            }
        }
        return null
    }

    /**
     * Adopts the shell permission identity through the public UiAutomation API. Returns null
     * when it could not be adopted, so the caller can fall back to the legacy delegate.
     */
    private fun adoptShellPermissionIdentity(): UiAutomation? {
        val uiAutomation = obtainUiAutomation() ?: return null
        return try {
            uiAutomation.adoptShellPermissionIdentity()
            uiAutomation
        } catch (e: Exception) {
            Log.w(TAG, "adoptShellPermissionIdentity failed", e)
            null
        } catch (e: LinkageError) {
            Log.w(TAG, "adoptShellPermissionIdentity is unavailable", e)
            null
        }
    }

    private fun <T> withShellPermissionIdentity(
        useUiAutomation: Boolean,
        block: () -> T,
    ): T {
        if (useUiAutomation) {
            val uiAutomation = adoptShellPermissionIdentity()
            if (uiAutomation != null) {
                Log.i(TAG, "Adopted the shell permission identity through UiAutomation")
                try {
                    return block()
                } finally {
                    try {
                        uiAutomation.dropShellPermissionIdentity()
                    } catch (e: Exception) {
                        Log.w(TAG, "dropShellPermissionIdentity failed", e)
                    }
                }
            }
            Log.w(TAG, "UiAutomation shell identity unavailable, falling back to the legacy delegate")
        }
        return LegacyShellPermissionDelegate.run(block)
    }

    @SuppressLint("MissingPermission")
    private fun overrideConfig(
        subId: Int,
        overrideValues: PersistableBundle?,
        persistent: Boolean,
        useUiAutomation: Boolean,
    ) {
        withShellPermissionIdentity(useUiAutomation) {
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)

            try {
                try {
                    configurationManager.overrideConfig(subId, overrideValues, persistent)
                } catch (e: SecurityException) {
                    if (!persistent) {
                        throw e
                    }
                    Log.w(TAG, "overrideConfig with persistent=true failed, retrying with persistent=false", e)
                    configurationManager.overrideConfig(subId, overrideValues, false)
                }
            } catch (_: NoSuchMethodError) {
                configurationManager.overrideConfig(subId, overrideValues)
            }
        }
    }

    private fun applyConfig(
        subId: Int,
        arguments: Bundle,
        persistent: Boolean,
        useUiAutomation: Boolean,
    ) {
        Log.i(TAG, "applyConfig")
        try {
            // Drop the broker's own control arguments so they are not written into the carrier config.
            val overrideValues = Bundle(arguments)
            overrideValues.keySet().filter { it.startsWith("moder_") }.forEach { overrideValues.remove(it) }
            this.overrideConfig(subId, toPersistableBundle(overrideValues), persistent, useUiAutomation)
        } finally {
            Log.i(TAG, "applyConfig done")
        }
    }

    private fun clearConfig(
        subId: Int,
        persistent: Boolean,
        useUiAutomation: Boolean,
    ) {
        Log.i(TAG, "clearConfig")
        try {
            this.overrideConfig(subId, null, persistent, useUiAutomation)
        } finally {
            Log.i(TAG, "clearConfig done")
        }
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }
        this.arguments = arguments
        // onCreate runs on the app's main thread (INSTR_FLAG_NO_RESTART). Move the work to the
        // instrumentation thread so connecting UiAutomation and its retries never block the UI.
        start()
    }

    override fun onStart() {
        super.onStart()
        val arguments = this.arguments ?: return finish(Activity.RESULT_CANCELED, Bundle())

        val clear = arguments.getBoolean(BROKER_ARG_CLEAR)
        val subId = arguments.getInt(BROKER_ARG_SUB_ID)
        val persistent = arguments.getBoolean(BROKER_ARG_PERSISTENT, true)
        val useUiAutomation = arguments.getBoolean(BROKER_ARG_UI_AUTOMATION, false)

        val failure: Throwable? =
            try {
                if (clear) {
                    this.clearConfig(subId, persistent, useUiAutomation)
                } else {
                    this.applyConfig(subId, arguments, persistent, useUiAutomation)
                }
                null
            } catch (e: Exception) {
                e
            } catch (e: LinkageError) {
                // e.g. NoSuchMethodError when a hidden API is missing on this Android version
                e
            }

        val results = Bundle()
        if (failure == null) {
            finish(Activity.RESULT_OK, results)
            return
        }

        // The app learns the outcome through the IInstrumentationWatcher it passed to
        // startInstrumentation (see BrokerRunner) and reports it to the user.
        Log.e(TAG, "Failed to override carrier config", failure)
        results.putString(BROKER_RESULT_ERROR, failure.message ?: failure.javaClass.simpleName)
        finish(Activity.RESULT_CANCELED, results)
    }
}
