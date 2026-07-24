package com.nndai.remotepump.ui.log

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nndai.remotepump.data.di.PumpRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PumpRepositoryProvider.provide()

    val isLogEnabled: StateFlow<Boolean> = repository.isLogEnabled

    // Keep the latest logs (max 1000)
    val logs = mutableStateListOf<String>()

    init {
        // Lấy trạng thái log hiện tại trên thiết bị
        viewModelScope.launch {
            repository.getLogMqtt()
        }

        viewModelScope.launch {
            repository.logs.collect { msg ->
                if (isLogEnabled.value) {
                    if (logs.size >= 2000) {
                        logs.removeAt(0)
                    }
                    logs.add(msg)
                }
            }
        }
    }

    fun setLogEnabled(enabled: Boolean) {
        if (enabled) {
            logs.clear()
        }
        viewModelScope.launch {
            repository.setLogMqtt(enabled)
        }
    }

    fun clearLogs() {
        logs.clear()
    }

    fun sendRawJson(rawJson: String) {
        if (rawJson.isBlank()) return
        viewModelScope.launch {
            repository.sendRawJson(rawJson.trim())
        }
    }
}
