package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.bluehouse.enablevolte.components.OnLifecycleEvent
import dev.bluehouse.enablevolte.pages.Config
import dev.bluehouse.enablevolte.pages.DumpedConfig
import dev.bluehouse.enablevolte.pages.Editor
import dev.bluehouse.enablevolte.pages.Home
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import java.lang.IllegalStateException

data class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val subId: Int? = null,
)

// Navigation routes are fixed so that the graph does not depend on the subscription list.
// A back stack restored after activity recreation must find its destinations before
// the subscriptions have been loaded again.
const val HOME_ROUTE = "home"
const val SUB_ID_ARG = "subId"
const val CONFIG_ROUTE = "config/{$SUB_ID_ARG}"
const val CONFIG_DUMP_ROUTE = "config/{$SUB_ID_ARG}/dump"
const val CONFIG_EDIT_ROUTE = "config/{$SUB_ID_ARG}/edit"

fun configRoute(subId: Int) = CONFIG_ROUTE.replace("{$SUB_ID_ARG}", subId.toString())

fun configDumpRoute(subId: Int) = CONFIG_DUMP_ROUTE.replace("{$SUB_ID_ARG}", subId.toString())

fun configEditRoute(subId: Int) = CONFIG_EDIT_ROUTE.replace("{$SUB_ID_ARG}", subId.toString())

private val topLevelRoutes = setOf(HOME_ROUTE, CONFIG_ROUTE)

val NavBackStackEntry.subIdArg: Int get() = this.arguments?.getInt(SUB_ID_ARG, -1) ?: -1

class HomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        HiddenApiBypass.addHiddenApiExemptions("L")
        HiddenApiBypass.addHiddenApiExemptions("I")

        setContent {
            EnableVoLTETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    PixelIMSApp()
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PixelIMSApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val carrierModer = CarrierModer(context)
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    var subscriptions by remember { mutableStateOf(listOf<SubscriptionInfo>()) }

    fun loadApplication() {
        val shizukuStatus = checkShizukuPermission(0)
        try {
            when (shizukuStatus) {
                ShizukuStatus.GRANTED -> {
                    Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                    subscriptions = carrierModer.subscriptions
                }
                ShizukuStatus.NOT_GRANTED -> {
                    Shizuku.addRequestPermissionResultListener { _, grantResult ->
                        if (grantResult == PackageManager.PERMISSION_GRANTED) {
                            Log.d(dev.bluehouse.enablevolte.pages.TAG, "Shizuku granted")
                            subscriptions = carrierModer.subscriptions
                        }
                    }
                }
                else -> {
                    subscriptions = listOf()
                }
            }
        } catch (_: IllegalStateException) {
        }
    }

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            loadApplication()
        }
    }
    val currentRoute = currentBackStackEntry?.destination?.route
    val isTopLevel = currentRoute != null && currentRoute in topLevelRoutes

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        currentBackStackEntry?.destination?.label?.toString() ?: stringResource(R.string.app_name),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                },
                navigationIcon = {
                    if (currentRoute != null && !isTopLevel) {
                        IconButton(onClick = {
                            navController.popBackStack()
                        }, colors = IconButtonDefaults.filledIconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Go back",
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute == HOME_ROUTE) {
                        IconButton(onClick = {
                            loadApplication()
                        }, colors = IconButtonDefaults.filledIconButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh contents",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    val currentSubId = currentBackStackEntry?.subIdArg
                    val items =
                        arrayListOf(
                            Screen(HOME_ROUTE, stringResource(R.string.home), Icons.Filled.Home),
                        )
                    for (subscription in subscriptions) {
                        items.add(
                            Screen(
                                configRoute(subscription.subscriptionId),
                                subscription.uniqueName,
                                Icons.Filled.Settings,
                                subscription.subscriptionId,
                            ),
                        )
                    }
                    // The back stack may be restored onto a SIM page before the subscription
                    // list is (re)loaded; keep a selectable tab for it in the meantime.
                    if (currentRoute == CONFIG_ROUTE && currentSubId != null && items.none { it.subId == currentSubId }) {
                        items.add(
                            Screen(configRoute(currentSubId), stringResource(R.string.sim_config), Icons.Filled.Settings, currentSubId),
                        )
                    }

                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = {
                                Text(screen.title)
                            },
                            selected =
                                if (screen.subId == null) {
                                    currentRoute == screen.route
                                } else {
                                    currentRoute == CONFIG_ROUTE && currentSubId == screen.subId
                                },
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    // on the back stack as users select items.
                                    // State is not saved/restored here: every SIM page shares
                                    // the single CONFIG_ROUTE destination, and saved state is
                                    // keyed by destination, so restoring it could bring back
                                    // another SIM's page.
                                    popUpTo(navController.graph.findStartDestination().id)
                                    // Avoid multiple copies of the same destination when
                                    // reselecting the same item
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(navController, startDestination = HOME_ROUTE, Modifier.padding(innerPadding)) {
            val subIdArguments = listOf(navArgument(SUB_ID_ARG) { type = NavType.IntType })
            composable(HOME_ROUTE, context.resources.getString(R.string.home)) {
                Home(navController)
            }
            composable(CONFIG_ROUTE, context.resources.getString(R.string.sim_config), subIdArguments) {
                Config(navController, it.subIdArg)
            }
            composable(CONFIG_DUMP_ROUTE, context.resources.getString(R.string.config_dump_viewer), subIdArguments) {
                DumpedConfig(context, it.subIdArg)
            }
            composable(CONFIG_EDIT_ROUTE, context.resources.getString(R.string.expert_mode), subIdArguments) {
                Editor(it.subIdArg)
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Preview
@Composable
fun PixelIMSAppPreview() {
    EnableVoLTETheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            PixelIMSApp()
        }
    }
}
