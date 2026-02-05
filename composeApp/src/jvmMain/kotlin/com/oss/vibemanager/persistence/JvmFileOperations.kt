package com.oss.vibemanager.persistence

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class JvmFileOperations : FileOperations {
    override fun readText(path: String): String? {
        val p = Paths.get(path)
        return if (Files.exists(p)) Files.readString(p) else null
    }

    override fun writeText(path: String, content: String) {
        Files.writeString(Paths.get(path), content)
    }

    override fun ensureDirectory(path: String) {
        Files.createDirectories(Paths.get(path))
    }

    override fun exists(path: String): Boolean {
        return Files.exists(Paths.get(path))
    }
}
