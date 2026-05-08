package com.oss.vibemanager

import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.rememberTabbedTerminalState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.oss.vibemanager.agents.AgentClientManager
import com.oss.vibemanager.agents.AgentRegistry
import com.oss.vibemanager.agents.AgentSessionManager
import com.oss.vibemanager.agents.parseAgentKind
import com.oss.vibemanager.git.GitOperations
import com.oss.vibemanager.git.JvmPlatformOperations
import com.oss.vibemanager.persistence.AppStateRepository
import com.oss.vibemanager.persistence.JvmFileOperations
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.platform.chooseDirectory
import com.oss.vibemanager.platform.getImagesFromClipboard
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

    AgentRegistry.configure(findBridgePath())
    val availableAgents = AgentRegistry.availableKinds()
        .map { it.name to AgentRegistry.displayName(it) }
        .ifEmpty { listOf("Claude" to "Claude Code") }
    val clientManager = AgentClientManager(sessionScope)
    val sessionManager = AgentSessionManager(
        sessionScope,
        stateDir,
        clientManager,
        onPlanApproved = { viewModel.setPermissionMode("acceptEdits") },
    )

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
                        .getConversationState(task.id, task.agentSessionId)
                        .collectAsState()

                    LaunchedEffect(task.id, task.agentKind) {
                        sessionManager.prewarmSession(
                            taskId = task.id,
                            sessionId = task.agentSessionId,
                            agentKind = parseAgentKind(task.agentKind),
                            workDir = task.worktreePath,
                        )
                    }

                    TaskChatScreen(
                        taskName = task.name,
                        conversationState = conversationState,
                        selectedModel = appState.model,
                        permissionMode = appState.permissionMode,
                        onBack = {
                            viewModel.navigateTo(NavigationTarget.ProjectDetail(task.projectId))
                        },
                        onSendMessage = { prompt, images ->
                            if (!task.agentSessionStarted) {
                                viewModel.markAgentSessionStarted(task.id)
                            }
                            sessionManager.sendMessage(
                                taskId = task.id,
                                sessionId = task.agentSessionId,
                                agentKind = parseAgentKind(task.agentKind),
                                prompt = prompt,
                                workDir = task.worktreePath,
                                permissionMode = appState.permissionMode,
                                model = appState.model,
                                hasExistingSession = task.agentSessionStarted,
                                images = images,
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
                        pasteImageHandler = { getImagesFromClipboard() },
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
            availableAgents = availableAgents,
        )
    }
}

private fun findBridgePath(): String {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val binName = if (isWindows) "acp-bridge.exe" else "acp-bridge"
    val candidates = mutableListOf<File>()

    candidates.add(File(System.getProperty("user.home"), ".vibemanager/$binName"))

    try {
        val codeUri = AgentSessionManager::class.java.protectionDomain.codeSource?.location?.toURI()
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
    return "acp-bridge"
}
