package com.oss.vibemanager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ConversationMessage
import com.oss.vibemanager.model.MessageRole
import com.oss.vibemanager.model.ToolStatus

/**
 * Represents a run of consecutive blocks of the same "kind" for grouping purposes.
 */
private sealed class BlockGroup {
    data class Single(val block: ContentBlock) : BlockGroup()
    data class ToolRun(
        val tools: List<ContentBlock.ToolUse>,
        val results: Map<String, ContentBlock.ToolResult>,
    ) : BlockGroup()
}

internal fun isPlanTool(name: String): Boolean =
    name.equals("ExitPlanMode", ignoreCase = true) ||
        name.equals("exit_plan_mode", ignoreCase = true)

/**
 * Groups consecutive ToolUse/ToolResult blocks into runs, leaving other blocks as singles.
 */
private fun groupBlocks(blocks: List<ContentBlock>): List<BlockGroup> {
    val groups = mutableListOf<BlockGroup>()
    var currentToolRun = mutableListOf<ContentBlock.ToolUse>()
    val allResults = blocks.filterIsInstance<ContentBlock.ToolResult>()
        .associateBy { it.toolUseId }

    for (block in blocks) {
        when (block) {
            is ContentBlock.ToolUse -> {
                if (isPlanTool(block.name)) {
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(BlockGroup.ToolRun(currentToolRun.toList(), allResults))
                        currentToolRun = mutableListOf()
                    }
                    groups.add(BlockGroup.Single(block))
                } else {
                    currentToolRun.add(block)
                }
            }
            is ContentBlock.ToolResult -> {
                // ToolResults are consumed by their matching ToolUse group; skip here.
                // But if we had a tool run going and this result doesn't match it,
                // or if there's no run, we flush.
                val matchesCurrentRun = currentToolRun.any { it.id == block.toolUseId }
                if (!matchesCurrentRun) {
                    // Flush any pending tool run
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(BlockGroup.ToolRun(currentToolRun.toList(), allResults))
                        currentToolRun = mutableListOf()
                    }
                    // Only render standalone if no matching ToolUse exists anywhere
                    val hasMatchingToolUse = blocks.filterIsInstance<ContentBlock.ToolUse>()
                        .any { it.id == block.toolUseId }
                    if (!hasMatchingToolUse) {
                        groups.add(BlockGroup.Single(block))
                    }
                }
                // If it matches the current run, it's consumed by that run's result map
            }
            else -> {
                // Flush any pending tool run before adding a non-tool block
                if (currentToolRun.isNotEmpty()) {
                    groups.add(BlockGroup.ToolRun(currentToolRun.toList(), allResults))
                    currentToolRun = mutableListOf()
                }
                groups.add(BlockGroup.Single(block))
            }
        }
    }
    // Flush remaining tool run
    if (currentToolRun.isNotEmpty()) {
        groups.add(BlockGroup.ToolRun(currentToolRun.toList(), allResults))
    }
    return groups
}

@Composable
fun MessageBubble(
    message: ConversationMessage,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        // Role label
        Text(
            text = if (message.role == MessageRole.User) "You" else "Claude",
            fontSize = 12.sp,
            color = if (message.role == MessageRole.User)
                FluentTheme.colors.text.accent.primary
            else
                FluentTheme.colors.text.text.secondary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Group blocks and render
        val groups = remember(message.blocks) { groupBlocks(message.blocks) }
        for (group in groups) {
            when (group) {
                is BlockGroup.Single -> {
                    RenderBlock(group.block)
                }
                is BlockGroup.ToolRun -> {
                    CollapsibleToolGroup(
                        tools = group.tools,
                        results = group.results,
                    )
                }
            }
        }
    }
}

@Composable
private fun RenderBlock(block: ContentBlock) {
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
                )
            }
        }
        is ContentBlock.ToolResult -> {
            ToolResultBlock(result = block)
        }
    }
}

/**
 * Groups consecutive tool calls into a collapsible summary.
 * Shows "N tool calls" header; click to expand individual cards.
 */
@Composable
fun CollapsibleToolGroup(
    tools: List<ContentBlock.ToolUse>,
    results: Map<String, ContentBlock.ToolResult>,
    modifier: Modifier = Modifier,
) {
    // If there's only 1 tool, just render it directly
    if (tools.size == 1) {
        val tool = tools.first()
        ToolCallCard(
            toolName = tool.name,
            input = tool.input,
            status = tool.status,
            result = results[tool.id],
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }

    // Count statuses for the summary
    val completedCount = tools.count { it.status == ToolStatus.Completed }
    val errorCount = tools.count { it.status == ToolStatus.Error }
    val runningCount = tools.count { it.status == ToolStatus.Running }

    Layer(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header row — always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Expand/collapse icon
                Text(
                    text = if (expanded) "▼" else "▶",
                    fontSize = 11.sp,
                    color = FluentTheme.colors.text.text.tertiary,
                )

                // Summary text
                val summaryParts = mutableListOf<String>()
                if (runningCount > 0) summaryParts.add("$runningCount running")
                if (completedCount > 0) summaryParts.add("$completedCount completed")
                if (errorCount > 0) summaryParts.add("$errorCount failed")

                Text(
                    text = "${tools.size} tool calls",
                    fontSize = 13.sp,
                    color = FluentTheme.colors.text.text.primary,
                )

                if (summaryParts.isNotEmpty()) {
                    Text(
                        text = "(${summaryParts.joinToString(", ")})",
                        fontSize = 12.sp,
                        color = FluentTheme.colors.text.text.secondary,
                    )
                }

                Spacer(Modifier.weight(1f))

                // Brief tool name summary when collapsed
                if (!expanded) {
                    val toolNames = tools.map { it.name }.distinct().take(3)
                    val namesSummary = toolNames.joinToString(", ") +
                        if (tools.map { it.name }.distinct().size > 3) ", ..." else ""
                    Text(
                        text = namesSummary,
                        fontSize = 11.sp,
                        color = FluentTheme.colors.text.text.tertiary,
                    )
                }
            }

            // Expanded: show all tool cards
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (tool in tools) {
                        ToolCallCard(
                            toolName = tool.name,
                            input = tool.input,
                            status = tool.status,
                            result = results[tool.id],
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolResultBlock(
    result: ContentBlock.ToolResult,
    modifier: Modifier = Modifier,
) {
    Layer(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = if (result.isError) "Error" else "Result",
                fontSize = 12.sp,
                color = if (result.isError)
                    androidx.compose.ui.graphics.Color(0xFFE81123)
                else
                    FluentTheme.colors.text.text.secondary,
            )
            if (result.content.isNotEmpty()) {
                Text(
                    text = result.content.take(500),
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
