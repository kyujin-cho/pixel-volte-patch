package dev.bluehouse.enablevolte

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.IInterface
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import androidx.annotation.RequiresApi
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper

private const val TAG = "CarrierModer"

object InterfaceCache {
    val cache = HashMap<String, IInterface>()
}

open class Moder {
    val KEY_IMS_USER_AGENT = "ims.ims_user_agent_string"

    protected inline fun <reified T : IInterface>loadCachedInterface(interfaceLoader: () -> T): T {
        InterfaceCache.cache[T::class.java.name]?.let {
            return it as T
        } ?: run {
            val i = interfaceLoader()
            InterfaceCache.cache[T::class.java.name] = i
            return i as T
        }
    }

    protected val carrierConfigLoader: ICarrierConfigLoader
        get() = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get(),
            ),
        )

    protected val telephony: ITelephony
        get() = ITelephony.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .telephonyServiceRegisterer
                    .get(),
            ),
        )

    protected val phoneSubInfo: IPhoneSubInfo
        get() = IPhoneSubInfo.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .phoneSubServiceRegisterer
                    .get(),
            ),
        )

    protected val sub: ISub
        get() = ISub.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .subscriptionServiceRegisterer
                    .get(),
            ),
        )
}

class CarrierModer(private val context: Context) : Moder() {
    val subscriptions: List<SubscriptionInfo>
        get() = this.loadCachedInterface { sub }.getActiveSubscriptionInfoList(null, null)

    val defaultSubId: Int
        get() {
            val sub = this.loadCachedInterface { sub }
            return sub.defaultSubId
        }

    val deviceSupportsIMS: Boolean
        get() {
            val res = Resources.getSystem()
            val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
            return res.getBoolean(volteConfigId)
        }
}

class SubscriptionModer(val subscriptionId: Int) : Moder() {
    fun updateCarrierConfig(key: String, value: Boolean) {
        Log.d(TAG, "Setting $key to $value")
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val overrideBundle = PersistableBundle()
        overrideBundle.putBoolean(key, value)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }

    fun updateCarrierConfig(key: String, value: String) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val overrideBundle = PersistableBundle()
        overrideBundle.putString(key, value)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }

    fun updateCarrierConfig(key: String, value: IntArray) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val overrideBundle = PersistableBundle()
        overrideBundle.putIntArray(key, value)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }

    fun clearCarrierConfig() {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        iCclInstance.overrideConfig(this.subscriptionId, null, true)
    }

    fun restartIMSRegistration() {
        val telephony = this.loadCachedInterface { telephony }
        val sub = this.loadCachedInterface { sub }
        telephony.resetIms(sub.getSlotIndex(this.subscriptionId))
    }

    private fun getStringValue(key: String): String {
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return ""
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getString(key)
    }

    private fun getBooleanValue(key: String): Boolean {
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return false
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getBoolean(key)
    }

    private fun getIntArrayValue(key: String): IntArray {
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return intArrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getIntArray(key)
    }

    val isVolteConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVowifiConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)

    val isVtConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL)

    val isShow4GForLteEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)

    val isHideEnhancedDataIconEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)

    val is4GPlusEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
            this.getBooleanValue(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL) &&
            !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)

    val isNRConfigEnabled: Boolean
        @RequiresApi(Build.VERSION_CODES.S)
        get() = this.getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
            .contentEquals(intArrayOf(1, 2))

    val userAgentConfig: String
        get() = this.getStringValue(KEY_IMS_USER_AGENT)

    val isIMSRegistered: Boolean
        get() {
            val telephony = this.loadCachedInterface { telephony }
            return telephony.isImsRegistered(this.subscriptionId)
        }
}
