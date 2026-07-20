package com.v2ir.data.model

import androidx.annotation.StringRes
import com.v2ir.R

data class LogMessage(
    @StringRes val messageRes: Int = 0,
    val args: List<Any> = emptyList(),
    @StringRes val tagRes: Int = R.string.logs_tag_xray,
    val level: LogLevel = LogLevel.INFO,
    val messageRaw: String? = null
)

enum class LogLevel {
    INFO, WARNING, ERROR, SYSTEM
}




