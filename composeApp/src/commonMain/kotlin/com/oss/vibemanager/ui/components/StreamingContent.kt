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

        // Render accumulated blocks
        for (block in blocks) {
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
                        result = blocks
                            .filterIsInstance<ContentBlock.ToolResult>()
                            .firstOrNull { it.toolUseId == block.id },
                    )
                }
                is ContentBlock.ToolResult -> {
                    // Rendered with ToolUse above
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
