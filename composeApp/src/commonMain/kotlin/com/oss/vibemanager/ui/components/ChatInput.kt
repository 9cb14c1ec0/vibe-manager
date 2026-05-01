package com.oss.vibemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import io.github.composefluent.component.TextField

@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { if (!isStreaming) text = it },
            modifier = Modifier.weight(1f).onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.Enter &&
                    !event.isShiftPressed &&
                    !isStreaming
                ) {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    }
                    true
                } else {
                    false
                }
            },
            placeholder = { Text(if (isStreaming) "Waiting for response..." else "Send a message...") },
        )
        if (isStreaming) {
            Button(onClick = onStop) { Text("Stop") }
        } else {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    }
                },
            ) { Text("Send") }
        }
    }
}
