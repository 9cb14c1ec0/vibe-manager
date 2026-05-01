package com.oss.vibemanager.claude

import com.oss.vibemanager.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap

class ClaudeSessionManager(private val scope: CoroutineScope) {
    private val processManager = ClaudeProcessManager()
    private val sessions = ConcurrentHashMap<String, ClaudeSessionState>()

    private class ClaudeSessionState(
        val conversationState: MutableStateFlow<ConversationState>,
        var readerJob: Job? = null,
    )

    fun getConversationState(taskId: String, sessionId: String): StateFlow<ConversationState> {
        return getOrCreateSession(taskId, sessionId).conversationState.asStateFlow()
    }

    private fun getOrCreateSession(taskId: String, sessionId: String): ClaudeSessionState {
        return sessions.getOrPut(taskId) {
            ClaudeSessionState(
                conversationState = MutableStateFlow(ConversationState(sessionId = sessionId))
            )
        }
    }

    fun sendMessage(
        taskId: String,
        sessionId: String,
        prompt: String,
        workDir: String,
        permissionMode: String = "acceptEdits",
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

        val isResume = state.value.messages.isNotEmpty()

        state.update { it.copy(
            messages = it.messages + userMessage,
            status = SessionStatus.Streaming,
            isStreaming = true,
            streamingText = "",
            streamingBlocks = emptyList(),
            error = null,
        )}

        // Cancel any previous reader
        session.readerJob?.cancel()

        // Spawn Claude process and start reading
        session.readerJob = scope.launch(Dispatchers.IO) {
            try {
                val process = processManager.startTurn(
                    sessionId = sessionId,
                    prompt = prompt,
                    workDir = workDir,
                    isResume = isResume,
                    permissionMode = permissionMode,
                )

                val currentBlocks = mutableListOf<ContentBlock>()
                var currentText = StringBuilder()
                var model = state.value.model

                ClaudeStreamParser.parseStream(process.inputStream).collect { event ->
                    when (event) {
                        is SystemInitEvent -> {
                            model = event.model
                            state.update { it.copy(model = model) }
                        }

                        is AssistantMessageEvent -> {
                            // Parse content blocks from the assistant message
                            val blocks = mutableListOf<ContentBlock>()
                            var latestText = ""
                            for (block in event.message.content) {
                                when (block) {
                                    is TextContentBlock -> {
                                        latestText = block.text
                                        blocks.add(ContentBlock.Text(block.text))
                                    }
                                    is ThinkingContentBlock -> {
                                        blocks.add(ContentBlock.Thinking(block.thinking))
                                    }
                                    is ToolUseContentBlock -> {
                                        blocks.add(ContentBlock.ToolUse(
                                            id = block.id,
                                            name = block.name,
                                            input = block.input.toString(),
                                            status = ToolStatus.Running,
                                        ))
                                    }
                                    is ToolResultContentBlock -> {
                                        blocks.add(ContentBlock.ToolResult(
                                            toolUseId = block.toolUseId,
                                            content = block.content,
                                            isError = block.isError,
                                        ))
                                    }
                                }
                            }

                            // Update streaming state - show latest blocks as they come in
                            currentBlocks.clear()
                            currentBlocks.addAll(blocks)
                            currentText = StringBuilder(latestText)

                            state.update { it.copy(
                                streamingText = latestText,
                                streamingBlocks = blocks.toList(),
                            )}
                        }

                        is UserToolResultEvent -> {
                            // Tool results from Claude executing tools
                            for (block in event.message.content) {
                                if (block is ToolResultContentBlock) {
                                    // Update the corresponding ToolUse block status
                                    val updatedBlocks = currentBlocks.map { cb ->
                                        if (cb is ContentBlock.ToolUse && cb.id == block.toolUseId) {
                                            cb.copy(status = if (block.isError) ToolStatus.Error else ToolStatus.Completed)
                                        } else cb
                                    }.toMutableList()
                                    // Add the result block
                                    updatedBlocks.add(ContentBlock.ToolResult(
                                        toolUseId = block.toolUseId,
                                        content = block.content,
                                        isError = block.isError,
                                    ))
                                    currentBlocks.clear()
                                    currentBlocks.addAll(updatedBlocks)

                                    state.update { it.copy(
                                        streamingBlocks = updatedBlocks.toList(),
                                    )}
                                }
                            }
                        }

                        is ResultEvent -> {
                            // Turn complete - finalize the assistant message
                            val assistantMessage = ConversationMessage(
                                id = "assistant-${System.currentTimeMillis()}",
                                role = MessageRole.Assistant,
                                blocks = currentBlocks.toList(),
                                timestamp = System.currentTimeMillis(),
                            )

                            state.update { it.copy(
                                messages = it.messages + assistantMessage,
                                status = if (event.isError) SessionStatus.Error else SessionStatus.Idle,
                                isStreaming = false,
                                streamingText = "",
                                streamingBlocks = emptyList(),
                                totalCostUsd = it.totalCostUsd + event.totalCostUsd,
                                error = if (event.isError) event.result else null,
                            )}
                        }

                        is RateLimitEvent -> {
                            // Could show rate limit info in UI - for now just log
                        }
                    }
                }

                // If we get here without a ResultEvent, the process ended unexpectedly
                if (state.value.isStreaming) {
                    // Finalize whatever we have
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
                }

            } catch (e: CancellationException) {
                throw e // don't catch cancellation
            } catch (e: Exception) {
                state.update { it.copy(
                    status = SessionStatus.Error,
                    isStreaming = false,
                    error = e.message ?: "Unknown error",
                )}
            }
        }
    }

    fun stopGeneration(taskId: String) {
        val session = sessions[taskId] ?: return
        session.readerJob?.cancel()
        val sessionId = session.conversationState.value.sessionId
        processManager.killProcess(sessionId)
        session.conversationState.update { it.copy(
            status = SessionStatus.Idle,
            isStreaming = false,
        )}
    }

    fun isIdle(taskId: String): Boolean {
        val session = sessions[taskId] ?: return false
        return session.conversationState.value.status == SessionStatus.Idle
    }

    fun dispose(taskId: String) {
        val session = sessions.remove(taskId) ?: return
        session.readerJob?.cancel()
        processManager.killProcess(session.conversationState.value.sessionId)
    }

    fun disposeAll() {
        sessions.keys.toList().forEach { dispose(it) }
        processManager.disposeAll()
    }
}
