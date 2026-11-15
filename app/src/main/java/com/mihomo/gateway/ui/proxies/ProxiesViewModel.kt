package com.mihomo.gateway.ui.proxies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.data.store.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.mihomo.gateway.model.ProxyNames
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
    private val api: MihomoApi = AppContainer.api,
    private val settings: SettingsStore = AppContainer.settings
) : ViewModel() {

    val favorites: StateFlow<Set<String>> = settings.settings
        .map { decodeFavorites(it.favoriteNodes) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggleFavorite(name: String) {
        viewModelScope.launch {
            settings.update { current ->
                val set = decodeFavorites(current.favoriteNodes).toMutableSet()
                if (set.contains(name)) set.remove(name) else set.add(name)
                current.copy(favoriteNodes = encodeFavorites(set))
            }
        }
    }

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
        fetch(showLoading = true)
    }

    /** 静默刷新：不显示整页 loading（用于测速/订阅切换后的状态同步）。 */
    private fun refreshQuietly() {
        fetch(showLoading = false)
    }

    private fun fetch(showLoading: Boolean) {
        viewModelScope.launch {
            if (showLoading) {
                _state.value = _state.value.copy(loading = true, error = null)
            }
            api.proxies()
                .onSuccess { nodes ->
                    val visible = nodes.filterNot { ProxyNames.isInfoNode(it.name) }
                    val byName = visible.associateBy { it.name }
                    val groups = visible
                        .filter { it.isGroup && !it.name.startsWith("__dev_") }
                        .map { g ->
                            ProxyGroupUi(
                                name = g.name,
                                type = g.type,
                                now = g.now?.takeUnless { ProxyNames.isInfoNode(it) },
                                members = g.all.mapNotNull { byName[it] }
                                    .filterNot { ProxyNames.isInfoNode(it.name) }
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
                    if (showLoading) {
                        _state.value = _state.value.copy(loading = false, error = it.message)
                    }
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

    /**
     * 优先使用内核的整组测速（一次请求返回全部延迟），
     * 失败时回退到逐个测速（并发 5）。
     */
    fun testGroup(group: ProxyGroupUi) {
        testJob?.cancel()
        val nodes = group.members.filter { !it.isGroup }
        if (nodes.isEmpty()) return
        _testing.value = nodes.map { it.name }.toSet()
        testJob = viewModelScope.launch {
            val result = api.groupDelay(group.name)
            result.onSuccess { delays ->
                val ok = delays.filterValues { it > 0 }
                val bad = delays.filterValues { it <= 0 }.keys
                _delays.update { it + ok }
                _failed.update { current -> (current - ok.keys) + bad }
            }.onFailure {
                testOneByOne(nodes)
            }
            _testing.value = emptySet()
            // 测速后 URLTest 组可能已选出新节点，静默刷新让「当前节点」跟上
            refreshQuietly()
        }
    }

    private suspend fun testOneByOne(nodes: List<ProxyNode>) {
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
private fun decodeFavorites(raw: String): Set<String> {
    if (raw.isBlank()) return emptySet()
    return runCatching {
        if (raw.trimStart().startsWith("[")) {
            val array = org.json.JSONArray(raw)
            (0 until array.length()).map { array.optString(it) }.filter(String::isNotEmpty).toSet()
        } else {
            raw.split(',').map(String::trim).filter(String::isNotEmpty).toSet()
        }
    }.getOrDefault(emptySet())
}

private fun encodeFavorites(values: Set<String>): String {
    val array = org.json.JSONArray()
    values.forEach { array.put(it) }
    return array.toString()
}