package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bluehouse.enablevolte.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserAgentUpdateDialog(
    labels: Array<String>,
    values: Array<String>,
    selectedIndex: Int,
    typedText: String,
    dropdownExpanded: Boolean,
    onTextUpdate: (String) -> Unit,
    onIndexUpdate: (Int) -> Unit,
    onDismissRequest: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(10),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(all = 16.dp)) {
                Text(text = stringResource(R.string.update_value), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 24.dp))
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = onExpandedChange,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    TextField(
                        // The `menuAnchor` modifier must be passed to the text field for correctness.
                        modifier = Modifier
                            .menuAnchor()
                            .wrapContentWidth(),
                        readOnly = true,
                        value = if (values[selectedIndex] == typedText) labels[selectedIndex] else "Custom",
                        onValueChange = {},
                        label = { Text(stringResource(R.string.presets)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { onExpandedChange(false) },
                    ) {
                        labels.forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = { Text(text = label) },
                                onClick = {
                                    onTextUpdate(values[i])
                                    onIndexUpdate(i)
                                    onExpandedChange(false)
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
                TextField(textStyle = TextStyle(fontSize = 14.sp), value = typedText, onValueChange = { onTextUpdate(it) })
                Row(modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                    TextButton(
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = ButtonDefaults.outlinedShape,
                        onClick = { onDismissRequest() },
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
                            onTextUpdate(typedText)
                            onDismissRequest()
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
fun UserAgentPropertyView(label: String, value: String?, onUpdate: ((String) -> Unit)? = null) {
    val labels = arrayOf(stringResource(R.string.default_), stringResource(R.string.lgu), stringResource(R.string.pixel_6_pro))
    val values = arrayOf(stringResource(R.string.ua_default), stringResource(R.string.ua_lgu), stringResource(R.string.p6p))

    var typedText by rememberSaveable { mutableStateOf("") }
    var openTextEditDialog by rememberSaveable { mutableStateOf(false) }
    var dropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(if (values.contains(value)) values.indexOf(value) else 0) }

    if (onUpdate != null) {
        if (openTextEditDialog) {
            UserAgentUpdateDialog(
                labels,
                values,
                selectedIndex,
                typedText,
                dropdownExpanded,
                onTextUpdate = {
                    typedText = it
                    onUpdate(typedText)
                },
                onIndexUpdate = {
                    selectedIndex = it
                },
                onDismissRequest = { openTextEditDialog = false },
                onExpandedChange = { dropdownExpanded = it },
            )
        }
    }
    ClickablePropertyView(label = label, value = value) {
        if (value != null) {
            typedText = value
            openTextEditDialog = true
        }
    }
}

@Preview
@Composable
fun UserAgentUpdateDialogPreview() {
    val labels = arrayOf(stringResource(R.string.default_), stringResource(R.string.lgu), stringResource(R.string.pixel_6_pro))
    val values = arrayOf(stringResource(R.string.ua_default), stringResource(R.string.ua_lgu), stringResource(R.string.p6p))

    var typedText by rememberSaveable { mutableStateOf("") }
    var openTextEditDialog by rememberSaveable { mutableStateOf(false) }
    var dropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    UserAgentUpdateDialog(
        labels,
        values,
        selectedIndex,
        typedText,
        dropdownExpanded,
        onTextUpdate = {
            typedText = it
        },
        onIndexUpdate = {
            selectedIndex = it
        },
        onDismissRequest = { openTextEditDialog = false },
        onExpandedChange = { dropdownExpanded = it },
    )
}

@Preview
@Composable
fun UserAgentPropertyViewPreview() {
    UserAgentPropertyView("Lorem Ipsum", value = stringResource(R.string.ua_default))
}
