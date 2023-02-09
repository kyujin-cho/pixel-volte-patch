package dev.bluehouse.enablevolte.pages

import android.telephony.CarrierConfigManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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

    var configurable by rememberSaveable { mutableStateOf(false) }
    var voLTEEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var vtEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent by rememberSaveable { mutableStateOf("") }

    fun loadFlags() {
        voLTEEnabled = moder.isVolteConfigEnabled
        voWiFiEnabled = moder.isVowifiConfigEnabled
        vtEnabled = moder.isVtConfigEnabled
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

    Column(modifier = Modifier.padding(Dp(16f))) {
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
