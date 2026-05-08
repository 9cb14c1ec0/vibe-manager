package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val permissionMode: String = "acceptEdits",
    val model: String = "",
    val themeMode: String = "light",
    val diffPanelWidth: Float = 400f,
    val terminalPanelHeight: Float = 280f,
    val defaultAgentKind: String = "Claude",
)
