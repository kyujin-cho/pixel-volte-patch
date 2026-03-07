package dev.bluehouse.enablevolte

import android.app.IActivityManager
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.IInterface
import android.os.PersistableBundle
import android.os.ServiceManager
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionInfo
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.internal.telephony.ICarrierConfigLoader
import com.android.internal.telephony.IPhoneSubInfo
import com.android.internal.telephony.ISub
import com.android.internal.telephony.ITelephony
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.time.LocalDate
import java.time.format.DateTimeParseException

object InterfaceCache {
    val cache = HashMap<String, IInterface>()
}

var configPersistent by mutableStateOf(true)

open class Moder {
    @Suppress("ktlint:standard:property-naming")
    val KEY_IMS_USER_AGENT = "ims.ims_user_agent_string"

    protected inline fun <reified T : IInterface> loadCachedInterface(interfaceLoader: () -> T): T {
        InterfaceCache.cache[T::class.java.name]?.let {
            return it as T
        } ?: run {
            val i = interfaceLoader()
            InterfaceCache.cache[T::class.java.name] = i
            return i
        }
    }

    protected val carrierConfigLoader: ICarrierConfigLoader
        get() =
            ICarrierConfigLoader.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .carrierConfigServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService(Context.CARRIER_CONFIG_SERVICE)
                    }!!,
                ),
            )

    protected val telephony: ITelephony
        get() =
            ITelephony.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .telephonyServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService(Context.TELEPHONY_SERVICE)
                    }!!,
                ),
            )

    protected val phoneSubInfo: IPhoneSubInfo?
        get() =
            try {
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .phoneSubServiceRegisterer
                    .get()
            } catch (_: NoClassDefFoundError) {
                ServiceManager.getService("iphonesubinfo")
            }?.let { IPhoneSubInfo.Stub.asInterface(ShizukuBinderWrapper(it)) }

    protected val sub: ISub
        get() =
            ISub.Stub.asInterface(
                ShizukuBinderWrapper(
                    try {
                        TelephonyFrameworkInitializer
                            .getTelephonyServiceManager()
                            .subscriptionServiceRegisterer
                            .get()
                    } catch (_: NoClassDefFoundError) {
                        ServiceManager.getService("isub")
                    }!!,
                ),
            )
}

class CarrierModer(
    private val context: Context,
) : Moder() {
    fun getActiveSubscriptionInfoForSimSlotIndex(index: Int): SubscriptionInfo? {
        val sub = this.loadCachedInterface { sub }
        return try {
            sub.getActiveSubscriptionInfoForSimSlotIndex(index, null, null)
        } catch (_: NoSuchMethodError) {
            val getActiveSubscriptionInfoForSimSlotIndexMethod =
                sub.javaClass.getMethod(
                    "getActiveSubscriptionInfoForSimSlotIndex",
                    Int::class.javaPrimitiveType,
                    String::class.java,
                )
            (getActiveSubscriptionInfoForSimSlotIndexMethod.invoke(sub, index, null) as? SubscriptionInfo)
        }
    }

    val subscriptions: List<SubscriptionInfo>
        get() {
            val sub = this.loadCachedInterface { sub }
            return try {
                sub.getActiveSubscriptionInfoList(null, null, true) ?: emptyList()
            } catch (_: NoSuchMethodError) {
                null
            } ?: try {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null, null) as? List<SubscriptionInfo>) ?: emptyList()
            } catch (_: NoSuchMethodException) {
                val getActiveSubscriptionInfoListMethod =
                    sub.javaClass.getMethod(
                        "getActiveSubscriptionInfoList",
                        String::class.java,
                    )
                (getActiveSubscriptionInfoListMethod.invoke(sub, null) as? List<SubscriptionInfo>) ?: emptyList()
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

class SubscriptionModer(
    private val context: Context,
    val subscriptionId: Int,
) : Moder() {
    @Suppress("ktlint:standard:property-naming")
    private val TAG = "SubscriptionModer"

    private fun overrideConfigDirectly(bundle: Bundle?) {
        val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
        val args = bundle?.let(::toPersistableBundle)

        try {
            iCclInstance.overrideConfig(subscriptionId, args, configPersistent)
        } catch (e: NoSuchMethodError) {
            val overrideConfigMethod =
                iCclInstance.javaClass.getMethod(
                    "overrideConfig",
                    Int::class.javaPrimitiveType,
                    PersistableBundle::class.java,
                )
            overrideConfigMethod.invoke(iCclInstance, subscriptionId, args)
            if (configPersistent) {
                throw e
            }
        }
    }

    private fun overrideConfigUsingBroker(bundle: Bundle?) {
        val am =
            IActivityManager.Stub.asInterface(
                ShizukuBinderWrapper(
                    SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE),
                ),
            )

        val arg =
            bundle ?: run {
                val empty = Bundle()
                empty.putBoolean("moder_clear", true)
                empty
            }
        arg.putInt("moder_subId", subscriptionId)

        am.startInstrumentation(
            ComponentName(context, Class.forName("dev.bluehouse.enablevolte.BrokerInstrumentation")),
            null,
            8,
            arg,
            null,
            UiAutomationConnection(),
            0,
            null,
        )
    }

    private fun overrideConfig(bundle: Bundle?) {
        val securityPatchDate =
            try {
                LocalDate.parse(Build.VERSION.SECURITY_PATCH)
            } catch (_: DateTimeParseException) {
                null
            }
        if (securityPatchDate == null || securityPatchDate.isBefore(LocalDate.of(2025, 10, 1))) {
            try {
                return this.overrideConfigDirectly(bundle)
            } catch (_: SecurityException) {
            } catch (_: NoSuchMethodError) {
            } catch (_: NoSuchMethodException) {
            }
            if (!configPersistent) {
                return
            }
        }
        this.overrideConfigUsingBroker(bundle)
    }

    private fun publishBundle(fn: (Bundle) -> Unit) {
        val overrideBundle = Bundle()
        fn(overrideBundle)
        this.overrideConfig(overrideBundle)
    }

    fun updateCarrierConfig(
        key: String,
        value: Boolean,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBoolean(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: String,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putString(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Int,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putInt(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Long,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLong(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: IntArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putIntArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: BooleanArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putBooleanArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: Array<String>,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putStringArray(key, value) }
    }

    fun updateCarrierConfig(
        key: String,
        value: LongArray,
    ) {
        Log.d(TAG, "Setting $key to $value")
        publishBundle { it.putLongArray(key, value) }
    }

    fun clearCarrierConfig() {
        this.overrideConfig(null)
    }

    fun restartIMSRegistration() {
        val telephony = this.loadCachedInterface { telephony }
        try {
            telephony.resetIms(this.simSlotIndex)
        } catch (_: NoSuchMethodError) {
            telephony.disableIms(this.simSlotIndex)
            telephony.enableIms(this.simSlotIndex)
        }
    }

    fun getStringValue(key: String): String {
        Log.d(TAG, "Resolving string value of key $key")
        return this.config?.getString(key) ?: ""
    }

    fun getBooleanValue(key: String): Boolean {
        Log.d(TAG, "Resolving boolean value of key $key")
        return this.config?.getBoolean(key) ?: false
    }

    fun getIntValue(key: String): Int {
        Log.d(TAG, "Resolving integer value of key $key")
        return this.config?.getInt(key) ?: -1
    }

    fun getLongValue(key: String): Long {
        Log.d(TAG, "Resolving long value of key $key")
        return this.config?.getLong(key) ?: -1L
    }

    fun getBooleanArrayValue(key: String): BooleanArray {
        Log.d(TAG, "Resolving boolean array value of key $key")
        return this.config?.getBooleanArray(key) ?: BooleanArray(0)
    }

    fun getIntArrayValue(key: String): IntArray {
        Log.d(TAG, "Resolving integer array value of key $key")
        return this.config?.getIntArray(key) ?: IntArray(0)
    }

    fun getStringArrayValue(key: String): Array<String> {
        Log.d(TAG, "Resolving string array value of key $key")
        return this.config?.getStringArray(key) ?: emptyArray()
    }

    fun getValue(key: String): Any? {
        Log.d(TAG, "Resolving value of key $key")
        return this.config?.get(key)
    }

    protected val config: PersistableBundle?
        get() {
            if (this.subscriptionId < 0) {
                return null
            }
            val iCclInstance = this.loadCachedInterface { carrierConfigLoader }
            return try {
                iCclInstance.getConfigForSubIdWithFeature(this.subscriptionId, iCclInstance.defaultCarrierServicePackageName, null)
            } catch (_: NoSuchMethodError) {
                null
            } ?: try {
                iCclInstance.getConfigForSubId(this.subscriptionId, iCclInstance.defaultCarrierServicePackageName)
            } catch (_: NoSuchMethodError) {
                val getConfigForSubIdMethod =
                    iCclInstance.javaClass.getMethod(
                        "getConfigForSubId",
                        Int::class.javaPrimitiveType,
                    )
                (getConfigForSubIdMethod.invoke(iCclInstance, this.subscriptionId) as? PersistableBundle)
            }
        }

    val simSlotIndex: Int
        get() = this.loadCachedInterface { sub }.getSlotIndex(subscriptionId)

    val isVoLteConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)

    val isVoNrConfigEnabled: Boolean
        @RequiresApi(VERSION_CODES.UPSIDE_DOWN_CAKE)
        get() =
            this.getBooleanValue(CarrierConfigManager.KEY_VONR_ENABLED_BOOL) &&
                this.getBooleanValue(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL)

    val isCrossSIMConfigEnabled: Boolean
        get() {
            return if (Build.VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
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
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_IMS_REGISTRATION_STATUS_BOOL)

    val allowAddingAPNs: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALLOW_ADDING_APNS_BOOL)

    val showVoWifiMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL)

    val showVoWifiRoamingMode: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL)

    val wfcSpnFormatIndex: Int
        get() = this.getIntValue(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT)

    val carrierName: String
        get() = this.loadCachedInterface { telephony }.getSubscriptionCarrierName(this.subscriptionId) ?: ""

    val showVoWifiIcon: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_WIFI_CALLING_ICON_IN_STATUS_BAR_BOOL)

    val alwaysDataRATIcon: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_ALWAYS_SHOW_DATA_RAT_ICON_BOOL)

    val supportWfcWifiOnly: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL)

    val isVtConfigEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL)

    val ssOverUtEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                this.getBooleanValue(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL)
            } else {
                false
            }

    val ssOverCDMAEnabled: Boolean
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SUPPORT_SS_OVER_CDMA_BOOL)

    val isShow4GForLteEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL)

    val isHideEnhancedDataIconEnabled: Boolean
        @RequiresApi(VERSION_CODES.R)
        get() = this.getBooleanValue(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL)

    val is4GPlusEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
                    this.getBooleanValue(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL) &&
                    !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
            } else {
                this.getBooleanValue(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL) &&
                    !this.getBooleanValue(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL)
            }

    val isNRConfigEnabled: Boolean
        get() =
            if (Build.VERSION.SDK_INT >= VERSION_CODES.S) {
                this
                    .getIntArrayValue(CarrierConfigManager.KEY_CARRIER_NR_AVAILABILITIES_INT_ARRAY)
                    .contentEquals(intArrayOf(1, 2))
            } else {
                false
            }

    val userAgentConfig: String
        get() = this.getStringValue(KEY_IMS_USER_AGENT) ?: ""

    val isIMSRegistered: Boolean
        get() {
            val telephony = this.loadCachedInterface { telephony }
            return telephony.isImsRegistered(this.subscriptionId)
        }
}
