package com.oss.vibemanager

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                    )
                }
            },
            gitInfoProvider = { repoPath ->
                val info = GitOperations.getGitInfo(repoPath).getOrNull()
                if (info != null) Pair(info.currentBranch, info.isClean)
                else Pair("unknown", true)
            },
            diffProvider = { repoPath ->
                GitOperations.getUncommittedDiff(repoPath).getOrDefault("")
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
    val candidates = mutableListOf<File>()

    // 1. Check ~/.vibemanager/acp-bridge.exe (primary location)
    candidates.add(File(System.getProperty("user.home"), ".vibemanager/acp-bridge.exe"))

    // 2. Check next to the running JAR (for distribution)
    try {
        val jarUri = ClaudeSessionManager::class.java.protectionDomain.codeSource?.location?.toURI()
        if (jarUri != null) {
            candidates.add(File(File(jarUri).parentFile, "acp-bridge.exe"))
        }
    } catch (_: Exception) {}

    // 3. Check relative to working directory (development mode)
    candidates.add(File("acp-bridge/dist/acp-bridge.exe"))

    for (candidate in candidates) {
        if (candidate.exists()) {
            System.err.println("[VibeManager] Found ACP bridge at: ${candidate.absolutePath}")
            return candidate.absolutePath
        }
    }

    System.err.println("[VibeManager] ACP bridge not found! Searched: ${candidates.map { it.absolutePath }}")
    // Last resort: assume it's in PATH
    return "acp-bridge"
}
