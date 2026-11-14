package com.mihomo.gateway.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.GatewayState
import com.mihomo.gateway.model.Traffic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class OverviewViewModel(
    private val gateway: GatewayController = AppContainer.gateway,
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    val state: StateFlow<GatewayState> = gateway.state

    private val _traffic = MutableStateFlow(Traffic(0, 0))
    val traffic: StateFlow<Traffic> = _traffic.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        viewModelScope.launch { gateway.refresh() }
        viewModelScope.launch {
            api.trafficFlow().catch { }.collect { _traffic.value = it }
        }
    }

    fun start() {
        viewModelScope.launch {
            _busy.value = true
            gateway.start()
            _busy.value = false
        }
    }

    fun stop() {
        viewModelScope.launch {
            _busy.value = true
            gateway.stop()
            _busy.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch { gateway.refresh() }
    }
}
