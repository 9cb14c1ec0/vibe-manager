package com.oss.vibemanager.ui.screens

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ConversationState
import com.oss.vibemanager.model.SessionStatus
import com.oss.vibemanager.ui.components.ChatInput
import com.oss.vibemanager.ui.components.MessageBubble
import com.oss.vibemanager.ui.components.PermissionBanner
import com.oss.vibemanager.ui.components.StreamingContent
import kotlinx.coroutines.flow.distinctUntilChanged

private data class ModelOption(val id: String, val label: String)

private val MODEL_OPTIONS = listOf(
    ModelOption("claude-sonnet-4-5", "Sonnet 4.5"),
    ModelOption("claude-sonnet-4-6", "Sonnet 4.6"),
    ModelOption("claude-sonnet-4-7", "Sonnet 4.7"),
    ModelOption("claude-opus-4-5", "Opus 4.5"),
    ModelOption("claude-opus-4-6", "Opus 4.6"),
    ModelOption("claude-opus-4-7", "Opus 4.7"),
)

@Composable
fun TaskChatScreen(
    taskName: String,
    conversationState: ConversationState,
    selectedModel: String,
    permissionMode: String,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onModelSelected: (String) -> Unit,
    onModeSelected: (String) -> Unit,
    onPermissionRespond: (requestId: String, optionId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Track whether user has scrolled away from bottom
    var userScrolledUp by remember { mutableStateOf(false) }

    // Detect manual scrolling away from bottom
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = info.totalItemsCount
            // Consider "at bottom" if last visible item is within 2 of the end
            totalItems <= 0 || lastVisible >= totalItems - 2
        }.distinctUntilChanged().collect { atBottom ->
            userScrolledUp = !atBottom
        }
    }

    // Auto-scroll to bottom when new messages arrive or streaming updates (unless user scrolled up)
    LaunchedEffect(conversationState.messages.size, conversationState.streamingBlocks.size, conversationState.streamingText) {
        if (!userScrolledUp) {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems > 0) {
                listState.animateScrollToItem(totalItems - 1)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text(
                taskName,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 4.dp),
            )

            // Spacer to push controls to the right
            Box(modifier = Modifier.weight(1f))

            // Model dropdown
            ModelDropdown(
                selectedModel = selectedModel,
                onModelSelected = onModelSelected,
            )

            // Mode selector
            SegmentedControl(
                options = listOf("Plan", "Build"),
                selectedIndex = if (permissionMode == "plan") 0 else 1,
                onSelected = { index ->
                    onModeSelected(if (index == 0) "plan" else "acceptEdits")
                },
            )

            val costStr = conversationState.totalCostUsd.let { cost ->
                val cents = ((cost * 100).toInt()).toString().padStart(2, '0')
                val dollars = (cost).toInt()
                "$$dollars.$cents"
            }
            Text(
                costStr,
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.secondary,
            )
            // Status indicator
            val statusText = when (conversationState.status) {
                SessionStatus.Idle -> "Ready"
                SessionStatus.Streaming -> "Thinking..."
                SessionStatus.Error -> "Error"
                SessionStatus.Disconnected -> "Disconnected"
            }
            val statusColor = when (conversationState.status) {
                SessionStatus.Idle -> FluentTheme.colors.text.text.secondary
                SessionStatus.Streaming -> FluentTheme.colors.text.accent.primary
                SessionStatus.Error -> Color(0xFFE81123)
                SessionStatus.Disconnected -> Color.Gray
            }
            Text(statusText, fontSize = 12.sp, color = statusColor)
        }

        // Message list with visible scrollbar
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(conversationState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                // Show streaming content at the bottom
                if (conversationState.isStreaming &&
                    (conversationState.streamingBlocks.isNotEmpty() || conversationState.streamingText.isNotEmpty())
                ) {
                    item(key = "streaming") {
                        StreamingContent(
                            blocks = conversationState.streamingBlocks,
                            streamingText = conversationState.streamingText,
                        )
                    }
                }
            }

            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
            )
        }

        // Error display
        conversationState.error?.let { errorMsg ->
            Text(
                errorMsg,
                color = Color(0xFFE81123),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Permission approval banner
        conversationState.pendingPermission?.let { permission ->
            PermissionBanner(
                permission = permission,
                onRespond = { optionId ->
                    onPermissionRespond(permission.requestId, optionId)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        // Input area
        ChatInput(
            onSend = onSendMessage,
            onStop = onStopGeneration,
            isStreaming = conversationState.isStreaming,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
    }
}

@Composable
private fun ModelDropdown(
    selectedModel: String,
    onModelSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = MODEL_OPTIONS.firstOrNull { it.id == selectedModel }?.label ?: selectedModel

    Box(modifier = modifier) {
        // Trigger button
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = FluentTheme.colors.stroke.divider.default,
                    shape = RoundedCornerShape(6.dp),
                )
                .clip(RoundedCornerShape(6.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = currentLabel,
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.primary,
                )
                Text(
                    text = "▾", // small down triangle
                    fontSize = 10.sp,
                    color = FluentTheme.colors.text.text.secondary,
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            MODEL_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = {
                        androidx.compose.material3.Text(
                            text = option.label,
                            fontSize = 13.sp,
                        )
                    },
                    onClick = {
                        onModelSelected(option.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)

    Row(
        modifier = modifier
            .border(
                width = 1.dp,
                color = FluentTheme.colors.stroke.divider.default,
                shape = shape,
            )
            .clip(shape),
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            val bgColor = if (isSelected) {
                FluentTheme.colors.fillAccent.default
            } else {
                Color.Transparent
            }
            val textColor = if (isSelected) {
                FluentTheme.colors.text.onAccent.primary
            } else {
                FluentTheme.colors.text.text.secondary
            }

            Box(
                modifier = Modifier
                    .clickable { onSelected(index) }
                    .background(bgColor)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = textColor,
                )
            }
        }
    }
}
