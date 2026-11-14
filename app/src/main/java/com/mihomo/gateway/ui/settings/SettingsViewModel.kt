package com.mihomo.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore = AppContainer.settings,
    private val gateway: GatewayController = AppContainer.gateway,
    private val profiles: ProfileRepository = AppContainer.profiles,
    private val api: MihomoApi = AppContainer.api
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _runtimeMode = MutableStateFlow("rule")
    val runtimeMode: StateFlow<String> = _runtimeMode.asStateFlow()

    init {
        viewModelScope.launch {
            api.configs().onSuccess { _runtimeMode.value = it.mode }
        }
    }

    fun setRuntimeMode(mode: String) {
        viewModelScope.launch {
            _busy.value = true
            api.setMode(mode)
                .onSuccess {
                    _runtimeMode.value = mode
                    _message.value = "运行模式: ${modeLabel(mode)}"
                }
                .onFailure { _message.value = "切换失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun setMode(mode: IpMode) {
        viewModelScope.launch {
            _busy.value = true
            store.update { it.copy(ipMode = mode) }
            val result = gateway.setIpMode(mode)
            _message.value = if (result.isSuccess) "已切换: ${mode.name}"
            else "切换失败: ${result.exceptionOrNull()?.message}"
            _busy.value = false
        }
    }

    fun setBlockIpv6(enabled: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            store.update { it.copy(blockIpv6 = enabled) }
            val result = gateway.setBlockIpv6(enabled)
            _message.value = if (result.isSuccess) "IPv6 拦截: ${if (enabled) "开" else "关"}"
            else "设置失败: ${result.exceptionOrNull()?.message}"
            _busy.value = false
        }
    }

    fun setAutoStart(enabled: Boolean) {
        viewModelScope.launch {
            store.update { it.copy(autoStart = enabled) }
            _message.value = if (enabled) "开机自启: 开" else "开机自启: 关"
        }
    }

    fun importSubscription(url: String) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "导入中…"
            val result = profiles.import(url)
            _message.value = result.fold(
                onSuccess = { "订阅导入成功" },
                onFailure = { "导入失败: ${it.message}" }
            )
            _busy.value = false
        }
    }

    fun installCore() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "安装中…"
            val result = profiles.installCore()
            _message.value = result.fold(
                onSuccess = { "核心: $it" },
                onFailure = { "安装失败: ${it.message}" }
            )
            _busy.value = false
        }
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "global" -> "全局"
        "direct" -> "直连"
        else -> "规则"
    }
}
