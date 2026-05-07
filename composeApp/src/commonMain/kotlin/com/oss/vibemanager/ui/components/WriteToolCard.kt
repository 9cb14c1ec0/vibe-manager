package com.oss.vibemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ToolStatus
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

private val AdditionBackground = Color(0xFF16C60C).copy(alpha = 0.15f)
private val AdditionTextColor = Color(0xFF16C60C)

internal fun isWriteTool(block: ContentBlock.ToolUse): Boolean =
    block.name.equals("Write", ignoreCase = true)

private data class WriteToolFields(val filePath: String?, val content: String?)

private fun parseWriteInput(input: String): WriteToolFields {
    if (input.isBlank()) return WriteToolFields(null, null)
    return try {
        val obj = planJson.parseToJsonElement(input).jsonObject
        val path = (obj["file_path"] as? JsonPrimitive)?.content
        val content = (obj["content"] as? JsonPrimitive)?.content
        WriteToolFields(path, content)
    } catch (_: Throwable) {
        WriteToolFields(null, null)
    }
}

@Composable
fun WriteToolCard(
    input: String,
    status: ToolStatus,
    result: ContentBlock.ToolResult? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fields = remember(input) { parseWriteInput(input) }

    Layer(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { expanded = !expanded },
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val statusIcon = when (status) {
                        ToolStatus.Running -> "▶"
                        ToolStatus.Completed -> "✓"
                        ToolStatus.Error -> "✗"
                    }
                    val statusColor = when (status) {
                        ToolStatus.Running -> FluentTheme.colors.text.accent.primary
                        ToolStatus.Completed -> Color(0xFF16C60C)
                        ToolStatus.Error -> Color(0xFFE81123)
                    }
                    Text(statusIcon, color = statusColor, fontSize = 14.sp)
                    Text(
                        text = "Write",
                        fontSize = 13.sp,
                        color = FluentTheme.colors.text.text.primary,
                    )
                    val pathDisplay = fields.filePath ?: "(unknown path)"
                    Text(
                        text = pathDisplay,
                        fontSize = 12.sp,
                        color = FluentTheme.colors.text.text.secondary,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    fontSize = 10.sp,
                    color = FluentTheme.colors.text.text.tertiary,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val content = fields.content
                    if (content != null) {
                        WriteDiffBlock(content = content)
                    } else {
                        Text(
                            text = input.take(2000),
                            fontSize = 11.sp,
                            color = FluentTheme.colors.text.text.secondary,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    if (result != null) {
                        Text(
                            text = if (result.isError) "Error:" else "Output:",
                            fontSize = 11.sp,
                            color = if (result.isError)
                                Color(0xFFE81123)
                            else
                                FluentTheme.colors.text.text.tertiary,
                            modifier = Modifier.padding(top = 8.dp),
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

@Composable
private fun WriteDiffBlock(content: String) {
    val lines = remember(content) { content.split("\n") }
    val horizontalScrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .horizontalScroll(horizontalScrollState),
    ) {
        items(lines) { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AdditionBackground)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "+ ${line.ifEmpty { " " }}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = AdditionTextColor,
                )
            }
        }
    }
}
