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

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private var job: Job? = null

    fun start(level: String = _level.value, clear: Boolean = true) {
        job?.cancel()
        _level.value = level
        if (clear) _logs.value = emptyList()
        _paused.value = false
        job = viewModelScope.launch {
            api.logFlow(level).collect { entry ->
                _logs.update { current -> (current + entry).takeLast(MAX_LINES) }
            }
        }
    }

    fun pause() {
        job?.cancel()
        job = null
        _paused.value = true
    }

    fun resume() {
        start(_level.value, clear = false)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val MAX_LINES = 500
    }
}
