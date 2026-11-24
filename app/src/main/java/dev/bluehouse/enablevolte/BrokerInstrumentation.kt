package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.util.Log
import android.widget.Toast
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

const val TAG = "BrokerInstrumentation"

class BrokerInstrumentation : Instrumentation() {
    @SuppressLint("MissingPermission")
    private fun overrideConfig(
        subId: Int,
        overrideValues: PersistableBundle?,
        persistent: Boolean,
    ) {
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))
        try {
            am.startDelegateShellPermissionIdentity(Os.getuid(), null)
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
        } finally {
            am.stopDelegateShellPermissionIdentity()
        }
    }

    private fun applyConfig(
        subId: Int,
        arguments: Bundle,
        persistent: Boolean,
    ) {
        Log.i(TAG, "applyConfig")
        try {
            // Drop the broker's own control arguments so they are not written into the carrier config.
            val overrideValues = Bundle(arguments)
            overrideValues.keySet().filter { it.startsWith("moder_") }.forEach { overrideValues.remove(it) }
            this.overrideConfig(subId, toPersistableBundle(overrideValues), persistent)
        } finally {
            Log.i(TAG, "applyConfig done")
        }
    }

    private fun clearConfig(
        subId: Int,
        persistent: Boolean,
    ) {
        Log.i(TAG, "clearConfig")
        try {
            this.overrideConfig(subId, null, persistent)
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

        val clear = arguments.getBoolean("moder_clear")
        val subId = arguments.getInt("moder_subId")
        val persistent = arguments.getBoolean("moder_persistent", true)

        val failure: Throwable? =
            try {
                if (clear) {
                    this.clearConfig(subId, persistent)
                } else {
                    this.applyConfig(subId, arguments, persistent)
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

        Log.e(TAG, "Failed to override carrier config", failure)
        results.putString("moder_error", failure.toString())
        try {
            reportFailure(failure)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to report the carrier config failure", e)
        } finally {
            finish(Activity.RESULT_CANCELED, results)
        }
    }

    private fun reportFailure(failure: Throwable) {
        // The broker runs on the app's main thread without a restart (API 31+ only), so surface
        // the error to the user here; the caller only schedules the instrumentation and cannot observe the outcome.
        val appContext = this.targetContext
        val message = appContext.getString(R.string.config_apply_failed, failure.message ?: failure.javaClass.simpleName)
        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }
}
