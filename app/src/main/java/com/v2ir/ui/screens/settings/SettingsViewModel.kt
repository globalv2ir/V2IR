package com.v2ir.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.v2ir.R
import com.v2ir.data.model.LogMessage
import com.v2ir.data.repository.LogRepository
import com.v2ir.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logRepository: LogRepository
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.v2ir.data.repository.AppSettings())

    fun setAutoConnect(value: Boolean) = viewModelScope.launch {
        settingsRepository.setAutoConnect(value)
    }

    fun setBypassLan(value: Boolean) = viewModelScope.launch {
        settingsRepository.setBypassLan(value)
    }

    fun setBypassIran(value: Boolean) = viewModelScope.launch {
        settingsRepository.setBypassIran(value)
        val messageRes = if (value) R.string.log_msg_bypass_iran_on else R.string.log_msg_bypass_iran_off
        logRepository.add(LogMessage(messageRes, tagRes = R.string.logs_tag_routing))
    }

    fun setCloudflareScanner(value: Boolean) = viewModelScope.launch {
        settingsRepository.setCloudflareScanner(value)
    }

    fun setLoadBalancer(value: Boolean) = viewModelScope.launch {
        settingsRepository.setLoadBalancer(value)
    }

    fun setLanguage(value: String) = viewModelScope.launch {
        settingsRepository.setLanguage(value)
    }

    fun setDnsServer(value: String) = viewModelScope.launch {
        settingsRepository.setDns(value)
    }

    fun setMtu(value: Int) = viewModelScope.launch {
        settingsRepository.setMtu(value)
    }

    fun setScanConcurrency(value: Int) = viewModelScope.launch {
        settingsRepository.setScanConcurrency(value)
    }

    fun setScanInterval(value: Int) = viewModelScope.launch {
        settingsRepository.setScanInterval(value)
    }

    fun setBalancerStrategy(value: String) = viewModelScope.launch {
        settingsRepository.setBalancerStrategy(value)
    }

    fun setLogLevel(value: String) = viewModelScope.launch {
        settingsRepository.setLogLevel(value)
    }
}




