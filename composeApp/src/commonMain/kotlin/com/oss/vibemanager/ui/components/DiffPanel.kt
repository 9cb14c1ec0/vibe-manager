package com.oss.vibemanager.ui.components

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.FluentTheme
import io.github.composefluent.background.Layer
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ChangedFile
import com.oss.vibemanager.model.DiffLine
import com.oss.vibemanager.model.DiffLineType
import com.oss.vibemanager.model.FileDiff

private val AdditionBackground = Color(0xFF16C60C).copy(alpha = 0.15f)
private val DeletionBackground = Color(0xFFE81123).copy(alpha = 0.15f)
private val HunkBackground = Color(0xFF0078D4).copy(alpha = 0.1f)

@Composable
fun DiffPanel(
    onGetChangedFiles: () -> Result<List<ChangedFile>>,
    onGetFileDiff: (ChangedFile) -> Result<FileDiff>,
    modifier: Modifier = Modifier,
) {
    var fileDiffs by remember { mutableStateOf<List<FileDiff>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val filesResult = onGetChangedFiles()
        filesResult.onFailure { loadError = it.message }
        filesResult.onSuccess { files ->
            fileDiffs = files.mapNotNull { file ->
                onGetFileDiff(file).getOrNull()
            }
        }
    }

    if (fileDiffs.isEmpty() && loadError == null) {
        Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = "No uncommitted changes",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.tertiary,
            )
        }
        return
    }

    Layer(modifier = modifier.fillMaxSize()) {
        if (loadError != null) {
            Text(
                text = "Error: $loadError",
                fontSize = 12.sp,
                color = Color(0xFFE81123),
                modifier = Modifier.padding(8.dp),
            )
        } else {
            val listState = rememberLazyListState()

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                ) {
                    items(fileDiffs) { diff ->
                        FileDiffSection(diff)
                    }
                }

                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(end = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FileDiffSection(diff: FileDiff) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // File name header
        Text(
            text = diff.filePath,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = FluentTheme.colors.text.text.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        // Diff lines
        Column {
            for (line in diff.lines) {
                DiffLineRow(line)
            }
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
            .padding(horizontal = 4.dp),
    ) {
        Text(
            text = line.content.ifEmpty { " " },
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = textColor,
        )
    }
}
