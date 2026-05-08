package com.oss.vibemanager.persistence

import com.oss.vibemanager.model.AppState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
            val raw = json.parseToJsonElement(text)
            val migrated = migrate(raw)
            json.decodeFromJsonElement(AppState.serializer(), migrated)
        } catch (_: Exception) {
            AppState()
        }
    }

    fun save(state: AppState) {
        fileOps.ensureDirectory(stateDir)
        fileOps.writeText(stateFile, json.encodeToString(AppState.serializer(), state))
    }

    /**
     * Rename pre-multi-agent task fields:
     *   claudeSessionId      -> agentSessionId
     *   claudeSessionStarted -> agentSessionStarted
     * Default agentKind to "Claude" for any task missing it.
     */
    private fun migrate(root: JsonElement): JsonElement {
        if (root !is JsonObject) return root
        val tasks = root["tasks"] as? JsonArray ?: return root
        val migratedTasks = buildJsonArray {
            for (task in tasks) {
                if (task !is JsonObject) { add(task); continue }
                val needsMigration = "claudeSessionId" in task || "claudeSessionStarted" in task ||
                    "agentKind" !in task
                if (!needsMigration) { add(task); continue }
                add(buildJsonObject {
                    for ((k, v) in task) {
                        when (k) {
                            "claudeSessionId" -> put("agentSessionId", v)
                            "claudeSessionStarted" -> put("agentSessionStarted", v)
                            else -> put(k, v)
                        }
                    }
                    if ("agentKind" !in task) put("agentKind", JsonPrimitive("Claude"))
                })
            }
        }
        return buildJsonObject {
            for ((k, v) in root) {
                if (k == "tasks") put(k, migratedTasks) else put(k, v)
            }
        }
    }
}
