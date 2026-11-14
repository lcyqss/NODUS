package com.mihomo.gateway.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.GatewayState
import com.mihomo.gateway.model.ProxyNames
import com.mihomo.gateway.model.Traffic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class OverviewStats(
    val connections: Int = 0,
    val uploadTotal: Long = 0,
    val downloadTotal: Long = 0
)

class OverviewViewModel(
    private val gateway: GatewayController = AppContainer.gateway,
    private val api: MihomoApi = AppContainer.api,
    private val settings: SettingsStore = AppContainer.settings
) : ViewModel() {

    val state: StateFlow<GatewayState> = gateway.state

    private val _traffic = MutableStateFlow(Traffic(0, 0))
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _upHistory = MutableStateFlow<List<Long>>(emptyList())
    val upHistory: StateFlow<List<Long>> = _upHistory.asStateFlow()

    private val _downHistory = MutableStateFlow<List<Long>>(emptyList())
    val downHistory: StateFlow<List<Long>> = _downHistory.asStateFlow()

    private val _memory = MutableStateFlow(0L)
    val memory: StateFlow<Long> = _memory.asStateFlow()

    private val _stats = MutableStateFlow(OverviewStats())
    val stats: StateFlow<OverviewStats> = _stats.asStateFlow()

    private val _currentGroup = MutableStateFlow<String?>(null)
    val currentGroup: StateFlow<String?> = _currentGroup.asStateFlow()

    private val _currentNode = MutableStateFlow<String?>(null)
    val currentNode: StateFlow<String?> = _currentNode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { gateway.refresh() }
        loadCurrentNode()
        viewModelScope.launch {
            api.trafficFlow().catch { }.collect { traffic ->
                _traffic.value = traffic
                _upHistory.update { list -> (list + traffic.up).takeLast(60) }
                _downHistory.update { list -> (list + traffic.down).takeLast(60) }
            }
        }
        viewModelScope.launch {
            api.memoryFlow().catch { }.collect { _memory.value = it }
        }
        viewModelScope.launch {
            while (isActive) {
                api.connections().onSuccess { list ->
                    _stats.value = OverviewStats(
                        connections = list.size,
                        uploadTotal = list.sumOf { it.upload },
                        downloadTotal = list.sumOf { it.download }
                    )
                }
                delay(3000)
            }
        }
    }

    fun toggle() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val running = gateway.state.value.running
            settings.update { it.copy(desiredRunning = !running) }
            if (running) gateway.stop() else gateway.start()
            _busy.value = false
            loadCurrentNode()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            gateway.refresh()
            loadCurrentNode()
        }
    }

    private fun loadCurrentNode() {
        viewModelScope.launch {
            api.proxies().onSuccess { nodes ->
                val groups = nodes.filter { it.isGroup && !it.now.isNullOrBlank() }
                val usable = groups.filter { !ProxyNames.isInfoNode(it.now.orEmpty()) }
                val group = usable.firstOrNull { it.name == "自动选择" }
                    ?: usable.firstOrNull { it.name != "GLOBAL" }
                    ?: usable.firstOrNull()
                    ?: groups.firstOrNull()
                _currentGroup.value = group?.name
                _currentNode.value = group?.now?.takeUnless { ProxyNames.isInfoNode(it) }
            }
        }
    }
}
