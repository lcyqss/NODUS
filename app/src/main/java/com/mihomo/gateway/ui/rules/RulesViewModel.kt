package com.mihomo.gateway.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.RuleRepository
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.CustomRule
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.model.RuleKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RulesUiState(
    val builtIn: List<RuleInfo> = emptyList(),
    val custom: List<CustomRule> = emptyList(),
    val conflicts: Set<String> = emptySet(),
    val targets: List<String> = emptyList(),
    val mode: String = "rule",
    val loading: Boolean = true,
    val applying: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

class RulesViewModel(
    private val api: MihomoApi = AppContainer.api,
    private val ruleRepository: RuleRepository = AppContainer.rules
) : ViewModel() {

    private val _state = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            ruleRepository.rules.collect { list ->
                _state.value = _state.value.copy(custom = list)
            }
        }
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val builtIn = api.rules().getOrElse {
                _state.value = _state.value.copy(loading = false, error = it.message)
                return@launch
            }
            val proxies = api.proxies().getOrNull().orEmpty()
            val targets = buildList {
                add("DIRECT")
                add("REJECT")
                proxies.filter { it.isGroup }.forEach { add(it.name) }
                proxies.filter { !it.isGroup }.take(80).forEach { add(it.name) }
            }.distinct()
            val mode = api.configs().getOrNull()?.mode ?: "rule"
            val conflicts = ruleRepository.conflicts()
            _state.value = RulesUiState(
                builtIn = builtIn,
                custom = ruleRepository.rules.value,
                conflicts = conflicts,
                targets = targets,
                mode = mode,
                loading = false
            )
        }
    }

    fun add(kind: RuleKind, value: String, target: String, noResolve: Boolean) {
        ruleRepository.add(kind, value, target, noResolve)
            .onSuccess { _state.value = _state.value.copy(message = "已添加，记得点“应用规则”") }
            .onFailure { _state.value = _state.value.copy(message = "添加失败: ${it.message}") }
    }

    fun addTemplate(name: String) {
        val items = TEMPLATES[name] ?: return
        items.forEach { (kind, value, target) ->
            ruleRepository.add(kind, value, target, kind.needsNoResolve)
        }
        _state.value = _state.value.copy(
            message = "已添加模板「$name」${items.size} 条，记得点“应用规则”"
        )
    }

    companion object {
        val TEMPLATE_NAMES = listOf("绕过局域网", "广告拦截", "常用直连")

        private val TEMPLATES: Map<String, List<Triple<RuleKind, String, String>>> = mapOf(
            "绕过局域网" to listOf(
                Triple(RuleKind.SRC_IP_CIDR, "192.168.0.0/16", "DIRECT"),
                Triple(RuleKind.SRC_IP_CIDR, "10.0.0.0/8", "DIRECT"),
                Triple(RuleKind.SRC_IP_CIDR, "172.16.0.0/12", "DIRECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "local", "DIRECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "lan", "DIRECT")
            ),
            "广告拦截" to listOf(
                Triple(RuleKind.DOMAIN_KEYWORD, "doubleclick", "REJECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "googlesyndication.com", "REJECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "adservice.google.com", "REJECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "ads.youtube.com", "REJECT")
            ),
            "常用直连" to listOf(
                Triple(RuleKind.DOMAIN_SUFFIX, "cn", "DIRECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "qq.com", "DIRECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "taobao.com", "DIRECT"),
                Triple(RuleKind.DOMAIN_SUFFIX, "bilibili.com", "DIRECT")
            )
        )
    }

    fun setEnabled(rule: CustomRule, enabled: Boolean) {
        ruleRepository.update(rule.copy(enabled = enabled))
    }

    fun delete(id: String) {
        ruleRepository.delete(id)
        _state.value = _state.value.copy(message = "已删除，记得点“应用规则”")
    }

    fun move(id: String, up: Boolean) {
        ruleRepository.move(id, up)
    }

    fun apply() {
        if (_state.value.applying) return
        viewModelScope.launch {
            _state.value = _state.value.copy(applying = true, message = null)
            val result = ruleRepository.apply()
            val conflicts = ruleRepository.conflicts()
            _state.value = _state.value.copy(
                applying = false,
                message = result.getOrElse { "应用失败: ${it.message}" },
                conflicts = conflicts
            )
        }
    }
}
