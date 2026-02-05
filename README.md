# Vibe Manager

A desktop application for managing multiple concurrent Claude Code sessions across your git projects. Create isolated worktrees for each task, launch Claude Code in embedded terminals, and switch between sessions without losing state.

## Safety First

Vibe Manager launches Claude Code in its **standard interactive mode**. Every action Claude proposes -- file edits, shell commands, etc. -- requires your explicit approval before execution. There is no `--dangerously-skip-permissions` flag, no auto-accept, no yolo mode. You stay in the loop for every decision.

This is by design. The goal is to make it easy to run many Claude Code sessions in parallel, not to remove the guardrails that keep your codebase safe.

## Features

- **Project management** -- Point Vibe Manager at any git repository and it appears in the sidebar.
- **Task isolation** -- Each task gets its own git worktree and branch, so parallel Claude sessions never collide.
- **Embedded terminals** -- Claude Code runs inside the app via a real PTY-backed terminal emulator (JediTerm + pty4j). Full ANSI/xterm-256color support.
- **Persistent sessions** -- Navigate away from a terminal and come back without losing your session. Claude keeps running in the background.
- **Resume after crash** -- If the process exits (reboot, crash), reopen the task to resume with `claude --continue`.
- **Idle detection** -- A yellow dot appears next to tasks in the sidebar when Claude is waiting for your input.
- **Shell access** -- Drop to a plain command line in any task's worktree without restarting Claude.

## Building

Requires JDK 17+.

```shell
# Windows
.\gradlew.bat :composeApp:run

# macOS / Linux
./gradlew :composeApp:run
```

## How It Works

1. **Add a project** -- Select a local git repository.
2. **Create a task** -- Give it a name and branch. Vibe Manager runs `git worktree add` to create an isolated copy.
3. **Open the task** -- An embedded terminal launches with Claude Code already running in the worktree directory.
4. **Work in parallel** -- Create more tasks, switch between them from the sidebar. Each session is independent.
5. **Clean up** -- Deleting a task removes the worktree and branch.

## Tech Stack

- Kotlin Multiplatform (desktop/JVM target)
- Compose for Desktop (UI)
- Compose Fluent UI (Windows design system)
- JediTerm (terminal emulator widget)
- pty4j (pseudo-terminal)
- kotlinx.serialization (persistence)

## State

Application state (projects, tasks) is persisted as JSON to `~/.vibemanager/state.json`.
