package com.oss.vibemanager

import com.oss.vibemanager.model.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun conversationRoundTrips() {
        val conversation = PersistedConversation(
            sessionId = "session-123",
            model = "claude-opus-4-7",
            messages = listOf(
                ConversationMessage(
                    id = "user-1",
                    role = MessageRole.User,
                    blocks = listOf(ContentBlock.Text("Hello")),
                    timestamp = 1700000000000L,
                ),
                ConversationMessage(
                    id = "assistant-1",
                    role = MessageRole.Assistant,
                    blocks = listOf(
                        ContentBlock.Thinking("Let me think..."),
                        ContentBlock.Text("Hi there!"),
                        ContentBlock.ToolUse("tool-1", "Read", """{"file_path":"test.kt"}""", ToolStatus.Completed),
                        ContentBlock.ToolResult("tool-1", "file contents here", false),
                    ),
                    timestamp = 1700000001000L,
                ),
            ),
            totalCostUsd = 0.05,
        )
        val encoded = json.encodeToString(PersistedConversation.serializer(), conversation)
        println("Serialized conversation:\n$encoded")
        val decoded = json.decodeFromString(PersistedConversation.serializer(), encoded)
        assertEquals(conversation, decoded)
        assertTrue(encoded.contains("Hello"))
        assertTrue(encoded.contains("claude-opus-4-7"))
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
