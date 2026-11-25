package dev.bluehouse.enablevolte

import android.app.Application
import android.content.Context
import dev.bluehouse.enablevolte.diagnostics.CrashReports

/**
 * Installs the Java crash recorder as early as the app process allows and starts collecting the
 * previous processes' crash and ANR exits in the background.
 *
 * Not covered: crashes in content providers that start before [attachBaseContext] completes, and
 * the Shizuku UserService `PrivilegedService`, which runs in a separate shell-uid process (its
 * failures surface in this process as Binder exceptions).
 */
class PixelIMSApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReports.installCrashHandler(this)
    }

    override fun onCreate() {
        super.onCreate()
        CrashReports.collectPreviousExits(this)
    }
}
