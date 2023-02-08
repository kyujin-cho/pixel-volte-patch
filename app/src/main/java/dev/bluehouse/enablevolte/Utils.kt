package dev.bluehouse.enablevolte

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

fun checkShizukuPermission(code: Int): Boolean {
    if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        return true
    } else if (Shizuku.shouldShowRequestPermissionRationale()) {
        return false
    } else {
        Shizuku.requestPermission(code)
        return false
    }
}