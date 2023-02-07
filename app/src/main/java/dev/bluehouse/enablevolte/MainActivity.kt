package dev.bluehouse.enablevolte

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.TelephonyManager
import androidx.databinding.DataBindingUtil
import com.android.internal.telephony.ICarrierConfigLoader
import dev.bluehouse.enablevolte.databinding.ActivityMainBinding
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

data class ShizukuData(
    var shizukuEnabled: Boolean,
    var shizukuGranted: Boolean,
    var deviceVolteEnabled: Boolean,
    var carrierVolteEnabled: Boolean,
    var carrierVowifiEnabled: Boolean,
    var isIMSRunning: Boolean,
    var subscriptionId: Int,
    var errorString: String
)

interface MainActivityProtocol {
    fun onEnableVoLTEClick()
    fun onEnableVoWiFiClick()
    fun onLoadVoLTEStatusClick()
    fun onClearSettingClick()
}


class MainActivity : AppCompatActivity(), MainActivityProtocol {
    private final val TAG = "MainActivity"
    var shizukuData = ShizukuData(
        false, false,
        false, false, false,
        false,
        -1, ""
    )
    var binding: ActivityMainBinding? = null

    private fun updateVolteConfig(enableVolte: Boolean) {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        var overrideBundle: PersistableBundle? = null
        if (enableVolte) {
            overrideBundle = PersistableBundle()
            overrideBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
        }
        iCclInstance.overrideConfig(shizukuData.subscriptionId, overrideBundle, true)
    }

    private fun updateVowifiConfig(enableVoWiFi: Boolean) {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        var overrideBundle: PersistableBundle? = null
        if (enableVoWiFi) {
            overrideBundle = PersistableBundle()
            overrideBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
        }
        iCclInstance.overrideConfig(shizukuData.subscriptionId, overrideBundle, true)
    }

    private val isVolteConfigEnabled: Boolean
        get() {
            shizukuData.subscriptionId = this.subscriptionId
            if (shizukuData.subscriptionId < 0) {
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
            val config = iCclInstance.getConfigForSubId(shizukuData.subscriptionId, iCclInstance.defaultCarrierServicePackageName)
            return config.getBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL)
        }

    private val isVowifiConfigEnabled: Boolean
        get() {
            shizukuData.subscriptionId = this.subscriptionId
            if (shizukuData.subscriptionId < 0) {
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
            val config = iCclInstance.getConfigForSubId(shizukuData.subscriptionId, iCclInstance.defaultCarrierServicePackageName)
            return config.getBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL)
        }

    private val subscriptionId: Int
        get() {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return -1
            if (telephonyManager.subscriptionId == DEFAULT_SUBSCRIPTION_ID) return -1
            val phoneId = SubscriptionManager.getPhoneId(telephonyManager.subscriptionId)
            if (phoneId == DEFAULT_SUBSCRIPTION_ID) return -1
            return telephonyManager.subscriptionId
        }

    private val deviceSupportsVolte: Boolean
        get () {
            val res = Resources.getSystem()
            val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
            return res.getBoolean(volteConfigId)
        }

    private val isIMSRegistered: Boolean get () {
        val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        return telephonyManager.isImsRegistered(this.shizukuData.subscriptionId)
    }
    private fun checkShizukuPermission(code: Int): Boolean {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            return true
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            return false
        } else {
            Shizuku.requestPermission(code)
            return false
        }
    }

    private fun loadShizukuPredicates() {
        this.shizukuData.shizukuGranted = true
        this.shizukuData.subscriptionId = this.subscriptionId

        this.shizukuData.deviceVolteEnabled = this.deviceSupportsVolte
    }

    private fun loadIMSStatuses() {
        this.shizukuData.carrierVolteEnabled = this.isVolteConfigEnabled
        this.shizukuData.carrierVowifiEnabled = this.isVowifiConfigEnabled
        this.shizukuData.isIMSRunning = this.isIMSRegistered

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.data = shizukuData
        binding.protocol = this
        this.binding = binding

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        try {
            if (this.checkShizukuPermission(0)) {
                this.loadShizukuPredicates()
                if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceVolteEnabled) {
                    this.loadIMSStatuses()
                }
            } else {
                Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        this.loadShizukuPredicates()
                        if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceVolteEnabled) {
                            this.loadIMSStatuses()
                        }
                        this.binding?.invalidateAll()
                    }
                }
            }
            this.shizukuData.shizukuEnabled = true
        } catch (e: java.lang.IllegalStateException) {
            this.shizukuData.shizukuEnabled = false
        }
        this.binding?.invalidateAll()
    }

    override fun onResume() {
        super.onResume()
        if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceVolteEnabled) {
            this.loadIMSStatuses()
        }
    }

    override fun onEnableVoLTEClick() {
        try {
            this.updateVolteConfig(true)
            this.shizukuData.carrierVolteEnabled = isVolteConfigEnabled
            this.shizukuData.errorString = ""
        } catch (e: Exception) {
            shizukuData.errorString = e.stackTraceToString()
        }
        binding?.invalidateAll()
    }

    override fun onEnableVoWiFiClick() {
        try {
            this.updateVowifiConfig(true)
            this.shizukuData.carrierVowifiEnabled = isVowifiConfigEnabled
            this.shizukuData.errorString = ""
        } catch (e: Exception) {
            shizukuData.errorString = e.stackTraceToString()
        }
        binding?.invalidateAll()
    }

    override fun onClearSettingClick() {
        try {
            this.updateVolteConfig(false)
            this.updateVowifiConfig(false)
            this.shizukuData.carrierVolteEnabled = isVolteConfigEnabled
            this.shizukuData.carrierVowifiEnabled = isVowifiConfigEnabled
            shizukuData.errorString = ""
        } catch (e: Exception) {
            shizukuData.errorString = e.stackTraceToString()
        }
        binding?.invalidateAll()
    }

    override fun onLoadVoLTEStatusClick() {
        TODO("Not yet implemented")
    }
}