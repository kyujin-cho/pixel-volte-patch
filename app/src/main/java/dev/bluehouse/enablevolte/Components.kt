package dev.bluehouse.enablevolte

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

@Composable
fun OnLifecycleEvent(onEvent: (owner: LifecycleOwner, event: Lifecycle.Event) -> Unit) {
    val eventHandler = rememberUpdatedState(onEvent)
    val lifecycleOwner = rememberUpdatedState(LocalLifecycleOwner.current)

    DisposableEffect(lifecycleOwner.value) {
        val lifecycle = lifecycleOwner.value.lifecycle
        val observer = LifecycleEventObserver { owner, event ->
            eventHandler.value(owner, event)
        }

        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }
}

@Composable
fun HeaderText(text: String) {
    Row(modifier = Modifier.padding(top = Dp(20f), bottom = Dp(12f))) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BooleanPropertyView(
    label: String,
    toggled: Boolean,
    enabled: Boolean = true,
    trueLabel: String = "Yes",
    falseLabel: String = "No",
    onClick: ((Boolean) -> Unit)? = null
) {
    if (onClick != null) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))
        ) {
            Text(text = label, modifier = Modifier.weight(1F), fontSize = 18.sp)
            Switch(checked = toggled, enabled = enabled, onCheckedChange = onClick)
        }
    } else {
        Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
            Text(text = label, fontSize = 18.sp, modifier = Modifier.padding(bottom = Dp(4f)))
            Text(
                text = if (toggled) { trueLabel } else { falseLabel },
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StringPropertyView(label: String, value: String, onUpdate: ((String) -> Unit)? = null) {
    var typedText by rememberSaveable { mutableStateOf("") }
    var openTextEditDialog by rememberSaveable { mutableStateOf(false) }

    if (onUpdate != null) {
        if (openTextEditDialog) {
            AlertDialog(
                onDismissRequest = {
                    // Dismiss the dialog when the user clicks outside the dialog or on the back
                    // button. If you want to disable that functionality, simply use an empty
                    // onDismissRequest.
                    openTextEditDialog = false
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onUpdate(typedText)
                            openTextEditDialog = false
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            openTextEditDialog = false
                        }
                    ) {
                        Text("Dismiss")
                    }
                },
                title = { Text(text = "Update Value", style = MaterialTheme.typography.titleLarge) },
                text = {
                    TextField(value = typedText, onValueChange = { typedText = it })
                }
            )
        }
    }
    ClickablePropertyView(label = label, value = value) {
        typedText = value
        openTextEditDialog = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClickablePropertyView(label: String, value: String, onClick: (() -> Unit)? = null) {
    if (onClick != null) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
                Text(text = label, modifier = Modifier.padding(bottom = Dp(4f)), fontSize = 18.sp)
                Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = 14f.sp)
            }
        }
    } else {
        Column(modifier = Modifier.padding(top = Dp(12f), bottom = Dp(12f))) {
            Text(text = label, modifier = Modifier.padding(bottom = Dp(4f)))
            Text(text = value, color = MaterialTheme.colorScheme.outline, fontSize = 14f.sp)
        }
    }
}
