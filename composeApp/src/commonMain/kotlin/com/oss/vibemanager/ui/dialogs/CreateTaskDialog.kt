package com.oss.vibemanager.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, branch: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }

    fun slugify(input: String): String =
        input.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .trim('-')

    ContentDialog(
        title = "New Task",
        visible = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Task name:", fontSize = 14.sp)
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                        branch = slugify(it)
                    },
                    placeholder = { Text("e.g. Add login page") },
                )
                Text("Branch name:", fontSize = 14.sp)
                TextField(
                    value = branch,
                    onValueChange = { branch = it },
                    placeholder = { Text("e.g. add-login-page") },
                )
            }
        },
        primaryButtonText = "Create",
        secondaryButtonText = "Cancel",
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> {
                    if (name.isNotBlank() && branch.isNotBlank()) {
                        onCreate(name.trim(), branch.trim())
                    }
                }
                else -> onDismiss()
            }
        },
    )
}
