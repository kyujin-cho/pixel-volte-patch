package dev.bluehouse.enablevolte

import android.content.Context
import android.content.res.Resources
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.TelephonyManager
import android.telephony.ims.ImsManager
import android.telephony.ims.aidl.IImsRcsController
import android.util.Log
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper

private val TAG = "CarrierModder"

class CarrierModder(val context: Context) {
    val KEY_IMS_USER_AGENT = "ims.ims_user_agent_string"

    fun updateCarrierConfig(key: String, value: Boolean) {
        Log.d(TAG, "Setting $key to $value")
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        val overrideBundle = PersistableBundle()
        overrideBundle.putBoolean(key, value)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }

    fun updateCarrierConfig(key: String, value: String) {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        val overrideBundle = PersistableBundle()
        overrideBundle.putString(key, value)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }

    fun clearCarrierConfig() {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        iCclInstance.overrideConfig(this.subscriptionId, null, true)
    }

    private fun getStringValue(key: String): String {
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return ""
        }

        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getString(key)
    }

    private fun getBooleanValue(key: String): Boolean {
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return false
        }

        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getBoolean(key)
    }

    val isVolteConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVowifiConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)

    val userAgentConfig: String
        get() = this.getStringValue(KEY_IMS_USER_AGENT)

    val subscriptionId: Int
        get() {
            Log.d(TAG, "loading subscriptionId")
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return -1
            if (telephonyManager.subscriptionId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) return -1
            val phoneId = SubscriptionManager.getPhoneId(telephonyManager.subscriptionId)
            if (phoneId == SubscriptionManager.DEFAULT_SUBSCRIPTION_ID) return -1
            return telephonyManager.subscriptionId
        }

    val deviceSupportsIMS: Boolean
        get () {
            val res = Resources.getSystem()
            val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
            return res.getBoolean(volteConfigId)
        }

    val isIMSRegistered: Boolean
        get () {
            val telephony = ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(
                    TelephonyFrameworkInitializer
                        .getTelephonyServiceManager()
                        .telephonyServiceRegisterer
                        .get()
                )
            )
            return telephony.isImsRegistered(this.subscriptionId)
        }
}