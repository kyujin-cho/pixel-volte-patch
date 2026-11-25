package dev.bluehouse.enablevolte.diagnostics

import android.os.Bundle
import android.system.Os
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.ui.theme.EnableVoLTETheme

/**
 * Debug builds only (lives in the `debug` source set, so release builds do not contain it).
 * Forces an ANR, a Java crash or a native crash so that report collection can be tested:
 *
 * `adb shell am start -n dev.bluehouse.enablevolte/.diagnostics.DebugCrashTriggerActivity`
 */
class DebugCrashTriggerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnableVoLTETheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Debug-only triggers for crash and ANR report collection.")
                        Text("ANR: tap the button, then tap the screen again until the ANR dialog appears, and choose Close app.")
                        Button(onClick = { Thread.sleep(ANR_SLEEP_MS) }) {
                            Text("Freeze main thread (ANR)")
                        }
                        Button(onClick = { throw IllegalStateException("Debug crash trigger") }) {
                            Text("Throw Java exception")
                        }
                        Button(onClick = { Os.kill(Os.getpid(), OsConstants.SIGSEGV) }) {
                            Text("Native crash (SIGSEGV)")
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val ANR_SLEEP_MS = 30_000L
    }
}
