package com.oss.vibemanager.web

import com.oss.vibemanager.model.AppState
import com.oss.vibemanager.model.ContentBlock
import com.oss.vibemanager.model.ConversationMessage
import com.oss.vibemanager.model.ConversationState
import com.oss.vibemanager.model.MessageRole
import com.oss.vibemanager.model.PendingPermission
import com.oss.vibemanager.model.Project
import com.oss.vibemanager.model.SessionStatus
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.model.ToolStatus
import kotlinx.html.HTML
import kotlinx.html.a
import kotlinx.html.body
import kotlinx.html.button
import kotlinx.html.div
import kotlinx.html.form
import kotlinx.html.h1
import kotlinx.html.h2
import kotlinx.html.h3
import kotlinx.html.head
import kotlinx.html.id
import kotlinx.html.input
import kotlinx.html.label
import kotlinx.html.li
import kotlinx.html.link
import kotlinx.html.meta
import kotlinx.html.option
import kotlinx.html.p
import kotlinx.html.pre
import kotlinx.html.script
import kotlinx.html.select
import kotlinx.html.span
import kotlinx.html.style
import kotlinx.html.textArea
import kotlinx.html.title
import kotlinx.html.ul
import kotlinx.html.unsafe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val webJson = Json { ignoreUnknownKeys = true }

internal fun renderUnauthorized(html: HTML) {
    html.head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"Vibe Manager — Authorize" }
        link(rel = "stylesheet", href = "/static/app.css")
    }
    html.body {
        div("container") {
            h1 { +"Vibe Manager" }
            p { +"This URL requires a valid token. Append ?token=YOUR_TOKEN to the URL or paste it below." }
            form(action = "/auth", method = kotlinx.html.FormMethod.get) {
                input(type = kotlinx.html.InputType.text, name = "token") {
                    attributes["placeholder"] = "Token"
                    attributes["autofocus"] = "true"
                }
                button(type = kotlinx.html.ButtonType.submit) { +"Authorize" }
            }
        }
    }
}

internal fun renderHome(html: HTML, state: AppState) {
    html.head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"Vibe Manager" }
        link(rel = "stylesheet", href = "/static/app.css")
    }
    html.body {
        div("container") {
            h1 { +"Vibe Manager" }
            h2 { +"Projects" }
            if (state.projects.isEmpty()) {
                p("muted") { +"No projects yet. Add one below." }
            } else {
                ul("project-list") {
                    for (project in state.projects) {
                        li {
                            a(href = "/projects/${project.id}") { +project.name }
                            span("muted path") { +project.repoPath }
                            val taskCount = state.tasks.count { it.projectId == project.id }
                            span("muted") { +" — $taskCount task${if (taskCount == 1) "" else "s"}" }
                            button(classes = "danger small") {
                                attributes["data-method"] = "DELETE"
                                attributes["data-href"] = "/projects/${project.id}"
                                attributes["data-confirm"] = "Remove ${project.name} and all its worktrees?"
                                attributes["data-redirect"] = "/"
                                +"Remove"
                            }
                        }
                    }
                }
            }

            h3 { +"Add project" }
            form(action = "/projects", method = kotlinx.html.FormMethod.post) {
                input(type = kotlinx.html.InputType.text, name = "repoPath") {
                    attributes["placeholder"] = "Absolute path to a git repo"
                    attributes["required"] = "true"
                }
                button(type = kotlinx.html.ButtonType.submit) { +"Add" }
            }
        }
    }
}

internal fun renderProject(
    html: HTML,
    project: Project,
    tasks: List<Task>,
    localBranches: List<String>,
    remoteBranches: List<String>,
    defaultAgentKind: String,
) {
    html.head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"${project.name} — Vibe Manager" }
        link(rel = "stylesheet", href = "/static/app.css")
    }
    html.body {
        div("container") {
            p { a(href = "/") { +"← Projects" } }
            h1 { +project.name }
            p("muted path") { +project.repoPath }

            h2 { +"Tasks" }
            if (tasks.isEmpty()) {
                p("muted") { +"No tasks yet." }
            } else {
                ul("task-list") {
                    for (task in tasks) {
                        li {
                            a(href = "/tasks/${task.id}") { +task.name }
                            span("muted") { +" (${task.branchName})" }
                            button(classes = "danger small") {
                                attributes["data-method"] = "DELETE"
                                attributes["data-href"] = "/tasks/${task.id}"
                                attributes["data-confirm"] = "Delete task ${task.name} and remove its worktree?"
                                attributes["data-redirect"] = "/projects/${project.id}"
                                +"Delete"
                            }
                        }
                    }
                }
            }

            h3 { +"New task (new branch)" }
            form(action = "/projects/${project.id}/tasks", method = kotlinx.html.FormMethod.post) {
                input(type = kotlinx.html.InputType.text, name = "name") {
                    attributes["placeholder"] = "Task name"; attributes["required"] = "true"
                }
                input(type = kotlinx.html.InputType.text, name = "branchName") {
                    attributes["placeholder"] = "New branch name"; attributes["required"] = "true"
                }
                input(type = kotlinx.html.InputType.hidden, name = "agentKind") {
                    value = defaultAgentKind
                }
                button(type = kotlinx.html.ButtonType.submit) { +"Create" }
            }

            h3 { +"New task (existing branch)" }
            if (localBranches.isEmpty() && remoteBranches.isEmpty()) {
                p("muted") { +"No branches found." }
            } else {
                form(action = "/projects/${project.id}/tasks", method = kotlinx.html.FormMethod.post) {
                    input(type = kotlinx.html.InputType.text, name = "name") {
                        attributes["placeholder"] = "Task name"; attributes["required"] = "true"
                    }
                    select {
                        attributes["name"] = "branchName"
                        attributes["required"] = "true"
                        if (localBranches.isNotEmpty()) {
                            for (b in localBranches) option { +b }
                        }
                        if (remoteBranches.isNotEmpty()) {
                            for (b in remoteBranches) option { +b }
                        }
                    }
                    input(type = kotlinx.html.InputType.hidden, name = "fromExistingBranch") { value = "true" }
                    input(type = kotlinx.html.InputType.hidden, name = "agentKind") { value = defaultAgentKind }
                    button(type = kotlinx.html.ButtonType.submit) { +"Create" }
                }
            }
        }
    }
}

internal fun renderTask(html: HTML, task: Task, conversation: ConversationState, permissionMode: String) {
    html.head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"${task.name} — Vibe Manager" }
        link(rel = "stylesheet", href = "/static/app.css")
        script(src = "https://cdn.jsdelivr.net/npm/marked@12/marked.min.js") {
            attributes["defer"] = "true"
        }
    }
    html.body {
        div("container chat") {
            div("chat-header") {
                div("header-row") {
                    a(href = "/projects/${task.projectId}", classes = "back-link") { +"← Back" }
                    span("task-title") { +task.name }
                    span("muted header-info") { +"${task.branchName} · ${task.agentKind}" }
                    div("header-spacer") {}
                    div("segmented-control") {
                        button(classes = "seg-btn${if (permissionMode == "plan") " active" else ""}") {
                            attributes["type"] = "button"
                            attributes["data-action"] = "set-mode"
                            attributes["data-task-id"] = task.id
                            attributes["data-mode"] = "plan"
                            +"Plan"
                        }
                        button(classes = "seg-btn${if (permissionMode != "plan") " active" else ""}") {
                            attributes["type"] = "button"
                            attributes["data-action"] = "set-mode"
                            attributes["data-task-id"] = task.id
                            attributes["data-mode"] = "acceptEdits"
                            +"Build"
                        }
                    }
                }
            }
            div("conversation") {
                id = "conversation"
                attributes["data-task-id"] = task.id
                unsafe { +renderConversationFragment(task.id, conversation) }
            }
            form("compose") {
                attributes["data-action"] = "send"
                attributes["data-task-id"] = task.id
                textArea {
                    attributes["name"] = "prompt"
                    attributes["rows"] = "4"
                    attributes["placeholder"] = "Send a message…"
                }
                div("compose-row") {
                    span("input-hint") { +"Enter to send · Shift+Enter for new line" }
                    button(type = kotlinx.html.ButtonType.submit) { +"Send" }
                    button(classes = "secondary") {
                        attributes["type"] = "button"
                        attributes["data-action"] = "stop"
                        attributes["data-task-id"] = task.id
                        +"Stop"
                    }
                }
            }
        }
        script(src = "/static/app.js") {}
    }
}

// ─── Conversation fragment (rendered at page load & pushed via SSE) ──────────

internal fun renderConversationFragment(taskId: String, state: ConversationState): String {
    val sb = StringBuilder()

    // Sticky status bar
    val statusText = when (state.status) {
        SessionStatus.Idle -> "Ready"
        SessionStatus.Streaming -> "Thinking…"
        SessionStatus.Error -> "Error"
        SessionStatus.Disconnected -> "Disconnected"
    }
    val statusClass = when (state.status) {
        SessionStatus.Idle -> "status-idle"
        SessionStatus.Streaming -> "status-streaming"
        SessionStatus.Error -> "status-error"
        SessionStatus.Disconnected -> "status-disconnected"
    }
    sb.append("<div class=\"convo-header\">")
    sb.append("<span class=\"status-indicator ").append(statusClass).append("\">").append(statusText).append("</span>")
    if (state.model.isNotBlank()) sb.append("<span class=\"convo-model\">").append(escape(state.model)).append("</span>")
    if (state.totalCostUsd > 0) {
        sb.append("<span class=\"cost-display\">$").append("%.4f".format(state.totalCostUsd)).append("</span>")
    }
    sb.append("</div>")

    if (state.error != null) {
        sb.append("<div class=\"error\">").append(escape(state.error)).append("</div>")
    }

    state.pendingPermission?.let { renderPermission(sb, taskId, it) }

    for (message in state.messages) {
        renderMessage(sb, message)
    }

    if (state.isStreaming) {
        sb.append("<div class=\"message assistant streaming\">")
        sb.append("<div class=\"role role-assistant\">Claude</div>")
        if (state.streamingBlocks.isNotEmpty()) {
            val groups = groupBlocks(state.streamingBlocks)
            for (group in groups) renderGroup(sb, group)
        } else if (state.streamingText.isNotEmpty()) {
            sb.append("<pre class=\"text streaming-cursor\">").append(escape(state.streamingText)).append("</pre>")
        } else {
            sb.append("<div class=\"thinking-dots\">Thinking</div>")
        }
        sb.append("</div>")
    }

    if (state.status == SessionStatus.Idle && state.messages.isEmpty() && !state.isStreaming) {
        sb.append("<p class=\"muted\">No messages yet. Send a prompt below.</p>")
    }

    return sb.toString()
}

// ─── Message rendering ──────────────────────────────────────────────────────

private fun renderMessage(sb: StringBuilder, message: ConversationMessage) {
    val role = if (message.role == MessageRole.User) "user" else "assistant"
    val roleLabel = if (message.role == MessageRole.User) "You" else "Claude"
    val roleClass = if (message.role == MessageRole.User) "role-user" else "role-assistant"
    sb.append("<div class=\"message ").append(role).append("\">")
    sb.append("<div class=\"role ").append(roleClass).append("\">").append(roleLabel).append("</div>")
    val groups = groupBlocks(message.blocks)
    for (group in groups) renderGroup(sb, group)
    sb.append("</div>")
}

private fun renderGroup(sb: StringBuilder, group: WebBlockGroup) {
    when (group) {
        is WebBlockGroup.Single -> renderBlock(sb, group.block)
        is WebBlockGroup.ToolRun -> renderToolGroup(sb, group)
    }
}

// ─── Block rendering ────────────────────────────────────────────────────────

private fun renderBlock(sb: StringBuilder, block: ContentBlock) {
    when (block) {
        is ContentBlock.Text -> {
            sb.append("<div class=\"md\" data-md>").append(escape(block.text)).append("</div>")
        }
        is ContentBlock.Thinking -> {
            sb.append("<details class=\"thinking\"><summary>thinking</summary><pre>")
            sb.append(escape(block.text))
            sb.append("</pre></details>")
        }
        is ContentBlock.ToolUse -> renderToolUseBlock(sb, block, result = null)
        is ContentBlock.ToolResult -> {
            val cls = if (block.isError) "tool-result error" else "tool-result"
            sb.append("<div class=\"").append(cls).append("\"><pre>")
                .append(escape(block.content.take(2000))).append("</pre></div>")
        }
        is ContentBlock.Image -> {
            sb.append("<img class=\"img-block\" src=\"data:")
                .append(escape(block.mediaType)).append(";base64,")
                .append(escape(block.base64Data)).append("\" />")
        }
    }
}

// ─── Tool use rendering ─────────────────────────────────────────────────────

private fun renderToolUseBlock(sb: StringBuilder, block: ContentBlock.ToolUse, result: ContentBlock.ToolResult?) {
    if (isEditToolWeb(block.name, block.input)) {
        renderEditToolBlock(sb, block, result)
    } else if (isWriteToolWeb(block.name, block.input)) {
        renderWriteToolBlock(sb, block, result)
    } else {
        renderGenericToolBlock(sb, block, result)
    }
}

private fun renderEditToolBlock(sb: StringBuilder, block: ContentBlock.ToolUse, result: ContentBlock.ToolResult?) {
    val fields = parseEditInputWeb(block.input)
    val statusCls = block.status.name.lowercase()
    val icon = statusIcon(block.status)
    val iconCls = "status-$statusCls"

    sb.append("<details class=\"tool-use edit-tool status-").append(statusCls).append("\" open>")
    sb.append("<summary class=\"tool-header\">")
    sb.append("<span class=\"tool-status-icon ").append(iconCls).append("\">").append(icon).append("</span>")
    sb.append("<span class=\"tool-name\">Edit")
    if (fields.replaceAll) sb.append(" (all)")
    sb.append("</span>")
    if (fields.filePath != null) {
        sb.append("<span class=\"tool-summary\">").append(escape(fields.filePath)).append("</span>")
    }
    sb.append("</summary>")
    sb.append("<div class=\"tool-details\">")

    if (fields.oldString != null || fields.newString != null) {
        sb.append("<div class=\"diff-block\">")
        fields.oldString?.lines()?.forEach { line ->
            sb.append("<div class=\"diff-line diff-del\">- ").append(escape(line)).append("</div>")
        }
        fields.newString?.lines()?.forEach { line ->
            sb.append("<div class=\"diff-line diff-add\">+ ").append(escape(line)).append("</div>")
        }
        sb.append("</div>")
    }

    if (result != null) {
        val rcls = if (result.isError) "tool-result-inline error" else "tool-result-inline"
        sb.append("<div class=\"").append(rcls).append("\"><pre>").append(escape(result.content.take(500))).append("</pre></div>")
    }

    sb.append("</div></details>")
}

private fun renderWriteToolBlock(sb: StringBuilder, block: ContentBlock.ToolUse, result: ContentBlock.ToolResult?) {
    val fields = parseWriteInputWeb(block.input)
    val statusCls = block.status.name.lowercase()
    val icon = statusIcon(block.status)
    val iconCls = "status-$statusCls"

    sb.append("<details class=\"tool-use write-tool status-").append(statusCls).append("\" open>")
    sb.append("<summary class=\"tool-header\">")
    sb.append("<span class=\"tool-status-icon ").append(iconCls).append("\">").append(icon).append("</span>")
    sb.append("<span class=\"tool-name\">Write</span>")
    if (fields.filePath != null) {
        sb.append("<span class=\"tool-summary\">").append(escape(fields.filePath)).append("</span>")
    }
    sb.append("</summary>")
    sb.append("<div class=\"tool-details\">")

    if (fields.content != null) {
        sb.append("<div class=\"diff-block\">")
        fields.content.lines().take(50).forEach { line ->
            sb.append("<div class=\"diff-line diff-add\">+ ").append(escape(line)).append("</div>")
        }
        val totalLines = fields.content.lines().size
        if (totalLines > 50) {
            sb.append("<div class=\"diff-truncated\">… ").append(totalLines - 50).append(" more lines</div>")
        }
        sb.append("</div>")
    }

    if (result != null) {
        val rcls = if (result.isError) "tool-result-inline error" else "tool-result-inline"
        sb.append("<div class=\"").append(rcls).append("\"><pre>").append(escape(result.content.take(500))).append("</pre></div>")
    }

    sb.append("</div></details>")
}

private fun renderGenericToolBlock(sb: StringBuilder, block: ContentBlock.ToolUse, result: ContentBlock.ToolResult?) {
    val statusCls = block.status.name.lowercase()
    val icon = statusIcon(block.status)
    val iconCls = "status-$statusCls"
    val summary = formatInputSummaryWeb(block.name, block.input)
    val isOpen = block.status == ToolStatus.Running

    sb.append("<details class=\"tool-use status-").append(statusCls).append("\"")
    if (isOpen) sb.append(" open")
    sb.append(">")
    sb.append("<summary class=\"tool-header\">")
    sb.append("<span class=\"tool-status-icon ").append(iconCls).append("\">").append(icon).append("</span>")
    sb.append("<span class=\"tool-name\">").append(escape(block.name)).append("</span>")
    if (summary.isNotBlank()) {
        sb.append("<span class=\"tool-summary\">").append(escape(summary)).append("</span>")
    }
    sb.append("</summary>")
    sb.append("<div class=\"tool-details\">")
    if (block.input.isNotBlank()) {
        sb.append("<pre class=\"tool-input\">").append(escape(block.input.take(2000))).append("</pre>")
    }
    if (result != null) {
        val rcls = if (result.isError) "tool-result-inline error" else "tool-result-inline"
        sb.append("<div class=\"").append(rcls).append("\"><pre>").append(escape(result.content.take(2000))).append("</pre></div>")
    }
    sb.append("</div></details>")
}

// ─── Tool group rendering ───────────────────────────────────────────────────

private fun renderToolGroup(sb: StringBuilder, group: WebBlockGroup.ToolRun) {
    val tools = group.tools
    val results = group.results

    if (tools.size == 1) {
        renderToolUseBlock(sb, tools.first(), results[tools.first().id])
        return
    }

    val completedCount = tools.count { it.status == ToolStatus.Completed }
    val errorCount = tools.count { it.status == ToolStatus.Error }
    val runningCount = tools.count { it.status == ToolStatus.Running }
    val hasRunning = runningCount > 0

    sb.append("<details class=\"tool-group\"")
    if (hasRunning) sb.append(" open")
    sb.append(">")
    sb.append("<summary class=\"tool-group-header\">")
    sb.append("<span class=\"tool-group-count\">").append(tools.size).append(" tool calls</span>")

    val summaryParts = mutableListOf<String>()
    if (runningCount > 0) summaryParts.add("$runningCount running")
    if (completedCount > 0) summaryParts.add("$completedCount completed")
    if (errorCount > 0) summaryParts.add("$errorCount failed")
    if (summaryParts.isNotEmpty()) {
        sb.append("<span class=\"tool-group-summary\">(").append(summaryParts.joinToString(", ")).append(")</span>")
    }

    val toolNames = tools.map { it.name }.distinct().take(3)
    val namesSummary = toolNames.joinToString(", ") +
        if (tools.map { it.name }.distinct().size > 3) ", …" else ""
    sb.append("<span class=\"tool-group-names\">").append(escape(namesSummary)).append("</span>")
    sb.append("</summary>")

    sb.append("<div class=\"tool-group-details\">")
    for (tool in tools) {
        renderToolUseBlock(sb, tool, results[tool.id])
    }
    sb.append("</div></details>")
}

// ─── Permission rendering ───────────────────────────────────────────────────

private fun renderPermission(sb: StringBuilder, taskId: String, perm: PendingPermission) {
    sb.append("<div class=\"permission\">")
    sb.append("<div class=\"permission-label\">Permission Required</div>")
    sb.append("<div class=\"permission-tool\">").append(escape(perm.toolTitle.ifBlank { perm.toolName })).append("</div>")
    if (perm.inputSummary.isNotBlank()) {
        sb.append("<pre class=\"permission-input\">").append(escape(perm.inputSummary)).append("</pre>")
    }
    sb.append("<div class=\"permission-options\">")
    val allowOptions = perm.options.filter { it.kind.startsWith("allow") }
    val rejectOptions = perm.options.filter { it.kind.startsWith("reject") }
    for (opt in allowOptions + rejectOptions) {
        sb.append("<button type=\"button\" class=\"perm-btn ").append(escape(opt.kind))
            .append("\" data-action=\"permission\" data-task-id=\"").append(escape(taskId))
            .append("\" data-request-id=\"").append(escape(perm.requestId))
            .append("\" data-option-id=\"").append(escape(opt.id)).append("\">")
            .append(escape(opt.name)).append("</button>")
    }
    sb.append("</div></div>")
}

// ─── Helpers ────────────────────────────────────────────────────────────────

private fun escape(s: String): String {
    val out = StringBuilder(s.length)
    for (c in s) {
        when (c) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(c)
        }
    }
    return out.toString()
}

private fun statusIcon(status: ToolStatus): String = when (status) {
    ToolStatus.Running -> "&#9654;"
    ToolStatus.Completed -> "&#10003;"
    ToolStatus.Error -> "&#10007;"
}

private fun formatInputSummaryWeb(toolName: String, input: String): String {
    return try {
        when (toolName.lowercase()) {
            "read", "edit", "write" -> extractJsonValue(input, "file_path") ?: input.take(80)
            "bash", "powershell" -> extractJsonValue(input, "command") ?: input.take(80)
            "glob" -> extractJsonValue(input, "pattern") ?: input.take(80)
            "grep" -> extractJsonValue(input, "pattern") ?: input.take(80)
            "agent" -> extractJsonValue(input, "description") ?: input.take(80)
            else -> input.take(80)
        }
    } catch (_: Exception) {
        input.take(80)
    }
}

private fun extractJsonValue(json: String, key: String): String? {
    val regex = Regex(""""$key"\s*:\s*"([^"]*?)"""")
    return regex.find(json)?.groupValues?.get(1)
}

private fun isEditToolWeb(name: String, input: String): Boolean {
    if (name.startsWith("Edit", ignoreCase = true)) return true
    return try {
        val obj = webJson.parseToJsonElement(input).jsonObject
        obj.containsKey("file_path") && obj.containsKey("old_string") && obj.containsKey("new_string")
    } catch (_: Throwable) {
        false
    }
}

private fun isWriteToolWeb(name: String, input: String): Boolean {
    if (name.startsWith("Write", ignoreCase = true)) return true
    return try {
        val obj = webJson.parseToJsonElement(input).jsonObject
        obj.containsKey("file_path") && obj.containsKey("content") && !obj.containsKey("old_string")
    } catch (_: Throwable) {
        false
    }
}

private data class EditFields(val filePath: String?, val oldString: String?, val newString: String?, val replaceAll: Boolean)

private fun parseEditInputWeb(input: String): EditFields {
    if (input.isBlank()) return EditFields(null, null, null, false)
    return try {
        val obj = webJson.parseToJsonElement(input).jsonObject
        val path = (obj["file_path"] as? JsonPrimitive)?.contentOrNull
        val oldString = (obj["old_string"] as? JsonPrimitive)?.contentOrNull
        val newString = (obj["new_string"] as? JsonPrimitive)?.contentOrNull
        val replaceAll = (obj["replace_all"] as? JsonPrimitive)?.contentOrNull == "true"
        EditFields(path, oldString, newString, replaceAll)
    } catch (_: Throwable) {
        EditFields(null, null, null, false)
    }
}

private data class WriteFields(val filePath: String?, val content: String?)

private fun parseWriteInputWeb(input: String): WriteFields {
    if (input.isBlank()) return WriteFields(null, null)
    return try {
        val obj = webJson.parseToJsonElement(input).jsonObject
        val path = (obj["file_path"] as? JsonPrimitive)?.contentOrNull
        val content = (obj["content"] as? JsonPrimitive)?.contentOrNull
        WriteFields(path, content)
    } catch (_: Throwable) {
        WriteFields(null, null)
    }
}

// ─── Block grouping ─────────────────────────────────────────────────────────

private sealed class WebBlockGroup {
    data class Single(val block: ContentBlock) : WebBlockGroup()
    data class ToolRun(
        val tools: List<ContentBlock.ToolUse>,
        val results: Map<String, ContentBlock.ToolResult>,
    ) : WebBlockGroup()
}

private fun groupBlocks(blocks: List<ContentBlock>): List<WebBlockGroup> {
    val groups = mutableListOf<WebBlockGroup>()
    var currentToolRun = mutableListOf<ContentBlock.ToolUse>()
    val allResults = blocks.filterIsInstance<ContentBlock.ToolResult>().associateBy { it.toolUseId }

    for (block in blocks) {
        when (block) {
            is ContentBlock.ToolUse -> {
                if (isEditToolWeb(block.name, block.input) || isWriteToolWeb(block.name, block.input)) {
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(WebBlockGroup.ToolRun(currentToolRun.toList(), allResults))
                        currentToolRun = mutableListOf()
                    }
                    groups.add(WebBlockGroup.Single(block))
                } else {
                    currentToolRun.add(block)
                }
            }
            is ContentBlock.ToolResult -> {
                val matchesCurrentRun = currentToolRun.any { it.id == block.toolUseId }
                if (!matchesCurrentRun) {
                    if (currentToolRun.isNotEmpty()) {
                        groups.add(WebBlockGroup.ToolRun(currentToolRun.toList(), allResults))
                        currentToolRun = mutableListOf()
                    }
                    val hasMatchingToolUse = blocks.filterIsInstance<ContentBlock.ToolUse>().any { it.id == block.toolUseId }
                    if (!hasMatchingToolUse) {
                        groups.add(WebBlockGroup.Single(block))
                    }
                }
            }
            else -> {
                if (currentToolRun.isNotEmpty()) {
                    groups.add(WebBlockGroup.ToolRun(currentToolRun.toList(), allResults))
                    currentToolRun = mutableListOf()
                }
                groups.add(WebBlockGroup.Single(block))
            }
        }
    }
    if (currentToolRun.isNotEmpty()) {
        groups.add(WebBlockGroup.ToolRun(currentToolRun.toList(), allResults))
    }
    return groups
}
