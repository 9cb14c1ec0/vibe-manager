package com.oss.vibemanager.agents

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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class AgentSessionManager(
    private val scope: CoroutineScope,
    private val stateDir: String,
    private val clientManager: AgentClientManager,
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
        val sessionCreateMutex: Mutex = Mutex(),
        val permissionResponders: ConcurrentHashMap<String, CompletableDeferred<RequestPermissionResponse>> = ConcurrentHashMap(),
    )

    fun getConversationState(taskId: String, sessionId: String): StateFlow<ConversationState> {
        return getOrCreateSession(taskId, sessionId).conversationState.asStateFlow()
    }

    /**
     * Open the ACP session for this task without sending a prompt, so the UI can show
     * the agent's available models / modes before the user types anything.
     * No-op if the session already exists or is being created.
     */
    fun prewarmSession(taskId: String, sessionId: String, agentKind: AgentKind, workDir: String) {
        val session = getOrCreateSession(taskId, sessionId)
        if (session.acpSession != null) return
        scope.launch(Dispatchers.IO) {
            try {
                getOrCreateAcpSession(session, taskId, workDir, agentKind)
            } catch (e: Exception) {
                System.err.println("[AgentSession] Prewarm failed for $taskId: ${e.message}")
            }
        }
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
        agentKind: AgentKind,
        prompt: String,
        workDir: String,
        permissionMode: String = "acceptEdits",
        model: String = "",
        hasExistingSession: Boolean = false,
        images: List<ContentBlock.Image> = emptyList(),
    ) {
        val session = getOrCreateSession(taskId, sessionId)
        val state = session.conversationState

        val messageBlocks = mutableListOf<ContentBlock>()
        messageBlocks.add(ContentBlock.Text(prompt))
        messageBlocks.addAll(images)

        val userMessage = ConversationMessage(
            id = "user-${System.currentTimeMillis()}",
            role = MessageRole.User,
            blocks = messageBlocks,
            timestamp = System.currentTimeMillis(),
        )

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

        session.readerJob?.cancel()

        val currentBlocks = mutableListOf<ContentBlock>()
        val currentText = StringBuilder()

        session.readerJob = scope.launch(Dispatchers.IO) {
            try {
                System.err.println("[AgentSession] Getting ACP session for task $taskId (agent=$agentKind)...")
                val acpSession = getOrCreateAcpSession(session, taskId, workDir, agentKind)

                if (effectiveModel.isNotEmpty() && acpSession.modelsSupported) {
                    try {
                        System.err.println("[AgentSession] Setting model: $effectiveModel")
                        acpSession.setModel(ModelId(effectiveModel))
                    } catch (e: Exception) {
                        System.err.println("[AgentSession] Failed to set model: ${e.message}")
                    }
                }
                if (acpSession.modesSupported) {
                    try {
                        System.err.println("[AgentSession] Setting permission mode: $permissionMode")
                        acpSession.setMode(SessionModeId(permissionMode))
                    } catch (e: Exception) {
                        System.err.println("[AgentSession] Failed to set mode: ${e.message}")
                    }
                }

                System.err.println("[AgentSession] Sending prompt: ${prompt.take(100)}... (images: ${images.size})")

                val acpContentBlocks = mutableListOf<AcpContentBlock>()
                acpContentBlocks.add(AcpContentBlock.Text(prompt))
                images.forEach { image ->
                    acpContentBlocks.add(AcpContentBlock.Image(image.base64Data, image.mediaType))
                }

                acpSession.prompt(acpContentBlocks).collect { event ->
                    when (event) {
                        is Event.SessionUpdateEvent -> {
                            handleSessionUpdate(
                                event.update, session, taskId,
                                currentBlocks, currentText,
                            )
                        }
                        is Event.PromptResponseEvent -> {
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
                System.err.println("[AgentSession] Task $taskId cancelled")
                throw e
            } catch (e: Exception) {
                System.err.println("[AgentSession] Task $taskId ERROR: ${e::class.simpleName}: ${e.message}")
                e.printStackTrace(System.err)
                if (currentBlocks.isNotEmpty()) {
                    val assistantMessage = ConversationMessage(
                        id = "assistant-${System.currentTimeMillis()}",
                        role = MessageRole.Assistant,
                        blocks = currentBlocks.toList(),
                        timestamp = System.currentTimeMillis(),
                    )
                    state.update { it.copy(messages = it.messages + assistantMessage) }
                }
                state.update { it.copy(
                    status = SessionStatus.Error,
                    isStreaming = false,
                    streamingText = "",
                    streamingBlocks = emptyList(),
                    error = e.message ?: "Unknown error",
                )}
                saveConversation(taskId, state.value)
                try { session.acpSession?.close(null) } catch (_: Exception) {}
                session.acpSession = null
            }
        }
    }

    private suspend fun getOrCreateAcpSession(
        session: TaskSessionState,
        taskId: String,
        workDir: String,
        agentKind: AgentKind,
    ): ClientSession = session.sessionCreateMutex.withLock {
        session.acpSession?.let { return@withLock it }

        System.err.println("[AgentSession] Creating new ACP session for $taskId, workDir=$workDir, agent=$agentKind")
        val client = clientManager.getClient(agentKind)
        val params = SessionCreationParameters(
            cwd = workDir,
            mcpServers = emptyList(),
        )

        val acpSession = client.newSession(params) { _, _ ->
            createClientOperations(session, taskId)
        }

        val models = if (acpSession.modelsSupported) {
            acpSession.availableModels.map { AgentModelOption(it.modelId.value, it.name) }
        } else emptyList()
        val modes = if (acpSession.modesSupported) {
            acpSession.availableModes.map { AgentModeOption(it.id.value, it.name) }
        } else emptyList()
        session.conversationState.update { it.copy(
            availableModels = models,
            availableModes = modes,
        ) }

        session.acpSession = acpSession
        acpSession
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

        val deferred = CompletableDeferred<RequestPermissionResponse>()
        session.permissionResponders[requestId] = deferred

        session.conversationState.update { it.copy(pendingPermission = pending) }

        return try {
            deferred.await()
        } finally {
            session.permissionResponders.remove(requestId)
            session.conversationState.update { it.copy(pendingPermission = null) }
        }
    }

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

                val existingIdx = currentBlocks.indexOfFirst {
                    it is ContentBlock.ToolUse && it.id == toolId
                }
                if (existingIdx >= 0) {
                    val existing = currentBlocks[existingIdx] as ContentBlock.ToolUse
                    val nextInput = when {
                        rawInputJson != null -> rawInputJson
                        existing.input.isBlank() && toolContent.isNotBlank() -> toolContent
                        else -> existing.input
                    }
                    currentBlocks[existingIdx] = existing.copy(status = status, input = nextInput)
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

            else -> {}
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

    fun dispose(taskId: String) {
        shutdown(taskId)
        try {
            File(conversationsDir, "$taskId.json").delete()
        } catch (_: Exception) {}
    }

    private fun shutdown(taskId: String) {
        val session = sessions.remove(taskId) ?: return
        session.readerJob?.cancel()
        scope.launch {
            try {
                session.acpSession?.close(null)
            } catch (_: Exception) {}
        }
    }

    fun disposeAll() {
        sessions.keys.toList().forEach { shutdown(it) }
        scope.launch {
            clientManager.shutdown()
        }
    }
}
