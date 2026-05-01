package com.oss.vibemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ToolStatus

@Composable
fun ToolCallCard(
    toolName: String,
    input: String,
    status: ToolStatus,
    result: ContentBlock.ToolResult? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Layer(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header: tool name + status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Status indicator
                    val statusIcon = when (status) {
                        ToolStatus.Running -> "▶" // play triangle
                        ToolStatus.Completed -> "✓" // checkmark
                        ToolStatus.Error -> "✗" // X mark
                    }
                    val statusColor = when (status) {
                        ToolStatus.Running -> FluentTheme.colors.text.accent.primary
                        ToolStatus.Completed -> androidx.compose.ui.graphics.Color(0xFF16C60C)
                        ToolStatus.Error -> androidx.compose.ui.graphics.Color(0xFFE81123)
                    }
                    Text(statusIcon, color = statusColor, fontSize = 14.sp)
                    Text(
                        text = toolName,
                        fontSize = 13.sp,
                        color = FluentTheme.colors.text.text.primary,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 10.sp,
                    color = FluentTheme.colors.text.text.tertiary,
                )
            }

            // Input summary (always visible, truncated)
            val inputSummary = formatInputSummary(toolName, input)
            if (inputSummary.isNotEmpty()) {
                Text(
                    text = inputSummary,
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.secondary,
                    fontFamily = FontFamily.Monospace,
                    maxLines = if (expanded) Int.MAX_VALUE else 1,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Expanded: show full input + result
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (input.length > inputSummary.length) {
                        Text(
                            text = "Input:",
                            fontSize = 11.sp,
                            color = FluentTheme.colors.text.text.tertiary,
                        )
                        Text(
                            text = input.take(2000),
                            fontSize = 11.sp,
                            color = FluentTheme.colors.text.text.secondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                    }

                    // Result
                    if (result != null) {
                        Text(
                            text = if (result.isError) "Error:" else "Output:",
                            fontSize = 11.sp,
                            color = if (result.isError)
                                androidx.compose.ui.graphics.Color(0xFFE81123)
                            else
                                FluentTheme.colors.text.text.tertiary,
                        )
                        Text(
                            text = result.content.take(2000),
                            fontSize = 11.sp,
                            color = FluentTheme.colors.text.text.secondary,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extracts a human-readable summary from tool input JSON.
 * For common tools, shows the most relevant parameter.
 */
private fun formatInputSummary(toolName: String, input: String): String {
    return try {
        // Quick extraction of key values from JSON-like input
        when (toolName.lowercase()) {
            "read" -> extractJsonValue(input, "file_path") ?: input.take(80)
            "edit" -> extractJsonValue(input, "file_path") ?: input.take(80)
            "write" -> extractJsonValue(input, "file_path") ?: input.take(80)
            "bash", "powershell" -> extractJsonValue(input, "command") ?: input.take(80)
            "glob" -> extractJsonValue(input, "pattern") ?: input.take(80)
            "grep" -> extractJsonValue(input, "pattern") ?: input.take(80)
            else -> input.take(80)
        }
    } catch (_: Exception) {
        input.take(80)
    }
}

private fun extractJsonValue(json: String, key: String): String? {
    // Simple regex extraction for common JSON patterns
    val regex = Regex(""""$key"\s*:\s*"([^"]*?)"""")
    return regex.find(json)?.groupValues?.get(1)
}
