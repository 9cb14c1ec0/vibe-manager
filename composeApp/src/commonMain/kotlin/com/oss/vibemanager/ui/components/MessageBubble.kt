package com.oss.vibemanager.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ConversationMessage
import com.oss.vibemanager.model.MessageRole

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

        // Content blocks
        for (block in message.blocks) {
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
                    ToolCallCard(
                        toolName = block.name,
                        input = block.input,
                        status = block.status,
                        // Find matching result in the same message
                        result = message.blocks
                            .filterIsInstance<ContentBlock.ToolResult>()
                            .firstOrNull { it.toolUseId == block.id },
                    )
                }
                is ContentBlock.ToolResult -> {
                    // ToolResults are rendered inline with their ToolUse card above
                    // Only render standalone if no matching ToolUse exists
                    val hasMatchingToolUse = message.blocks
                        .filterIsInstance<ContentBlock.ToolUse>()
                        .any { it.id == block.toolUseId }
                    if (!hasMatchingToolUse) {
                        ToolResultBlock(result = block)
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
