package com.oss.vibemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.TaskState

@Composable
fun TaskStatusBadge(state: TaskState, modifier: Modifier = Modifier) {
    val isDark = FluentTheme.colors.darkMode
    val (text, bgColor, fgColor) = when (state) {
        TaskState.Created -> Triple(
            "Created",
            if (isDark) Color(0xFF3A3A3A) else Color(0xFFE0E0E0),
            if (isDark) Color(0xFFE0E0E0) else Color(0xFF424242),
        )
        TaskState.Running -> Triple("Running", Color(0xFF0078D4), Color.White)
        TaskState.Completed -> Triple("Completed", Color(0xFF107C10), Color.White)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = fgColor)
    }
}
