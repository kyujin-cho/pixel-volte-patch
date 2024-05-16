package dev.bluehouse.enablevolte.components

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

@Composable
fun BooleanPropertyView(
    label: String,
    toggled: Boolean?,
    enabled: Boolean = true,
    trueLabel: String = stringResource(R.string.yes),
    falseLabel: String = stringResource(R.string.no),
    minSdk: Int = Build.VERSION.SDK_INT,
    onClick: ((Boolean) -> Unit)? = null,
) {
    val localEnabled = enabled && Build.VERSION.SDK_INT >= minSdk

    if (toggled == null) {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Text(text = label, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = stringResource(R.string.unknown), fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    if (onClick != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Text(text = label, modifier = Modifier.weight(1F), fontSize = 18.sp)
            Switch(checked = toggled, enabled = localEnabled, onCheckedChange = onClick)
        }
    } else {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Text(text = label, fontSize = 18.sp, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = if (toggled) { trueLabel } else { falseLabel }, fontSize = 14.sp, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Preview
@Composable
fun BooleanPropertyViewPreview() {
    var toggled by remember { mutableStateOf(false) }
    BooleanPropertyView(label = "Lorem Ipsum", toggled = toggled) { toggled = !toggled }
}

@Preview
@Composable
fun LowSDKBooleanPropertyViewPreview() {
    var toggled by remember { mutableStateOf(false) }
    BooleanPropertyView(label = "Lorem Ipsum", toggled = toggled, minSdk = 999) { toggled = !toggled }
}
