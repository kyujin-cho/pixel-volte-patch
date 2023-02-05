package dev.bluehouse.enablevolte

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
import android.telephony.TelephonyFrameworkInitializer
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

data class ShizukuData(var enabled: Boolean, var VoLTEEnabled: Boolean)
interface MainActivityProtocol {
    fun onEnableVoLTEClick()
    fun onLoadVoLTEStatusClick()
    fun onClearSettingClick()
}


class MainActivity : AppCompatActivity(), MainActivityProtocol {
    private final val TAG = "MainActivity"
    var shizukuData = ShizukuData(false, false)
    var binding: ActivityMainBinding? = null

    private fun overrideCarrierConfig() {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        val overrideBundle = PersistableBundle()
        overrideBundle.putBoolean("carrier_volte_available_bool", true)
        iCclInstance.overrideConfig(DEFAULT_SUBSCRIPTION_ID, overrideBundle, true)
    }

    private fun deleteCarrierConfig() {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        iCclInstance.overrideConfig(DEFAULT_SUBSCRIPTION_ID, null, true)
    }

    private fun getCarrierConfigForDefaultSubscription() {
        val iCclInstance = ICarrierConfigLoader.Stub.asInterface(
            ShizukuBinderWrapper(
                TelephonyFrameworkInitializer
                    .getTelephonyServiceManager()
                    .carrierConfigServiceRegisterer
                    .get()
            )
        )
        val config = iCclInstance.getConfigForSubId(DEFAULT_SUBSCRIPTION_ID, iCclInstance.defaultCarrierServicePackageName)
        shizukuData.VoLTEEnabled = config.getBoolean("carrier_volte_available_bool")
        binding?.invalidateAll()
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

    private fun getDeviceVoLTEEnabled(): Boolean {
        val res = Resources.getSystem()
        val volteConfigId = res.getIdentifier("config_device_volte_available", "bool", "android")
        return res.getBoolean(volteConfigId)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding: ActivityMainBinding = DataBindingUtil.setContentView(this, R.layout.activity_main)
        binding.lifecycleOwner = this
        binding.data = shizukuData
        binding.protocol = this
        this.binding = binding

        HiddenApiBypass.addHiddenApiExemptions("L")

        if (this.checkShizukuPermission(0)) {
            this.shizukuData.enabled = true
            getCarrierConfigForDefaultSubscription()
        } else {
            Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    this.shizukuData.enabled = true
                    getCarrierConfigForDefaultSubscription()
                }
            }
        }
    }

    override fun onEnableVoLTEClick() {
        overrideCarrierConfig()
        getCarrierConfigForDefaultSubscription()
        Toast.makeText(this, "Enabled VoLTE", Toast.LENGTH_SHORT).show()
    }

    override fun onClearSettingClick() {
        deleteCarrierConfig()
        getCarrierConfigForDefaultSubscription()
        Toast.makeText(this, "Cleared Setting", Toast.LENGTH_SHORT).show()
    }

    override fun onLoadVoLTEStatusClick() {
        TODO("Not yet implemented")
    }
}