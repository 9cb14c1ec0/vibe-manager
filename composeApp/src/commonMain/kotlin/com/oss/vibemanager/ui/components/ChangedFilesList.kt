package com.oss.vibemanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ChangedFile
import com.oss.vibemanager.model.FileChangeStatus

@Composable
fun ChangedFilesList(
    files: List<ChangedFile>,
    selectedFile: ChangedFile?,
    onFileSelected: (ChangedFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (file in files) {
            val isSelected = file == selectedFile
            val backgroundColor = if (isSelected) {
                FluentTheme.colors.subtleFill.secondary
            } else {
                Color.Transparent
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onFileSelected(file) }
                    .then(
                        if (isSelected) Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        else Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val (statusChar, statusColor) = when (file.status) {
                    FileChangeStatus.Modified -> "M" to Color(0xFFCA5010)
                    FileChangeStatus.Added -> "A" to Color(0xFF16C60C)
                    FileChangeStatus.Deleted -> "D" to Color(0xFFE81123)
                    FileChangeStatus.Untracked -> "?" to Color(0xFF6B6B6B)
                }

                Text(
                    text = statusChar,
                    fontSize = 12.sp,
                    color = statusColor,
                )

                Text(
                    text = file.path,
                    fontSize = 12.sp,
                    color = if (isSelected) {
                        FluentTheme.colors.text.text.primary
                    } else {
                        FluentTheme.colors.text.text.secondary
                    },
                )
            }
        }
    }
}
