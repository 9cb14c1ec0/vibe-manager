package com.oss.vibemanager.persistence

interface FileOperations {
    fun readText(path: String): String?
    fun writeText(path: String, content: String)
    fun ensureDirectory(path: String)
    fun exists(path: String): Boolean
}
