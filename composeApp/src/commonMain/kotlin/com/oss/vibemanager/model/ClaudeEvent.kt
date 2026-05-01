package com.oss.vibemanager.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

// --- Content blocks for assistant/user messages ---

sealed interface MessageContentBlock

data class TextContentBlock(
    val text: String,
) : MessageContentBlock

data class ThinkingContentBlock(
    val thinking: String,
) : MessageContentBlock

data class ToolUseContentBlock(
    val id: String,
    val name: String,
    val input: JsonObject,
) : MessageContentBlock

data class ToolResultContentBlock(
    val toolUseId: String,
    val content: String,
    val isError: Boolean = false,
) : MessageContentBlock

// --- Message wrapper ---

data class ClaudeMessage(
    val role: String,
    val content: List<MessageContentBlock>,
    val stopReason: String? = null,
)

// --- Usage info ---

@Serializable
data class UsageInfo(
    @SerialName("input_tokens") val inputTokens: Int = 0,
    @SerialName("output_tokens") val outputTokens: Int = 0,
    @SerialName("cache_creation_input_tokens") val cacheCreationInputTokens: Int = 0,
    @SerialName("cache_read_input_tokens") val cacheReadInputTokens: Int = 0,
)

// --- Rate limit info ---

@Serializable
data class RateLimitInfo(
    val status: String = "",
    @SerialName("resetsAt") val resetsAt: Long? = null,
)

// --- Permission request ---

data class PermissionRequest(
    val requestId: String,
    val toolName: String,
    val input: JsonObject,
)

// --- Events ---

sealed interface ClaudeEvent {
    val sessionId: String?
}

data class SystemInitEvent(
    override val sessionId: String?,
    val model: String,
    val tools: List<String>,
    val cwd: String,
    val permissionMode: String,
) : ClaudeEvent

data class RateLimitEvent(
    override val sessionId: String?,
    val rateLimitInfo: RateLimitInfo,
) : ClaudeEvent

data class AssistantMessageEvent(
    override val sessionId: String?,
    val message: ClaudeMessage,
    val parentToolUseId: String? = null,
) : ClaudeEvent

data class UserToolResultEvent(
    override val sessionId: String?,
    val message: ClaudeMessage,
) : ClaudeEvent

data class ResultEvent(
    override val sessionId: String?,
    val subtype: String,
    val isError: Boolean,
    val result: String,
    val totalCostUsd: Double,
    val durationMs: Long,
    val numTurns: Int,
    val stopReason: String? = null,
    val usage: UsageInfo? = null,
) : ClaudeEvent

/** Permission request from the CLI (requires --permission-prompt-tool stdio). */
data class ControlRequestEvent(
    override val sessionId: String?,
    val requestId: String,
    val subtype: String,
    val toolName: String,
    val input: JsonObject,
) : ClaudeEvent

/** Response to a control_request we sent (e.g. initialize, set_model). */
data class ControlResponseEvent(
    override val sessionId: String?,
    val requestId: String,
    val subtype: String,
    val response: JsonObject?,
) : ClaudeEvent

// --- Parser ---

object ClaudeEventParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(line: String): ClaudeEvent? {
        return try {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return null
            val obj = json.parseToJsonElement(trimmed).jsonObject
            when (obj["type"]?.jsonPrimitive?.contentOrNull) {
                "system" -> parseSystemEvent(obj)
                "rate_limit_event" -> parseRateLimitEvent(obj)
                "assistant" -> parseAssistantEvent(obj)
                "user" -> parseUserEvent(obj)
                "result" -> parseResultEvent(obj)
                "control_request" -> parseControlRequest(obj)
                "control_response" -> parseControlResponse(obj)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseSystemEvent(obj: JsonObject): ClaudeEvent? {
        val subtype = obj["subtype"]?.jsonPrimitive?.contentOrNull
        if (subtype != "init") return null
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val model = obj["model"]?.jsonPrimitive?.contentOrNull ?: ""
        val cwd = obj["cwd"]?.jsonPrimitive?.contentOrNull ?: ""
        val permissionMode = obj["permissionMode"]?.jsonPrimitive?.contentOrNull ?: ""
        val tools = try {
            obj["tools"]?.jsonArray?.mapNotNull { el ->
                try {
                    el.jsonPrimitive.contentOrNull
                } catch (_: Exception) {
                    try {
                        el.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                    } catch (_: Exception) {
                        null
                    }
                }
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return SystemInitEvent(
            sessionId = sessionId,
            model = model,
            tools = tools,
            cwd = cwd,
            permissionMode = permissionMode,
        )
    }

    private fun parseRateLimitEvent(obj: JsonObject): RateLimitEvent {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val infoObj = obj["rate_limit_info"]?.jsonObject
        val status = infoObj?.get("status")?.jsonPrimitive?.contentOrNull ?: ""
        val resetsAt = infoObj?.get("resetsAt")?.jsonPrimitive?.longOrNull
        return RateLimitEvent(
            sessionId = sessionId,
            rateLimitInfo = RateLimitInfo(status = status, resetsAt = resetsAt),
        )
    }

    private fun parseAssistantEvent(obj: JsonObject): AssistantMessageEvent {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val parentToolUseId = obj["parent_tool_use_id"]?.jsonPrimitive?.contentOrNull
        val message = parseMessage(obj["message"]?.jsonObject)
        return AssistantMessageEvent(
            sessionId = sessionId,
            message = message,
            parentToolUseId = parentToolUseId,
        )
    }

    private fun parseUserEvent(obj: JsonObject): UserToolResultEvent {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val message = parseMessage(obj["message"]?.jsonObject)
        return UserToolResultEvent(
            sessionId = sessionId,
            message = message,
        )
    }

    private fun parseResultEvent(obj: JsonObject): ResultEvent {
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        val subtype = obj["subtype"]?.jsonPrimitive?.contentOrNull ?: "success"
        val isError = obj["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
        val result = obj["result"]?.jsonPrimitive?.contentOrNull ?: ""
        val totalCostUsd = obj["total_cost_usd"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val durationMs = obj["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L
        val numTurns = obj["num_turns"]?.jsonPrimitive?.intOrNull ?: 0
        val stopReason = obj["stop_reason"]?.jsonPrimitive?.contentOrNull

        val usage = try {
            obj["usage"]?.let { json.decodeFromJsonElement(UsageInfo.serializer(), it) }
        } catch (_: Exception) {
            null
        }

        return ResultEvent(
            sessionId = sessionId,
            subtype = subtype,
            isError = isError,
            result = result,
            totalCostUsd = totalCostUsd,
            durationMs = durationMs,
            numTurns = numTurns,
            stopReason = stopReason,
            usage = usage,
        )
    }

    private fun parseControlRequest(obj: JsonObject): ControlRequestEvent {
        val requestId = obj["request_id"]?.jsonPrimitive?.contentOrNull ?: ""
        val request = obj["request"]?.jsonObject
        val subtype = request?.get("subtype")?.jsonPrimitive?.contentOrNull ?: ""
        val toolName = request?.get("tool_name")?.jsonPrimitive?.contentOrNull ?: ""
        val input = try {
            request?.get("input")?.jsonObject ?: JsonObject(emptyMap())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        return ControlRequestEvent(
            sessionId = sessionId,
            requestId = requestId,
            subtype = subtype,
            toolName = toolName,
            input = input,
        )
    }

    private fun parseControlResponse(obj: JsonObject): ControlResponseEvent {
        val responseObj = obj["response"]?.jsonObject
        val requestId = responseObj?.get("request_id")?.jsonPrimitive?.contentOrNull ?: ""
        val subtype = responseObj?.get("subtype")?.jsonPrimitive?.contentOrNull ?: ""
        val inner = try {
            responseObj?.get("response")?.jsonObject
        } catch (_: Exception) {
            null
        }
        val sessionId = obj["session_id"]?.jsonPrimitive?.contentOrNull
        return ControlResponseEvent(
            sessionId = sessionId,
            requestId = requestId,
            subtype = subtype,
            response = inner,
        )
    }

    private fun parseMessage(msgObj: JsonObject?): ClaudeMessage {
        if (msgObj == null) return ClaudeMessage(role = "unknown", content = emptyList())
        val role = msgObj["role"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val stopReason = msgObj["stop_reason"]?.jsonPrimitive?.contentOrNull
        val contentBlocks = try {
            msgObj["content"]?.jsonArray?.mapNotNull { el ->
                parseContentBlock(el.jsonObject)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        return ClaudeMessage(role = role, content = contentBlocks, stopReason = stopReason)
    }

    private fun parseContentBlock(block: JsonObject): MessageContentBlock? {
        return when (block["type"]?.jsonPrimitive?.contentOrNull) {
            "text" -> {
                val text = block["text"]?.jsonPrimitive?.contentOrNull ?: ""
                TextContentBlock(text = text)
            }

            "thinking" -> {
                val thinking = block["thinking"]?.jsonPrimitive?.contentOrNull ?: ""
                ThinkingContentBlock(thinking = thinking)
            }

            "tool_use" -> {
                val id = block["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val name = block["name"]?.jsonPrimitive?.contentOrNull ?: ""
                val input = try {
                    block["input"]?.jsonObject ?: JsonObject(emptyMap())
                } catch (_: Exception) {
                    JsonObject(emptyMap())
                }
                ToolUseContentBlock(id = id, name = name, input = input)
            }

            "tool_result" -> {
                val toolUseId = block["tool_use_id"]?.jsonPrimitive?.contentOrNull ?: ""
                val content = try {
                    block["content"]?.jsonPrimitive?.contentOrNull ?: ""
                } catch (_: Exception) {
                    try {
                        block["content"]?.toString() ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                }
                val isError = block["is_error"]?.jsonPrimitive?.booleanOrNull ?: false
                ToolResultContentBlock(toolUseId = toolUseId, content = content, isError = isError)
            }

            else -> null
        }
    }
}
