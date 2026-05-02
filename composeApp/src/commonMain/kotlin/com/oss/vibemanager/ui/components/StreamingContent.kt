package com.oss.vibemanager.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ToolStatus

@Composable
fun StreamingContent(
    blocks: List<ContentBlock>,
    streamingText: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // Role label
        Text(
            text = "Claude",
            fontSize = 12.sp,
            color = FluentTheme.colors.text.text.secondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Group consecutive tool blocks, render others directly
        val grouped = remember(blocks) { groupStreamingBlocks(blocks) }
        for (group in grouped) {
            when (group) {
                is StreamBlockGroup.Single -> {
                    RenderStreamBlock(group.block, blocks)
                }
                is StreamBlockGroup.ToolRun -> {
                    val results = blocks.filterIsInstance<ContentBlock.ToolResult>()
                        .associateBy { it.toolUseId }
                    CollapsibleToolGroup(
                        tools = group.tools,
                        results = results,
                    )
                }
            }
        }

        // If there are no text blocks yet but streaming text is available, show it
        if (blocks.none { it is ContentBlock.Text } && streamingText.isNotEmpty()) {
            // Show streaming text with blinking cursor
            val cursorVisible by rememberInfiniteTransition().animateFloat(
                initialValue = 1f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
            Text(
                text = streamingText + if (cursorVisible > 0.5f) "█" else " ",
                fontSize = 14.sp,
                color = FluentTheme.colors.text.text.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        // Streaming indicator when no content yet
        if (blocks.isEmpty() && streamingText.isEmpty()) {
            val dotCount by rememberInfiniteTransition().animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000),
                    repeatMode = RepeatMode.Restart,
                ),
            )
            Text(
                text = "Thinking" + ".".repeat(dotCount.toInt() + 1),
                fontSize = 14.sp,
                color = FluentTheme.colors.text.text.tertiary,
            )
        }
    }
}

private sealed class StreamBlockGroup {
    data class Single(val block: ContentBlock) : StreamBlockGroup()
    data class ToolRun(val tools: List<ContentBlock.ToolUse>) : StreamBlockGroup()
}

private fun groupStreamingBlocks(blocks: List<ContentBlock>): List<StreamBlockGroup> {
    val groups = mutableListOf<StreamBlockGroup>()
    var currentToolRun = mutableListOf<ContentBlock.ToolUse>()

    for (block in blocks) {
        when (block) {
            is ContentBlock.ToolUse -> {
                if (isPlanTool(block.name)) {
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(StreamBlockGroup.ToolRun(currentToolRun.toList()))
                        currentToolRun = mutableListOf()
                    }
                    groups.add(StreamBlockGroup.Single(block))
                } else {
                    currentToolRun.add(block)
                }
            }
            is ContentBlock.ToolResult -> {
                // ToolResults consumed by their matching ToolUse group
                val matchesCurrentRun = currentToolRun.any { it.id == block.toolUseId }
                if (!matchesCurrentRun) {
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(StreamBlockGroup.ToolRun(currentToolRun.toList()))
                        currentToolRun = mutableListOf()
                    }
                    // Only render standalone if no matching ToolUse
                    val hasMatch = blocks.filterIsInstance<ContentBlock.ToolUse>()
                        .any { it.id == block.toolUseId }
                    if (!hasMatch) {
                        groups.add(StreamBlockGroup.Single(block))
                    }
                }
            }
            else -> {
                if (currentToolRun.isNotEmpty()) {
                    groups.add(StreamBlockGroup.ToolRun(currentToolRun.toList()))
                    currentToolRun = mutableListOf()
                }
                groups.add(StreamBlockGroup.Single(block))
            }
        }
    }
    if (currentToolRun.isNotEmpty()) {
        groups.add(StreamBlockGroup.ToolRun(currentToolRun.toList()))
    }
    return groups
}

@Composable
private fun RenderStreamBlock(block: ContentBlock, allBlocks: List<ContentBlock>) {
    when (block) {
        is ContentBlock.Text -> {
            Text(
                text = block.text,
                fontSize = 14.sp,
                color = FluentTheme.colors.text.text.primary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        is ContentBlock.Thinking -> {
            ThinkingBlock(text = block.text)
        }
        is ContentBlock.ToolUse -> {
            if (isPlanTool(block.name)) {
                PlanCard(input = block.input)
            } else {
                ToolCallCard(
                    toolName = block.name,
                    input = block.input,
                    status = block.status,
                    result = allBlocks
                        .filterIsInstance<ContentBlock.ToolResult>()
                        .firstOrNull { it.toolUseId == block.id },
                )
            }
        }
        is ContentBlock.ToolResult -> {
            // Rendered with ToolUse
        }
    }
}

@Composable
fun ThinkingBlock(
    text: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier.clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (expanded) "▼" else "▶",
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.tertiary,
            )
            Text(
                text = "Thinking...",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.tertiary,
            )
        }
        if (expanded) {
            Text(
                text = text,
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.tertiary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp),
            )
        }
    }
}
