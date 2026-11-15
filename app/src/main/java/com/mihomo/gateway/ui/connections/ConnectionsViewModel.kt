package com.mihomo.gateway.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.NeighborInfo
import com.mihomo.gateway.core.NeighborResolver
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ConnectionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DeviceSeen(
    val ip: String,
    val label: String,
    val lastSeen: Long,
    val traffic: Long
)

data class ConnectionsUiState(
    val connections: List<ConnectionInfo> = emptyList(),
    val error: String? = null,
    val loaded: Boolean = false
)

class ConnectionsViewModel(
    private val api: MihomoApi = AppContainer.api,
    private val neighbors: NeighborResolver = AppContainer.neighbors
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    private val _neighbors = MutableStateFlow<Map<String, NeighborInfo>>(emptyMap())
    val neighborMap: StateFlow<Map<String, NeighborInfo>> = _neighbors.asStateFlow()

    private val _deviceHistory = MutableStateFlow<Map<String, DeviceSeen>>(emptyMap())
    val deviceHistory: StateFlow<Map<String, DeviceSeen>> = _deviceHistory.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            var tick = 0
            while (isActive) {
                val result = api.connections()
                result.onSuccess { list ->
                    _state.value = ConnectionsUiState(connections = list, loaded = true)
                    updateHistory(list)
                }.onFailure {
                    _state.value = _state.value.copy(error = it.message, loaded = true)
                }

                if (tick % 10 == 0) {
                    runCatching { neighbors.resolve() }.onSuccess { map ->
                        _neighbors.value = map
                        relabelHistory()
                    }
                }
                tick++
                delay(2000)
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

    private fun labelFor(ip: String): String {
        val info = _neighbors.value[ip] ?: return ip
        return info.hostname ?: info.mac
    }

    private fun updateHistory(list: List<ConnectionInfo>) {
        val now = System.currentTimeMillis()
        val totals = list.groupBy { it.sourceIP }
            .mapValues { entry -> entry.value.sumOf { it.upload + it.download } }
        _deviceHistory.update { old ->
            val next = old.toMutableMap()
            totals.forEach { (ip, traffic) ->
                val previous = next[ip]
                next[ip] = DeviceSeen(
                    ip = ip,
                    label = labelFor(ip),
                    lastSeen = now,
                    traffic = maxOf(previous?.traffic ?: 0L, traffic)
                )
            }
            next.mapValues { (ip, device) ->
                device.copy(label = labelFor(ip))
            }
        }
    }

    private fun relabelHistory() {
        _deviceHistory.update { old ->
            old.mapValues { (ip, device) -> device.copy(label = labelFor(ip)) }
        }
    }
}
