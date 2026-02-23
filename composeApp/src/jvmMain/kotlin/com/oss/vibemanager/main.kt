package com.oss.vibemanager

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.oss.vibemanager.git.GitOperations
import com.oss.vibemanager.git.JvmPlatformOperations
import com.oss.vibemanager.persistence.AppStateRepository
import com.oss.vibemanager.persistence.JvmFileOperations
import com.oss.vibemanager.platform.chooseDirectory
import com.oss.vibemanager.terminal.TerminalSessionManager
import com.oss.vibemanager.ui.screens.TaskTerminalScreen
import com.oss.vibemanager.viewmodel.AppViewModel
import com.oss.vibemanager.viewmodel.NavigationTarget

fun main() = application {
    val stateDir = System.getProperty("user.home") + "/.vibemanager"
    val fileOps = JvmFileOperations()
    val repository = AppStateRepository(fileOps, stateDir)
    val platformOps = JvmPlatformOperations()
    val viewModel = AppViewModel(repository, platformOps)
    val sessionManager = TerminalSessionManager()

    Window(
        onCloseRequest = {
            sessionManager.disposeAll()
            exitApplication()
        },
        title = "Vibe Manager",
        state = rememberWindowState(width = 1280.dp, height = 800.dp),
    ) {
        App(
            viewModel = viewModel,
            onBrowseDirectory = { chooseDirectory() },
            terminalContent = { taskId, isActive ->
                val appState by viewModel.appState.collectAsState()
                val task = appState.tasks.find { it.id == taskId }
                if (task != null) {
                    TaskTerminalScreen(
                        task = task,
                        sessionManager = sessionManager,
                        onBack = {
                            viewModel.navigateTo(NavigationTarget.ProjectDetail(task.projectId))
                        },
                        onProcessExit = {
                            // Don't auto-complete — user can reopen to resume
                        },
                        onClaudeSessionStarted = {
                            viewModel.markClaudeSessionStarted(task.id)
                        },
                        isActive = isActive,
                    )
                }
            },
            gitInfoProvider = { repoPath ->
                val info = GitOperations.getGitInfo(repoPath).getOrNull()
                if (info != null) Pair(info.currentBranch, info.isClean)
                else Pair("unknown", true)
            },
            onDeleteTask = { taskId ->
                sessionManager.dispose(taskId)
            },
            isTaskIdle = { taskId ->
                sessionManager.isIdle(taskId)
            },
        )
    }
}
