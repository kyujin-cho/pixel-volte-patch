package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.os.BaseBundle
import android.os.Bundle
import android.os.PersistableBundle
import android.telephony.SubscriptionInfo
import androidx.compose.animation.AnimatedContentScope
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
    GRANTED,
    NOT_GRANTED,
    STOPPED,
}

fun checkShizukuPermission(code: Int): ShizukuStatus =
    if (Shizuku.getBinder() != null) {
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
                        handler(
                            result
                                .get()
                                .array()
                                .getJSONObject(0)
                                .getString("tag_name"),
                        )
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
    content: @Composable AnimatedContentScope.(@JvmSuppressWildcards NavBackStackEntry) -> Unit,
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

/**
 * Creates a new [PersistableBundle] from the specified [Bundle].
 * Will ignore all values that are not persistable, according
 * to [.isPersistableBundleType].
 */
fun toPersistableBundle(bundle: Bundle): PersistableBundle {
    val persistableBundle = PersistableBundle()
    for (key in bundle.keySet()) {
        val value = bundle.get(key)
        if (isPersistableBundleType(value)) {
            putIntoBundle(persistableBundle, key, value!!)
        }
    }
    return persistableBundle
}

/**
 * Checks if the specified object can be put into a [PersistableBundle].
 *
 * @see [PersistableBundle Implementation](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/os/PersistableBundle.java.49)
 */
fun isPersistableBundleType(value: Any?): Boolean =
    (
        (value is PersistableBundle) ||
            (value is Int) || (value is IntArray) ||
            (value is Long) || (value is LongArray) ||
            (value is Double) || (value is DoubleArray) ||
            (value is String) || (value is Array<*> && value.isArrayOf<String>()) ||
            (value is Boolean) || (value is BooleanArray)
    )

/**
 * Attempts to insert the specified key value pair into the specified bundle.
 *
 * @throws IllegalArgumentException if the value type can not be put into the bundle.
 */
@Throws(IllegalArgumentException::class)
fun putIntoBundle(
    baseBundle: BaseBundle,
    key: String?,
    value: Any?,
) {
    requireNotNull(value != null) { "Unable to determine type of null values" }
    if (value is Int) {
        baseBundle.putInt(key, value)
    } else if (value is IntArray) {
        baseBundle.putIntArray(key, value)
    } else if (value is Long) {
        baseBundle.putLong(key, value)
    } else if (value is LongArray) {
        baseBundle.putLongArray(key, value)
    } else if (value is Double) {
        baseBundle.putDouble(key, value)
    } else if (value is DoubleArray) {
        baseBundle.putDoubleArray(key, value)
    } else if (value is String) {
        baseBundle.putString(key, value)
    } else if (value is Array<*> && value.isArrayOf<String>()) {
        baseBundle.putStringArray(key, value as Array<String?>)
    } else if (value is Boolean) {
        baseBundle.putBoolean(key, value)
    } else if (value is BooleanArray) {
        baseBundle.putBooleanArray(key, value)
    } else {
        throw IllegalArgumentException(
            ("Objects of type ${value?.javaClass?.simpleName ?: "Unknown"} can not be put into a ${BaseBundle::class.java.simpleName}"),
        )
    }
}
