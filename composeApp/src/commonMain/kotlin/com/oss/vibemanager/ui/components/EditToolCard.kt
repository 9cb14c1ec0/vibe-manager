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

private val DeletionBackground = Color(0xFFE81123).copy(alpha = 0.15f)
private val DeletionTextColor = Color(0xFFE81123)
private val EditAdditionBackground = Color(0xFF16C60C).copy(alpha = 0.15f)
private val EditAdditionTextColor = Color(0xFF16C60C)

internal fun isEditTool(block: ContentBlock.ToolUse): Boolean =
    block.name.equals("Edit", ignoreCase = true)

private data class EditToolFields(
    val filePath: String?,
    val oldString: String?,
    val newString: String?,
    val replaceAll: Boolean,
)

private fun parseEditInput(input: String): EditToolFields {
    if (input.isBlank()) return EditToolFields(null, null, null, false)
    return try {
        val obj = planJson.parseToJsonElement(input).jsonObject
        val path = (obj["file_path"] as? JsonPrimitive)?.content
        val oldString = (obj["old_string"] as? JsonPrimitive)?.content
        val newString = (obj["new_string"] as? JsonPrimitive)?.content
        val replaceAll = (obj["replace_all"] as? JsonPrimitive)?.content == "true"
        EditToolFields(path, oldString, newString, replaceAll)
    } catch (_: Throwable) {
        EditToolFields(null, null, null, false)
    }
}

@Composable
fun EditToolCard(
    input: String,
    status: ToolStatus,
    result: ContentBlock.ToolResult? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val fields = remember(input) { parseEditInput(input) }

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
                        text = if (fields.replaceAll) "Edit (all)" else "Edit",
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
                    if (fields.oldString != null || fields.newString != null) {
                        EditDiffBlock(
                            oldString = fields.oldString.orEmpty(),
                            newString = fields.newString.orEmpty(),
                        )
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
private fun EditDiffBlock(oldString: String, newString: String) {
    val oldLines = remember(oldString) { if (oldString.isEmpty()) emptyList() else oldString.split("\n") }
    val newLines = remember(newString) { if (newString.isEmpty()) emptyList() else newString.split("\n") }
    val horizontalScrollState = rememberScrollState()

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .horizontalScroll(horizontalScrollState),
    ) {
        items(oldLines) { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeletionBackground)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "- ${line.ifEmpty { " " }}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = DeletionTextColor,
                )
            }
        }
        items(newLines) { line ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EditAdditionBackground)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "+ ${line.ifEmpty { " " }}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = EditAdditionTextColor,
                )
            }
        }
    }
}
