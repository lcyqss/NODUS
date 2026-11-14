package com.mihomo.gateway.ui.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ProxyNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class ProxyGroupUi(
    val name: String,
    val type: String,
    val now: String?,
    val members: List<ProxyNode>
)

data class ProxiesUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val groups: List<ProxyGroupUi> = emptyList(),
    val selectedGroup: String? = null
)

class ProxiesViewModel(
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    private val _state = MutableStateFlow(ProxiesUiState())
    val state: StateFlow<ProxiesUiState> = _state.asStateFlow()

    private val _delays = MutableStateFlow<Map<String, Int>>(emptyMap())
    val delays: StateFlow<Map<String, Int>> = _delays.asStateFlow()

    private val _testing = MutableStateFlow<Set<String>>(emptySet())
    val testing: StateFlow<Set<String>> = _testing.asStateFlow()

    private val _failed = MutableStateFlow<Set<String>>(emptySet())
    val failed: StateFlow<Set<String>> = _failed.asStateFlow()

    private var testJob: Job? = null

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            api.proxies()
                .onSuccess { nodes ->
                    val byName = nodes.associateBy { it.name }
                    val groups = nodes
                        .filter { it.isGroup }
                        .map { g ->
                            ProxyGroupUi(
                                name = g.name,
                                type = g.type,
                                now = g.now,
                                members = g.all.mapNotNull { byName[it] }
                            )
                        }
                        .filter { it.members.isNotEmpty() }
                    val preferred = groups.firstOrNull { it.name == "自动选择" }
                        ?: groups.firstOrNull { it.name != "GLOBAL" }
                        ?: groups.firstOrNull()
                    val selected = _state.value.selectedGroup
                        ?.takeIf { name -> groups.any { it.name == name } }
                        ?: preferred?.name
                    _state.value = ProxiesUiState(
                        loading = false,
                        error = null,
                        groups = groups,
                        selectedGroup = selected
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun selectGroup(name: String) {
        _state.update { it.copy(selectedGroup = name) }
    }

    fun selectNode(group: String, node: String) {
        viewModelScope.launch {
            api.select(group, node).onSuccess { load() }
        }
    }

    fun testGroup(group: ProxyGroupUi) {
        testJob?.cancel()
        val nodes = group.members.filter { !it.isGroup }
        if (nodes.isEmpty()) return
        _testing.value = nodes.map { it.name }.toSet()
        testJob = viewModelScope.launch {
            val semaphore = Semaphore(5)
            coroutineScope {
                nodes.map { node ->
                    async {
                        semaphore.withPermit {
                            val delay = api.delay(node.name).getOrNull()
                            if (delay != null) {
                                _delays.update { it + (node.name to delay) }
                                _failed.update { it - node.name }
                            } else {
                                _failed.update { it + node.name }
                            }
                            _testing.update { it - node.name }
                        }
                    }
                }.awaitAll()
            }
        }
    }
}
