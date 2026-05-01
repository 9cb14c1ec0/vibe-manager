package com.oss.vibemanager.claude

import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.Implementation
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import java.io.File

/**
 * Manages the ACP bridge subprocess and provides an initialized ACP Client.
 *
 * The bridge binary (acp-bridge.exe) is a Bun-compiled standalone that wraps
 * @agentclientprotocol/claude-agent-acp, translating ACP JSON-RPC to Claude Code.
 */
class AcpBridgeManager(
    private val scope: CoroutineScope,
    private val bridgePath: String,
) {
    private var process: Process? = null
    private var client: Client? = null
    private var protocol: Protocol? = null
    private val mutex = Mutex()

    /**
     * Get or create the ACP Client. Spawns the bridge process if needed.
     */
    suspend fun getClient(): Client = mutex.withLock {
        client?.let { return@withLock it }

        // Find the claude executable
        val claudePath = findClaudeExecutable()

        // Spawn the bridge process
        val pb = ProcessBuilder(bridgePath)
            .redirectErrorStream(false)
        if (claudePath != null) {
            pb.environment()["CLAUDE_CODE_EXECUTABLE"] = claudePath
            System.err.println("[AcpBridge] Using Claude executable: $claudePath")
        } else {
            System.err.println("[AcpBridge] WARNING: Claude executable not found, bridge will try to find it")
        }
        System.err.println("[AcpBridge] Starting bridge process: $bridgePath")
        val proc = pb.start()
        process = proc

        // Read stderr from bridge in background for debugging
        Thread({
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    System.err.println("[AcpBridge:stderr] $line")
                }
            } catch (_: Exception) {}
        }, "acp-bridge-stderr").apply { isDaemon = true; start() }

        System.err.println("[AcpBridge] Bridge process started, PID: ${proc.pid()}")

        // Create transport from process stdin/stdout
        val input = proc.inputStream.asSource().buffered()
        val output = proc.outputStream.asSink().buffered()
        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = input,
            output = output,
        )

        // Create protocol and client
        val proto = Protocol(scope, transport)
        protocol = proto
        val acpClient = Client(proto)
        client = acpClient

        // Start the protocol (begins message processing)
        System.err.println("[AcpBridge] Starting protocol...")
        proto.start()
        System.err.println("[AcpBridge] Protocol started, initializing client...")

        // Initialize the client
        acpClient.initialize(
            ClientInfo(
                capabilities = ClientCapabilities(),
                implementation = Implementation(
                    name = "Vibe Manager",
                    version = "1.0.0",
                ),
            ),
            _meta = null,
        )
        System.err.println("[AcpBridge] Client initialized successfully!")

        acpClient
    }

    private fun findClaudeExecutable(): String? {
        // Try common locations
        val candidates = listOf(
            // Check PATH via 'where' on Windows
            runCatching {
                ProcessBuilder("where", "claude")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream.bufferedReader().readLine()?.trim()
            }.getOrNull(),
            // Common install locations
            System.getenv("LOCALAPPDATA")?.let { "$it\\Programs\\claude\\claude.exe" },
            System.getenv("APPDATA")?.let { "$it\\npm\\claude.cmd" },
        )

        return candidates.firstOrNull { path ->
            path != null && File(path).exists()
        }
    }

    suspend fun shutdown() {
        mutex.withLock {
            try {
                protocol?.close()
            } catch (_: Exception) {}
            try {
                process?.let { proc ->
                    proc.descendants().forEach { it.destroyForcibly() }
                    proc.destroyForcibly()
                }
            } catch (_: Exception) {}
            client = null
            protocol = null
            process = null
        }
    }
}
