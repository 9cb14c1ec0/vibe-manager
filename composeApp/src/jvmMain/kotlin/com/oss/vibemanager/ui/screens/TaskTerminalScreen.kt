package com.oss.vibemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.composefluent.component.Button
import io.github.composefluent.component.Text
import com.oss.vibemanager.model.ShellType
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.model.TaskState
import com.oss.vibemanager.terminal.TerminalSessionManager
import com.oss.vibemanager.terminal.TerminalWidget
import com.oss.vibemanager.ui.components.TaskStatusBadge

@Composable
fun TaskTerminalScreen(
    task: Task,
    sessionManager: TerminalSessionManager,
    onBack: () -> Unit,
    onProcessExit: () -> Unit,
    onClaudeSessionStarted: () -> Unit = {},
    shellType: ShellType = ShellType.Cmd,
    gitBashPath: String? = null,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var resetCounter by remember(task.id) { mutableIntStateOf(0) }
    var launchClaude by remember(task.id) { mutableStateOf(true) }

    LaunchedEffect(task.id) {
        if (!task.claudeSessionStarted) {
            onClaudeSessionStarted()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onBack) { Text("Back") }
            Text(
                task.name,
                fontSize = 18.sp,
                modifier = Modifier.padding(start = 4.dp).weight(1f),
            )
            TaskStatusBadge(task.state)
            Button(onClick = {
                sessionManager.dispose(task.id)
                launchClaude = false
                resetCounter++
            }) { Text("Shell") }
            Button(onClick = {
                sessionManager.dispose(task.id)
                launchClaude = true
                resetCounter++
            }) { Text("Reset") }
        }
        key(resetCounter) {
            TerminalWidget(
                taskId = task.id,
                workingDir = task.worktreePath,
                resume = task.claudeSessionStarted || resetCounter > 0,
                claudeSessionId = task.claudeSessionId,
                sessionManager = sessionManager,
                onProcessExit = onProcessExit,
                launchClaude = launchClaude,
                shellType = shellType,
                gitBashPath = gitBashPath,
                isActive = isActive,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
