package com.oss.vibemanager.ui.components

import com.oss.vibemanager.model.ContentBlock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal val planJson = Json { isLenient = true; ignoreUnknownKeys = true }

internal fun isPlanTool(block: ContentBlock.ToolUse): Boolean =
    isExitPlanModeTool(block.name, block.input)

internal fun isExitPlanModeTool(name: String, input: String): Boolean {
    if (name.equals("ExitPlanMode", ignoreCase = true) ||
        name.equals("exit_plan_mode", ignoreCase = true)
    ) return true
    return extractPlan(input) != null
}

internal fun extractPlan(input: String): String? {
    if (input.isBlank()) return null
    return try {
        val obj = planJson.parseToJsonElement(input).jsonObject
        ((obj["plan"] as? JsonPrimitive)?.content
            ?: (obj["content"] as? JsonPrimitive)?.content)
            ?.takeIf { it.isNotBlank() }
    } catch (_: Throwable) {
        null
    }
}
