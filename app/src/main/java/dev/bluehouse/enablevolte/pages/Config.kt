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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BooleanPropertyView
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.ClickablePropertyView
import dev.bluehouse.enablevolte.HeaderText
import dev.bluehouse.enablevolte.OnLifecycleEvent
import dev.bluehouse.enablevolte.R
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
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var showVoWifiMode by rememberSaveable { mutableStateOf(false) }
    var showVoWifiInNetworkName by rememberSaveable { mutableStateOf(false) }
    var supportWfcWifiOnly by rememberSaveable { mutableStateOf(false) }
    var vtEnabled by rememberSaveable { mutableStateOf(false) }
    var show4GForLteEnabled by rememberSaveable { mutableStateOf(false) }
    var hideEnhancedDataIconEnabled by rememberSaveable { mutableStateOf(false) }
    var is4GPlusEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent: String? by rememberSaveable { mutableStateOf("") }

    fun loadFlags() {
        voLTEEnabled = moder.isVolteConfigEnabled
        voWiFiEnabled = moder.isVowifiConfigEnabled
        showVoWifiMode = moder.showVoWifiMode
        showVoWifiInNetworkName = (moder.showVoWifiInNetworkName == 1)
        supportWfcWifiOnly = moder.supportWfcWifiOnly
        vtEnabled = moder.isVtConfigEnabled
        show4GForLteEnabled = moder.isShow4GForLteEnabled
        hideEnhancedDataIconEnabled = moder.isHideEnhancedDataIconEnabled
        is4GPlusEnabled = moder.is4GPlusEnabled
        configuredUserAgent = try {
            moder.userAgentConfig
        } catch (e: java.lang.NullPointerException) {
            null
        }
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
        HeaderText(text = stringResource(R.string.toggles))
        BooleanPropertyView(label = stringResource(R.string.enable_volte), toggled = voLTEEnabled) {
            voLTEEnabled = if (voLTEEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.enable_vowifi), toggled = voWiFiEnabled) {
            voWiFiEnabled = if (voWiFiEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.show_vowifi_preference_in_settings), toggled = showVoWifiMode) {
            showVoWifiMode = if (showVoWifiMode) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_WFC_MODE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.add_wifi_calling_to_network_name), toggled = showVoWifiInNetworkName) {
            showVoWifiInNetworkName = if (showVoWifiInNetworkName) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT, 0)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_WFC_SPN_FORMAT_IDX_INT, 1)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.allow_vowifi_in_aeroplane_mode), toggled = supportWfcWifiOnly) {
            supportWfcWifiOnly = if (supportWfcWifiOnly) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.enable_video_calling_vt), toggled = vtEnabled) {
            vtEnabled = if (vtEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VT_AVAILABLE_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.show_4g_for_lte_data_icon), toggled = show4GForLteEnabled) {
            show4GForLteEnabled = if (show4GForLteEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_SHOW_4G_FOR_LTE_DATA_ICON_BOOL, true)
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.hide_enhanced_data_icon), toggled = hideEnhancedDataIconEnabled) {
            hideEnhancedDataIconEnabled = if (hideEnhancedDataIconEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_HIDE_LTE_PLUS_DATA_ICON_BOOL, true)
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.enable_enhanced_4g_lte_lte_untested), toggled = is4GPlusEnabled) {
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

        HeaderText(text = stringResource(R.string.string_values))
        StringPropertyView(label = stringResource(R.string.user_agent), value = configuredUserAgent) {
            moder.updateCarrierConfig(moder.KEY_IMS_USER_AGENT, it)
            configuredUserAgent = it
        }

        HeaderText(text = stringResource(R.string.miscellaneous))
        ClickablePropertyView(
            label = stringResource(R.string.reset_all_settings),
            value = stringResource(R.string.reverts_to_carrier_default),
        ) {
            moder.clearCarrierConfig()
            loadFlags()
        }
    }
}
