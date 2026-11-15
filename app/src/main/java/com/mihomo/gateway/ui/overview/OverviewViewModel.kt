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
import kotlinx.coroutines.flow.first
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

    private val _today = MutableStateFlow(Traffic(0, 0))
    val today: StateFlow<Traffic> = _today.asStateFlow()

    private var sampleCount = 0

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())

    private val _stats = MutableStateFlow(OverviewStats())
    val stats: StateFlow<OverviewStats> = _stats.asStateFlow()

    private val _currentGroup = MutableStateFlow<String?>(null)
    val currentGroup: StateFlow<String?> = _currentGroup.asStateFlow()

    private val _currentNode = MutableStateFlow<String?>(null)
    val currentNode: StateFlow<String?> = _currentNode.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _runtimeMode = MutableStateFlow("rule")
    val runtimeMode: StateFlow<String> = _runtimeMode.asStateFlow()

    init {
        viewModelScope.launch { gateway.refresh() }
        viewModelScope.launch { api.configs().onSuccess { _runtimeMode.value = it.mode } }
        viewModelScope.launch {
            val s = settings.settings.first()
            if (s.todayDate == todayKey()) {
                _today.value = Traffic(s.todayUp, s.todayDown)
            }
        }
        viewModelScope.launch { loadCurrentNode() }
        viewModelScope.launch {
            while (isActive) {
            runCatching {
            api.trafficFlow().collect { traffic ->
                _traffic.value = traffic
                _upHistory.update { list -> (list + traffic.up).takeLast(60) }
                _downHistory.update { list -> (list + traffic.down).takeLast(60) }
                _today.update { current -> Traffic(current.up + traffic.up, current.down + traffic.down) }
                sampleCount++
                if (sampleCount % 10 == 0) {
                    val snapshot = _today.value
                    settings.update {
                        it.copy(
                            todayDate = todayKey(),
                            todayUp = snapshot.up,
                            todayDown = snapshot.down
                        )
                    }
                }
            }
            }
            delay(2000)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                runCatching { api.memoryFlow().collect { _memory.value = it } }
                delay(2000)
            }
        }
        // 启动/重装核心后，内核可能晚于界面就绪：在前 30 秒内自动重试刷新
        viewModelScope.launch {
            repeat(10) {
                delay(3000)
                if (!gateway.state.value.running) {
                    val desired = runCatching { settings.settings.first().desiredRunning }.getOrDefault(true)
                    if (desired) gateway.refresh()
                }
            }
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
                loadCurrentNode()
                delay(5000)
            }
        }
    }

    fun setRuntimeMode(mode: String) {
        viewModelScope.launch {
            api.setMode(mode).onSuccess { _runtimeMode.value = mode }
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

    private suspend fun loadCurrentNode() {
        api.proxies().onSuccess { nodes ->
                val byName = nodes.associateBy { it.name }
                // 设备策略的 __dev_ 组是按设备的隐藏出口，不代表首页展示的主链路
                val groups = nodes.filter {
                    it.isGroup && !it.now.isNullOrBlank() && !it.name.startsWith("__dev_")
                }
                val nested = groups.filter { it.name != "GLOBAL" }.flatMap { it.all }.toSet()
                // 顶层策略组：不被其他组引用的组（GLOBAL 除外）
                val topLevel = groups.filter { it.name !in nested && it.name != "GLOBAL" }
                val start = topLevel.firstOrNull { !ProxyNames.isInfoNode(it.now.orEmpty()) }
                    ?: topLevel.firstOrNull()
                    ?: groups.firstOrNull { it.name == "自动选择" }
                    ?: groups.firstOrNull { it.name != "GLOBAL" }
                    ?: groups.firstOrNull()
                // 顶层组可能委托给子组，顺着 now 链找到最终节点
                var current = start?.now
                val seen = mutableSetOf<String>()
                while (current != null && seen.add(current)) {
                    val next = byName[current]
                    if (next?.isGroup == true) current = next.now else break
                }
                _currentGroup.value = start?.name
                _currentNode.value = current?.takeUnless { ProxyNames.isInfoNode(it) }
            }
    }
}
