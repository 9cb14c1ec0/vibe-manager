package com.oss.vibemanager.agents

import java.io.File

class ClaudeLauncher(private val bridgePath: String) : AgentLauncher {
    override val kind = AgentKind.Claude
    override val displayName = "Claude Code"

    override fun resolveLaunchSpec(): AgentLaunchSpec? {
        if (!File(bridgePath).let { it.isFile || bridgePath == "acp-bridge" }) return null
        val claudePath = findClaudeExecutable()
        val env = if (claudePath != null) mapOf("CLAUDE_CODE_EXECUTABLE" to claudePath) else emptyMap()
        return AgentLaunchSpec(command = listOf(bridgePath), env = env)
    }

    private fun findClaudeExecutable(): String? {
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val pathHit = whichExecutable("claude")
        if (pathHit != null) return pathHit

        val home = System.getProperty("user.home")
        val candidates = if (isWindows) emptyList() else listOf("$home/.claude/local/claude")
        return candidates.firstOrNull { File(it).exists() }
    }
}
