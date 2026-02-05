package com.oss.vibemanager

import com.oss.vibemanager.model.AppState
import com.oss.vibemanager.model.Project
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.model.TaskState
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelSerializationTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun projectRoundTrips() {
        val project = Project(
            id = "test-id",
            name = "my-repo",
            repoPath = "/home/user/my-repo",
            addedAt = 1700000000000L,
        )
        val encoded = json.encodeToString(Project.serializer(), project)
        val decoded = json.decodeFromString(Project.serializer(), encoded)
        assertEquals(project, decoded)
    }

    @Test
    fun taskRoundTrips() {
        val task = Task(
            id = "task-id",
            projectId = "proj-id",
            name = "Add feature",
            branchName = "add-feature",
            worktreePath = "/tmp/worktree",
            state = TaskState.Running,
            createdAt = 1700000000000L,
        )
        val encoded = json.encodeToString(Task.serializer(), task)
        val decoded = json.decodeFromString(Task.serializer(), encoded)
        assertEquals(task, decoded)
    }

    @Test
    fun appStateRoundTrips() {
        val state = AppState(
            projects = listOf(
                Project("p1", "repo1", "/path/repo1", 1700000000000L),
            ),
            tasks = listOf(
                Task("t1", "p1", "Task 1", "task-1", "/tmp/wt1", TaskState.Created, 1700000000000L),
            ),
        )
        val encoded = json.encodeToString(AppState.serializer(), state)
        val decoded = json.decodeFromString(AppState.serializer(), encoded)
        assertEquals(state, decoded)
    }
}
