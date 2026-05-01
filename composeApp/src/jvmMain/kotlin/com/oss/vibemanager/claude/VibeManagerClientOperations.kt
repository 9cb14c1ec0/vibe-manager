package com.oss.vibemanager.claude

import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.model.*
import kotlinx.serialization.json.JsonElement

/**
 * Implements ACP ClientSessionOperations for Vibe Manager.
 *
 * Handles permission requests by delegating to the UI via a callback,
 * and stubs out file system / terminal operations (Claude Code handles those internally).
 */
class VibeManagerClientOperations(
    private val onPermissionRequest: suspend (
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ) -> RequestPermissionResponse,
) : ClientSessionOperations {

    override suspend fun requestPermissions(
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        _meta: JsonElement?,
    ): RequestPermissionResponse {
        return onPermissionRequest(toolCall, permissions)
    }

    override suspend fun notify(
        notification: SessionUpdate,
        _meta: JsonElement?,
    ) {
        // Session update notifications are handled via the prompt() Flow
    }

    // --- File system operations (not needed; Claude Code handles these) ---

    override suspend fun fsReadTextFile(
        path: String,
        line: UInt?,
        limit: UInt?,
        _meta: JsonElement?,
    ): ReadTextFileResponse {
        return ReadTextFileResponse(content = "", _meta = null)
    }

    override suspend fun fsWriteTextFile(
        path: String,
        content: String,
        _meta: JsonElement?,
    ): WriteTextFileResponse {
        return WriteTextFileResponse(_meta = null)
    }

    // --- Terminal operations (Claude Code handles these internally) ---

    override suspend fun terminalCreate(
        command: String,
        args: List<String>,
        cwd: String?,
        env: List<EnvVariable>,
        outputByteLimit: ULong?,
        _meta: JsonElement?,
    ): CreateTerminalResponse {
        return CreateTerminalResponse(terminalId = "", _meta = null)
    }

    override suspend fun terminalOutput(
        terminalId: String,
        _meta: JsonElement?,
    ): TerminalOutputResponse {
        return TerminalOutputResponse(output = "", truncated = false, _meta = null)
    }

    override suspend fun terminalRelease(
        terminalId: String,
        _meta: JsonElement?,
    ): ReleaseTerminalResponse {
        return ReleaseTerminalResponse(_meta = null)
    }

    override suspend fun terminalWaitForExit(
        terminalId: String,
        _meta: JsonElement?,
    ): WaitForTerminalExitResponse {
        return WaitForTerminalExitResponse(exitCode = 0u, _meta = null)
    }

    override suspend fun terminalKill(
        terminalId: String,
        _meta: JsonElement?,
    ): KillTerminalCommandResponse {
        return KillTerminalCommandResponse(_meta = null)
    }
}
