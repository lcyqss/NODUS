package com.mihomo.gateway.ui.policy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.catalog.DeviceTarget
import com.mihomo.gateway.data.catalog.NodeInfo
import com.mihomo.gateway.data.policy.DevicePolicyRepository
import com.mihomo.gateway.data.policy.PolicyBackupService
import com.mihomo.gateway.data.policy.PolicyRuntime
import com.mihomo.gateway.data.tags.ConditionTemplateRepository
import com.mihomo.gateway.data.tags.NodeTagRepository
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.ConditionTemplate
import com.mihomo.gateway.model.DevicePolicy
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.model.TagExpr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DevicePolicyViewModel(
    private val repo: DevicePolicyRepository = AppContainer.devicePolicies,
    private val tagRepo: NodeTagRepository = AppContainer.nodeTags,
    private val templateRepo: ConditionTemplateRepository = AppContainer.conditionTemplates,
    private val backupService: PolicyBackupService = AppContainer.policyBackup
) : ViewModel() {

    val policies: StateFlow<List<DevicePolicy>> = repo.policies
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _runtime = MutableStateFlow<List<PolicyRuntime>>(emptyList())
    val runtime: StateFlow<List<PolicyRuntime>> = _runtime.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceTarget>>(emptyList())
    val devices: StateFlow<List<DeviceTarget>> = _devices.asStateFlow()

    private val _nodes = MutableStateFlow<List<NodeInfo>>(emptyList())
    val nodes: StateFlow<List<NodeInfo>> = _nodes.asStateFlow()

    private val _tags = MutableStateFlow<List<NodeTag>>(emptyList())
    val tags: StateFlow<List<NodeTag>> = _tags.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val templates: StateFlow<List<ConditionTemplate>> = templateRepo.templates
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _backupJson = MutableStateFlow("")
    val backupJson: StateFlow<String> = _backupJson.asStateFlow()

    private val _backupMessage = MutableStateFlow<String?>(null)
    val backupMessage: StateFlow<String?> = _backupMessage.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val intervalSec: StateFlow<Int> = AppContainer.settings.settings
        .map { it.policyIntervalSec }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)

    val ipIntervalSec: StateFlow<Int> = AppContainer.settings.settings
        .map { it.ipFollowIntervalSec }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 60)

    fun setInterval(seconds: Int) {
        viewModelScope.launch {
            AppContainer.settings.update { it.copy(policyIntervalSec = seconds) }
        }
    }

    fun setIpInterval(seconds: Int) {
        viewModelScope.launch {
            AppContainer.settings.update { it.copy(ipFollowIntervalSec = seconds) }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _busy.value = true
            val nodes = runCatching { repo.nodes() }.getOrDefault(emptyList())
            _nodes.value = nodes
            _tags.value = runCatching { repo.tags() }.getOrDefault(emptyList())
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
                .onFailure { _message.value = "应用失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun saveTag(tag: NodeTag) {
        viewModelScope.launch {
            tagRepo.save(tag)
                .onSuccess {
                    _tags.value = repo.tags()
                    _message.value = "标签已保存"
                }
                .onFailure { _message.value = "标签保存失败: ${it.message}" }
        }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch {
            tagRepo.delete(id)
            _tags.value = repo.tags()
            _message.value = "标签已删除"
        }
    }

    /** 条件命中的节点数，用于 UI 预览。 */
    fun matchCount(expr: TagExpr?): Int =
        _nodes.value.count { node -> tagRepo.matches(_tags.value, expr, node) }

    fun countTag(tag: NodeTag): Int =
        _nodes.value.count { node -> tagRepo.matches(_tags.value, TagExpr.Ref(tag.id), node) }

    fun saveTemplate(name: String, expr: TagExpr) {
        viewModelScope.launch {
            templateRepo.save(name, expr)
                .onSuccess { _message.value = "模板已保存" }
                .onFailure { _message.value = "模板保存失败: ${it.message}" }
        }
    }

    fun deleteTemplate(id: String) {
        viewModelScope.launch { templateRepo.delete(id) }
    }

    fun renameTemplate(id: String, name: String) {
        viewModelScope.launch {
            templateRepo.rename(id, name)
                .onSuccess { _message.value = "模板已重命名" }
                .onFailure { _message.value = "重命名失败: ${it.message}" }
        }
    }

    fun moveTemplate(id: String, up: Boolean) {
        viewModelScope.launch { templateRepo.move(id, up) }
    }

    fun exportBackup() {
        viewModelScope.launch {
            runCatching { backupService.export() }
                .onSuccess {
                    _backupJson.value = it
                    _backupMessage.value = null
                }
                .onFailure { _backupMessage.value = "导出失败: ${it.message}" }
        }
    }

    fun importBackup(raw: String, merge: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            _backupMessage.value = if (merge) "正在合并导入…" else "正在覆盖导入…"
            backupService.import(raw, merge)
                .onSuccess { summary ->
                    _backupMessage.value =
                        "已导入 ${summary.policies} 条策略、${summary.tags} 个标签、${summary.templates} 个模板"
                    refresh()
                    runCatching { repo.apply() }
                }
                .onFailure { _backupMessage.value = "导入失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun matchNodes(expr: TagExpr?): List<NodeInfo> =
        _nodes.value.filter { node -> tagRepo.matches(_tags.value, expr, node) }
}
