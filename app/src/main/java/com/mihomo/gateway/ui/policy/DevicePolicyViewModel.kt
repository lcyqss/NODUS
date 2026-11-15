package com.mihomo.gateway.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.data.policy.DevicePolicyRepository
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.DevicePolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicePolicyViewModel(
    private val repo: DevicePolicyRepository = AppContainer.devicePolicies
) : ViewModel() {

    val policies: StateFlow<List<DevicePolicy>> = repo.policies
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _runtime = MutableStateFlow<List<PolicyRuntime>>(emptyList())
    val runtime: StateFlow<List<PolicyRuntime>> = _runtime.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceTarget>>(emptyList())
    val devices: StateFlow<List<DeviceTarget>> = _devices.asStateFlow()

    private val _nodes = MutableStateFlow<List<NodeInfo>>(emptyList())
    val nodes: StateFlow<List<NodeInfo>> = _nodes.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            _nodes.value = runCatching { repo.nodes() }.getOrDefault(emptyList())
            _devices.value = runCatching { repo.devices() }.getOrDefault(emptyList())
            _runtime.value = runCatching { repo.runtime() }.getOrDefault(emptyList())
            _busy.value = false
        }
    }

    fun save(policy: DevicePolicy) {
        viewModelScope.launch {
            _busy.value = true
            repo.save(policy)
                .onSuccess {
                    _message.value = it
                    _runtime.value = repo.runtime()
                    _devices.value = repo.devices()
                }
                .onFailure { _message.value = "保存失败: ${it.message}" }
            _busy.value = false
        }
    }

    /** 重新解析设备 IP 并重新应用（设备离线/上线后手动刷新）。 */
    fun reapply() {
        viewModelScope.launch {
            _busy.value = true
            repo.apply()
                .onSuccess {
                    _message.value = it
                    _runtime.value = repo.runtime()
                    _devices.value = repo.devices()
                }
                .onFailure { _message.value = "应用失败: " }
            _busy.value = false
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            _busy.value = true
            repo.delete(id)
                .onSuccess {
                    _message.value = it
                    _runtime.value = repo.runtime()
                }
                .onFailure { _message.value = "删除失败: ${it.message}" }
            _busy.value = false
        }
    }
}
