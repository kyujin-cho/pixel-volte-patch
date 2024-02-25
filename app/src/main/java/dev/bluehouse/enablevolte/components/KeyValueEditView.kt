package dev.bluehouse.enablevolte.components

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bluehouse.enablevolte.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ValueType {
    Int,
    Long,
    Bool,
    String,
    IntArray,
    LongArray,
    BoolArray,
    StringArray,
    Unknown,
}

data class ArrayValueType<T : ValueType>(val v: T)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPropertyDialog(
    availableKeys: Iterable<String>?,
    configKey: String,
    selectedValueType: ValueType?,
    value: String,
    keyDropdownExpanded: Boolean,
    valueTypeDropdownExpanded: Boolean,
    filteringOptions: List<String>,
    onConfigKeyChange: (String) -> Unit,
    onValueTypeChange: (ValueType) -> Unit,
    onValueChange: (String) -> Unit,
    onUpdate: (String, ValueType?, String) -> Boolean,
    onKeyDropdownExpandedChange: (Boolean) -> Unit,
    onValueDropdownExpandedChange: (Boolean) -> Unit,
    dismissDialog: () -> Unit,
) {
    val TAG = "Components:EditPropertyDialog"
    Dialog(
        onDismissRequest = { dismissDialog() },
        properties = DialogProperties(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = AlertDialogDefaults.containerColor,
                contentColor = AlertDialogDefaults.textContentColor,
            ),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.update_value), modifier = Modifier.padding(bottom = 16.dp), style = MaterialTheme.typography.titleMedium)
                if (availableKeys != null) {
                    ExposedDropdownMenuBox(
                        expanded = keyDropdownExpanded,
                        onExpandedChange = {
                            Log.d(TAG, "Expand state change requested: ${!keyDropdownExpanded}")
                            onKeyDropdownExpandedChange(it)
                        },
                    ) {
                        TextField(
                            // The `menuAnchor` modifier must be passed to the text field for correctness.
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .onFocusChanged {
                                    onKeyDropdownExpandedChange(it.isFocused)
                                },
                            value = configKey,
                            onValueChange = { onConfigKeyChange(it) },
                            label = { Text(stringResource(R.string.property_name)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = keyDropdownExpanded) },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )
                        if (filteringOptions.isNotEmpty() && keyDropdownExpanded) {
                            ExposedDropdownMenu(
                                expanded = true,
                                onDismissRequest = {},
                            ) {
                                filteringOptions.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption) },
                                        onClick = {
                                            onConfigKeyChange(selectionOption)
                                            onKeyDropdownExpandedChange(false)
                                        },
                                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    TextField(
                        value = configKey,
                        label = { Text(stringResource(R.string.property_name)) },
                        onValueChange = { onConfigKeyChange(it) },
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = if (selectedValueType == ValueType.Bool) { Alignment.CenterVertically } else { Alignment.Top },
                ) {
                    ExposedDropdownMenuBox(
                        expanded = valueTypeDropdownExpanded,
                        onExpandedChange = { onValueDropdownExpandedChange(it) },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .weight(1.0F),
                    ) {
                        TextField(
                            // The `menuAnchor` modifier must be passed to the text field for correctness.
                            modifier = Modifier
                                .menuAnchor()
                                .weight(1.0F),
                            readOnly = true,
                            value = selectedValueType?.name ?: "",
                            onValueChange = {},
                            label = { Text(stringResource(R.string.property_type)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = valueTypeDropdownExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )
                        ExposedDropdownMenu(
                            expanded = valueTypeDropdownExpanded,
                            onDismissRequest = { onValueDropdownExpandedChange(false) },
                        ) {
                            ValueType.values().forEach { valueType ->
                                DropdownMenuItem(
                                    text = { Text(text = valueType.name) },
                                    onClick = {
                                        if (selectedValueType != valueType) {
                                            onValueTypeChange(valueType)
                                            onValueChange("")
                                        }
                                        onValueDropdownExpandedChange(false)
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(0.05F))
                    Box(modifier = Modifier.weight(1.0F, fill = true)) {
                        when (selectedValueType) {
                            ValueType.Bool -> Row(
                                modifier = Modifier.selectableGroup(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = value == "true",
                                    onClick = { onValueChange("true") },
                                )
                                Text(stringResource(R.string.true_))
                                RadioButton(
                                    selected = value == "false",
                                    onClick = { onValueChange("false") },
                                )
                                Text(stringResource(R.string.false_))
                            }
                            ValueType.Int, ValueType.Long -> TextField(
                                value = value,
                                onValueChange = { onValueChange(it) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                            )
                            is ValueType -> TextField(value = value, onValueChange = { onValueChange(it) })
                            else -> Box(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                Row(modifier = Modifier.align(Alignment.End).padding(top = 16.dp)) {
                    TextButton(
                        border = if (selectedValueType != null) BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary) else null,
                        shape = ButtonDefaults.outlinedShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        onClick = {
                            if (onUpdate(configKey, selectedValueType, value)) {
                                dismissDialog()
                            }
                        },
                        enabled = selectedValueType != null,
                    ) { Text(stringResource(R.string.confirm)) }
                    TextButton(
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = ButtonDefaults.outlinedShape,
                        onClick = { dismissDialog() },
                    ) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}

@Composable
fun KeyValueEditView(label: String, availableKeys: Iterable<String>? = null, onUpdate: ((String, ValueType?, String) -> Boolean)) {
    var configKey by rememberSaveable { mutableStateOf("") }
    var selectedValueType: ValueType? by rememberSaveable { mutableStateOf(null) }
    var value by rememberSaveable { mutableStateOf("") }
    var openEditPropertyDialog by rememberSaveable { mutableStateOf(false) }
    var keyDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var valueTypeDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var filteringOptions by rememberSaveable { mutableStateOf(listOf<String>()) }

    LaunchedEffect(configKey) {
        if (availableKeys != null) {
            withContext(Dispatchers.Default) {
                filteringOptions = if (configKey.length >= 3) {
                    val filteredItems = availableKeys.filter { it.contains(configKey, ignoreCase = false) }
                    if (filteredItems.size > 7) {
                        filteredItems.subList(0, 7)
                    } else {
                        filteredItems
                    }
                } else {
                    listOf()
                }
            }
        }
    }

    if (openEditPropertyDialog) {
        EditPropertyDialog(
            availableKeys,
            configKey,
            selectedValueType,
            value,
            keyDropdownExpanded,
            valueTypeDropdownExpanded,
            filteringOptions,
            onConfigKeyChange = { configKey = it },
            onValueTypeChange = { selectedValueType = it },
            onValueChange = { value = it },
            onUpdate = onUpdate,
            onKeyDropdownExpandedChange = { keyDropdownExpanded = it },
            onValueDropdownExpandedChange = { valueTypeDropdownExpanded = it },
            dismissDialog = { openEditPropertyDialog = false },
        )
    }
    ClickablePropertyView(label = label, value = "") {
        openEditPropertyDialog = true
    }
}

@Preview
@Composable
fun KeyValueEditViewPreview() {
    KeyValueEditView("Lorem Ipsum", "dolor sit amet consectetur adipiscing elit".split(" ")) { _, _, _ ->
        false
    }
}

@Preview
@Composable
fun EditPropertyDialogPreview() {
    val availableKeys = "Lorem ipsum dolor sit amet consectetur adipiscing elit".split(" ")
    var configKey by rememberSaveable { mutableStateOf("") }
    var selectedValueType: ValueType? by rememberSaveable { mutableStateOf(null) }
    var value by rememberSaveable { mutableStateOf("") }
    var openEditPropertyDialog by rememberSaveable { mutableStateOf(false) }
    var keyDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    var valueTypeDropdownExpanded by rememberSaveable { mutableStateOf(false) }
    val filteringOptions = listOf<String>()

    EditPropertyDialog(
        availableKeys,
        configKey,
        selectedValueType,
        value,
        keyDropdownExpanded,
        valueTypeDropdownExpanded,
        filteringOptions,
        onConfigKeyChange = { configKey = it },
        onValueTypeChange = { selectedValueType = it },
        onValueChange = { value = it },
        onUpdate = { _, _, _ -> false },
        onKeyDropdownExpandedChange = { keyDropdownExpanded = it },
        onValueDropdownExpandedChange = { valueTypeDropdownExpanded = it },
        dismissDialog = { openEditPropertyDialog = false },
    )
}
