package com.mihomo.gateway.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.GatewayState
import com.mihomo.gateway.model.Traffic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverviewViewModel(
    private val gateway: GatewayController = AppContainer.gateway,
    private val api: MihomoApi = AppContainer.api,
    private val settings: SettingsStore = AppContainer.settings
) : ViewModel() {

    val state: StateFlow<GatewayState> = gateway.state

    private val _traffic = MutableStateFlow(Traffic(0, 0))
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _history = MutableStateFlow<List<Long>>(emptyList())
    val history: StateFlow<List<Long>> = _history.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { gateway.refresh() }
        viewModelScope.launch {
            api.trafficFlow().catch { }.collect { traffic ->
                _traffic.value = traffic
                _history.update { list -> (list + traffic.down).takeLast(60) }
            }
        }
    }

    /** 单一开关：运行中则停止，否则启动；同时持久化期望状态。 */
    fun toggle() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val running = gateway.state.value.running
            settings.update { it.copy(desiredRunning = !running) }
            if (running) gateway.stop() else gateway.start()
            _busy.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch { gateway.refresh() }
    }
}
