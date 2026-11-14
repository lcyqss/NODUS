package com.mihomo.gateway.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ProviderInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProvidersUiState(
    val loading: Boolean = true,
    val providers: List<ProviderInfo> = emptyList(),
    val error: String? = null,
    val busyProvider: String? = null,
    val message: String? = null
)

class ProvidersViewModel(
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    private val _state = MutableStateFlow(ProvidersUiState())
    val state: StateFlow<ProvidersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            api.providers()
                .onSuccess { list ->
                    _state.value = _state.value.copy(loading = false, providers = list)
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = it.message)
                }
        }
    }

    fun healthCheck(name: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busyProvider = name, message = null)
            api.providerHealthCheck(name)
                .onSuccess {
                    _state.value = _state.value.copy(busyProvider = null, message = "已触发健康检查: $name")
                }
                .onFailure {
                    _state.value = _state.value.copy(busyProvider = null, message = "失败: ${it.message}")
                }
        }
    }
}
