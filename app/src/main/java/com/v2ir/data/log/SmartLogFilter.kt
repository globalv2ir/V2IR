package com.v2ir.data.log

import com.v2ir.R
import com.v2ir.data.model.LogEntry
import com.v2ir.data.model.LogLevel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartLogFilter @Inject constructor() {

    fun filter(rawMessage: String, tag: String = "Xray"): FilteredLog {
        val lower = rawMessage.lowercase()
        val hintRes = when {
            lower.contains("certificate") || lower.contains("tls") || lower.contains("x509") ->
                R.string.log_hint_tls_error
            lower.contains("timeout") || lower.contains("deadline") ->
                R.string.log_hint_timeout
            lower.contains("dns") || lower.contains("resolve") || lower.contains("nxdomain") ->
                R.string.log_hint_dns_error
            lower.contains("clock") || lower.contains("time") || lower.contains("expired") ->
                R.string.log_hint_clock_skew
            lower.contains("refused") || lower.contains("blocked") || lower.contains("filter") ->
                R.string.log_hint_ip_blocked
            lower.contains("network unreachable") || lower.contains("no route") ||
                lower.contains("no internet") ->
                R.string.log_hint_no_internet
            else -> null
        }

        val level = when {
            lower.contains("error") || lower.contains("fail") -> LogLevel.ERROR
            hintRes != null -> LogLevel.ERROR
            lower.contains("warn") -> LogLevel.WARNING
            else -> LogLevel.INFO
        }

        return FilteredLog(
            level = level,
            tag = tag,
            displayMessageRes = hintRes,
            displayMessageRaw = if (hintRes == null) rawMessage else null,
            rawMessage = rawMessage
        )
    }

    fun toLogEntry(id: Long, filtered: FilteredLog, resolvedMessage: String, resolvedTag: String): LogEntry =
        LogEntry(
            id = id,
            level = filtered.level,
            tag = resolvedTag,
            message = resolvedMessage,
            rawMessage = filtered.rawMessage
        )
}

data class FilteredLog(
    val level: LogLevel,
    val tag: String,
    val displayMessageRes: Int?,
    val displayMessageRaw: String?,
    val rawMessage: String
)




