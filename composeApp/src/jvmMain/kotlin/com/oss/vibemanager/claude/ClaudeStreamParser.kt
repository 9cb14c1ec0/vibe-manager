package com.oss.vibemanager.claude

import com.oss.vibemanager.model.ClaudeEvent
import com.oss.vibemanager.model.ClaudeEventParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.BufferedReader
import java.io.InputStream
import kotlin.coroutines.coroutineContext

object ClaudeStreamParser {

    /**
     * Reads NDJSON lines from the given input stream and emits parsed ClaudeEvents.
     * Runs on Dispatchers.IO. Completes when the stream ends (process exits).
     */
    fun parseStream(inputStream: InputStream): Flow<ClaudeEvent> = flow {
        val reader: BufferedReader = inputStream.bufferedReader()
        try {
            while (coroutineContext.isActive) {
                val line = reader.readLine() ?: break // EOF = process exited
                if (line.isBlank()) continue
                val event = ClaudeEventParser.parse(line)
                if (event != null) {
                    emit(event)
                }
            }
        } catch (_: Exception) {
            // Stream closed or interrupted - normal during process kill
        }
    }.flowOn(Dispatchers.IO)
}
