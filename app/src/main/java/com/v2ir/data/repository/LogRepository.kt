package com.v2ir.data.repository

import android.content.Context
import com.v2ir.data.log.SmartLogFilter
import com.v2ir.data.model.LogEntry
import com.v2ir.data.model.LogLevel
import com.v2ir.data.model.LogMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smartLogFilter: SmartLogFilter
) {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    // FIX (Bug #14): Use AtomicLong to prevent ID collisions when addRaw/addRawSystem
    // are called concurrently from multiple dispatchers (IO thread for Xray logs,
    // Main thread for UI events, Default for scan results).
    private val nextId = AtomicLong(1L)

    fun add(message: LogMessage) {
        val tag = context.getString(message.tagRes)
        val text = message.messageRaw ?: if (message.messageRes != 0) {
            context.getString(message.messageRes, *message.args.toTypedArray())
        } else ""
        if (text.isNotBlank()) {
            addRaw(text, message.level, tag)
        }
    }

    fun addRaw(message: String, level: LogLevel = LogLevel.INFO, tag: String = "Xray") {
        val filtered = smartLogFilter.filter(message, tag)
        val displayText = filtered.displayMessageRes?.let { context.getString(it) }
            ?: filtered.displayMessageRaw
            ?: message
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            level = filtered.level.takeIf { filtered.displayMessageRes != null } ?: level,
            tag = tag,
            message = displayText,
            rawMessage = filtered.rawMessage
        )
        _logs.update { current -> (current + entry).takeLast(500) }
    }

    fun addRawSystem(raw: String, tag: String = "Xray") {
        val filtered = smartLogFilter.filter(raw, tag)
        val displayText = filtered.displayMessageRes?.let { context.getString(it) }
            ?: filtered.displayMessageRaw
            ?: raw
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            level = filtered.level,
            tag = tag,
            message = displayText,
            rawMessage = raw
        )
        _logs.update { current -> (current + entry).takeLast(500) }
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun exportText(): String = _logs.value.joinToString("\n") { entry ->
        "[${entry.level}] [${entry.tag}] ${entry.message}"
    }
}




