package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val permissionMode: String = "acceptEdits",
    val model: String = "claude-opus-4-7",
    val themeMode: String = "light",
    val diffPanelWidth: Float = 400f,
    val terminalPanelHeight: Float = 280f,
)
