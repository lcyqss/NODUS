package com.mihomo.gateway.ui.connections

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.NeighborInfo
import com.mihomo.gateway.core.NeighborResolver
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ConnectionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MAX_DEVICES = 200

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
    private val neighbors: NeighborResolver = AppContainer.neighbors,
    private val settings: SettingsStore = AppContainer.settings
) : ViewModel() {

    private var lastAutoCleanAt = 0L

    private val _state = MutableStateFlow(ConnectionsUiState())
    val state: StateFlow<ConnectionsUiState> = _state.asStateFlow()

    private val _neighbors = MutableStateFlow<Map<String, NeighborInfo>>(emptyMap())
    val neighborMap: StateFlow<Map<String, NeighborInfo>> = _neighbors.asStateFlow()

    private val _deviceHistory = MutableStateFlow<Map<String, DeviceSeen>>(emptyMap())
    val deviceHistory: StateFlow<Map<String, DeviceSeen>> = _deviceHistory.asStateFlow()

    /** 自动清理配置：开关 + 触发阈值（连接数）。 */
    val autoCleanConfig: StateFlow<Pair<Boolean, Int>> = settings.settings
        .map { it.autoCleanIdle to it.autoCleanThreshold }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false to 800)

    fun setAutoClean(enabled: Boolean, threshold: Int) {
        viewModelScope.launch {
            settings.update {
                it.copy(
                    autoCleanIdle = enabled,
                    autoCleanThreshold = threshold.coerceIn(200, 10_000)
                )
            }
        }
    }

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob != null) return
        pollJob = viewModelScope.launch {
            var tick = 0
            var backoff = 2_000L
            while (isActive) {
                val result = api.connections()
                var ok = false
                result.onSuccess { list ->
                    ok = true
                    val annotated = AppContainer.connectionActivity.annotate(list)
                    _state.value = ConnectionsUiState(connections = annotated, loaded = true)
                    updateHistory(annotated)
                    runCatching { maybeAutoClean(annotated) }
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
                backoff = if (ok) 2_000L else (backoff * 2).coerceAtMost(30_000L)
                delay(backoff)
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

    /** 当前可清理的空闲连接（供确认弹窗展示数量）。 */
    fun cleanCandidates(): List<ConnectionInfo> =
        AppContainer.connectionActivity.cleanCandidates(_state.value.connections)

    /** 手动清理：关闭空闲超过 10 分钟且零流量的连接。 */
    fun cleanIdle() {
        viewModelScope.launch {
            val candidates = cleanCandidates()
            if (candidates.isEmpty()) return@launch
            candidates.forEach { api.closeConnection(it.id) }
            api.connections().onSuccess { list ->
                _state.value = ConnectionsUiState(
                    connections = AppContainer.connectionActivity.annotate(list),
                    loaded = true
                )
            }
        }
    }

    /** 自动清理：仅在设置开启且连接数超过阈值时，关闭空闲超过 30 分钟且零流量的连接。 */
    private suspend fun maybeAutoClean(list: List<ConnectionInfo>) {
        val cfg = runCatching { settings.settings.first() }.getOrNull() ?: return
        if (!cfg.autoCleanIdle || list.size <= cfg.autoCleanThreshold) return
        val now = System.currentTimeMillis()
        if (now - lastAutoCleanAt < 60_000L) return
        val candidates = AppContainer.connectionActivity.autoCleanCandidates(list)
        if (candidates.isEmpty()) return
        lastAutoCleanAt = now
        candidates.forEach { api.closeConnection(it.id) }
    }

    private fun labelFor(ip: String): String {
        val info = _neighbors.value[ip] ?: return ip
        return info.hostname?.takeIf { it.isNotBlank() } ?: ip
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
            }.entries
                .sortedByDescending { it.value.lastSeen }
                .take(MAX_DEVICES)
                .associate { it.key to it.value }
        }
    }

    private fun relabelHistory() {
        _deviceHistory.update { old ->
            old.mapValues { (ip, device) -> device.copy(label = labelFor(ip)) }
        }
    }
}
