package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioSelectPropertyUpdateDialog(
    label: String,
    values: Array<String>,
    selectedIndex: Int?,
    onUpdate: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var newIndex by remember { mutableIntStateOf(selectedIndex ?: 0) }
    BasicAlertDialog(onDismissRequest = onClose) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(10),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(all = 16.dp).fillMaxWidth()) {
                Text(text = stringResource(R.string.update_value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 24.dp))
                values.forEachIndexed { index, s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = newIndex == index, onClick = { newIndex = index })
                        Text(s)
                    }
                }
                Row(modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                    TextButton(
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = ButtonDefaults.outlinedShape,
                        onClick = { onClose() },
                    ) {
                        Text(stringResource(R.string.dismiss))
                    }
                    TextButton(
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                        shape = ButtonDefaults.outlinedShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = {
                            onUpdate(newIndex)
                            onClose()
                        },
                    ) {
                        Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun RadioSelectPropertyView(label: String, values: Array<String>, selectedIndex: Int?, onUpdate: ((Int) -> Unit)? = null) {
    var openDialog by rememberSaveable { mutableStateOf(false) }

    if (onUpdate != null) {
        if (openDialog) {
            RadioSelectPropertyUpdateDialog(
                label,
                values,
                selectedIndex,
                onUpdate = { onUpdate(it) },
                onClose = { openDialog = false },
            )
        }
    }
    ClickablePropertyView(label = label, value = if (selectedIndex != null) values[selectedIndex] else "") {
        openDialog = true
    }
}

val values = arrayOf(
    "Phasellus sit amet urna ut.",
    "Nullam sit amet diam sagittis, fermentum purus sed, blandit felis.",
    "Nulla sed velit ac nibh fermentum efficitur.",
    "Sed pharetra nibh at nisi lacinia imperdiet.",
    "Nullam sollicitudin elit et hendrerit malesuada.",
    "Cras in nulla sed nulla accumsan vehicula eget id augue.",
    "Duis in urna quis massa bibendum sollicitudin.",
    "Maecenas vel elit at libero sagittis tristique.",
    "Vestibulum tempor felis a ex cursus, ut molestie lectus porttitor.",
)

@Preview
@Composable
fun RadioSelectPropertyViewPreview() {
    var selectedIndex: Int? by remember { mutableStateOf(null) }
    RadioSelectPropertyView("Lorem Ipsum", values, selectedIndex) {
        selectedIndex = it
    }
}

@Preview
@Composable
fun RadioSelectPropertyUpdateDialogPreview() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    RadioSelectPropertyUpdateDialog(
        "Lorem Ipsum",
        values,
        selectedIndex,
        { selectedIndex = it },
        {},
    )
}
