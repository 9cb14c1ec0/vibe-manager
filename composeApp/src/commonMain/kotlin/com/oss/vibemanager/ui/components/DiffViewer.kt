package com.oss.vibemanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.DiffLine
import com.oss.vibemanager.model.DiffLineType
import com.oss.vibemanager.model.FileDiff

private val AdditionBackground = Color(0xFF16C60C).copy(alpha = 0.15f)
private val DeletionBackground = Color(0xFFE81123).copy(alpha = 0.15f)
private val HunkBackground = Color(0xFF0078D4).copy(alpha = 0.1f)

@Composable
fun DiffViewer(
    diff: FileDiff?,
    modifier: Modifier = Modifier,
) {
    if (diff == null) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
        ) {
            Text(
                text = "Select a file to view diff",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.tertiary,
            )
        }
        return
    }

    val horizontalScrollState = rememberScrollState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .horizontalScroll(horizontalScrollState),
    ) {
        items(diff.lines) { line ->
            DiffLineRow(line)
        }
    }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val backgroundColor = when (line.type) {
        DiffLineType.Addition -> AdditionBackground
        DiffLineType.Deletion -> DeletionBackground
        DiffLineType.Hunk -> HunkBackground
        DiffLineType.Context -> Color.Transparent
    }

    val textColor = when (line.type) {
        DiffLineType.Addition -> Color(0xFF16C60C)
        DiffLineType.Deletion -> Color(0xFFE81123)
        DiffLineType.Hunk -> Color(0xFF0078D4)
        DiffLineType.Context -> FluentTheme.colors.text.text.secondary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 1.dp),
    ) {
        Text(
            text = line.content.ifEmpty { " " },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
        )
    }
}
