package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.get
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import rikka.shizuku.Shizuku

enum class ShizukuStatus {
    GRANTED, NOT_GRANTED, STOPPED
}
fun checkShizukuPermission(code: Int): ShizukuStatus {
    return if (Shizuku.getBinder() != null) {
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            ShizukuStatus.GRANTED
        } else {
            if (!Shizuku.shouldShowRequestPermissionRationale()) {
                Shizuku.requestPermission(0)
            }
            ShizukuStatus.NOT_GRANTED
        }
    } else {
        ShizukuStatus.STOPPED
    }
}

val SubscriptionInfo.uniqueName: String
    get() = "${this.displayName} (SIM ${this.simSlotIndex + 1})"

fun getLatestAppVersion(handler: (String) -> Unit) {
    "https://api.github.com/repos/kyujin-cho/pixel-volte-patch/releases"
        .httpGet()
        .header("X-GitHub-Api-Version", "2022-11-28")
        .responseJson { _, _, result ->
            when (result) {
                is Result.Failure -> {
                    handler("0.0.0")
                }
                is Result.Success -> {
                    try {
                        handler(result.get().array().getJSONObject(0).getString("tag_name"))
                    } catch (e: java.lang.Exception) {
                        handler("0.0.0")
                    }
                }
            }
        }
}

fun NavGraphBuilder.composable(
    route: String,
    label: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    addDestination(
        ComposeNavigator.Destination(provider[ComposeNavigator::class], content).apply {
            this.route = route
            this.label = label
            arguments.forEach { (argumentName, argument) ->
                addArgument(argumentName, argument)
            }
            deepLinks.forEach { deepLink ->
                addDeepLink(deepLink)
            }
        },
    )
}
