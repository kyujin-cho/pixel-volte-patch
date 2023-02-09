package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
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
