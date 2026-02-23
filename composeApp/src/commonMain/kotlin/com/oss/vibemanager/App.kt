package com.oss.vibemanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Mica
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Icon
import io.github.composefluent.component.MenuItem
import io.github.composefluent.component.NavigationDisplayMode
import io.github.composefluent.component.NavigationView
import io.github.composefluent.component.Text
import io.github.composefluent.component.rememberNavigationState
import io.github.composefluent.icons.Icons
import io.github.composefluent.icons.regular.Add
import io.github.composefluent.icons.regular.Folder
import io.github.composefluent.icons.regular.WindowDevTools
import com.oss.vibemanager.model.TaskState
import com.oss.vibemanager.ui.dialogs.AddProjectDialog
import com.oss.vibemanager.ui.screens.ProjectDetailScreen
import com.oss.vibemanager.ui.screens.WelcomeScreen
import com.oss.vibemanager.viewmodel.AppViewModel
import com.oss.vibemanager.viewmodel.NavigationTarget
import kotlinx.coroutines.delay

@Composable
fun App(
    viewModel: AppViewModel,
    onBrowseDirectory: () -> String?,
    terminalContent: @Composable (taskId: String, isActive: Boolean) -> Unit,
    gitInfoProvider: (repoPath: String) -> Pair<String, Boolean>,
    onDeleteTask: (taskId: String) -> Unit = {},
    isTaskIdle: (taskId: String) -> Boolean = { false },
) {
    val appState by viewModel.appState.collectAsState()
    val navigation by viewModel.navigation.collectAsState()
    val error by viewModel.error.collectAsState()
    var showAddProject by remember { mutableStateOf(false) }
    val navigationState = rememberNavigationState()
    val openedTaskIds = remember { mutableStateListOf<String>() }
    val idleTaskIds = remember { mutableStateMapOf<String, Boolean>() }

    // Track opened tasks
    val activeTerminalId = (navigation as? NavigationTarget.TaskTerminal)?.taskId
    if (activeTerminalId != null && activeTerminalId !in openedTaskIds) {
        openedTaskIds.add(activeTerminalId)
    }

    // Remove deleted tasks from opened list
    val existingTaskIds = appState.tasks.map { it.id }.toSet()
    openedTaskIds.removeAll { it !in existingTaskIds }

    // Periodically poll idle state for opened tasks
    LaunchedEffect(openedTaskIds.toList()) {
        while (true) {
            delay(2000)
            for (taskId in openedTaskIds) {
                idleTaskIds[taskId] = isTaskIdle(taskId)
            }
        }
    }

    val anyDialogOpen = showAddProject || error != null

    FluentTheme {
        Mica(modifier = Modifier.fillMaxSize()) {
            NavigationView(
                displayMode = NavigationDisplayMode.Left,
                state = navigationState,
                menuItems = {
                    // Build a flat list: for each project, a project item + its task items
                    val entries = mutableListOf<SidebarEntry>()
                    for (project in appState.projects) {
                        entries.add(SidebarEntry.ProjectItem(project.id, project.name))
                        val tasks = appState.tasks.filter { it.projectId == project.id }
                        for (task in tasks) {
                            val idle = idleTaskIds[task.id] == true
                            entries.add(SidebarEntry.TaskItem(task.id, task.name, task.state, idle))
                        }
                    }
                    entries.add(SidebarEntry.AddProject)

                    items(entries.size) { index ->
                        when (val entry = entries[index]) {
                            is SidebarEntry.ProjectItem -> {
                                val isSelected = (navigation as? NavigationTarget.ProjectDetail)?.projectId == entry.projectId
                                MenuItem(
                                    selected = isSelected,
                                    onClick = { viewModel.navigateTo(NavigationTarget.ProjectDetail(entry.projectId)) },
                                    text = { Text(entry.name) },
                                    icon = { Icon(Icons.Regular.Folder, contentDescription = entry.name) },
                                )
                            }
                            is SidebarEntry.TaskItem -> {
                                val isSelected = (navigation as? NavigationTarget.TaskTerminal)?.taskId == entry.taskId
                                MenuItem(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.updateTaskState(entry.taskId, TaskState.Running)
                                        viewModel.navigateTo(NavigationTarget.TaskTerminal(entry.taskId))
                                    },
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(entry.name)
                                            if (entry.idle) {
                                                Spacer(Modifier.width(6.dp))
                                                Box(
                                                    Modifier.size(8.dp)
                                                        .background(Color(0xFFFFB900), CircleShape)
                                                )
                                            }
                                        }
                                    },
                                    icon = {
                                        Row {
                                            Spacer(Modifier.width(24.dp))
                                            Icon(Icons.Regular.WindowDevTools, contentDescription = entry.name)
                                        }
                                    },
                                )
                            }
                            is SidebarEntry.AddProject -> {
                                MenuItem(
                                    selected = false,
                                    onClick = { showAddProject = true },
                                    text = { Text("Add Project") },
                                    icon = { Icon(Icons.Regular.Add, contentDescription = "Add project") },
                                )
                            }
                        }
                    }
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (val nav = navigation) {
                        is NavigationTarget.Empty -> WelcomeScreen()
                        is NavigationTarget.ProjectDetail -> {
                            val project = appState.projects.find { it.id == nav.projectId }
                            if (project != null) {
                                val tasks = viewModel.getProjectTasks(project.id)
                                val (branch, clean) = gitInfoProvider(project.repoPath)
                                ProjectDetailScreen(
                                    project = project,
                                    tasks = tasks,
                                    gitBranch = branch,
                                    gitClean = clean,
                                    onCreateTask = { name, branchName ->
                                        viewModel.createTask(project.id, name, branchName)
                                    },
                                    onOpenTask = { task ->
                                        viewModel.updateTaskState(task.id, TaskState.Running)
                                        viewModel.navigateTo(NavigationTarget.TaskTerminal(task.id))
                                    },
                                    onDeleteTask = { task ->
                                        onDeleteTask(task.id)
                                        viewModel.deleteTask(task.id)
                                    },
                                    onRemoveProject = { viewModel.removeProject(project.id) },
                                )
                            } else {
                                WelcomeScreen()
                            }
                        }
                        is NavigationTarget.TaskTerminal -> {
                            // Content handled by persistent overlays below
                        }
                    }

                    // Render all opened terminals persistently inside content area
                    for (taskId in openedTaskIds) {
                        key(taskId) {
                            val isActive = taskId == activeTerminalId && !anyDialogOpen
                            val mod = if (isActive) Modifier.fillMaxSize() else Modifier.size(0.dp)
                            Box(modifier = mod) {
                                terminalContent(taskId, isActive)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddProject) {
        AddProjectDialog(
            onDismiss = { showAddProject = false },
            onAdd = { path ->
                viewModel.addProject(path)
                showAddProject = false
            },
            onBrowse = onBrowseDirectory,
        )
    }

    error?.let { msg ->
        ContentDialog(
            title = "Error",
            visible = true,
            content = { Text(msg) },
            primaryButtonText = "OK",
            onButtonClick = { viewModel.clearError() },
        )
    }
}

private sealed class SidebarEntry {
    data class ProjectItem(val projectId: String, val name: String) : SidebarEntry()
    data class TaskItem(val taskId: String, val name: String, val state: TaskState, val idle: Boolean) : SidebarEntry()
    data object AddProject : SidebarEntry()
}
