package com.mihomo.gateway.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.LogEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LogsViewModel(
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _level = MutableStateFlow("info")
    val level: StateFlow<String> = _level.asStateFlow()

    private var job: Job? = null

    fun start(level: String = _level.value) {
        job?.cancel()
        _level.value = level
        _logs.value = emptyList()
        job = viewModelScope.launch {
            api.logFlow(level).collect { entry ->
                _logs.update { current ->
                    (current + entry).takeLast(MAX_LINES)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clear() {
        _logs.value = emptyList()
    }

    companion object {
        private const val MAX_LINES = 500
    }
}
