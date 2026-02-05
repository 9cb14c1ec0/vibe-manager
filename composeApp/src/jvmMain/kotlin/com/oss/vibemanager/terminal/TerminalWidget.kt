package com.oss.vibemanager.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import java.awt.BorderLayout

@Composable
fun TerminalWidget(
    taskId: String,
    workingDir: String,
    resume: Boolean,
    sessionManager: TerminalSessionManager,
    onProcessExit: () -> Unit,
    launchClaude: Boolean = true,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val session = remember(taskId) {
        sessionManager.getOrCreate(taskId, workingDir, resume, launchClaude, onProcessExit)
    }

    SwingPanel(
        modifier = modifier,
        factory = {
            val wrapper = JPanel(BorderLayout())
            wrapper.add(session.panel, BorderLayout.CENTER)
            wrapper
        },
        update = {
            if (isActive) {
                SwingUtilities.invokeLater {
                    session.widget.requestFocusInWindow()
                }
            }
        },
    )
}
