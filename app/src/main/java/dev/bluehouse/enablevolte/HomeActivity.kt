package dev.bluehouse.enablevolte

import android.annotation.StringRes
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.CarrierConfigManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku

private val TAG = "HomeActivity"

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

class HomeActivity : ComponentActivity() {
    private val viewModel: HomeActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        setContent {
            EnableVoLTETheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PixelIMSApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp() {
    val navController = rememberNavController()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(id = R.string.app_name)) })
        },
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.resourceId)) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                // Pop up to the start destination of the graph to
                                // avoid building up a large stack of destinations
                                // on the back stack as users select items
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                // Avoid multiple copies of the same destination when
                                // reselecting the same item
                                launchSingleTop = true
                                // Restore state when reselecting a previously selected item
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) {
                Home(navController)
            }
            composable(Screen.Config.route) {
                Config(navController)
            }
        }
    }
}

sealed class Screen(val route: String, @StringRes val resourceId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Filled.Home)
    object Config : Screen("config", R.string.config, Icons.Filled.Settings)
}

val items = listOf(
    Screen.Home,
    Screen.Config,
)

@Composable
fun HeaderText(text: String) {
    Row(modifier = Modifier.padding(top = Dp(20f), bottom = Dp(12f))) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun BooleanPropertyView(label: String, toggled: Boolean, enabled: Boolean = true, onClick: ((Boolean) -> Unit)? = null) {
    if (onClick != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
            Text(text = label, modifier = Modifier.weight(1F), fontSize = 18.sp)
            Switch(checked = toggled, enabled = enabled, onCheckedChange = onClick)
        }
    } else {
        Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
            Text(text = label, fontSize = 18.sp, modifier = Modifier.padding(bottom = Dp(4f)))
            Text(text = if (toggled) { "Yes" } else { "No" }, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringPropertyView(label: String, value: String, onUpdate: ((String) -> Unit)? = null) {
    var typedText by rememberSaveable { mutableStateOf("") }
    var openTextEditDialog by rememberSaveable { mutableStateOf(false) }

    if (onUpdate != null) {
        if (openTextEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    // Dismiss the dialog when the user clicks outside the dialog or on the back
                    // button. If you want to disable that functionality, simply use an empty
                    // onDismissRequest.
                    openTextEditDialog = false
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onUpdate(typedText)
                            openTextEditDialog = false
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            openTextEditDialog = false
                        }
                    ) {
                        Text("Dismiss")
                    }
                },
                title = { Text(text = "Update Value", style = MaterialTheme.typography.titleLarge)},
                text = {
                    TextField(value = typedText, onValueChange = { typedText = it })
                }
            )
        }
        Surface(onClick = {
            typedText = value
            openTextEditDialog = true
        }) {
            Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
                Text(text = label, modifier = Modifier.padding(bottom = Dp(4f)), fontSize = 18.sp)
                Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = 14f.sp)
            }
        }
    } else {
        Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
            Text(text = label, modifier = Modifier.padding(bottom = Dp(4f)))
            Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = 14f.sp)
        }
    }

}

@Composable
fun Home(navController: NavController) {
    val modder = CarrierModder(LocalContext.current)

    var shizukuEnabled by rememberSaveable { mutableStateOf(false) }
    var shizukuGranted by rememberSaveable { mutableStateOf(false) }
    var subscriptionId by rememberSaveable { mutableStateOf(-1) }
    var deviceIMSEnabled by rememberSaveable { mutableStateOf(false) }

    var carrierIMSEnabled by rememberSaveable { mutableStateOf(false) }
    var isIMSRunning by rememberSaveable { mutableStateOf(false) }

    fun loadFlags() {
        shizukuGranted = true
        subscriptionId = modder.subscriptionId
        deviceIMSEnabled = modder.deviceSupportsIMS

        if (subscriptionId >= 0 && deviceIMSEnabled) {
            carrierIMSEnabled = modder.isVoLTEConfigEnabled
            isIMSRunning = modder.isIMSRegistered
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
            } catch (e: java.lang.IllegalStateException) {
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
        BooleanPropertyView(label = "VoLTE Registered", toggled = isIMSRunning)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Config(navController: NavController) {
    val modder = CarrierModder(LocalContext.current)

    var configurable by rememberSaveable { mutableStateOf(false) }
    var voLTEEnabled by rememberSaveable { mutableStateOf(false) }
    var voWiFiEnabled by rememberSaveable { mutableStateOf(false) }
    var configuredUserAgent by rememberSaveable { mutableStateOf("") }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            configurable = try {
                if (checkShizukuPermission(0)) {
                    val c = modder.deviceSupportsIMS && modder.subscriptionId >= 0
                    if (c) {
                        voLTEEnabled = modder.isVoLTEConfigEnabled
                        voWiFiEnabled = modder.isVoWiFiConfigEnabled
                        configuredUserAgent = modder.userAgentConfig
                    }
                    c
                } else {
                    false
                }
            } catch (e: java.lang.IllegalStateException) {
                false
            }
        }
    }
//    overrideBundle.putString("ims.ims_user_agent_string", "TTA-VoLTE/3.0 GB17L/T1B1.220819.007 Device-Type/Android_Phone OMD")

    Column(modifier = Modifier.padding(Dp(16f))) {
        HeaderText(text = "Toggles")
        BooleanPropertyView(label = "Enable VoLTE", toggled = voLTEEnabled) {
            voLTEEnabled = if (voLTEEnabled) {
                modder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, false)
                false
            } else {
                modder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_VOLTE_AVAILABLE_BOOL, true)
                true
            }
        }
        BooleanPropertyView(label = "Enable VoWiFi", toggled = voWiFiEnabled) {
            voWiFiEnabled = if (voWiFiEnabled) {
                modder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, false)
                false
            } else {
                modder.updateCarrierConfig(CarrierConfigManager.KEY_CARRIER_WFC_IMS_AVAILABLE_BOOL, true)
                true
            }
        }

        HeaderText(text = "String Values")
        StringPropertyView(label = "User Agent", value = configuredUserAgent) {
            modder.updateCarrierConfig(modder.KEY_IMS_USER_AGENT, it)
            configuredUserAgent = it
        }
    }
}