package com.oss.vibemanager.agents

class CodexLauncher : AgentLauncher {
    override val kind = AgentKind.Codex
    override val displayName = "codex"

    override fun resolveLaunchSpec(): AgentLaunchSpec? {
        // Codex itself doesn't speak ACP — the @zed-industries/codex-acp adapter does.
        // Prefer a directly-installed codex-acp binary; otherwise run the npm package
        // through Bun (already a required dependency of this app).
        whichExecutable("codex-acp")?.let {
            return AgentLaunchSpec(command = listOf(it))
        }
        val bun = whichExecutable("bun") ?: return null
        return AgentLaunchSpec(command = listOf(bun, "x", "@zed-industries/codex-acp"))
    }
}
