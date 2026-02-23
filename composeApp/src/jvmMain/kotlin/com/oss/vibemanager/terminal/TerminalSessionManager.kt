package com.oss.vibemanager.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.ui.JediTermWidget
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import com.oss.vibemanager.model.ShellType
import javax.swing.JPanel
import java.awt.BorderLayout
import kotlin.concurrent.thread

private class DarkSettingsProvider : DefaultSettingsProvider() {
    override fun getDefaultForeground(): TerminalColor = TerminalColor.rgb(204, 204, 204)
    override fun getDefaultBackground(): TerminalColor = TerminalColor.rgb(30, 30, 30)
    override fun getDefaultStyle(): TextStyle = TextStyle(defaultForeground, defaultBackground)
    override fun useAntialiasing(): Boolean = true
    override fun scrollToBottomOnTyping(): Boolean = true

    override fun getTerminalFont(): java.awt.Font {
        val preferred = listOf("Cascadia Mono", "Cascadia Code", "Consolas", "Courier New")
        val available = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        val family = preferred.firstOrNull { it in available } ?: java.awt.Font.MONOSPACED
        return java.awt.Font(family, java.awt.Font.PLAIN, getTerminalFontSize().toInt())
    }
}

data class TerminalSession(
    val panel: JPanel,
    val widget: JediTermWidget,
    val connector: PtyProcessTtyConnector,
)

class TerminalSessionManager {
    private val sessions = mutableMapOf<String, TerminalSession>()
    private val factory = PtyConnectorFactory()

    fun getOrCreate(
        taskId: String,
        workingDir: String,
        resume: Boolean,
        claudeSessionId: String = "",
        launchClaude: Boolean = true,
        shellType: ShellType = ShellType.Cmd,
        gitBashPath: String? = null,
        onProcessExit: () -> Unit,
    ): TerminalSession {
        // If session exists and is still alive, reuse it
        sessions[taskId]?.let { existing ->
            if (existing.connector.isConnected) return existing
            // Dead session — clean it up
            sessions.remove(taskId)
        }

        val settings = DarkSettingsProvider()
        val widget = JediTermWidget(120, 40, settings)
        val connector = factory.createConnector(workingDir, shellType, gitBashPath)
        widget.setTtyConnector(connector)
        widget.start()

        if (launchClaude) {
            val claudeCmd = if (claudeSessionId.isNotEmpty()) {
                if (resume) "claude --resume $claudeSessionId\r"
                else "claude --session-id $claudeSessionId\r"
            } else {
                if (resume) "claude --continue\r" else "claude\r"
            }
            thread(isDaemon = true) {
                Thread.sleep(500)
                connector.sendCommand(claudeCmd)
            }
        }

        thread(isDaemon = true) {
            try {
                connector.waitFor()
            } catch (_: Exception) {}
            onProcessExit()
        }

        val panel = JPanel(BorderLayout())
        panel.add(widget, BorderLayout.CENTER)

        val session = TerminalSession(panel, widget, connector)
        sessions[taskId] = session
        return session
    }

    fun dispose(taskId: String) {
        val session = sessions.remove(taskId) ?: return
        try {
            session.connector.close()
        } catch (_: Exception) {}
    }

    fun isIdle(taskId: String, thresholdMs: Long = 3000): Boolean {
        val session = sessions[taskId] ?: return false
        if (!session.connector.isConnected) return false
        return System.currentTimeMillis() - session.connector.lastOutputTime > thresholdMs
    }

    fun disposeAll() {
        sessions.keys.toList().forEach { dispose(it) }
    }
}
