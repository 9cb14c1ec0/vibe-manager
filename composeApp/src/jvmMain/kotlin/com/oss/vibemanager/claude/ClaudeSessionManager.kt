package com.oss.vibemanager.claude

import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ContentBlock as AcpContentBlock
import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.model.SessionModeId
import com.agentclientprotocol.model.ToolCallContent
import com.agentclientprotocol.model.ToolCallStatus
import com.oss.vibemanager.model.*
import com.oss.vibemanager.ui.components.isExitPlanModeTool
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionManager(
    private val scope: CoroutineScope,
    private val stateDir: String,
    private val bridgeManager: AcpBridgeManager,
    private val onPlanApproved: (taskId: String) -> Unit = {},
) {
    private val sessions = ConcurrentHashMap<String, TaskSessionState>()
    private val conversationsDir = File(stateDir, "conversations")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        conversationsDir.mkdirs()
    }

    private class TaskSessionState(
        val conversationState: MutableStateFlow<ConversationState>,
        var acpSession: ClientSession? = null,
        var readerJob: Job? = null,
        val permissionResponders: ConcurrentHashMap<String, CompletableDeferred<RequestPermissionResponse>> = ConcurrentHashMap(),
    )

    fun getConversationState(taskId: String, sessionId: String): StateFlow<ConversationState> {
        return getOrCreateSession(taskId, sessionId).conversationState.asStateFlow()
    }

    private fun getOrCreateSession(taskId: String, sessionId: String): TaskSessionState {
        return sessions.getOrPut(taskId) {
            val persisted = loadConversation(taskId)
            val initialState = if (persisted != null) {
                ConversationState(
                    sessionId = sessionId,
                    model = persisted.model,
                    messages = persisted.messages,
                    totalCostUsd = persisted.totalCostUsd,
                )
            } else {
                ConversationState(sessionId = sessionId)
            }
            TaskSessionState(
                conversationState = MutableStateFlow(initialState)
            )
        }
    }

    private fun saveConversation(taskId: String, state: ConversationState) {
        try {
            val persisted = PersistedConversation(
                sessionId = state.sessionId,
                model = state.model,
                messages = state.messages,
                totalCostUsd = state.totalCostUsd,
            )
            val file = File(conversationsDir, "$taskId.json")
            val jsonStr = json.encodeToString(PersistedConversation.serializer(), persisted)
            file.writeText(jsonStr)
        } catch (e: Exception) {
            System.err.println("Failed to save conversation $taskId: ${e.message}")
        }
    }

    private fun loadConversation(taskId: String): PersistedConversation? {
        return try {
            val file = File(conversationsDir, "$taskId.json")
            if (file.exists()) {
                json.decodeFromString(PersistedConversation.serializer(), file.readText())
            } else null
        } catch (e: Exception) {
            System.err.println("Failed to load conversation $taskId: ${e.message}")
            null
        }
    }

    fun sendMessage(
        taskId: String,
        sessionId: String,
        prompt: String,
        workDir: String,
        permissionMode: String = "acceptEdits",
        model: String = "",
        hasExistingSession: Boolean = false,
    ) {
        val session = getOrCreateSession(taskId, sessionId)
        val state = session.conversationState

        // Add user message
        val userMessage = ConversationMessage(
            id = "user-${System.currentTimeMillis()}",
            role = MessageRole.User,
            blocks = listOf(ContentBlock.Text(prompt)),
            timestamp = System.currentTimeMillis(),
        )

        // Store the selected model on conversation state
        val effectiveModel = model.ifEmpty { state.value.model }

        state.update { it.copy(
            messages = it.messages + userMessage,
            model = effectiveModel,
            status = SessionStatus.Streaming,
            isStreaming = true,
            streamingText = "",
            streamingBlocks = emptyList(),
            error = null,
            pendingPermission = null,
        )}

        saveConversation(taskId, state.value)

        // Cancel any previous reader
        session.readerJob?.cancel()

        session.readerJob = scope.launch(Dispatchers.IO) {
            try {
                // Get or create ACP session
                System.err.println("[SessionMgr] Getting ACP session for task $taskId...")
                val isNewSession = session.acpSession == null
                val acpSession = getOrCreateAcpSession(session, taskId, workDir)

                // Apply model and permission mode after session creation or when changed
                if (effectiveModel.isNotEmpty()) {
                    try {
                        System.err.println("[SessionMgr] Setting model: $effectiveModel")
                        acpSession.setModel(ModelId(effectiveModel))
                    } catch (e: Exception) {
                        System.err.println("[SessionMgr] Failed to set model: ${e.message}")
                    }
                }
                try {
                    System.err.println("[SessionMgr] Setting permission mode: $permissionMode")
                    acpSession.setMode(SessionModeId(permissionMode))
                } catch (e: Exception) {
                    System.err.println("[SessionMgr] Failed to set mode: ${e.message}")
                }

                System.err.println("[SessionMgr] Sending prompt: ${prompt.take(100)}...")

                // Collect streaming blocks for the current turn
                val currentBlocks = mutableListOf<ContentBlock>()
                var currentText = StringBuilder()

                // Send the prompt and collect events
                acpSession.prompt(
                    listOf(AcpContentBlock.Text(prompt)),
                ).collect { event ->
                    System.err.println("[SessionMgr] Received event: ${event::class.simpleName}")
                    when (event) {
                        is Event.SessionUpdateEvent -> {
                            System.err.println("[SessionMgr]   Update type: ${event.update::class.simpleName}")
                            handleSessionUpdate(
                                event.update, session, taskId,
                                currentBlocks, currentText,
                            )
                        }
                        is Event.PromptResponseEvent -> {
                            // Turn complete — finalize assistant message
                            if (currentBlocks.isNotEmpty()) {
                                val assistantMessage = ConversationMessage(
                                    id = "assistant-${System.currentTimeMillis()}",
                                    role = MessageRole.Assistant,
                                    blocks = currentBlocks.toList(),
                                    timestamp = System.currentTimeMillis(),
                                )
                                state.update { it.copy(
                                    messages = it.messages + assistantMessage,
                                    status = SessionStatus.Idle,
                                    isStreaming = false,
                                    streamingText = "",
                                    streamingBlocks = emptyList(),
                                )}
                            } else {
                                state.update { it.copy(
                                    status = SessionStatus.Idle,
                                    isStreaming = false,
                                    streamingText = "",
                                    streamingBlocks = emptyList(),
                                )}
                            }
                            saveConversation(taskId, state.value)
                        }
                    }
                }

                // If stream ended without PromptResponseEvent, clean up
                if (state.value.isStreaming) {
                    if (currentBlocks.isNotEmpty()) {
                        val assistantMessage = ConversationMessage(
                            id = "assistant-${System.currentTimeMillis()}",
                            role = MessageRole.Assistant,
                            blocks = currentBlocks.toList(),
                            timestamp = System.currentTimeMillis(),
                        )
                        state.update { it.copy(
                            messages = it.messages + assistantMessage,
                            status = SessionStatus.Idle,
                            isStreaming = false,
                            streamingText = "",
                            streamingBlocks = emptyList(),
                        )}
                    } else {
                        state.update { it.copy(
                            status = SessionStatus.Idle,
                            isStreaming = false,
                        )}
                    }
                    saveConversation(taskId, state.value)
                }

            } catch (e: CancellationException) {
                System.err.println("[SessionMgr] Task $taskId cancelled")
                throw e
            } catch (e: Exception) {
                System.err.println("[SessionMgr] Task $taskId ERROR: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace(System.err)
                state.update { it.copy(
                    status = SessionStatus.Error,
                    isStreaming = false,
                    error = e.message ?: "Unknown error",
                )}
            }
        }
    }

    private suspend fun getOrCreateAcpSession(
        session: TaskSessionState,
        taskId: String,
        workDir: String,
    ): ClientSession {
        session.acpSession?.let {
            System.err.println("[SessionMgr] Reusing existing ACP session for task $taskId")
            return it
        }

        System.err.println("[SessionMgr] Creating new ACP session for task $taskId, workDir=$workDir")
        val client = bridgeManager.getClient()
        System.err.println("[SessionMgr] Got ACP client, creating session...")
        val params = SessionCreationParameters(
            cwd = workDir,
            mcpServers = emptyList(),
        )

        val acpSession = client.newSession(params) { sessionId, sessionResponse ->
            System.err.println("[SessionMgr] Session created callback: sessionId=$sessionId")
            createClientOperations(session, taskId)
        }
        System.err.println("[SessionMgr] ACP session created successfully")

        session.acpSession = acpSession
        return acpSession
    }

    private fun createClientOperations(
        session: TaskSessionState,
        taskId: String,
    ): ClientSessionOperations {
        return VibeManagerClientOperations(
            onPermissionRequest = { toolCall, permissions ->
                handlePermissionRequest(session, taskId, toolCall, permissions)
            }
        )
    }

    private suspend fun handlePermissionRequest(
        session: TaskSessionState,
        taskId: String,
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ): RequestPermissionResponse {
        val requestId = "perm-${System.currentTimeMillis()}"

        // Map ACP permission options to our model
        val choices = permissions.map { opt ->
            PermissionChoice(
                id = opt.optionId.value,
                name = opt.name,
                kind = when (opt.kind) {
                    PermissionOptionKind.ALLOW_ONCE -> "allow_once"
                    PermissionOptionKind.ALLOW_ALWAYS -> "allow_always"
                    PermissionOptionKind.REJECT_ONCE -> "reject_once"
                    PermissionOptionKind.REJECT_ALWAYS -> "reject_always"
                },
            )
        }

        // Build input summary from tool call content
        val inputSummary = toolCall.content
            ?.filterIsInstance<ToolCallContent.Content>()
            ?.mapNotNull { (it.content as? AcpContentBlock.Text)?.text }
            ?.joinToString("\n")
            ?: ""

        val rawInputJson = toolCall.rawInput?.let {
            Json.encodeToString(JsonElement.serializer(), it)
        } ?: ""

        val pending = PendingPermission(
            requestId = requestId,
            toolName = toolCall.title ?: toolCall.toolCallId.value,
            toolTitle = toolCall.title ?: "",
            inputSummary = inputSummary,
            options = choices,
            rawInputJson = rawInputJson,
        )

        // Create a deferred for the response
        val deferred = CompletableDeferred<RequestPermissionResponse>()
        session.permissionResponders[requestId] = deferred

        // Update UI state with pending permission
        session.conversationState.update { it.copy(pendingPermission = pending) }

        // Wait for user response from UI
        return try {
            deferred.await()
        } finally {
            session.permissionResponders.remove(requestId)
            session.conversationState.update { it.copy(pendingPermission = null) }
        }
    }

    /**
     * Called from the UI when the user approves or denies a permission request.
     */
    fun respondToPermission(taskId: String, requestId: String, optionId: String) {
        val session = sessions[taskId] ?: return
        val deferred = session.permissionResponders[requestId] ?: return

        val pending = session.conversationState.value.pendingPermission
        if (pending != null && pending.requestId == requestId) {
            val chosen = pending.options.firstOrNull { it.id == optionId }
            val isAllow = chosen?.kind == "allow_once" || chosen?.kind == "allow_always"
            if (isAllow && isExitPlanModeTool(pending.toolName, pending.rawInputJson)) {
                onPlanApproved(taskId)
            }
        }

        deferred.complete(
            RequestPermissionResponse(
                outcome = RequestPermissionOutcome.Selected(PermissionOptionId(optionId)),
                _meta = null,
            )
        )
    }

    private fun handleSessionUpdate(
        update: SessionUpdate,
        session: TaskSessionState,
        taskId: String,
        currentBlocks: MutableList<ContentBlock>,
        currentText: StringBuilder,
    ) {
        val state = session.conversationState
        when (update) {
            is SessionUpdate.AgentMessageChunk -> {
                val block = update.content
                if (block is AcpContentBlock.Text) {
                    currentText.append(block.text)
                    // Merge consecutive text blocks
                    val lastBlock = currentBlocks.lastOrNull()
                    if (lastBlock is ContentBlock.Text) {
                        currentBlocks[currentBlocks.lastIndex] =
                            ContentBlock.Text(lastBlock.text + block.text)
                    } else {
                        currentBlocks.add(ContentBlock.Text(block.text))
                    }
                }
                state.update { it.copy(
                    streamingText = currentText.toString(),
                    streamingBlocks = currentBlocks.toList(),
                )}
            }

            is SessionUpdate.AgentThoughtChunk -> {
                val block = update.content
                if (block is AcpContentBlock.Text) {
                    val lastBlock = currentBlocks.lastOrNull()
                    if (lastBlock is ContentBlock.Thinking) {
                        currentBlocks[currentBlocks.lastIndex] =
                            ContentBlock.Thinking(lastBlock.text + block.text)
                    } else {
                        currentBlocks.add(ContentBlock.Thinking(block.text))
                    }
                }
                state.update { it.copy(streamingBlocks = currentBlocks.toList()) }
            }

            is SessionUpdate.ToolCallUpdate -> {
                val toolId = update.toolCallId.value
                val status = when (update.status) {
                    ToolCallStatus.COMPLETED -> ToolStatus.Completed
                    ToolCallStatus.FAILED -> ToolStatus.Error
                    else -> ToolStatus.Running
                }
                val toolContent = update.content
                    ?.filterIsInstance<ToolCallContent.Content>()
                    ?.mapNotNull { (it.content as? AcpContentBlock.Text)?.text }
                    ?.joinToString("\n")
                    ?: ""
                val rawInputJson: String? = update.rawInput?.let {
                    Json.encodeToString(JsonElement.serializer(), it)
                }
                val toolInput = rawInputJson ?: toolContent

                // Check if we already have this tool call
                val existingIdx = currentBlocks.indexOfFirst {
                    it is ContentBlock.ToolUse && it.id == toolId
                }
                if (existingIdx >= 0) {
                    // Update existing
                    val existing = currentBlocks[existingIdx] as ContentBlock.ToolUse
                    val nextInput = when {
                        rawInputJson != null -> rawInputJson
                        existing.input.isBlank() && toolContent.isNotBlank() -> toolContent
                        else -> existing.input
                    }
                    currentBlocks[existingIdx] = existing.copy(status = status, input = nextInput)
                    // Add/update result if completed
                    if (status == ToolStatus.Completed || status == ToolStatus.Error) {
                        val resultIdx = currentBlocks.indexOfFirst {
                            it is ContentBlock.ToolResult && it.toolUseId == toolId
                        }
                        val result = ContentBlock.ToolResult(
                            toolUseId = toolId,
                            content = toolContent,
                            isError = status == ToolStatus.Error,
                        )
                        if (resultIdx >= 0) {
                            currentBlocks[resultIdx] = result
                        } else {
                            currentBlocks.add(result)
                        }
                    }
                } else {
                    // New tool call
                    currentBlocks.add(ContentBlock.ToolUse(
                        id = toolId,
                        name = update.title ?: "Tool",
                        input = toolInput,
                        status = status,
                    ))
                }
                state.update { it.copy(streamingBlocks = currentBlocks.toList()) }
            }

            is SessionUpdate.UsageUpdate -> {
                val cost = update.cost?.amount ?: 0.0
                if (cost > 0) {
                    state.update { it.copy(totalCostUsd = it.totalCostUsd + cost) }
                }
            }

            else -> {
                // Ignore other update types for now
            }
        }
    }

    fun stopGeneration(taskId: String) {
        val session = sessions[taskId] ?: return
        session.readerJob?.cancel()
        scope.launch {
            try {
                session.acpSession?.cancel()
            } catch (_: Exception) {}
        }
        session.conversationState.update { it.copy(
            status = SessionStatus.Idle,
            isStreaming = false,
            pendingPermission = null,
        )}
    }

    fun isIdle(taskId: String): Boolean {
        val session = sessions[taskId] ?: return false
        return session.conversationState.value.status == SessionStatus.Idle
    }

    /**
     * Dispose a task's session AND delete its persisted conversation.
     * Use this when the user explicitly deletes a task.
     */
    fun dispose(taskId: String) {
        shutdown(taskId)
        try {
            File(conversationsDir, "$taskId.json").delete()
        } catch (_: Exception) {}
    }

    /**
     * Shut down a task's session but keep the persisted conversation file.
     */
    private fun shutdown(taskId: String) {
        val session = sessions.remove(taskId) ?: return
        session.readerJob?.cancel()
        scope.launch {
            try {
                session.acpSession?.close(null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Shut down all sessions on app exit without deleting conversation files.
     */
    fun disposeAll() {
        sessions.keys.toList().forEach { shutdown(it) }
        scope.launch {
            bridgeManager.shutdown()
        }
    }
}
