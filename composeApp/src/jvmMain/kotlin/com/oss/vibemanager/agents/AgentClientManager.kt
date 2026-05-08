package com.oss.vibemanager.agents

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

/**
 * Manages one ACP client connection per agent kind. Each agent's process is
 * spawned lazily on first use; subsequent sessions for the same agent reuse it.
 */
class AgentClientManager(
    private val scope: CoroutineScope,
) {
    private data class AgentRuntime(
        val process: Process,
        val protocol: Protocol,
        val client: Client,
    )

    private val runtimes = mutableMapOf<AgentKind, AgentRuntime>()
    private val mutex = Mutex()

    suspend fun getClient(kind: AgentKind): Client = mutex.withLock {
        runtimes[kind]?.client?.let { return@withLock it }

        val launcher = AgentRegistry.launcher(kind)
        val spec = launcher.resolveLaunchSpec()
            ?: throw IllegalStateException("Agent ${launcher.displayName} not found on this system")

        val pb = ProcessBuilder(spec.command).redirectErrorStream(false)
        spec.env.forEach { (k, v) -> pb.environment()[k] = v }
        System.err.println("[AgentClient] Starting ${launcher.displayName}: ${spec.command.joinToString(" ")}")
        val proc = pb.start()

        Thread({
            try {
                proc.errorStream.bufferedReader().forEachLine { line ->
                    System.err.println("[${launcher.displayName}:stderr] $line")
                }
            } catch (_: Exception) {}
        }, "${launcher.displayName}-stderr").apply { isDaemon = true; start() }

        val input = proc.inputStream.asSource().buffered()
        val output = proc.outputStream.asSink().buffered()
        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = input,
            output = output,
        )
        val proto = Protocol(scope, transport)
        val acpClient = Client(proto)
        proto.start()

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
        System.err.println("[AgentClient] ${launcher.displayName} client initialized")

        runtimes[kind] = AgentRuntime(proc, proto, acpClient)
        acpClient
    }

    suspend fun shutdown() {
        mutex.withLock {
            for ((_, rt) in runtimes) {
                try { rt.protocol.close() } catch (_: Exception) {}
                try {
                    rt.process.descendants().forEach { it.destroyForcibly() }
                    rt.process.destroyForcibly()
                } catch (_: Exception) {}
            }
            runtimes.clear()
        }
    }
}
