package com.mihomo.gateway.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ConnectionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ConnectionsUiState(
    val connections: List<ConnectionInfo> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false
)

class ConnectionsViewModel(
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                val result = api.connections()
                result.onSuccess { list ->
                    _state.value = ConnectionsUiState(connections = list, loaded = true)
                }.onFailure {
                    _state.value = _state.value.copy(error = it.message, loaded = true)
                }
                delay(1000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun close(id: String) {
        viewModelScope.launch { api.closeConnection(id) }
    }

    fun closeAll() {
        viewModelScope.launch { api.closeAllConnections() }
    }
}
