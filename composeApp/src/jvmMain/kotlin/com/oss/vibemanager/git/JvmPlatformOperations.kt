package com.oss.vibemanager.git

import com.oss.vibemanager.viewmodel.PlatformOperations
import java.io.File
import java.util.UUID

class JvmPlatformOperations : PlatformOperations {

    override fun generateId(): String = UUID.randomUUID().toString()

    override fun currentTimeMillis(): Long = System.currentTimeMillis()

    override fun isGitRepo(path: String): Boolean = GitOperations.isGitRepo(path)

    override fun getRepoName(path: String): String = File(path).name

    override fun gitPull(repoPath: String): Result<Unit> =
        GitOperations.pull(repoPath)

    override fun createWorktree(repoPath: String, worktreePath: String, branchName: String): Result<Unit> =
        GitOperations.createWorktree(repoPath, worktreePath, branchName)

    override fun removeWorktree(repoPath: String, worktreePath: String): Result<Unit> =
        GitOperations.removeWorktree(repoPath, worktreePath)

    override fun deleteBranch(repoPath: String, branchName: String): Result<Unit> =
        GitOperations.deleteBranch(repoPath, branchName)

    override fun getWorktreeBasePath(repoPath: String): String {
        val parent = File(repoPath).parentFile ?: File(repoPath)
        val repoName = File(repoPath).name
        return File(parent, "${repoName}-worktrees").absolutePath
    }

    override fun fileExists(path: String): Boolean = File(path).exists()
}
