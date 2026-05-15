package com.oss.vibemanager.agents

import java.io.File

class ClaudeTerminalLauncher : AgentLauncher {
    override val kind = AgentKind.ClaudeTerminal
    override val displayName = "Claude Code (Terminal)"

    override fun resolveLaunchSpec(): AgentLaunchSpec? {
        val claudePath = findClaudeExecutable() ?: return null
        return AgentLaunchSpec(command = listOf(claudePath))
    }

    private fun findClaudeExecutable(): String? {
        whichExecutable("claude")?.let { return it }
        val home = System.getProperty("user.home")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val candidates = if (isWindows) emptyList() else listOf("$home/.claude/local/claude")
        return candidates.firstOrNull { File(it).exists() }
    }
}
