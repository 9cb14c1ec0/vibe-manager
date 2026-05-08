package com.oss.vibemanager.agents

import java.io.File

enum class AgentKind { Claude, OpenCode, Codex }

fun parseAgentKind(value: String?): AgentKind = when (value?.lowercase()) {
    "opencode" -> AgentKind.OpenCode
    "codex" -> AgentKind.Codex
    else -> AgentKind.Claude
}

data class AgentLaunchSpec(
    val command: List<String>,
    val env: Map<String, String> = emptyMap(),
)

interface AgentLauncher {
    val kind: AgentKind
    val displayName: String
    fun resolveLaunchSpec(): AgentLaunchSpec?
}

object AgentRegistry {
    private var bridgePath: String = "acp-bridge"
    private val launchers = mutableMapOf<AgentKind, AgentLauncher>()

    fun configure(bridgePath: String) {
        this.bridgePath = bridgePath
        launchers.clear()
        register(ClaudeLauncher(bridgePath))
        register(OpenCodeLauncher())
        register(CodexLauncher())
    }

    private fun register(launcher: AgentLauncher) {
        launchers[launcher.kind] = launcher
    }

    fun launcher(kind: AgentKind): AgentLauncher =
        launchers[kind] ?: error("No launcher registered for $kind")

    fun availableKinds(): List<AgentKind> =
        AgentKind.values().filter { launchers[it]?.resolveLaunchSpec() != null }

    fun displayName(kind: AgentKind): String = launchers[kind]?.displayName ?: kind.name
}

internal fun whichExecutable(name: String): String? {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val cmd = if (isWindows) listOf("where", name) else listOf("which", name)
    val pathHit = runCatching {
        ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readLine()?.trim()
    }.getOrNull()
    if (!pathHit.isNullOrBlank() && File(pathHit).exists()) return pathHit

    val candidates = mutableListOf<String>()
    if (isWindows) {
        System.getenv("LOCALAPPDATA")?.let { candidates.add("$it\\Programs\\$name\\$name.exe") }
        System.getenv("APPDATA")?.let { candidates.add("$it\\npm\\$name.cmd") }
    } else {
        val home = System.getProperty("user.home")
        candidates += listOf(
            "$home/.local/bin/$name",
            "$home/.npm-global/bin/$name",
            "$home/.bun/bin/$name",
            "$home/.cargo/bin/$name",
            "/usr/local/bin/$name",
            "/opt/homebrew/bin/$name",
        )
    }
    return candidates.firstOrNull { File(it).exists() }
}
