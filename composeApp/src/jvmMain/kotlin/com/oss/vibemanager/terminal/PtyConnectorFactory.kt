package com.oss.vibemanager.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.ProcessTtyConnector
import com.oss.vibemanager.model.ShellType
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import com.pty4j.WinSize
import java.nio.charset.StandardCharsets

class PtyConnectorFactory {
    fun createConnector(
        workingDir: String,
        shellType: ShellType = ShellType.Cmd,
        gitBashPath: String? = null,
    ): PtyProcessTtyConnector {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val cmd = if (isWindows) {
            when (shellType) {
                ShellType.GitBash -> {
                    val bash = gitBashPath ?: "C:/Program Files/Git/bin/bash.exe"
                    arrayOf(bash, "--login")
                }
                ShellType.Cmd -> arrayOf("cmd.exe")
            }
        } else {
            arrayOf("/bin/bash", "--login")
        }

        val env = System.getenv().toMutableMap()
        env["TERM"] = "xterm-256color"

        val process = PtyProcessBuilder()
            .setCommand(cmd)
            .setDirectory(workingDir)
            .setEnvironment(env)
            .setConsole(false)
            .setInitialColumns(120)
            .setInitialRows(40)
            .start()

        return PtyProcessTtyConnector(process)
    }
}

class PtyProcessTtyConnector(
    private val ptyProcess: PtyProcess,
) : ProcessTtyConnector(ptyProcess, StandardCharsets.UTF_8) {

    @Volatile
    var lastOutputTime: Long = System.currentTimeMillis()
        private set

    override fun getName(): String = "Claude Code"

    override fun read(buf: CharArray, offset: Int, length: Int): Int {
        val n = super.read(buf, offset, length)
        if (n > 0) {
            lastOutputTime = System.currentTimeMillis()
        }
        return n
    }

    override fun resize(termSize: TermSize) {
        ptyProcess.winSize = WinSize(termSize.columns, termSize.rows)
        super.resize(termSize)
    }

    fun sendCommand(command: String) {
        write(command.toByteArray(StandardCharsets.UTF_8))
    }
}
