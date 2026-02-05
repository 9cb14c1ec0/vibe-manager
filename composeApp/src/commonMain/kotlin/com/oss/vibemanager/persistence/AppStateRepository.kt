package com.oss.vibemanager.persistence

import com.oss.vibemanager.model.AppState
import kotlinx.serialization.json.Json

class AppStateRepository(
    private val fileOps: FileOperations,
    private val stateDir: String,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val stateFile get() = "$stateDir/state.json"

    fun load(): AppState {
        fileOps.ensureDirectory(stateDir)
        if (!fileOps.exists(stateFile)) return AppState()
        val text = fileOps.readText(stateFile) ?: return AppState()
        return try {
            json.decodeFromString<AppState>(text)
        } catch (_: Exception) {
            AppState()
        }
    }

    fun save(state: AppState) {
        fileOps.ensureDirectory(stateDir)
        fileOps.writeText(stateFile, json.encodeToString(AppState.serializer(), state))
    }
}
