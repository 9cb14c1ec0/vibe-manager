package com.oss.vibemanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class SessionStatus {
    Idle,
    Streaming,
    Error,
    Disconnected,
}

@Serializable
enum class MessageRole {
    User,
    Assistant,
}

@Serializable
enum class ToolStatus {
    Running,
    Completed,
    Error,
}

@Serializable
sealed class ContentBlock {
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("thinking")
    data class Thinking(val text: String, val collapsed: Boolean = true) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: String,
        val status: ToolStatus = ToolStatus.Running,
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false,
    ) : ContentBlock()
}

@Serializable
data class ConversationMessage(
    val id: String,
    val role: MessageRole,
    val blocks: List<ContentBlock>,
    val timestamp: Long,
)

@Serializable
data class PersistedConversation(
    val sessionId: String,
    val model: String = "",
    val messages: List<ConversationMessage> = emptyList(),
    val totalCostUsd: Double = 0.0,
)

/**
 * A pending permission request from Claude, awaiting user approval.
 */
data class PendingPermission(
    val requestId: String,
    val toolName: String,
    val toolTitle: String,
    val inputSummary: String,
    val options: List<PermissionChoice>,
    val rawInputJson: String = "",
)

data class PermissionChoice(
    val id: String,
    val name: String,
    val kind: String, // allow_once, allow_always, reject_once, reject_always
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
    val pendingPermission: PendingPermission? = null,
)
