package dev.bluehouse.enablevolte

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.radio.sim.Carrier
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager
import android.telephony.SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
import android.telephony.SubscriptionManager.MAX_SUBSCRIPTION_ID_VALUE
import android.telephony.TelephonyFrameworkInitializer
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import com.android.internal.telephony.ICarrierConfigLoader
import dev.bluehouse.enablevolte.databinding.ActivityMainBinding
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.ShizukuProvider
import rikka.sui.Sui

data class ShizukuData(
    var shizukuEnabled: Boolean,
    var shizukuGranted: Boolean,
    var deviceIMSEnabled: Boolean,
    var carrierIMSEnabled: Boolean,
    var isIMSRunning: Boolean,
    var subscriptionId: Int,
    var errorString: String
)

interface MainActivityProtocol {
    fun onEnableVoLTEClick()
    fun onLoadVoLTEStatusClick()
    fun onClearSettingClick()
}


class MainActivity : AppCompatActivity(), MainActivityProtocol {
    private final val TAG = "MainActivity"
    var shizukuData = ShizukuData(
        false, false,
        false, false,
        false,
        -1, ""
    )
    var binding: ActivityMainBinding? = null

    private fun updateCarrierConfig(enableIMS: Boolean) {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        var overrideBundle: PersistableBundle? = null
        if (enableIMS) {
            overrideBundle = PersistableBundle()
            overrideBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
            overrideBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
            overrideBundle.putBoolean(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
        }
        iCclInstance.overrideConfig(shizukuData.subscriptionId, overrideBundle, true)
    }

    private val isIMSConfigEnabled: Boolean
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

    private val subscriptionId: Int
        get() {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                ?: return -1
            if (telephonyManager.subscriptionId == DEFAULT_SUBSCRIPTION_ID) return -1
            val phoneId = SubscriptionManager.getPhoneId(telephonyManager.subscriptionId)
            if (phoneId == DEFAULT_SUBSCRIPTION_ID) return -1
            return telephonyManager.subscriptionId
        }

    private val deviceSupportsIMS: Boolean
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

        this.shizukuData.deviceIMSEnabled = this.deviceSupportsIMS
    }

    private fun loadIMSStatuses() {
        this.shizukuData.carrierIMSEnabled = this.isIMSConfigEnabled
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
                if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceIMSEnabled) {
                    this.loadIMSStatuses()
                }
            } else {
                Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                    if (grantResult == PackageManager.PERMISSION_GRANTED) {
                        this.loadShizukuPredicates()
                        if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceIMSEnabled) {
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
        if (this.shizukuData.subscriptionId >= 0 && this.shizukuData.deviceIMSEnabled) {
            this.loadIMSStatuses()
        }
    }

    override fun onEnableVoLTEClick() {
        try {
            this.updateCarrierConfig(true)
            this.shizukuData.carrierIMSEnabled = isIMSConfigEnabled
            this.shizukuData.errorString = ""
        } catch (e: Exception) {
            shizukuData.errorString = e.stackTraceToString()
        }
        binding?.invalidateAll()
    }

    override fun onClearSettingClick() {
        try {
            this.updateCarrierConfig(false)
            this.shizukuData.carrierIMSEnabled = isIMSConfigEnabled
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