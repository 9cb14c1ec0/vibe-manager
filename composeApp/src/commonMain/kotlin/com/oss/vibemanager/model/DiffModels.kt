package com.oss.vibemanager.model

enum class FileChangeStatus {
    Modified,
    Added,
    Deleted,
    Untracked,
}

data class ChangedFile(
    val path: String,
    val status: FileChangeStatus,
)

enum class DiffLineType {
    Context,
    Addition,
    Deletion,
    Hunk,
}

data class DiffLine(
    val content: String,
    val type: DiffLineType,
)

data class FileDiff(
    val filePath: String,
    val lines: List<DiffLine>,
)
