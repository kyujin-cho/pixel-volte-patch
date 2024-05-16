package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

@Composable
fun ClickablePropertyView(
    label: String,
    value: String?,
    labelFontSize: TextUnit = 18.sp,
    valueFontSize: TextUnit = 14.sp,
    labelFontFamily: FontFamily? = null,
    valueFontFamily: FontFamily? = null,
    onClick: (() -> Unit)? = null,
) {
    if (value == null) {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Text(text = label, fontSize = labelFontSize, modifier = Modifier.padding(bottom = 4.dp))
            Text(text = stringResource(R.string.unknown), color = MaterialTheme.colorScheme.outline, fontSize = valueFontSize)
        }
        return
    }
    if (onClick != null) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
                if (value != "") {
                    Text(text = label, modifier = Modifier.padding(bottom = 4.dp), fontSize = labelFontSize, fontFamily = labelFontFamily)
                    Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = valueFontSize, fontFamily = valueFontFamily)
                } else {
                    Text(text = label, modifier = Modifier.padding(top = 2.dp, bottom = 2.dp), fontSize = labelFontSize, fontFamily = labelFontFamily)
                }
            }
        }
    } else {
        Column(modifier = Modifier.padding(top = 12.dp, bottom = 12.dp)) {
            Text(text = label, modifier = Modifier.padding(bottom = 4.dp), fontSize = labelFontSize, fontFamily = labelFontFamily)
            Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = valueFontSize, fontFamily = valueFontFamily)
        }
    }
}

@Preview
@Composable
fun ClickablePropertyViewPreview() {
    val value by remember { mutableStateOf("dolor sit amet") }
    ClickablePropertyView("Lorem Ipsum", value) {}
}
