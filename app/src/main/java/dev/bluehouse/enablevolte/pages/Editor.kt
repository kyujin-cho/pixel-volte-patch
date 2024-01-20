package dev.bluehouse.enablevolte.pages

import android.telephony.CarrierConfigManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bluehouse.enablevolte.ClickablePropertyView
import dev.bluehouse.enablevolte.FiniteLoadingDialog
import dev.bluehouse.enablevolte.InfiniteLoadingDialog
import dev.bluehouse.enablevolte.R
import dev.bluehouse.enablevolte.SubscriptionModer
import dev.bluehouse.enablevolte.ValueType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.reflect.Field

fun getValueTypeFromFieldName(key: String): ValueType {
    val split = key.split("_")
    return when (split.last()) {
        "BOOL", "BOOLEAN" -> ValueType.Bool
        "STRING" -> ValueType.String
        "STRINGS" -> ValueType.StringArray
        "INT" -> ValueType.Int
        "LONG" -> ValueType.Long
        "ARRAY" -> {
            when (split[split.size - 2]) {
                "INT" -> ValueType.IntArray
                "BOOL", "BOOLEAN" -> ValueType.BoolArray
                "STRING" -> ValueType.StringArray
                "LONG" -> ValueType.LongArray
                else -> ValueType.Unknown
            }
        }
        else -> ValueType.Unknown
    }
}

abstract class BaseDataRow(open val field: Field, open val rawValue: Any?) {
    val key: String get() {
        return try {
            this.field.get(this.field) as String
        } catch (e: NullPointerException) {
            "(unknown)"
        }
    }
    val fieldName: String get() = this.field.name

    open val fieldType: ValueType get() = getValueTypeFromFieldName(this.fieldName)
    abstract val typedValue: Any?
}

data class DataRow(override val field: Field, override val rawValue: Any?) : BaseDataRow(field, rawValue) {
    val value: String? get() = this.rawValue?.toString()

    override fun toString(): String {
        return this.value ?: "(null)"
    }
    override val typedValue: Any? get() =
        when (fieldType) {
            ValueType.Int, ValueType.IntArray -> this.value?.toInt()
            ValueType.Long, ValueType.LongArray -> this.value?.toLong()
            ValueType.Bool, ValueType.BoolArray -> this.value?.let { it == "true" } ?: { null }
            ValueType.String, ValueType.StringArray -> this.value
            else -> null
        }
}

data class ListDataRow(override val field: Field, override val rawValue: List<Any?>) : BaseDataRow(field, rawValue) {
    val value: List<String?> get() = this.rawValue.map { it?.toString() }

    override fun toString(): String {
        return "[" + this.value.joinToString(",") + "]"
    }
    override val fieldType: ValueType get() = when (super.fieldType) {
        ValueType.IntArray -> ValueType.Int
        ValueType.LongArray -> ValueType.Long
        ValueType.BoolArray -> ValueType.Bool
        ValueType.StringArray -> ValueType.String
        else -> ValueType.Unknown
    }
    override val typedValue: List<Any?> get() = this.value.map {
        when (fieldType) {
            ValueType.Int -> it?.toInt()
            ValueType.Long -> it?.toLong()
            ValueType.Bool -> it?.let { it == "true" } ?: { null }
            ValueType.String -> it
            else -> null
        }
    }
}

fun <T>List<T>.replaceAt(index: Int, newItem: T): List<T> {
    return this.subList(0, index) + listOf(newItem) + this.subList(index + 1, this.size)
}
fun <T>List<T>.append(newItem: T): List<T> {
    return this + listOf(newItem)
}
fun <T>List<T>.removeAt(index: Int): List<T> {
    return this.subList(0, index) + this.subList(index + 1, this.size)
}

@Composable
fun SingleValueEditor(data: DataRow, onValueChange: (String) -> Unit) {
    when (val typedValue = data.typedValue) {
        is Boolean -> Row(
            modifier = Modifier.selectableGroup().fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = typedValue,
                onClick = { onValueChange("true") },
            )
            Text(stringResource(R.string.true_))
            RadioButton(
                selected = !typedValue,
                onClick = { onValueChange("false") },
            )
            Text(stringResource(R.string.false_))
        }
        is Int -> TextField(
            value = data.value ?: "",
            onValueChange = { onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        is Long -> TextField(
            value = data.value ?: "",
            onValueChange = { onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        is String, null -> TextField(
            value = data.value ?: "",
            onValueChange = { onValueChange(it) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
        else -> {}
    }
}

@Composable
fun MultiValueEditor(_data: ListDataRow, onUpdate: (List<String?>) -> Unit) {
    val scrollState = rememberScrollState()
    var data by remember { mutableStateOf(_data) }
    var editIndex by remember { mutableStateOf(-1) }
    val items by remember(data.value) { derivedStateOf { data.value } }
    LaunchedEffect(items) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    Column {
        Column(modifier = Modifier.height(150.dp).verticalScroll(scrollState)) {
            items.mapIndexed { index, it ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}: ")
                    if (index == editIndex) {
                        Box(modifier = Modifier.weight(1f)) {
                            SingleValueEditor(DataRow(field = data.field, rawValue = it)) { updated ->
                                val newItems = items.replaceAt(editIndex, updated)
                                data = data.copy(rawValue = newItems)
                            }
                        }
                        IconButton(onClick = { editIndex = -1 }) { Icon(imageVector = Icons.Filled.Close, contentDescription = "") }
                        IconButton(onClick = {
                            onUpdate(items)
                            editIndex = -1
                        }) { Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = "") }
                    } else {
                        Text(it.toString(), fontSize = 18.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f, fill = true))
                        IconButton(onClick = { editIndex = index }) { Icon(imageVector = Icons.Filled.Edit, contentDescription = "") }
                        IconButton(onClick = {
                            val newItems = items.removeAt(index)
                            data = data.copy(rawValue = newItems)
                            onUpdate(items)
                        }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = "") }
                    }
                }
                Divider()
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f, fill = true))
            IconButton(onClick = {
                val newItems = items.append(null)
                data = data.copy(rawValue = newItems)
            }) { Icon(imageVector = Icons.Filled.Add, contentDescription = "") }
        }
    }
}

data class Section(val name: String, val rows: List<BaseDataRow>)

fun fieldToDataRow(moder: SubscriptionModer, field: Field): BaseDataRow {
    try {
        val value = field.get(field) as String
        return when (getValueTypeFromFieldName(field.name)) {
            ValueType.Int -> DataRow(field, moder.getIntValue(value))
            ValueType.String -> DataRow(field, moder.getStringValue(value))
            ValueType.Bool -> DataRow(
                field,
                moder.getBooleanValue(value),
            )
            ValueType.Long -> DataRow(field, moder.getLongValue(value))
            ValueType.IntArray -> ListDataRow(
                field,
                moder.getIntArrayValue(value).toList(),
            )
            ValueType.BoolArray -> ListDataRow(
                field,
                moder.getBooleanArrayValue(value).toList(),
            )
            ValueType.StringArray -> ListDataRow(
                field,
                moder.getStringArrayValue(value).toList(),
            )
            ValueType.LongArray -> ListDataRow(
                field,
                moder.getIntArrayValue(value).toList(),
            )
            ValueType.Unknown -> {
                val anyVal = moder.getValue(value)
                if (anyVal != null) {
                    DataRow(field, anyVal.toString())
                } else {
                    DataRow(field, null)
                }
            }
        }
    } catch (e: NullPointerException) {
        return DataRow(field, null)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun Editor(subId: Int) {
    var sections by remember { mutableStateOf(listOf<Section>()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var dataToEdit by remember { mutableStateOf<BaseDataRow?>(null) }
    var sectionIndexOfEditingData by remember { mutableStateOf(-1) }
    var rowIndexOfEditingData by remember { mutableStateOf(-1) }
    var rowsLoaded by remember { mutableStateOf(0) }
    var rowsToLoad by remember { mutableStateOf(1) }
    var searchKeyword by remember { mutableStateOf("") }
    var showFieldNameInsteadOfKey by remember { mutableStateOf(false) }
    val filteredSections = remember(sections, searchKeyword) {
        if (searchKeyword.isEmpty()) {
            sections
        } else {
            sections.map {
                Section(name = it.name, rows = it.rows.filter { row -> row.key.contains(searchKeyword) || row.fieldName.contains(searchKeyword) }.map { it })
            }
        }
    }

    val moder = SubscriptionModer(subId)

    LaunchedEffect(true) {
        withContext(Dispatchers.Default) {
            val classes = listOf(
                CarrierConfigManager::class.java,
                *CarrierConfigManager::class.java.declaredClasses,
            )
            val fields = listOf(
                CarrierConfigManager::class.java,
                *CarrierConfigManager::class.java.declaredClasses,
            ).map {
                it.declaredFields.filter { field ->
                    field.name != "KEY_PREFIX" && field.name.startsWith(
                        "KEY_",
                    )
                }
            }.flatten()
            rowsToLoad = fields.size
            sections = classes.map { cls ->
                Section(
                    name = cls.simpleName,
                    rows = cls.declaredFields.filter { field -> field.name != "KEY_PREFIX" && field.name.startsWith("KEY_") }.map {
                        rowsLoaded += 1
                        fieldToDataRow(moder, it)
                    },
                )
            }
            loading = false
        }
    }

    if (loading) {
        FiniteLoadingDialog(current = rowsLoaded, total = rowsToLoad)
        return
    }
    if (saving) {
        InfiniteLoadingDialog()
        return
    }

    LazyColumn {
        stickyHeader {
            Row(modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ showFieldNameInsteadOfKey = !showFieldNameInsteadOfKey }) {
                    if (showFieldNameInsteadOfKey) { Text("a") } else { Text("A") }
                }
                TextField(searchKeyword, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(stringResource(R.string.search)) }, onValueChange = { searchKeyword = it }, singleLine = true, trailingIcon = {
                    if (searchKeyword.isNotEmpty()) {
                        IconButton({ searchKeyword = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Localized description")
                        }
                    }
                })
            }
        }
        filteredSections.forEachIndexed { sectionIndex, section ->
            item {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .fillMaxWidth(),
                ) {
                    Text(section.name, modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp))
                }
            }
            items(section.rows) { row ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                ) {
                    ClickablePropertyView(
                        label = if (showFieldNameInsteadOfKey) { row.fieldName } else { row.key },
                        value = row.toString(),
                        labelFontFamily = FontFamily.Monospace,
                        onClick = if (getValueTypeFromFieldName(row.fieldName) != ValueType.Unknown) { {
                            sectionIndexOfEditingData = sectionIndex
                            rowIndexOfEditingData = sections[sectionIndex].rows.indexOf(row)
                            dataToEdit = row
                        } } else {
                            null
                        },
                    )
                }
            }
        }
    }

    fun updateRow(data: BaseDataRow) {
        if (data.rawValue == null) return
        when (data) {
            is DataRow -> {
                when (data.fieldType) {
                    ValueType.Int -> moder.updateCarrierConfig(data.key, data.typedValue as Int)
                    ValueType.Long -> moder.updateCarrierConfig(data.key, data.typedValue as Long)
                    ValueType.Bool -> moder.updateCarrierConfig(data.key, data.typedValue as Boolean)
                    ValueType.String -> moder.updateCarrierConfig(data.key, data.typedValue as String)
                    else -> {}
                }
            }
            is ListDataRow -> {
                when (data.fieldType) {
                    ValueType.Int -> moder.updateCarrierConfig(data.key, (data.typedValue as List<Int>).toIntArray())
                    ValueType.Long -> moder.updateCarrierConfig(data.key, (data.typedValue as List<Long>).toLongArray())
                    ValueType.Bool -> moder.updateCarrierConfig(data.key, (data.typedValue as List<Boolean>).toBooleanArray())
                    ValueType.String -> moder.updateCarrierConfig(data.key, (data.typedValue as List<String>).toTypedArray())
                    else -> {}
                }
            }
        }
        val updatedData = fieldToDataRow(moder, data.field)
        val newRows = sections[sectionIndexOfEditingData].rows.replaceAt(rowIndexOfEditingData, updatedData)
        val newSections = sections.replaceAt(sectionIndexOfEditingData, sections[sectionIndexOfEditingData].copy(rows = newRows))
        sections = newSections
    }

    @Composable
    fun renderEditDialog(data: BaseDataRow) {
        Dialog(
            onDismissRequest = { dataToEdit = null },
            properties = DialogProperties(),
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AlertDialogDefaults.containerColor,
                    contentColor = AlertDialogDefaults.textContentColor,
                ),
            ) {
                Column {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Column {
                            Text(stringResource(R.string.edit_value), fontWeight = FontWeight.Medium, fontSize = 24.sp)
                            Text("${data.key} (${data.fieldName})", modifier = Modifier.padding(top = 6.dp), fontFamily = FontFamily.Monospace)
                        }
                    }
                    Row(modifier = Modifier.padding(8.dp).fillMaxWidth()) {
                        when (data) {
                            is DataRow -> SingleValueEditor(data) { dataToEdit = data.copy(rawValue = it) }
                            is ListDataRow -> MultiValueEditor(data) {
                                dataToEdit = data.copy(rawValue = it)
                            }
                            else -> Box(modifier = Modifier.fillMaxWidth())
                        }
                    }
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = {
                            dataToEdit = null
                            saving = true
                            updateRow(data)
                            saving = false
                        }) { Text(stringResource(R.string.confirm)) }
                        TextButton(onClick = { dataToEdit = null }) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }

    dataToEdit?.let { data ->
        renderEditDialog(data)
    }
}
