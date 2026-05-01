package com.oss.vibemanager.model

enum class SessionStatus {
    Idle,
    Streaming,
    Error,
    Disconnected,
}

enum class MessageRole {
    User,
    Assistant,
}

enum class ToolStatus {
    Running,
    Completed,
    Error,
}

sealed class ContentBlock {
    data class Text(val text: String) : ContentBlock()
    data class Thinking(val text: String, val collapsed: Boolean = true) : ContentBlock()
    data class ToolUse(
        val id: String,
        val name: String,
        val input: String,
        val status: ToolStatus = ToolStatus.Running,
    ) : ContentBlock()
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false,
    ) : ContentBlock()
}

data class ConversationMessage(
    val id: String,
    val role: MessageRole,
    val blocks: List<ContentBlock>,
    val timestamp: Long,
)

data class ConversationState(
    val sessionId: String,
    val model: String = "",
    val messages: List<ConversationMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val streamingText: String = "",
    val streamingBlocks: List<ContentBlock> = emptyList(),
    val totalCostUsd: Double = 0.0,
    val error: String? = null,
    val status: SessionStatus = SessionStatus.Idle,
)
