package com.oss.vibemanager.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskState { Created, Running, Completed }

@Serializable
data class Task(
    val id: String,
    val projectId: String,
    val name: String,
    val branchName: String,
    val worktreePath: String,
    val state: TaskState = TaskState.Created,
    val createdAt: Long,
    val agentKind: String = "Claude",
    val agentSessionId: String = "",
    val agentSessionStarted: Boolean = false,
    val totalCostUsd: Double = 0.0,
)
