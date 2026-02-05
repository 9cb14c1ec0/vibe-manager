package com.oss.vibemanager.git

import java.io.File

data class GitInfo(
    val repoName: String,
    val currentBranch: String,
    val isClean: Boolean,
)

object GitOperations {

    fun isGitRepo(path: String): Boolean {
        return runGit(path, "rev-parse", "--is-inside-work-tree")
            .map { it.trim() == "true" }
            .getOrDefault(false)
    }

    fun getGitInfo(repoPath: String): Result<GitInfo> = runCatching {
        val repoName = File(repoPath).name
        val branch = runGit(repoPath, "rev-parse", "--abbrev-ref", "HEAD").getOrThrow().trim()
        val status = runGit(repoPath, "status", "--porcelain").getOrThrow()
        GitInfo(
            repoName = repoName,
            currentBranch = branch,
            isClean = status.isBlank(),
        )
    }

    fun createWorktree(repoPath: String, worktreePath: String, branchName: String): Result<Unit> {
        return runGit(repoPath, "worktree", "add", "-b", branchName, worktreePath).map { }
    }

    fun removeWorktree(repoPath: String, worktreePath: String): Result<Unit> {
        return runGit(repoPath, "worktree", "remove", "--force", worktreePath).map { }
    }

    fun deleteBranch(repoPath: String, branchName: String): Result<Unit> {
        return runGit(repoPath, "branch", "-D", branchName).map { }
    }

    private fun runGit(workingDir: String, vararg args: String): Result<String> = runCatching {
        val process = ProcessBuilder("git", *args)
            .directory(File(workingDir))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            error("git ${args.first()} failed (exit $exitCode): $output")
        }
        output
    }
}
