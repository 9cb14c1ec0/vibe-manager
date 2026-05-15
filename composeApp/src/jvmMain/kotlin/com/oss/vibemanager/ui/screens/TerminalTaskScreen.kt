package com.oss.vibemanager.ui.screens

import ai.rever.bossterm.compose.TabbedTerminal
import ai.rever.bossterm.compose.rememberTabbedTerminalState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oss.vibemanager.model.ChangedFile
import com.oss.vibemanager.model.FileDiff
import com.oss.vibemanager.ui.components.DiffPanel
import io.github.composefluent.FluentTheme
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private val DiffPanelMinWidth: Dp = 240.dp
private val DiffPanelMaxWidth: Dp = 900.dp

@Composable
fun TerminalTaskScreen(
    taskName: String,
    workingDirectory: String,
    onBack: () -> Unit,
    onGetChangedFiles: () -> Result<List<ChangedFile>>,
    onGetFileDiff: (ChangedFile) -> Result<FileDiff>,
    diffPanelWidth: Float = 400f,
    onDiffPanelWidthChanged: (Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showDiffPanel by remember { mutableStateOf(false) }
    var diffWidth by remember {
        mutableStateOf(diffPanelWidth.dp.coerceIn(DiffPanelMinWidth, DiffPanelMaxWidth))
    }
    val density = LocalDensity.current
    val terminalState = rememberTabbedTerminalState()

    LaunchedEffect(terminalState, workingDirectory) {
        snapshotFlow { terminalState.isInitialized && terminalState.activeTabId != null }
            .filter { it }
            .first()
        terminalState.write("claude\r")
    }

    Column(modifier = modifier.fillMaxSize()) {
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
            Box(modifier = Modifier.weight(1f))
            Button(onClick = { showDiffPanel = !showDiffPanel }) {
                Text(if (showDiffPanel) "Hide Diff" else "Show Diff")
            }
            Text(
                "Claude Code (Terminal)",
                fontSize = 12.sp,
                color = FluentTheme.colors.text.text.secondary,
            )
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = if (showDiffPanel) 0.dp else 12.dp, bottom = 12.dp),
            ) {
                TabbedTerminal(
                    state = terminalState,
                    onExit = onBack,
                    workingDirectory = workingDirectory,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (showDiffPanel) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight()
                        .padding(vertical = 8.dp)
                        .background(FluentTheme.colors.stroke.divider.default)
                        .pointerHoverIcon(PointerIcon.Crosshair)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = { onDiffPanelWidthChanged(diffWidth.value) },
                            ) { change, dragAmount ->
                                change.consume()
                                val deltaDp = with(density) { -dragAmount.x.toDp() }
                                diffWidth = (diffWidth + deltaDp).coerceIn(DiffPanelMinWidth, DiffPanelMaxWidth)
                            }
                        },
                )
                Box(
                    modifier = Modifier
                        .width(diffWidth)
                        .fillMaxHeight()
                        .padding(end = 12.dp, top = 8.dp, bottom = 12.dp),
                ) {
                    DiffPanel(
                        onGetChangedFiles = onGetChangedFiles,
                        onGetFileDiff = onGetFileDiff,
                        refreshTrigger = 0,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
