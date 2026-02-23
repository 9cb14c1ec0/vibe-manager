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

    override fun findGitBashPath(): String? {
        val candidates = listOf(
            "C:/Program Files/Git/bin/bash.exe",
            "C:/Program Files (x86)/Git/bin/bash.exe",
        )
        candidates.firstOrNull { File(it).exists() }?.let { return it }

        // Try to find via git --exec-path for portable installs
        return try {
            val process = ProcessBuilder("git", "--exec-path")
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0 && output.isNotEmpty()) {
                // git --exec-path returns something like C:/Program Files/Git/mingw64/libexec/git-core
                // Navigate up to find bin/bash.exe
                var dir = File(output)
                repeat(3) { dir = dir.parentFile ?: return null }
                val bash = File(dir, "bin/bash.exe")
                if (bash.exists()) bash.absolutePath else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
