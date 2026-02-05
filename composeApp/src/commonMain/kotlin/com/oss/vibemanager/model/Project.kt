package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
data class Project(
    val id: String,
    val name: String,
    val repoPath: String,
    val addedAt: Long,
)
