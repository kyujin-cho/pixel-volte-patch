package dev.bluehouse.enablevolte.pages

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SubscriptionInfo
import android.util.Log
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
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import dev.bluehouse.enablevolte.BooleanPropertyView
import dev.bluehouse.enablevolte.BuildConfig
import dev.bluehouse.enablevolte.CarrierModer
import dev.bluehouse.enablevolte.ClickablePropertyView
import dev.bluehouse.enablevolte.HeaderText
import dev.bluehouse.enablevolte.OnLifecycleEvent
import dev.bluehouse.enablevolte.StringPropertyView
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.checkShizukuPermission
import dev.bluehouse.enablevolte.getLatestAppVersion
import dev.bluehouse.enablevolte.uniqueName
import net.swiftzer.semver.SemVer
import rikka.shizuku.Shizuku

const val TAG = "HomeActivity:Home"

@Composable
fun Home(navController: NavController) {
    val carrierModer = CarrierModer(LocalContext.current)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var subscriptions by rememberSaveable { mutableStateOf(listOf<SubscriptionInfo>()) }
    var deviceIMSEnabled by rememberSaveable { mutableStateOf(false) }

    var isIMSRegistered by rememberSaveable { mutableStateOf(listOf<Boolean>()) }
    var newerVersion by rememberSaveable { mutableStateOf("") }

    fun loadFlags() {
        shizukuGranted = true
        subscriptions = carrierModer.subscriptions
        deviceIMSEnabled = carrierModer.deviceSupportsIMS

        if (subscriptions.isNotEmpty() && deviceIMSEnabled) {
            isIMSRegistered = subscriptions.map { SubscriptionModer(it.subscriptionId).isIMSRegistered }
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            shizukuEnabled = try {
                if (checkShizukuPermission(0)) {
                    loadFlags()
                } else {
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            loadFlags()
                        }
                    }
                }
                true
            } catch (e: IllegalStateException) {
                false
            }
            getLatestAppVersion {
                Log.d(TAG, "Fetched version $it")
                val latest = SemVer.parse(it)
                val current = SemVer.parse(BuildConfig.VERSION_NAME)
                if (latest > current) {
                    newerVersion = it
                }
            }
        }
    }

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        HeaderText(text = "Version")
        if (newerVersion.isNotEmpty()) {
            ClickablePropertyView(label = BuildConfig.VERSION_NAME, value = "Newer version $newerVersion available!") {
                val url = "https://github.com/kyujin-cho/pixel-volte-patch/releases/tag/$newerVersion"
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(url)
                startActivity(context, i, null)
            }
        } else {
            StringPropertyView(label = BuildConfig.VERSION_NAME, value = "Running latest version")
        }
        HeaderText(text = "Permissions & Capabilities")
        BooleanPropertyView(label = "Shizuku Service Running", toggled = shizukuEnabled)
        BooleanPropertyView(label = "Shizuku Permission Granted", toggled = shizukuGranted)
        BooleanPropertyView(label = "SIM Detected", toggled = subscriptions.isNotEmpty())
        BooleanPropertyView(label = "VoLTE Supported by Device", toggled = deviceIMSEnabled)

        for (idx in subscriptions.indices) {
            HeaderText(text = "IMS Status for ${subscriptions[idx].uniqueName}")
            BooleanPropertyView(label = "IMS Status", toggled = isIMSRegistered[idx], trueLabel = "Registered", falseLabel = "Unregistered")
        }
    }
}
