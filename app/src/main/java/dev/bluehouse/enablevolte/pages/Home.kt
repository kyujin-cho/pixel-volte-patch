package dev.bluehouse.enablevolte.pages

import android.content.pm.PackageManager
import android.util.Log
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
import dev.bluehouse.enablevolte.*
import rikka.shizuku.Shizuku
import java.lang.IllegalStateException

const val TAG = "HomeActivity:Home"
@Composable
fun Home(navController: NavController) {
    val modder = CarrierModder(LocalContext.current)

    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var subscriptionId by rememberSaveable { mutableStateOf(-1) }
    var deviceIMSEnabled by rememberSaveable { mutableStateOf(false) }

    var carrierIMSEnabled by rememberSaveable { mutableStateOf(false) }
    var isIMSRegistered by rememberSaveable { mutableStateOf(false) }


    fun loadFlags() {
        shizukuGranted = true
        subscriptionId = modder.subscriptionId
        deviceIMSEnabled = modder.deviceSupportsIMS

        if (subscriptionId >= 0 && deviceIMSEnabled) {
            carrierIMSEnabled = modder.isVolteConfigEnabled
            isIMSRegistered = modder.isIMSRegistered
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            shizukuEnabled = try {
                if (checkShizukuPermission(0)) {
                    Log.d(TAG, "Shizuku granted")
                    loadFlags()
                } else {
                    Shizuku.addRequestPermissionResultListener { requestCode, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, "Shizuku granted")
                            loadFlags()
                        }
                    }
                }
                true
            } catch (e: IllegalStateException) {
                false
            }
        }
    }


    Column(modifier = Modifier.padding(Dp(16f))) {
        HeaderText(text = "Permissions & Capabilities")
        BooleanPropertyView(label = "Shizuku Service Running", toggled = shizukuEnabled)
        BooleanPropertyView(label = "Shizuku Permission Granted", toggled = shizukuGranted)
        BooleanPropertyView(label = "SIM Detected", toggled = subscriptionId >= 0)
        BooleanPropertyView(label = "VoLTE Supported by Device", toggled = deviceIMSEnabled)

        HeaderText(text = "IMS Status")
        BooleanPropertyView(label = "IMS Registered", toggled = isIMSRegistered)
    }
}