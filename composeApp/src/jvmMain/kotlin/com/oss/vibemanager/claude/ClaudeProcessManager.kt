package com.oss.vibemanager.claude

import java.io.File

class ClaudeProcessManager {
    private val activeProcesses = mutableMapOf<String, Process>()

    /**
     * Starts a new Claude CLI turn. Each call spawns a new process.
     * For the first turn, use isResume=false (uses --session-id).
     * For subsequent turns, use isResume=true (uses --resume).
     */
    fun startTurn(
        sessionId: String,
        prompt: String,
        workDir: String,
        isResume: Boolean,
        permissionMode: String = "acceptEdits",
        model: String = "",
    ): Process {
        // Kill any existing process for this session
        killProcess(sessionId)

        val command = buildList {
            add("claude")
            add("-p")
            add("--output-format")
            add("stream-json")
            add("--verbose")
            add("--include-partial-messages")
            if (isResume) {
                add("--resume")
                add(sessionId)
            } else {
                add("--session-id")
                add(sessionId)
            }
            add("--permission-mode")
            add(permissionMode)
            if (model.isNotEmpty()) {
                add("--model")
                add(model)
            }
            add(prompt)
        }

        val processBuilder = ProcessBuilder(command)
            .directory(File(workDir))
            .redirectErrorStream(false)

        val process = processBuilder.start()
        activeProcesses[sessionId] = process
        return process
    }

    fun killProcess(sessionId: String) {
        activeProcesses.remove(sessionId)?.let { process ->
            try {
                process.descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            } catch (_: Exception) {}
        }
    }

    fun isRunning(sessionId: String): Boolean {
        return activeProcesses[sessionId]?.isAlive == true
    }

    fun disposeAll() {
        activeProcesses.keys.toList().forEach { killProcess(it) }
    }
}
