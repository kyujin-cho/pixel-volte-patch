package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.os.PersistableBundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.util.Log
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
            this.overrideConfig(subId, toPersistableBundle(arguments), persistent)
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
            return
        }

        val clear = arguments.getBoolean("moder_clear")
        val subId = arguments.getInt("moder_subId")
        val persistent = arguments.getBoolean("moder_persistent", true)

        try {
            if (clear) {
                this.clearConfig(subId, persistent)
            } else {
                this.applyConfig(subId, arguments, persistent)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to override carrier config", e)
        } finally {
            finish(0, Bundle())
        }
    }
}
