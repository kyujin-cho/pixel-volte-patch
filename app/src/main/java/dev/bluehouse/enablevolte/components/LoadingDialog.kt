package dev.bluehouse.enablevolte.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.bluehouse.enablevolte.R

@Composable
fun InfiniteLoadingDialog() {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AlertDialogDefaults.containerColor,
                    contentColor = AlertDialogDefaults.textContentColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(30.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(text = stringResource(R.string.please_wait), modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
fun FiniteLoadingDialog(current: Int, total: Int) {
    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AlertDialogDefaults.containerColor,
                    contentColor = AlertDialogDefaults.textContentColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Column {
                        Text(stringResource(R.string.loading), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        LinearProgressIndicator(
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .padding(top = 24.dp, bottom = 4.dp)
                                .fillMaxWidth(),
                            progress = current.toFloat() / total,
                        )
                        Text(stringResource(R.string.loaded, current, total))
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun InfiniteLoadingDialogPreview() {
    InfiniteLoadingDialog()
}

@Preview
@Composable
fun FiniteLoadingDialogPreview() {
    FiniteLoadingDialog(1, 2)
}
