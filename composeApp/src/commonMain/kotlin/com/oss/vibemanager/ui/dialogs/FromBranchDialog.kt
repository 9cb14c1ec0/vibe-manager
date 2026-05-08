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
import androidx.compose.foundation.layout.Row
import io.github.composefluent.component.Button
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

private data class BranchItem(val name: String, val isRemote: Boolean)

@Composable
fun FromBranchDialog(
    localBranches: List<String>,
    remoteBranches: List<String>,
    availableAgents: List<Pair<String, String>>,
    defaultAgent: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, branch: String, agentKind: String) -> Unit,
) {
    var selectedBranch by remember { mutableStateOf<BranchItem?>(null) }
    var taskName by remember { mutableStateOf("") }
    var agentKind by remember(defaultAgent) {
        mutableStateOf(
            if (availableAgents.any { it.first == defaultAgent }) defaultAgent
            else availableAgents.firstOrNull()?.first ?: "Claude"
        )
    }

    val items = remember(localBranches, remoteBranches) {
        buildList {
            if (localBranches.isNotEmpty()) {
                localBranches.forEach { add(BranchItem(it, isRemote = false)) }
            }
            if (remoteBranches.isNotEmpty()) {
                remoteBranches.forEach { add(BranchItem(it, isRemote = true)) }
            }
        }
    }

    ContentDialog(
        title = "Create Task from Branch",
        visible = true,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a branch:", fontSize = 14.sp)
                if (items.isEmpty()) {
                    Text("No available branches found.", fontSize = 12.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (localBranches.isNotEmpty()) {
                            item {
                                Text(
                                    "Local",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
                                )
                            }
                            items(items.filter { !it.isRemote }) { item ->
                                BranchRow(
                                    item = item,
                                    isSelected = item == selectedBranch,
                                    onClick = {
                                        val wasAutoPopulated = taskName.isBlank() || taskName == selectedBranch?.name
                                        selectedBranch = item
                                        if (wasAutoPopulated) {
                                            taskName = item.name
                                        }
                                    },
                                )
                            }
                        }
                        if (remoteBranches.isNotEmpty()) {
                            item {
                                Text(
                                    "Remote",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp),
                                )
                            }
                            items(items.filter { it.isRemote }) { item ->
                                BranchRow(
                                    item = item,
                                    isSelected = item == selectedBranch,
                                    onClick = {
                                        val wasAutoPopulated = taskName.isBlank() || taskName == selectedBranch?.name
                                        selectedBranch = item
                                        if (wasAutoPopulated) {
                                            taskName = item.name
                                        }
                                    },
                                )
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
                Text("Agent:", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((id, label) in availableAgents) {
                        Button(onClick = { agentKind = id }) {
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
                    if (selectedBranch != null && taskName.isNotBlank()) {
                        onCreate(taskName.trim(), selectedBranch!!.name, agentKind)
                    }
                }
                else -> onDismiss()
            }
        },
    )
}

@Composable
private fun BranchRow(
    item: BranchItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) Color(0xFF0078D4) else Color.Transparent
    val textColor = if (isSelected) Color.White else Color.Unspecified
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Text(item.name, color = textColor)
    }
}
