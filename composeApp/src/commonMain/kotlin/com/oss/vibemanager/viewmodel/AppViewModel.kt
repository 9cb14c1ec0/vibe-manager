package com.oss.vibemanager.viewmodel

import com.oss.vibemanager.model.AppState
import com.oss.vibemanager.model.Project
import com.oss.vibemanager.model.ShellType
import com.oss.vibemanager.model.Task
import com.oss.vibemanager.model.TaskState
import com.oss.vibemanager.persistence.AppStateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface PlatformOperations {
    fun generateId(): String
    fun currentTimeMillis(): Long
    fun isGitRepo(path: String): Boolean
    fun getRepoName(path: String): String
    fun gitPull(repoPath: String): Result<Unit>
    fun createWorktree(repoPath: String, worktreePath: String, branchName: String): Result<Unit>
    fun removeWorktree(repoPath: String, worktreePath: String): Result<Unit>
    fun deleteBranch(repoPath: String, branchName: String): Result<Unit>
    fun getWorktreeBasePath(repoPath: String): String
    fun fileExists(path: String): Boolean
    fun findGitBashPath(): String?
    fun listBranches(repoPath: String): Result<List<String>>
    fun listRemoteBranches(repoPath: String): Result<List<String>>
    fun checkoutWorktree(repoPath: String, worktreePath: String, branchName: String): Result<Unit>
}

sealed class NavigationTarget {
    data object Empty : NavigationTarget()
    data class ProjectDetail(val projectId: String) : NavigationTarget()
    data class TaskTerminal(val taskId: String) : NavigationTarget()
}

class AppViewModel(
    private val repository: AppStateRepository,
    private val platform: PlatformOperations,
) {
    private val _appState = MutableStateFlow(repository.load())
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    private val _navigation = MutableStateFlow<NavigationTarget>(NavigationTarget.Empty)
    val navigation: StateFlow<NavigationTarget> = _navigation.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val gitBashPath: String? = platform.findGitBashPath()
    val gitBashAvailable: Boolean = gitBashPath != null

    fun navigateTo(target: NavigationTarget) {
        _navigation.value = target
    }

    fun clearError() {
        _error.value = null
    }

    fun addProject(repoPath: String): Boolean {
        if (!platform.isGitRepo(repoPath)) {
            _error.value = "Not a valid git repository: $repoPath"
            return false
        }
        val existing = _appState.value.projects.any { it.repoPath == repoPath }
        if (existing) {
            _error.value = "Project already added"
            return false
        }
        val project = Project(
            id = platform.generateId(),
            name = platform.getRepoName(repoPath),
            repoPath = repoPath,
            addedAt = platform.currentTimeMillis(),
        )
        _appState.update { it.copy(projects = it.projects + project) }
        save()
        _navigation.value = NavigationTarget.ProjectDetail(project.id)
        return true
    }

    fun removeProject(id: String) {
        val tasks = _appState.value.tasks.filter { it.projectId == id }
        val project = _appState.value.projects.find { it.id == id } ?: return
        for (task in tasks) {
            platform.removeWorktree(project.repoPath, task.worktreePath)
            platform.deleteBranch(project.repoPath, task.branchName)
        }
        _appState.update {
            it.copy(
                projects = it.projects.filter { p -> p.id != id },
                tasks = it.tasks.filter { t -> t.projectId != id },
            )
        }
        save()
        if ((_navigation.value as? NavigationTarget.ProjectDetail)?.projectId == id) {
            _navigation.value = NavigationTarget.Empty
        }
    }

    fun createTask(projectId: String, name: String, branchName: String): Boolean {
        val project = _appState.value.projects.find { it.id == projectId }
        if (project == null) {
            _error.value = "Project not found"
            return false
        }
        val worktreeBase = platform.getWorktreeBasePath(project.repoPath)
        val worktreePath = "$worktreeBase/$branchName"

        // Pull latest before creating worktree to reduce conflicts
        platform.gitPull(project.repoPath)

        val result = platform.createWorktree(project.repoPath, worktreePath, branchName)
        if (result.isFailure) {
            _error.value = "Failed to create worktree: ${result.exceptionOrNull()?.message}"
            return false
        }

        val task = Task(
            id = platform.generateId(),
            projectId = projectId,
            name = name,
            branchName = branchName,
            worktreePath = worktreePath,
            createdAt = platform.currentTimeMillis(),
            claudeSessionId = platform.generateId(),
        )
        _appState.update { it.copy(tasks = it.tasks + task) }
        save()
        return true
    }

    fun listBranches(projectId: String): Result<List<String>> {
        val project = _appState.value.projects.find { it.id == projectId }
            ?: return Result.failure(Exception("Project not found"))
        return platform.listBranches(project.repoPath)
    }

    fun listRemoteBranches(projectId: String): Result<List<String>> {
        val project = _appState.value.projects.find { it.id == projectId }
            ?: return Result.failure(Exception("Project not found"))
        return platform.listRemoteBranches(project.repoPath)
    }

    fun createTaskFromBranch(projectId: String, name: String, branchName: String): Boolean {
        val project = _appState.value.projects.find { it.id == projectId }
        if (project == null) {
            _error.value = "Project not found"
            return false
        }
        val worktreeBase = platform.getWorktreeBasePath(project.repoPath)
        val worktreePath = "$worktreeBase/$branchName"

        val result = platform.checkoutWorktree(project.repoPath, worktreePath, branchName)
        if (result.isFailure) {
            _error.value = "Failed to create worktree: ${result.exceptionOrNull()?.message}"
            return false
        }

        val task = Task(
            id = platform.generateId(),
            projectId = projectId,
            name = name,
            branchName = branchName,
            worktreePath = worktreePath,
            createdAt = platform.currentTimeMillis(),
            claudeSessionId = platform.generateId(),
        )
        _appState.update { it.copy(tasks = it.tasks + task) }
        save()
        return true
    }

    fun deleteTask(id: String) {
        val task = _appState.value.tasks.find { it.id == id } ?: return
        val project = _appState.value.projects.find { it.id == task.projectId }
        if (project != null) {
            platform.removeWorktree(project.repoPath, task.worktreePath)
            platform.deleteBranch(project.repoPath, task.branchName)
        }
        _appState.update { it.copy(tasks = it.tasks.filter { t -> t.id != id }) }
        save()
        if ((_navigation.value as? NavigationTarget.TaskTerminal)?.taskId == id) {
            val target = if (project != null) NavigationTarget.ProjectDetail(project.id) else NavigationTarget.Empty
            _navigation.value = target
        }
    }

    fun markClaudeSessionStarted(id: String) {
        _appState.update { state ->
            state.copy(
                tasks = state.tasks.map { t ->
                    if (t.id == id) t.copy(claudeSessionStarted = true) else t
                }
            )
        }
        save()
    }

    fun updateTaskState(id: String, newState: TaskState) {
        _appState.update { state ->
            state.copy(
                tasks = state.tasks.map { t ->
                    if (t.id == id) t.copy(state = newState) else t
                }
            )
        }
        save()
    }

    fun getProjectTasks(projectId: String): List<Task> {
        return _appState.value.tasks.filter { it.projectId == projectId }
    }

    fun setShellType(shellType: ShellType) {
        _appState.update { it.copy(shellType = shellType) }
        save()
    }

    private fun save() {
        repository.save(_appState.value)
    }
}
