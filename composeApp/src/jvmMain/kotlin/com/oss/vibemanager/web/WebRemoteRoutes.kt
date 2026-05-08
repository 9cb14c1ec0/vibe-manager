package com.oss.vibemanager.web

import com.oss.vibemanager.agents.AgentSessionManager
import com.oss.vibemanager.agents.parseAgentKind
import com.oss.vibemanager.viewmodel.AppViewModel
import com.oss.vibemanager.viewmodel.PlatformOperations
import io.ktor.http.CacheControl
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.html.respondHtml
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.cacheControl
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeStringUtf8
import java.security.MessageDigest

fun Application.webRemoteModule(
    viewModel: AppViewModel,
    sessionManager: AgentSessionManager,
    platform: PlatformOperations,
    tokenProvider: () -> String,
) {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            System.err.println("[WebRemote] Unhandled error on ${call.request.local.uri}: ${cause.message}")
            cause.printStackTrace(System.err)
            call.respondText("Server error: ${cause.message}", status = HttpStatusCode.InternalServerError)
        }
    }

    routing {
        staticResources("/static", "web")

        get("/auth") {
            val supplied = call.parameters["token"]
            if (supplied == null || !tokensEqual(supplied, tokenProvider())) {
                call.respondHtml(HttpStatusCode.Unauthorized) { renderUnauthorized(this) }
                return@get
            }
            call.response.cookies.append(
                Cookie(
                    name = TOKEN_COOKIE,
                    value = supplied,
                    path = "/",
                    httpOnly = true,
                    maxAge = 60 * 60 * 24 * 30,
                )
            )
            call.respondRedirect("/")
        }

        // Auth gate for everything below.
        get("{...}") {
            if (!authorize(call, tokenProvider())) {
                call.respondHtml(HttpStatusCode.Unauthorized) { renderUnauthorized(this) }
                return@get
            }
            handleGet(call, viewModel, sessionManager)
        }

        post("{...}") {
            if (!authorize(call, tokenProvider())) {
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                return@post
            }
            handlePost(call, viewModel, sessionManager, platform)
        }

        delete("{...}") {
            if (!authorize(call, tokenProvider())) {
                call.respondText("Unauthorized", status = HttpStatusCode.Unauthorized)
                return@delete
            }
            handleDelete(call, viewModel, sessionManager)
        }
    }
}

private const val TOKEN_COOKIE = "vm_token"

private fun authorize(call: ApplicationCall, token: String): Boolean {
    if (token.isBlank()) return false
    val auth = call.request.headers[HttpHeaders.Authorization]
    if (auth != null && auth.startsWith("Bearer ", ignoreCase = true)) {
        if (tokensEqual(auth.substring(7).trim(), token)) return true
    }
    val cookie = call.request.cookies[TOKEN_COOKIE]
    return cookie != null && tokensEqual(cookie, token)
}

private fun tokensEqual(a: String, b: String): Boolean {
    val ab = a.toByteArray(Charsets.UTF_8)
    val bb = b.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(ab, bb)
}

private suspend fun handleGet(
    call: ApplicationCall,
    viewModel: AppViewModel,
    sessionManager: AgentSessionManager,
) {
    val path = call.request.local.uri.substringBefore('?')
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }

    when {
        segments.isEmpty() -> {
            val state = viewModel.appState.value
            call.respondHtml { renderHome(this, state) }
        }
        segments.size == 2 && segments[0] == "projects" -> {
            val project = viewModel.appState.value.projects.find { it.id == segments[1] }
            if (project == null) {
                call.respondText("Project not found", status = HttpStatusCode.NotFound)
                return
            }
            val tasks = viewModel.getProjectTasks(project.id)
            val branchesResult = viewModel.listBranches(project.id)
            val remoteResult = viewModel.listRemoteBranches(project.id)
            call.respondHtml {
                renderProject(
                    this,
                    project = project,
                    tasks = tasks,
                    localBranches = branchesResult.getOrDefault(emptyList()),
                    remoteBranches = remoteResult.getOrDefault(emptyList()),
                    defaultAgentKind = viewModel.appState.value.defaultAgentKind,
                )
            }
        }
        segments.size == 2 && segments[0] == "tasks" -> {
            val task = viewModel.appState.value.tasks.find { it.id == segments[1] }
            if (task == null) {
                call.respondText("Task not found", status = HttpStatusCode.NotFound)
                return
            }
            sessionManager.prewarmSession(
                taskId = task.id,
                sessionId = task.agentSessionId,
                agentKind = parseAgentKind(task.agentKind),
                workDir = task.worktreePath,
            )
            val convoState = sessionManager.getConversationState(task.id, task.agentSessionId).value
            val permissionMode = viewModel.appState.value.permissionMode
            call.respondHtml { renderTask(this, task, convoState, permissionMode) }
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "stream" -> {
            val task = viewModel.appState.value.tasks.find { it.id == segments[1] }
            if (task == null) {
                call.respondText("Task not found", status = HttpStatusCode.NotFound)
                return
            }
            streamConversation(call, sessionManager, task.id, task.agentSessionId)
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "fragment" -> {
            val task = viewModel.appState.value.tasks.find { it.id == segments[1] }
            if (task == null) {
                call.respondText("Task not found", status = HttpStatusCode.NotFound)
                return
            }
            val convoState = sessionManager.getConversationState(task.id, task.agentSessionId).value
            call.respondText(
                renderConversationFragment(task.id, convoState),
                contentType = ContentType.Text.Html,
            )
        }
        else -> call.respondText("Not found: $path", status = HttpStatusCode.NotFound)
    }
}

private suspend fun streamConversation(
    call: ApplicationCall,
    sessionManager: AgentSessionManager,
    taskId: String,
    sessionId: String,
) {
    call.response.cacheControl(CacheControl.NoCache(null))
    call.response.headers.append("X-Accel-Buffering", "no")
    call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
        try {
            sessionManager.getConversationState(taskId, sessionId)
                .collect { state ->
                    val html = renderConversationFragment(taskId, state)
                    val payload = buildString {
                        append("event: update\n")
                        html.lines().forEach { append("data: ").append(it).append('\n') }
                        append('\n')
                    }
                    writeStringUtf8(payload)
                    flush()
                }
        } catch (_: Exception) {
            // Client disconnected or flow cancelled — exit cleanly.
        }
    }
}

private suspend fun handlePost(
    call: ApplicationCall,
    viewModel: AppViewModel,
    sessionManager: AgentSessionManager,
    platform: PlatformOperations,
) {
    val path = call.request.local.uri.substringBefore('?')
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }
    val params = call.receiveParameters()

    when {
        segments.size == 1 && segments[0] == "projects" -> {
            val repoPath = params["repoPath"]?.trim().orEmpty()
            if (repoPath.isBlank()) {
                call.respondText("Missing repoPath", status = HttpStatusCode.BadRequest)
                return
            }
            viewModel.addProject(repoPath)
            call.respondRedirect("/")
        }
        segments.size == 3 && segments[0] == "projects" && segments[2] == "tasks" -> {
            val projectId = segments[1]
            val name = params["name"]?.trim().orEmpty()
            val branchName = params["branchName"]?.trim().orEmpty()
            val agentKind = params["agentKind"]?.trim()?.takeIf { it.isNotEmpty() }
                ?: viewModel.appState.value.defaultAgentKind
            val fromExisting = params["fromExistingBranch"] == "true"
            if (name.isBlank() || branchName.isBlank()) {
                call.respondText("Missing name or branchName", status = HttpStatusCode.BadRequest)
                return
            }
            val ok = if (fromExisting) {
                viewModel.createTaskFromBranch(projectId, name, branchName, agentKind)
            } else {
                viewModel.createTask(projectId, name, branchName, agentKind)
            }
            if (!ok) {
                call.respondText(
                    viewModel.error.value ?: "Failed to create task",
                    status = HttpStatusCode.BadRequest,
                )
                viewModel.clearError()
                return
            }
            call.respondRedirect("/projects/$projectId")
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "messages" -> {
            val taskId = segments[1]
            val task = viewModel.appState.value.tasks.find { it.id == taskId }
            if (task == null) {
                call.respondText("Task not found", status = HttpStatusCode.NotFound)
                return
            }
            val prompt = params["prompt"]?.trim().orEmpty()
            if (prompt.isBlank()) {
                call.respondText("Empty prompt", status = HttpStatusCode.BadRequest)
                return
            }
            val app = viewModel.appState.value
            if (!task.agentSessionStarted) {
                viewModel.markAgentSessionStarted(task.id)
            }
            sessionManager.sendMessage(
                taskId = task.id,
                sessionId = task.agentSessionId,
                agentKind = parseAgentKind(task.agentKind),
                prompt = prompt,
                workDir = task.worktreePath,
                permissionMode = app.permissionMode,
                model = app.model,
                hasExistingSession = task.agentSessionStarted,
            )
            // Return an empty fragment — the SSE stream will deliver the update.
            call.respondText("", contentType = ContentType.Text.Html)
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "stop" -> {
            val taskId = segments[1]
            sessionManager.stopGeneration(taskId)
            call.respondText("", contentType = ContentType.Text.Html)
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "mode" -> {
            val mode = params["mode"]?.trim().orEmpty()
            if (mode !in listOf("plan", "acceptEdits")) {
                call.respondText("Invalid mode", status = HttpStatusCode.BadRequest)
                return
            }
            viewModel.setPermissionMode(mode)
            call.respondText("", contentType = ContentType.Text.Html)
        }
        segments.size == 3 && segments[0] == "tasks" && segments[2] == "permission" -> {
            val taskId = segments[1]
            val requestId = params["requestId"]?.trim().orEmpty()
            val optionId = params["optionId"]?.trim().orEmpty()
            if (requestId.isBlank() || optionId.isBlank()) {
                call.respondText("Missing requestId or optionId", status = HttpStatusCode.BadRequest)
                return
            }
            sessionManager.respondToPermission(taskId, requestId, optionId)
            call.respondText("", contentType = ContentType.Text.Html)
        }
        else -> call.respondText("Not found: $path", status = HttpStatusCode.NotFound)
    }
}

private suspend fun handleDelete(
    call: ApplicationCall,
    viewModel: AppViewModel,
    sessionManager: AgentSessionManager,
) {
    val path = call.request.local.uri.substringBefore('?')
    val segments = path.trim('/').split('/').filter { it.isNotBlank() }

    when {
        segments.size == 2 && segments[0] == "projects" -> {
            viewModel.removeProject(segments[1])
            call.respond(HttpStatusCode.NoContent)
        }
        segments.size == 2 && segments[0] == "tasks" -> {
            val taskId = segments[1]
            sessionManager.dispose(taskId)
            viewModel.deleteTask(taskId)
            call.respond(HttpStatusCode.NoContent)
        }
        else -> call.respondText("Not found: $path", status = HttpStatusCode.NotFound)
    }
}

