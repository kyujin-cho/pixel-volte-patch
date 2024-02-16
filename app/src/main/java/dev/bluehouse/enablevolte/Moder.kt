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
    fun getActiveSubscriptionInfoForSimSlotIndex(index: Int): SubscriptionInfo? {
        val sub = this.loadCachedInterface { sub }
        return sub.getActiveSubscriptionInfoForSimSlotIndex(index, null, null)
    }

    val subscriptions: List<SubscriptionInfo>
        get() {
            val sub = this.loadCachedInterface { sub }
            return try {
                sub.getActiveSubscriptionInfoList(null, null)
            } catch (e: NoSuchMethodError) {
                // FIXME: lift up reflect as soon as official source code releases
                val getActiveSubscriptionInfoListMethod = sub.javaClass.getMethod(
                    "getActiveSubscriptionInfoList",
                    String::class.java,
                    String::class.java,
                    Boolean::class.java,
                )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null, null, false) as List<SubscriptionInfo>)
            }
        }

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
    private fun publishBundle(fn: (PersistableBundle) -> Unit) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val overrideBundle = PersistableBundle()
        fn(overrideBundle)
        iCclInstance.overrideConfig(this.subscriptionId, overrideBundle, true)
    }
    fun updateCarrierConfig(key: String, value: Boolean) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBoolean(key, value) }
    }

    fun updateCarrierConfig(key: String, value: String) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putString(key, value) }
    }

    fun updateCarrierConfig(key: String, value: Int) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putInt(key, value) }
    }
    fun updateCarrierConfig(key: String, value: Long) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLong(key, value) }
    }

    fun updateCarrierConfig(key: String, value: IntArray) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putIntArray(key, value) }
    }

    fun updateCarrierConfig(key: String, value: BooleanArray) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBooleanArray(key, value) }
    }

    fun updateCarrierConfig(key: String, value: Array<String>) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putStringArray(key, value) }
    }

    fun updateCarrierConfig(key: String, value: LongArray) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLongArray(key, value) }
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

    fun getStringValue(key: String): String {
        Log.d(TAG, "Resolving string value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return ""
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getString(key)
    }

    fun getBooleanValue(key: String): Boolean {
        Log.d(TAG, "Resolving boolean value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return false
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getBoolean(key)
    }

    fun getIntValue(key: String): Int {
        Log.d(TAG, "Resolving integer value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return -1
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getInt(key)
    }

    fun getLongValue(key: String): Long {
        Log.d(TAG, "Resolving long value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return -1
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getLong(key)
    }

    fun getBooleanArrayValue(key: String): BooleanArray {
        Log.d(TAG, "Resolving boolean array value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return booleanArrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getBooleanArray(key)
    }

    fun getIntArrayValue(key: String): IntArray {
        Log.d(TAG, "Resolving integer value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return intArrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getIntArray(key)
    }

    fun getStringArrayValue(key: String): Array<String> {
        Log.d(TAG, "Resolving string array value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return arrayOf()
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.getStringArray(key)
    }
    fun getValue(key: String): Any? {
        Log.d(TAG, "Resolving value of key $key")
        val subscriptionId = this.subscriptionId
        if (subscriptionId < 0) {
            return null
        }
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }

        val config = iCclInstance.getConfigForSubId(subscriptionId, iCclInstance.defaultCarrierServicePackageName)
        return config.get(key)
    }

    val simSlotIndex: Int
        get() = this.loadCachedInterface { sub }.getSlotIndex(subscriptionId)

    val isVoLteConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVoNrConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL) &&
            this.getBooleanValue(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL)

    val isCrossSIMConfigEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL) &&
                    this.getBooleanValue(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL)
            } else {
                false
            }
        }

    val isVoWifiConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)

    val isVoWifiWhileRoamingEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL)

    val showIMSinSIMInfo: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL)

    val allowAddingAPNs: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL)

    val showVoWifiMode: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL)

    val showVoWifiRoamingMode: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL)

    val showVoWifiInNetworkName: Int
        get() = this.getIntValue(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT)

    val showVoWifiIcon: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)

    val alwaysDataRATIcon: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL)

    val supportWfcWifiOnly: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL)

    val isVtConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL)

    val ssOverUtEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL)

    val ssOverCDMAEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL)

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
