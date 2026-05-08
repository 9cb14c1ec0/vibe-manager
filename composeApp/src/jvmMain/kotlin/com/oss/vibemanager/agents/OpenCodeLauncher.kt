package com.oss.vibemanager.agents

class OpenCodeLauncher : AgentLauncher {
    override val kind = AgentKind.OpenCode
    override val displayName = "opencode"

    override fun resolveLaunchSpec(): AgentLaunchSpec? {
        val exe = whichExecutable("opencode") ?: return null
        return AgentLaunchSpec(command = listOf(exe, "acp"))
    }
}
