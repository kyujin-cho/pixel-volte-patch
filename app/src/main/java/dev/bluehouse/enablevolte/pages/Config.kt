package dev.bluehouse.enablevolte.pages

import android.telephony.CarrierConfigManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BooleanPropertyView
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.ClickablePropertyView
import dev.bluehouse.enablevolte.HeaderText
import dev.bluehouse.enablevolte.OnLifecycleEvent
import dev.bluehouse.enablevolte.StringPropertyView
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.checkShizukuPermission
import java.lang.IllegalStateException

@Composable
fun Config(navController: NavController, subId: Int) {
    val moder = SubscriptionModer(subId)
    val carrierModer = CarrierModer(LocalContext.current)
    val scrollState = rememberScrollState()

    var configurable by rememberSaveable { mutableStateOf(false) }
    var voLTEEnabled by rememberSaveable { mutableStateOf(false) }
    var voNREnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var crosssimEnabled by rememberSaveable { mutableStateOf(false) }
    var vtEnabled by rememberSaveable { mutableStateOf(false) }
    var show4GForLteEnabled by rememberSaveable { mutableStateOf(false) }
    var hideEnhancedDataIconEnabled by rememberSaveable { mutableStateOf(false) }
    var is4GPlusEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent by rememberSaveable { mutableStateOf("") }

    fun loadFlags() {
        voLTEEnabled = moder.isVolteConfigEnabled
        voNREnabled = moder.isVonrConfigEnabled
        voWiFiEnabled = moder.isVowifiConfigEnabled
        crosssimEnabled = moder.isCrosssimConfigEnabled
        vtEnabled = moder.isVtConfigEnabled
        show4GForLteEnabled = moder.isShow4GForLteEnabled
        hideEnhancedDataIconEnabled = moder.isHideEnhancedDataIconEnabled
        is4GPlusEnabled = moder.is4GPlusEnabled
        configuredUserAgent = moder.userAgentConfig
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            configurable = try {
                if (checkShizukuPermission(0)) {
                    if (carrierModer.deviceSupportsIMS && subId >= 0) {
                        loadFlags()
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: IllegalStateException) {
                false
            }
        }
    }

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        HeaderText(text = "Toggles")
        BooleanPropertyView(label = "Enable VoLTE", toggled = voLTEEnabled) {
            voLTEEnabled = if (voLTEEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = "Enable VoNR", toggled = voNREnabled) {
            voNREnabled = if (voNREnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, false)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_ENABLED_BOOL, true)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_VONR_SETTING_VISIBILITY_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = "Enable VoWiFi", toggled = voWiFiEnabled) {
            voWiFiEnabled = if (voWiFiEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = "Enable Voice over Cross SIM", toggled = crosssimEnabled) {
            crosssimEnabled = if (crosssimEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, false)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_CROSS_SIM_IMS_AVAILABLE_BOOL, true)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_ENABLE_CROSS_SIM_CALLING_ON_OPPORTUNISTIC_DATA_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = "Enable Video Calling (VT)", toggled = vtEnabled) {
            vtEnabled = if (vtEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = "Show 4G for LTE Data Icon", toggled = show4GForLteEnabled) {
            show4GForLteEnabled = if (show4GForLteEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, true)
                true
            }
        }
        BooleanPropertyView(label = "Hide Enhanced Data Icon", toggled = hideEnhancedDataIconEnabled) {
            hideEnhancedDataIconEnabled = if (hideEnhancedDataIconEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, true)
                true
            }
        }
        BooleanPropertyView(label = "Enable Enhanced 4G LTE/LTE+ (Untested)", toggled = is4GPlusEnabled) {
            is4GPlusEnabled = if (is4GPlusEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, false)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, false)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, true)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_ENHANCED_4G_LTE_BOOL, true)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_ENHANCED_4G_LTE_ON_BY_DEFAULT_BOOL, true)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_ENHANCED_4G_LTE_BOOL, false)
                true
            }
        }

        HeaderText(text = "String Values")
        StringPropertyView(label = "User Agent", value = configuredUserAgent) {
            moder.updateCarrierConfig(moder.KEY_IMS_USER_AGENT, it)
            configuredUserAgent = it
        }

        HeaderText(text = "Miscellaneous")
        ClickablePropertyView(label = "Reset all settings", value = "Reverts to carrier default") {
            moder.clearCarrierConfig()
            loadFlags()
        }
    }
}
