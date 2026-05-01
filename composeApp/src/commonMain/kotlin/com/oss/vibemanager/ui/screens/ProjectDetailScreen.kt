package com.oss.vibemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Icon
import io.github.composefluent.component.Text
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Folder
import com.oss.vibemanager.model.Project
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.ui.components.DiffPanel
import com.oss.vibemanager.ui.components.GitInfoBar
import com.oss.vibemanager.ui.components.TaskCard
import com.oss.vibemanager.ui.dialogs.ConfirmDeleteDialog
import com.oss.vibemanager.ui.dialogs.CreateTaskDialog
import com.oss.vibemanager.ui.dialogs.FromBranchDialog

@Composable
fun ProjectDetailScreen(
    project: Project,
    tasks: List<Task>,
    gitBranch: String,
    gitClean: Boolean,
    onCreateTask: (name: String, branch: String) -> Unit,
    onListBranches: () -> Result<List<String>>,
    onListRemoteBranches: () -> Result<List<String>>,
    onCreateTaskFromBranch: (name: String, branch: String) -> Unit,
    onOpenTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRemoveProject: () -> Unit,
    onFetchDiff: () -> String = { "" },
    modifier: Modifier = Modifier,
) {
    var showCreateTask by remember { mutableStateOf(false) }
    var showFromBranch by remember { mutableStateOf(false) }
    var availableBranches by remember { mutableStateOf<List<String>>(emptyList()) }
    var availableRemoteBranches by remember { mutableStateOf<List<String>>(emptyList()) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var showDeleteProject by remember { mutableStateOf(false) }
    var showDiff by remember { mutableStateOf(false) }
    var diffText by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    project.name,
                    fontSize = 28.sp,
                    color = FluentTheme.colors.text.text.primary,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Regular.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = FluentTheme.colors.text.text.tertiary,
                    )
                    Text(
                        project.repoPath,
                        fontSize = 12.sp,
                        color = FluentTheme.colors.text.text.tertiary,
                    )
                }
            }
            Button(onClick = { showDeleteProject = true }) { Text("Remove") }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GitInfoBar(branch = gitBranch, isClean = gitClean)
            if (!gitClean) {
                Button(onClick = {
                    if (!showDiff) {
                        diffText = onFetchDiff()
                    }
                    showDiff = !showDiff
                }) {
                    Text(if (showDiff) "Hide Changes" else "Show Changes")
                }
            }
        }

        if (showDiff) {
            Spacer(Modifier.height(12.dp))
            DiffPanel(
                diffText = diffText,
                onClose = { showDiff = false },
                onRefresh = { diffText = onFetchDiff() },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Divider
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FluentTheme.colors.stroke.divider.default)
        )

        Spacer(Modifier.height(16.dp))

        // Tasks section header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tasks",
                fontSize = 16.sp,
                color = FluentTheme.colors.text.text.secondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    availableBranches = onListBranches().getOrDefault(emptyList())
                    availableRemoteBranches = onListRemoteBranches().getOrDefault(emptyList())
                    showFromBranch = true
                }) { Text("From Branch") }
                Button(onClick = { showCreateTask = true }) { Text("New Task") }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (tasks.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "No tasks yet",
                    fontSize = 16.sp,
                    color = FluentTheme.colors.text.text.secondary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Create a task to start a new worktree",
                    fontSize = 13.sp,
                    color = FluentTheme.colors.text.text.tertiary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onOpen = { onOpenTask(task) },
                        onDelete = { taskToDelete = task },
                    )
                }
            }
        }
    }

    if (showCreateTask) {
        CreateTaskDialog(
            onDismiss = { showCreateTask = false },
            onCreate = { name, branch ->
                onCreateTask(name, branch)
                showCreateTask = false
            },
        )
    }

    if (showFromBranch) {
        FromBranchDialog(
            localBranches = availableBranches,
            remoteBranches = availableRemoteBranches,
            onDismiss = { showFromBranch = false },
            onCreate = { name, branch ->
                onCreateTaskFromBranch(name, branch)
                showFromBranch = false
            },
        )
    }

    taskToDelete?.let { task ->
        ConfirmDeleteDialog(
            title = "Delete Task",
            message = "Delete task \"${task.name}\"? This will remove the worktree.",
            onDismiss = { taskToDelete = null },
            onConfirm = {
                onDeleteTask(task)
                taskToDelete = null
            },
        )
    }

    if (showDeleteProject) {
        ConfirmDeleteDialog(
            title = "Remove Project",
            message = "Remove \"${project.name}\"? All tasks and worktrees will be deleted.",
            onDismiss = { showDeleteProject = false },
            onConfirm = {
                onRemoveProject()
                showDeleteProject = false
            },
        )
    }
}
