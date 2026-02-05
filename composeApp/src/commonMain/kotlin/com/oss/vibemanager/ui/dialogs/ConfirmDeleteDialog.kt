package com.oss.vibemanager.ui.dialogs

import androidx.compose.runtime.Composable
import io.github.composefluent.component.ContentDialog
import io.github.composefluent.component.ContentDialogButton
import io.github.composefluent.component.Text

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    ContentDialog(
        title = title,
        visible = true,
        content = { Text(message) },
        primaryButtonText = "Delete",
        secondaryButtonText = "Cancel",
        onButtonClick = { button ->
            when (button) {
                ContentDialogButton.Primary -> onConfirm()
                else -> onDismiss()
            }
        },
    )
}
