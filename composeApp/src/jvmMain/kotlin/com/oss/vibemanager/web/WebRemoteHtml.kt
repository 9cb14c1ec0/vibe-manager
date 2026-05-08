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

internal fun renderTask(html: HTML, task: Task, conversation: ConversationState) {
    html.head {
        meta(charset = "utf-8")
        meta(name = "viewport", content = "width=device-width, initial-scale=1")
        title { +"${task.name} — Vibe Manager" }
        link(rel = "stylesheet", href = "/static/app.css")
    }
    html.body {
        div("container chat") {
            div("chat-header") {
                a(href = "/projects/${task.projectId}") { +"← Project" }
                h1 { +task.name }
                span("muted") { +"branch: ${task.branchName} · agent: ${task.agentKind}" }
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
                    attributes["rows"] = "3"
                    attributes["placeholder"] = "Send a message…"
                    attributes["required"] = "true"
                }
                div("compose-row") {
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

internal fun renderConversationFragment(taskId: String, state: ConversationState): String {
    val sb = StringBuilder()
    sb.append("<div class=\"meta\">")
    sb.append("status: ").append(escape(state.status.name))
    if (state.model.isNotBlank()) sb.append(" · model: ").append(escape(state.model))
    if (state.totalCostUsd > 0) sb.append(" · cost: $").append("%.4f".format(state.totalCostUsd))
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
        sb.append("<div class=\"role\">assistant (streaming)</div>")
        if (state.streamingBlocks.isNotEmpty()) {
            for (block in state.streamingBlocks) renderBlock(sb, block)
        } else if (state.streamingText.isNotEmpty()) {
            sb.append("<pre class=\"text\">").append(escape(state.streamingText)).append("</pre>")
        }
        sb.append("</div>")
    }

    if (state.status == SessionStatus.Idle && state.messages.isEmpty() && !state.isStreaming) {
        sb.append("<p class=\"muted\">No messages yet. Send a prompt below.</p>")
    }

    return sb.toString()
}

private fun renderMessage(sb: StringBuilder, message: ConversationMessage) {
    val role = if (message.role == MessageRole.User) "user" else "assistant"
    sb.append("<div class=\"message ").append(role).append("\">")
    sb.append("<div class=\"role\">").append(role).append("</div>")
    for (block in message.blocks) renderBlock(sb, block)
    sb.append("</div>")
}

private fun renderBlock(sb: StringBuilder, block: ContentBlock) {
    when (block) {
        is ContentBlock.Text -> {
            sb.append("<pre class=\"text\">").append(escape(block.text)).append("</pre>")
        }
        is ContentBlock.Thinking -> {
            sb.append("<details class=\"thinking\"><summary>thinking</summary><pre>")
            sb.append(escape(block.text))
            sb.append("</pre></details>")
        }
        is ContentBlock.ToolUse -> {
            sb.append("<div class=\"tool-use status-").append(block.status.name.lowercase()).append("\">")
            sb.append("<div class=\"tool-name\">").append(escape(block.name)).append(" · ")
                .append(block.status.name.lowercase()).append("</div>")
            if (block.input.isNotBlank()) {
                sb.append("<pre class=\"tool-input\">").append(escape(block.input)).append("</pre>")
            }
            sb.append("</div>")
        }
        is ContentBlock.ToolResult -> {
            val cls = if (block.isError) "tool-result error" else "tool-result"
            sb.append("<div class=\"").append(cls).append("\"><pre>")
                .append(escape(block.content)).append("</pre></div>")
        }
        is ContentBlock.Image -> {
            sb.append("<img class=\"img-block\" src=\"data:")
                .append(escape(block.mediaType)).append(";base64,")
                .append(escape(block.base64Data)).append("\" />")
        }
    }
}

private fun renderPermission(sb: StringBuilder, taskId: String, perm: PendingPermission) {
    sb.append("<div class=\"permission\">")
    sb.append("<h3>Permission requested: ").append(escape(perm.toolTitle.ifBlank { perm.toolName })).append("</h3>")
    if (perm.inputSummary.isNotBlank()) {
        sb.append("<pre>").append(escape(perm.inputSummary)).append("</pre>")
    }
    sb.append("<div class=\"permission-options\">")
    for (opt in perm.options) {
        sb.append("<button class=\"perm-btn ").append(escape(opt.kind))
            .append("\" data-action=\"permission\" data-task-id=\"").append(escape(taskId))
            .append("\" data-request-id=\"").append(escape(perm.requestId))
            .append("\" data-option-id=\"").append(escape(opt.id)).append("\">")
            .append(escape(opt.name)).append("</button>")
    }
    sb.append("</div></div>")
}

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
