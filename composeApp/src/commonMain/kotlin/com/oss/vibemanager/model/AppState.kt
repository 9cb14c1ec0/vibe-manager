package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
data class AppState(
    val projects: List<Project> = emptyList(),
    val tasks: List<Task> = emptyList(),
)
