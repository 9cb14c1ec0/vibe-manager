package com.oss.vibemanager

import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.rememberTabbedTerminalState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.oss.vibemanager.claude.AcpBridgeManager
import com.oss.vibemanager.claude.ClaudeSessionManager
import com.oss.vibemanager.git.GitOperations
import com.oss.vibemanager.git.JvmPlatformOperations
import com.oss.vibemanager.persistence.AppStateRepository
import com.oss.vibemanager.persistence.JvmFileOperations
import com.oss.vibemanager.platform.chooseDirectory
import com.oss.vibemanager.ui.screens.TaskChatScreen
import com.oss.vibemanager.viewmodel.AppViewModel
import com.oss.vibemanager.viewmodel.NavigationTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

fun main() = application {
    val stateDir = System.getProperty("user.home") + "/.vibemanager"
    val fileOps = JvmFileOperations()
    val repository = AppStateRepository(fileOps, stateDir)
    val platformOps = JvmPlatformOperations()
    val viewModel = AppViewModel(repository, platformOps)
    val sessionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Find the ACP bridge binary
    val bridgePath = findBridgePath()
    val bridgeManager = AcpBridgeManager(sessionScope, bridgePath)
    val sessionManager = ClaudeSessionManager(sessionScope, stateDir, bridgeManager)

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
            chatContent = { taskId, isActive ->
                val appState by viewModel.appState.collectAsState()
                val task = appState.tasks.find { it.id == taskId }
                if (task != null) {
                    val conversationState by sessionManager
                        .getConversationState(task.id, task.claudeSessionId)
                        .collectAsState()

                    TaskChatScreen(
                        taskName = task.name,
                        conversationState = conversationState,
                        selectedModel = appState.model,
                        permissionMode = appState.permissionMode,
                        onBack = {
                            viewModel.navigateTo(NavigationTarget.ProjectDetail(task.projectId))
                        },
                        onSendMessage = { prompt ->
                            if (!task.claudeSessionStarted) {
                                viewModel.markClaudeSessionStarted(task.id)
                            }
                            sessionManager.sendMessage(
                                taskId = task.id,
                                sessionId = task.claudeSessionId,
                                prompt = prompt,
                                workDir = task.worktreePath,
                                permissionMode = appState.permissionMode,
                                model = appState.model,
                                hasExistingSession = task.claudeSessionStarted,
                            )
                        },
                        onStopGeneration = {
                            sessionManager.stopGeneration(task.id)
                        },
                        onModelSelected = { model ->
                            viewModel.setModel(model)
                        },
                        onModeSelected = { mode ->
                            viewModel.setPermissionMode(mode)
                        },
                        onPermissionRespond = { requestId, optionId ->
                            sessionManager.respondToPermission(task.id, requestId, optionId)
                        },
                        onGetChangedFiles = {
                            GitOperations.getChangedFiles(task.worktreePath)
                        },
                        onGetFileDiff = { file ->
                            GitOperations.getFileDiff(task.worktreePath, file.path, file.status)
                        },
                        diffPanelWidth = appState.diffPanelWidth,
                        onDiffPanelWidthChanged = viewModel::setDiffPanelWidth,
                        terminalPanelHeight = appState.terminalPanelHeight,
                        onTerminalPanelHeightChanged = viewModel::setTerminalPanelHeight,
                        terminalContent = { onExit ->
                            val terminalState = rememberTabbedTerminalState()
                            TabbedTerminal(
                                state = terminalState,
                                onExit = onExit,
                                workingDirectory = task.worktreePath,
                                modifier = Modifier.fillMaxSize(),
                            )
                        },
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

private fun findBridgePath(): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val binName = if (isWindows) "acp-bridge.exe" else "acp-bridge"
    val candidates = mutableListOf<File>()

    // 1. Check ~/.vibemanager/acp-bridge (primary location)
    candidates.add(File(System.getProperty("user.home"), ".vibemanager/$binName"))

    // 2. Resolve relative to the running code's location (JAR or classes dir).
    //    Walks up parents looking for either a sibling binary (distribution)
    //    or acp-bridge/dist/<binName> (development mode), so cwd doesn't matter.
    try {
        val codeUri = ClaudeSessionManager::class.java.protectionDomain.codeSource?.location?.toURI()
        if (codeUri != null) {
            var dir: File? = File(codeUri).let { if (it.isDirectory) it else it.parentFile }
            while (dir != null) {
                candidates.add(File(dir, binName))
                candidates.add(File(dir, "acp-bridge/dist/$binName"))
                dir = dir.parentFile
            }
        }
    } catch (_: Exception) {}

    for (candidate in candidates) {
        if (candidate.isFile && candidate.canExecute()) {
            System.err.println("[VibeManager] Found ACP bridge at: ${candidate.absolutePath}")
            return candidate.absolutePath
        }
    }

    System.err.println("[VibeManager] ACP bridge not found! Searched: ${candidates.map { it.absolutePath }}")
    // Last resort: assume it's in PATH
    return "acp-bridge"
}
