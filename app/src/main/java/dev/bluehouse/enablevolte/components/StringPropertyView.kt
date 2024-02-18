package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bluehouse.enablevolte.R

@Composable
fun StringPropertyUpdateDialog(typedText: String, onUpdate: (String) -> Unit, onConfirm: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = {
            // Dismiss the dialog when the user clicks outside the dialog or on the back
            // button. If you want to disable that functionality, simply use an empty
            // onDismissRequest.
            onClose()
        },
        dismissButton = {
            TextButton(
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(end = 8.dp),
                shape = ButtonDefaults.outlinedShape,
                onClick = { onClose() },
            ) {
                Text(stringResource(R.string.dismiss))
            }
        },
        confirmButton = {
            TextButton(
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary),
                shape = ButtonDefaults.outlinedShape,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                onClick = {
                    onConfirm()
                    onClose()
                },
            ) {
                Text(stringResource(R.string.confirm), color = MaterialTheme.colorScheme.onPrimary)
            }
        },
        title = { Text(text = stringResource(R.string.update_value), style = MaterialTheme.typography.titleMedium) },
        text = {
            TextField(value = typedText, onValueChange = onUpdate)
        },
    )
}

@Composable
fun StringPropertyView(label: String, value: String?, onUpdate: ((String) -> Unit)? = null) {
    var typedText by rememberSaveable { mutableStateOf("") }
    var openTextEditDialog by rememberSaveable { mutableStateOf(false) }

    if (onUpdate != null) {
        if (openTextEditDialog) {
            StringPropertyUpdateDialog(
                typedText,
                onUpdate = { typedText = it },
                onConfirm = { onUpdate(typedText) },
                onClose = { openTextEditDialog = false },
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
fun StringPropertyViewPreview() {
    var value by remember { mutableStateOf("") }
    StringPropertyView("Lorem Ipsum", value) { value = it }
}

@Preview
@Composable
fun StringPropertyUpdateDialogPreview() {
    var typedText by remember { mutableStateOf("") }
    StringPropertyUpdateDialog(typedText, { typedText = it }, {}, {})
}
