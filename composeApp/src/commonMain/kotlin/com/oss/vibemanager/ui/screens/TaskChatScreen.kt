package com.oss.vibemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.oss.vibemanager.ui.components.StreamingContent

@Composable
fun TaskChatScreen(
    taskName: String,
    conversationState: ConversationState,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive or streaming updates
    LaunchedEffect(conversationState.messages.size, conversationState.streamingText) {
        val totalItems = listState.layoutInfo.totalItemsCount
        if (totalItems > 0) {
            listState.animateScrollToItem(totalItems - 1)
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
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
            if (conversationState.model.isNotEmpty()) {
                Text(
                    conversationState.model,
                    fontSize = 12.sp,
                    color = FluentTheme.colors.text.text.secondary,
                )
            }
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

        // Message list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
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

        // Error display
        conversationState.error?.let { errorMsg ->
            Text(
                errorMsg,
                color = Color(0xFFE81123),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
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
