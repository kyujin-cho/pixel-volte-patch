package dev.bluehouse.enablevolte.pages

import android.telephony.CarrierConfigManager
import android.widget.Toast
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
import dev.bluehouse.enablevolte.KeyValueEditView
import dev.bluehouse.enablevolte.OnLifecycleEvent
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.UserAgentPropertyView
import dev.bluehouse.enablevolte.ValueType
import dev.bluehouse.enablevolte.checkShizukuPermission
import java.lang.IllegalStateException
import java.util.*

@Composable
fun Config(navController: NavController, subId: Int) {
    val moder = SubscriptionModer(subId)
    val carrierModer = CarrierModer(LocalContext.current)
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val cannotFindKeyText = stringResource(R.string.cannot_find_key)
    var configurable by rememberSaveable { mutableStateOf(false) }
    var voLTEEnabled by rememberSaveable { mutableStateOf(false) }
    var voNREnabled by rememberSaveable { mutableStateOf(false) }
    var crosssimEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabledWhileRoaming by rememberSaveable { mutableStateOf(false) }
    var showVoWifiMode by rememberSaveable { mutableStateOf(false) }
    var showVoWifiRoamingMode by rememberSaveable { mutableStateOf(false) }
    var showVoWifiInNetworkName by rememberSaveable { mutableStateOf(false) }
    var supportWfcWifiOnly by rememberSaveable { mutableStateOf(false) }
    var vtEnabled by rememberSaveable { mutableStateOf(false) }
    var ssOverUtEnabled by rememberSaveable { mutableStateOf(false) }
    var show4GForLteEnabled by rememberSaveable { mutableStateOf(false) }
    var hideEnhancedDataIconEnabled by rememberSaveable { mutableStateOf(false) }
    var is4GPlusEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent: String? by rememberSaveable { mutableStateOf("") }

    fun loadFlags() {
        voLTEEnabled = moder.isVolteConfigEnabled
        voNREnabled = moder.isVonrConfigEnabled
        crosssimEnabled = moder.isCrosssimConfigEnabled
        voWiFiEnabled = moder.isVoWifiConfigEnabled
        voWiFiEnabledWhileRoaming = moder.isVoWifiWhileRoamingEnabled
        showVoWifiMode = moder.showVoWifiMode
        showVoWifiRoamingMode = moder.showVoWifiRoamingMode
        showVoWifiInNetworkName = (moder.showVoWifiInNetworkName == 1)
        supportWfcWifiOnly = moder.supportWfcWifiOnly
        vtEnabled = moder.isVtConfigEnabled
        ssOverUtEnabled = moder.ssOverUtEnabled
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
        HeaderText(text = stringResource(R.string.feature_toggles))
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

        BooleanPropertyView(label = stringResource(R.string.enable_vonr), toggled = voNREnabled) {
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
        BooleanPropertyView(label = stringResource(R.string.enable_crosssim), toggled = crosssimEnabled) {
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
        BooleanPropertyView(label = stringResource(R.string.enable_vowifi), toggled = voWiFiEnabled) {
            voWiFiEnabled = if (voWiFiEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ENABLED_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.enable_vowifi_while_roamed), toggled = voWiFiEnabledWhileRoaming) {
            voWiFiEnabledWhileRoaming = if (voWiFiEnabledWhileRoaming) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_DEFAULT_WFC_IMS_ROAMING_ENABLED_BOOL, true)
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
        BooleanPropertyView(label = stringResource(R.string.enable_enhanced_4g_lte_plus), toggled = is4GPlusEnabled) {
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
        UserAgentPropertyView(label = stringResource(R.string.user_agent), value = configuredUserAgent) {
            moder.updateCarrierConfig(moder.KEY_IMS_USER_AGENT, it)
            configuredUserAgent = it
        }

        HeaderText(text = stringResource(R.string.cosmetic_toggles))
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
        BooleanPropertyView(label = stringResource(R.string.show_vowifi_roaming_preference_in_settings), toggled = showVoWifiRoamingMode) {
            showVoWifiRoamingMode = if (showVoWifiRoamingMode) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_EDITABLE_WFC_ROAMING_MODE_BOOL, true)
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
        BooleanPropertyView(label = stringResource(R.string.show_wifi_only_for_vowifi), toggled = supportWfcWifiOnly) {
            supportWfcWifiOnly = if (supportWfcWifiOnly) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_SUPPORTS_WIFI_ONLY_BOOL, true)
                moder.restartIMSRegistration()
                true
            }
        }
        BooleanPropertyView(label = stringResource(R.string.enable_ss_over_ut), toggled = ssOverUtEnabled) {
            ssOverUtEnabled = if (ssOverUtEnabled) {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, false)
                false
            } else {
                moder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_SUPPORTS_SS_OVER_UT_BOOL, true)
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

        HeaderText(text = stringResource(R.string.miscellaneous))
        ClickablePropertyView(
            label = stringResource(R.string.reset_all_settings),
            value = stringResource(R.string.reverts_to_carrier_default),
        ) {
            moder.clearCarrierConfig()
            loadFlags()
        }
        KeyValueEditView(label = stringResource(id = R.string.manually_set_config)) { key, valueType, value ->
            val foundKey = CarrierConfigManager::class.java.declaredFields.find { it.name == key.uppercase() || (it.name.startsWith("KEY_") && (it.get(it) as String) == key.lowercase()) }
            if (foundKey == null) {
                Toast.makeText(context, cannotFindKeyText, Toast.LENGTH_SHORT).show()
                false
            } else {
                val actualKey = foundKey.get(foundKey) as String
                try {
                    when (valueType) {
                        ValueType.Bool -> moder.updateCarrierConfig(actualKey, value == "true")
                        ValueType.String -> moder.updateCarrierConfig(actualKey, value)
                        ValueType.Int -> moder.updateCarrierConfig(actualKey, value.toInt())
                        ValueType.Long -> moder.updateCarrierConfig(actualKey, value.toLong())
                        ValueType.BoolArray -> moder.updateCarrierConfig(actualKey, (value.split(",").map { it.trim() == "true" }).toBooleanArray())
                        ValueType.StringArray -> moder.updateCarrierConfig(actualKey, (value.split(",").map { it.trim() }).toTypedArray())
                        ValueType.IntArray -> moder.updateCarrierConfig(actualKey, (value.split(",").map { it.trim().toInt() }).toIntArray())
                        ValueType.LongArray -> moder.updateCarrierConfig(actualKey, (value.split(",").map { it.trim().toLong() }).toLongArray())
                        else -> {}
                    }
                    true
                } catch (e: Exception) {
                    Toast.makeText(context, "Error while updating: ${e.message}", Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }
        ClickablePropertyView(
            label = stringResource(R.string.dump_config),
            value = "",
        ) {
            navController.navigate("dumpConfig$subId")
        }
    }
}
