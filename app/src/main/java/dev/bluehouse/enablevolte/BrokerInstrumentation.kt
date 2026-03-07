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
    ) {
        val am = IActivityManager.Stub.asInterface(ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)))
        try {
            am.startDelegateShellPermissionIdentity(Os.getuid(), null)
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)

            try {
                configurationManager.overrideConfig(subId, overrideValues, configPersistent)
            } catch (e: NoSuchMethodError) {
                configurationManager.overrideConfig(subId, overrideValues)
            }
        } finally {
            am.stopDelegateShellPermissionIdentity()
        }
    }

    private fun applyConfig(
        subId: Int,
        arguments: Bundle,
    ) {
        Log.i(TAG, "applyConfig")
        try {
            this.overrideConfig(subId, toPersistableBundle(arguments))
        } finally {
            Log.i(TAG, "applyConfig done")
        }
    }

    private fun clearConfig(subId: Int) {
        Log.i(TAG, "clearConfig")
        try {
            this.overrideConfig(subId, null)
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
