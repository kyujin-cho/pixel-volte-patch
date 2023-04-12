package dev.bluehouse.enablevolte.pages

import android.telephony.CarrierConfigManager
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import dev.bluehouse.enablevolte.OnLifecycleEvent
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DumpedConfig(subId: Int) {
    val scrollState = rememberScrollState()
    var dumpedConfig by rememberSaveable { mutableStateOf("") }
    val moder = SubscriptionModer(subId)
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val dumpDoneText = stringResource(R.string.dump_completed)

    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_CREATE) {
            val sb = CarrierConfigManager::class.java.declaredFields.filter {
                it.name.startsWith("KEY")
            }.map {
                try {
                    val split = it.name.split("_")
                    val value = it.get(it) as String
                    when (split.last()) {
                        "BOOL", "BOOLEAN" -> "${it.name}: ${moder.getBooleanValue(value)}"
                        "STRING" -> "${it.name}: ${moder.getStringValue(value)}"
                        "STRINGS" -> "${it.name}: ${moder.getStringArrayValue(value).joinToString(",")}"
                        "INT" -> "${it.name}: ${moder.getIntValue(value)}"
                        "LONG" -> "${it.name}: ${moder.getLongValue(value)}"
                        "ARRAY" -> {
                            when (split[split.size - 2]) {
                                "INT" -> "${it.name}: [${moder.getIntArrayValue(value).joinToString(",")}]"
                                "BOOL", "BOOLEAN" -> "${it.name}: [${moder.getBooleanArrayValue(value).joinToString(",")}]"
                                "STRING" -> "${it.name}: [${moder.getStringArrayValue(value).joinToString(",")}]"
                                else -> "${it.name}: Unknown (${split[split.size - 2]}Array)"
                            }
                        }
                        else -> {
                            val anyVal = moder.getValue(value)
                            if (anyVal != null) {
                                "${it.name}: $anyVal"
                            } else {
                                "${it.name}: Unknown (${split.last()})"
                            }
                        }
                    }
                } catch (e: NullPointerException) {
                    "${it.name}: null"
                }
            }
            dumpedConfig = sb.joinToString("\n")
        }
    }

    Column(modifier = Modifier.padding(Dp(16f)).verticalScroll(scrollState)) {
        Surface(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(buildAnnotatedString { append(dumpedConfig) })
                    Toast.makeText(context, dumpDoneText, Toast.LENGTH_SHORT).show()
                },
            ),
        ) {
            Text(text = dumpedConfig, fontFamily = FontFamily.Monospace)
        }
    }
}
