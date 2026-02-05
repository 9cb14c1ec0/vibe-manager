package com.oss.vibemanager.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

@Composable
fun AddProjectDialog(
    onDismiss: () -> Unit,
    onAdd: (path: String) -> Unit,
    onBrowse: () -> String?,
) {
    var path by remember { mutableStateOf("") }

    ContentDialog(
        title = "Add Project",
        visible = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Git repository path:", fontSize = 14.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = path,
                        onValueChange = { path = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Path to git repository") },
                    )
                    Button(onClick = {
                        onBrowse()?.let { path = it }
                    }) { Text("Browse") }
                }
            }
        },
        primaryButtonText = "Add",
        secondaryButtonText = "Cancel",
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> if (path.isNotBlank()) onAdd(path.trim())
                else -> onDismiss()
            }
        },
    )
}
