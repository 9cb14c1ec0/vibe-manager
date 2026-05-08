package com.oss.vibemanager.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.util.decodeBase64ToImageBitmap
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text

@Composable
fun ChatInput(
    onSend: (String, List<ContentBlock.Image>) -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    pasteImageHandler: () -> List<ContentBlock.Image> = { emptyList() },
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var pastedImages by remember { mutableStateOf(listOf<ContentBlock.Image>()) }

    fun doSend() {
        if (textFieldValue.text.isNotBlank() || pastedImages.isNotEmpty()) {
            onSend(textFieldValue.text.trim(), pastedImages)
            textFieldValue = TextFieldValue("")
            pastedImages = emptyList()
        }
    }

    Column(
        modifier = modifier
            .border(
                width = 2.dp,
                color = FluentTheme.colors.stroke.divider.default,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(12.dp)
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                        if (event.isShiftPressed || event.isCtrlPressed) {
                            // Shift+Enter or Ctrl+Enter: insert newline at cursor
                            val cursorPos = textFieldValue.selection.start
                            val currentText = textFieldValue.text
                            val newText = currentText.substring(0, cursorPos) + "\n" + currentText.substring(cursorPos)
                            textFieldValue = TextFieldValue(
                                text = newText,
                                selection = TextRange(cursorPos + 1),
                            )
                            true
                        } else if (!isStreaming) {
                            // Enter alone: send message
                            doSend()
                            true
                        } else {
                            true // consume Enter during streaming too
                        }
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.V -> {
                        // Handle paste shortcut (Ctrl+V or Cmd+V)
                        if (event.isCtrlPressed || event.isMetaPressed) {
                            val newImages = pasteImageHandler()
                            if (newImages.isNotEmpty()) {
                                pastedImages = pastedImages + newImages
                            }
                            true // consume the event
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            },
    ) {
        // Show pasted image thumbnails
        if (pastedImages.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pastedImages) { image ->
                    Box(
                        modifier = Modifier.size(80.dp).border(1.dp, FluentTheme.colors.stroke.divider.default)
                    ) {
                        val imageBitmap = decodeBase64ToImageBitmap(image.base64Data)
                        if (imageBitmap != null) {
                            Image(
                                bitmap = imageBitmap,
                                contentDescription = "Pasted image",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                        Text(
                            text = "Invalid image",
                            fontSize = 10.sp,
                            color = androidx.compose.ui.graphics.Color(0xFFE81123),
                            modifier = Modifier.align(Alignment.Center)
                        )
                        }
                        // Remove button
                        Box(
                            modifier = Modifier.align(Alignment.TopEnd)
                                .clickable { pastedImages = pastedImages - image }
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "×",
                                color = FluentTheme.colors.text.text.primary,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        BasicTextField(
            value = textFieldValue,
            onValueChange = { if (!isStreaming) textFieldValue = it },
            maxLines = 8,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 180.dp)
                .verticalScroll(rememberScrollState()),
            textStyle = TextStyle(
                color = FluentTheme.colors.text.text.primary,
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(FluentTheme.colors.text.accent.primary),
            decorationBox = { innerTextField ->
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = if (isStreaming) "Waiting for response..." else "Send a message...",
                        fontSize = 14.sp,
                        color = FluentTheme.colors.text.text.tertiary,
                    )
                }
                innerTextField()
            },
        )

        // Button row
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Enter to send, Shift+Enter for new line",
                fontSize = 11.sp,
                color = FluentTheme.colors.text.text.tertiary,
                modifier = Modifier.weight(1f),
            )
            if (isStreaming) {
                Button(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = { doSend() }) { Text("Send") }
            }
        }
    }
}
