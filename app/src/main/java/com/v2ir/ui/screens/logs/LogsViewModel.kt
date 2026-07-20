package com.v2ir.ui.screens.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ir.data.model.LogLevel
import com.v2ir.data.repository.LogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class LogsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logRepository: LogRepository
) : ViewModel() {

    private val filterLevel = MutableStateFlow<LogLevel?>(null)

    val logs = combine(logRepository.logs, filterLevel) { allLogs, filter ->
        if (filter == null) allLogs else allLogs.filter { it.level == filter }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filter: StateFlow<LogLevel?> = filterLevel.asStateFlow()

    fun setFilter(level: LogLevel?) {
        filterLevel.update { level }
    }

    fun clearLogs() {
        logRepository.clear()
    }

    fun copyToClipboard(): Boolean {
        val text = logRepository.exportText()
        if (text.isBlank()) return false
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("logs", text))
        return true
    }

    fun exportText(): String = logRepository.exportText()
}




