package com.oss.vibemanager.git

import com.oss.vibemanager.model.ChangedFile
import com.oss.vibemanager.model.DiffLine
import com.oss.vibemanager.model.DiffLineType
import com.oss.vibemanager.model.FileChangeStatus
import com.oss.vibemanager.model.FileDiff
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

    fun pull(repoPath: String): Result<Unit> {
        return runGit(repoPath, "pull").map { }
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

    fun listBranches(repoPath: String): Result<List<String>> = runCatching {
        val branches = runGit(repoPath, "branch", "--format=%(refname:short)").getOrThrow()
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        val worktreeBranches = runGit(repoPath, "worktree", "list", "--porcelain").getOrThrow()
            .lines().filter { it.startsWith("branch ") }
            .map { it.removePrefix("branch refs/heads/") }
            .toSet()
        branches.filter { it !in worktreeBranches }
    }

    fun listRemoteBranches(repoPath: String): Result<List<String>> = runCatching {
        // Fetch latest from remote
        runGit(repoPath, "fetch", "--prune").getOrThrow()

        val localBranches = runGit(repoPath, "branch", "--format=%(refname:short)").getOrThrow()
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val worktreeBranches = runGit(repoPath, "worktree", "list", "--porcelain").getOrThrow()
            .lines().filter { it.startsWith("branch ") }
            .map { it.removePrefix("branch refs/heads/") }
            .toSet()
        val usedBranches = localBranches + worktreeBranches

        runGit(repoPath, "branch", "-r", "--format=%(refname:short)").getOrThrow()
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
            .filter { !it.endsWith("/HEAD") }
            .map { it.removePrefix("origin/") }
            .filter { it !in usedBranches }
    }

    fun checkoutWorktree(repoPath: String, worktreePath: String, branchName: String): Result<Unit> {
        return runGit(repoPath, "worktree", "add", worktreePath, branchName).map { }
    }

    fun getChangedFiles(repoPath: String): Result<List<ChangedFile>> = runCatching {
        val output = runGit(repoPath, "status", "--porcelain").getOrThrow()
        output.lines()
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                if (line.length < 3) return@mapNotNull null
                val statusCode = line.take(2)
                val path = line.drop(3).trim()
                val status = when {
                    statusCode == "??" -> FileChangeStatus.Untracked
                    statusCode.contains('A') -> FileChangeStatus.Added
                    statusCode.contains('D') -> FileChangeStatus.Deleted
                    statusCode.contains('M') -> FileChangeStatus.Modified
                    else -> FileChangeStatus.Modified
                }
                ChangedFile(path = path, status = status)
            }
    }

    fun getFileDiff(repoPath: String, filePath: String, status: FileChangeStatus): Result<FileDiff> = runCatching {
        val diffOutput = when (status) {
            FileChangeStatus.Untracked -> {
                val file = File(repoPath, filePath)
                if (file.exists()) {
                    file.readText()
                        .lines()
                        .mapIndexed { _, line -> "+$line" }
                        .joinToString("\n")
                } else ""
            }
            else -> runGit(repoPath, "diff", "HEAD", "--", filePath).getOrThrow()
        }

        val lines = diffOutput.lines().map { line ->
            val type = when {
                line.startsWith("@@") -> DiffLineType.Hunk
                line.startsWith("+") && !line.startsWith("+++") -> DiffLineType.Addition
                line.startsWith("-") && !line.startsWith("---") -> DiffLineType.Deletion
                else -> DiffLineType.Context
            }
            DiffLine(content = line, type = type)
        }

        FileDiff(filePath = filePath, lines = lines)
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
