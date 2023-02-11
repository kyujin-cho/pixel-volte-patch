package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.json.responseJson
import com.github.kittinunf.result.Result
import rikka.shizuku.Shizuku

fun checkShizukuPermission(code: Int): Boolean {
    return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        true
    } else if (Shizuku.shouldShowRequestPermissionRationale()) {
        false
    } else {
        Shizuku.requestPermission(code)
        false
    }
}

val SubscriptionInfo.uniqueName: String
    get() = "${this.cardId} - ${this.displayName}"

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