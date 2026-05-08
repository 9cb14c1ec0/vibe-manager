package com.oss.vibemanager.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

@Composable
fun CreateTaskDialog(
    availableAgents: List<Pair<String, String>>, // (kindId, displayName)
    defaultAgent: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, branch: String, agentKind: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var agentKind by remember(defaultAgent) {
        mutableStateOf(
            if (availableAgents.any { it.first == defaultAgent }) defaultAgent
            else availableAgents.firstOrNull()?.first ?: "Claude"
        )
    }

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
                Text("Agent:", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((id, label) in availableAgents) {
                        Button(
                            onClick = { agentKind = id },
                            modifier = Modifier,
                        ) {
                            Text(if (agentKind == id) "$label ✓" else label)
                        }
                    }
                }
            }
        },
        primaryButtonText = "Create",
        secondaryButtonText = "Cancel",
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> {
                    if (name.isNotBlank() && branch.isNotBlank()) {
                        onCreate(name.trim(), branch.trim(), agentKind)
                    }
                }
                else -> onDismiss()
            }
        },
    )
}
