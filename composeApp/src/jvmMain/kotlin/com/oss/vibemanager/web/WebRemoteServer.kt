package com.oss.vibemanager.web

import com.oss.vibemanager.agents.AgentSessionManager
import com.oss.vibemanager.viewmodel.AppViewModel
import com.oss.vibemanager.viewmodel.PlatformOperations
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class WebRemoteServer(
    private val viewModel: AppViewModel,
    private val sessionManager: AgentSessionManager,
    private val platform: PlatformOperations,
) {
    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var currentPort: Int = -1
    private var currentToken: String = ""

    @Synchronized
    fun start(port: Int, token: String) {
        if (engine != null && currentPort == port && currentToken == token) return
        stop()
        if (token.isBlank()) {
            System.err.println("[WebRemote] Refusing to start: empty token")
            return
        }
        try {
            val server = embeddedServer(
                Netty,
                port = port,
                host = "0.0.0.0",
            ) {
                webRemoteModule(viewModel, sessionManager, platform, tokenProvider = { currentToken })
            }
            server.start(wait = false)
            engine = server
            currentPort = port
            currentToken = token
            System.err.println("[WebRemote] Listening on http://0.0.0.0:$port (LAN: ${detectLanAddress() ?: "?"})")
        } catch (e: Exception) {
            System.err.println("[WebRemote] Failed to start on port $port: ${e.message}")
            engine = null
            currentPort = -1
            currentToken = ""
        }
    }

    @Synchronized
    fun stop() {
        engine?.let {
            try {
                it.stop(gracePeriodMillis = 200, timeoutMillis = 1500)
                System.err.println("[WebRemote] Stopped")
            } catch (e: Exception) {
                System.err.println("[WebRemote] Error stopping: ${e.message}")
            }
        }
        engine = null
        currentPort = -1
        currentToken = ""
    }

    fun bind(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) {
        viewModel.appState
            .map { Triple(it.webRemoteEnabled, it.webRemotePort, it.webRemoteToken) }
            .distinctUntilChanged()
            .collect { (enabled, port, token) ->
                if (enabled && token.isNotBlank()) {
                    start(port, token)
                } else {
                    stop()
                }
            }
    }

    companion object {
        fun detectLanAddress(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces().asSequence()
                    .filter { it.isUp && !it.isLoopback && !it.isVirtual }
                    .flatMap { it.inetAddresses.asSequence() }
                    .filterIsInstance<Inet4Address>()
                    .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
