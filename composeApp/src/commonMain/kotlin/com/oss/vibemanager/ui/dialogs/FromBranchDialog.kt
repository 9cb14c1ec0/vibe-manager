package com.oss.vibemanager.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

@Composable
fun FromBranchDialog(
    branches: List<String>,
    onDismiss: () -> Unit,
    onCreate: (name: String, branch: String) -> Unit,
) {
    var selectedBranch by remember { mutableStateOf("") }
    var taskName by remember { mutableStateOf("") }

    ContentDialog(
        title = "Create Task from Branch",
        visible = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a branch:", fontSize = 14.sp)
                if (branches.isEmpty()) {
                    Text("No available branches found.", fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(branches) { branch ->
                            val isSelected = branch == selectedBranch
                            val bgColor = if (isSelected) Color(0xFF0078D4) else Color.Transparent
                            val textColor = if (isSelected) Color.White else Color.Unspecified
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgColor, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val wasAutoPopulated = taskName.isBlank() || taskName == selectedBranch
                                        selectedBranch = branch
                                        if (wasAutoPopulated) {
                                            taskName = branch
                                        }
                                    }
                                    .padding(8.dp),
                            ) {
                                Text(branch, color = textColor)
                            }
                        }
                    }
                }
                Text("Task name:", fontSize = 14.sp)
                TextField(
                    value = taskName,
                    onValueChange = { taskName = it },
                    placeholder = { Text("e.g. Fix login bug") },
                )
            }
        },
        primaryButtonText = "Create",
        secondaryButtonText = "Cancel",
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> {
                    if (selectedBranch.isNotBlank() && taskName.isNotBlank()) {
                        onCreate(taskName.trim(), selectedBranch)
                    }
                }
                else -> onDismiss()
            }
        },
    )
}
