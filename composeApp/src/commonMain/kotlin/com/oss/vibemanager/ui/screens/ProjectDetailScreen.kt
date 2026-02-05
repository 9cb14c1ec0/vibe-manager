package com.oss.vibemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.Project
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.ui.components.GitInfoBar
import com.oss.vibemanager.ui.components.TaskCard
import com.oss.vibemanager.ui.dialogs.ConfirmDeleteDialog
import com.oss.vibemanager.ui.dialogs.CreateTaskDialog

@Composable
fun ProjectDetailScreen(
    project: Project,
    tasks: List<Task>,
    gitBranch: String,
    gitClean: Boolean,
    onCreateTask: (name: String, branch: String) -> Unit,
    onOpenTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onRemoveProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateTask by remember { mutableStateOf(false) }
    var taskToDelete by remember { mutableStateOf<Task?>(null) }
    var showDeleteProject by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(project.name, fontSize = 24.sp)
            Button(onClick = { showDeleteProject = true }) { Text("Remove Project") }
        }

        GitInfoBar(branch = gitBranch, isClean = gitClean, modifier = Modifier.padding(vertical = 8.dp))

        Text(project.repoPath, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tasks", fontSize = 18.sp)
            Button(onClick = { showCreateTask = true }) { Text("New Task") }
        }

        if (tasks.isEmpty()) {
            Text(
                "No tasks yet. Create a task to start working.",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
