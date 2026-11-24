package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

const val TAG = "BrokerInstrumentation"

/**
 * Android 17 removed [IActivityManager.stopDelegateShellPermissionIdentity]. The carrier
 * config write has already returned by the time this runs, so a missing cleanup method must
 * not kill the process.
 */
private fun stopShellPermissionDelegate(am: IActivityManager) {
    try {
        am.stopDelegateShellPermissionIdentity()
    } catch (e: LinkageError) {
        Log.i(TAG, "stopDelegateShellPermissionIdentity is unavailable on this platform: ${e.message}")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to stop shell permission identity delegate", e)
    }
}

class BrokerInstrumentation : Instrumentation() {
    @SuppressLint("MissingPermission")
    private fun applyConfig(
        subId: Int,
        arguments: Bundle,
    ) {
        Log.i(TAG, "applyConfig")
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)
            val overrideValues = toPersistableBundle(arguments)

            try {
                configurationManager.overrideConfig(subId, overrideValues, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("overrideConfig with persistent=true only can be invoked by system app") == true) {
                    configurationManager.overrideConfig(subId, overrideValues, false)
                } else {
                    throw e
                }
            }
        } finally {
            Log.i(TAG, "applyConfig done")
            stopShellPermissionDelegate(am)
        }
    }

    @SuppressLint("MissingPermission")
    private fun clearConfig(subId: Int) {
        Log.i(TAG, "clearConfig")
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)

            try {
                configurationManager.overrideConfig(subId, null, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("overrideConfig with persistent=true only can be invoked by system app") == true) {
                    configurationManager.overrideConfig(subId, null, false)
                } else {
                    throw e
                }
            }
        } finally {
            Log.i(TAG, "clearConfig done")
            stopShellPermissionDelegate(am)
        }
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        if (arguments == null) {
            return
        }

        val clear = arguments.getBoolean("moder_clear")
        val subId = arguments.getInt("moder_subId")

        try {
            if (clear) {
                this.clearConfig(subId)
            } else {
                this.applyConfig(subId, arguments)
            }
        } finally {
            finish(0, Bundle())
        }
    }
}
