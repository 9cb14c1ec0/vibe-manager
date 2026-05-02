# Vibe Manager

A desktop application for managing multiple concurrent Claude Code sessions across your git projects. Each task runs in its own git worktree with its own conversation, so you can drive several Claude sessions in parallel without them stepping on each other.

<img width="1920" height="1040" alt="image" src="https://github.com/user-attachments/assets/fcd4be59-054d-43b3-8624-ec42595ed462" />

## Safety First

Vibe Manager talks to Claude Code over the [Agent Client Protocol](https://github.com/zed-industries/agent-client-protocol). Every action Claude proposes -- file edits, shell commands, etc. -- surfaces as a permission request that you approve or deny in the UI. There is no auto-accept, no yolo mode. You stay in the loop for every decision.

Plan mode is wired up as a first-class toggle: switch to **Plan** before sending a prompt and Claude will draft a plan for your review instead of touching files. Switch to **Build** when you're ready to execute.

## Features

- **Project management** -- Point Vibe Manager at any git repository and it appears in the sidebar.
- **Task isolation** -- Each task gets its own git worktree and branch, so parallel Claude sessions never collide.
- **Native chat UI** -- Streamed assistant messages, thinking blocks, tool calls, and tool results render inline. Markdown formatting is preserved.
- **Plan / Build modes** -- Toggle the permission mode per task. Active plans get their own focus view with a one-click "show full conversation" escape hatch.
- **Live diff panel** -- See the worktree's changed files and full per-file diffs side-by-side with the chat, refreshed on demand.
- **Optional terminal pane** -- Drop a multi-tab PTY-backed terminal (BossTerm) under the chat for any task when you need a real shell in the worktree.
- **Permission approvals inline** -- Tool-use permission requests appear as a banner above the input; pick an option to respond.
- **Model picker** -- Switch between Claude Sonnet/Opus variants per task from the header.
- **Persistent sessions** -- Conversation state and ACP sessions survive navigating between tasks; resume seamlessly when you come back.
- **Cost tracking** -- Running USD cost for the session is shown in the header.
- **Idle detection** -- A status indicator on each task shows whether Claude is streaming, idle, or disconnected.

## Architecture

Vibe Manager is a Compose for Desktop (JVM) app. It launches a small Bun-based **ACP bridge** (`acp-bridge/`) that wraps `@agentclientprotocol/claude-agent-acp` and speaks ACP over stdio to the Kotlin host. The host translates ACP session updates into a Compose-friendly conversation model and renders messages, tool calls, plans, and diffs.

- `composeApp/` -- the Kotlin Multiplatform / Compose for Desktop app (UI, ACP client, session/state management).
- `acp-bridge/` -- the bundled Bun script that runs Claude Agent ACP.

## Building

Requires JDK 17+ and [Bun](https://bun.sh) (used to run the ACP bridge).

```shell
# Windows
.\gradlew.bat :composeApp:run

# macOS / Linux
./gradlew :composeApp:run
```

## How It Works

1. **Add a project** -- Select a local git repository.
2. **Create a task** -- Give it a name and branch. Vibe Manager runs `git worktree add` to create an isolated copy.
3. **Open the task** -- A chat session connects to Claude through the ACP bridge, scoped to the worktree directory.
4. **Plan or build** -- Pick a mode, send a prompt, approve permission requests as Claude works.
5. **Review changes** -- Pop open the diff panel to inspect what Claude has changed; pop open the terminal if you need a shell.
6. **Work in parallel** -- Switch between tasks from the sidebar. Each session is independent and keeps streaming in the background.
7. **Clean up** -- Deleting a task removes the worktree and branch.

## Tech Stack

- Kotlin Multiplatform (desktop/JVM target)
- Compose for Desktop + Compose Fluent UI
- Agent Client Protocol (`com.agentclientprotocol:acp` on the JVM, `@agentclientprotocol/claude-agent-acp` in the bridge)
- multiplatform-markdown-renderer (assistant text and plan rendering)
- BossTerm (embedded PTY terminal pane)
- kotlinx.serialization (persistence)

## State

Application state (projects, tasks, panel sizes, model/mode preferences) is persisted as JSON to `~/.vibemanager/state.json`.
