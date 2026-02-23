package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class ShellType {
    Cmd,
    GitBash,
}

@Serializable
data class AppState(
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val shellType: ShellType = ShellType.Cmd,
)
