package com.mihomo.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.model.SubscriptionCodec
import com.mihomo.gateway.model.SubscriptionProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.mihomo.gateway.data.repo.RuleRepository
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val store: SettingsStore = AppContainer.settings,
    private val gateway: GatewayController = AppContainer.gateway,
    private val profiles: ProfileRepository = AppContainer.profiles,
    private val api: MihomoApi = AppContainer.api,
    private val ruleRepository: RuleRepository = AppContainer.rules
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

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
            _message.value = if (result.isSuccess) "已切换: ${mode.label}"
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

    fun cleanup() {
        viewModelScope.launch {
            _busy.value = true
            val result = gateway.cleanup()
            _message.value = result.fold(
                onSuccess = { "已停止网关并清理 /data/adb/mihomo，可以卸载 App 了" },
                onFailure = { "清理失败: ${it.message}" }
            )
            _busy.value = false
        }
    }

    val subscriptions: kotlinx.coroutines.flow.StateFlow<List<SubscriptionProfile>> =
        store.settings
            .map { SubscriptionCodec.decode(it.subscriptions) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addSubscription(name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _message.value = "订阅链接不能为空"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            profiles.import(trimmed).onSuccess {
                val current = settings.value
                val list = SubscriptionCodec.decode(current.subscriptions).toMutableList()
                val profile = SubscriptionProfile(
                    id = java.util.UUID.randomUUID().toString(),
                    name = name.ifBlank { "订阅 ${list.size + 1}" },
                    url = trimmed,
                    lastUpdateAt = current.lastUpdateAt,
                    nodeCount = current.nodeCount
                )
                list.removeAll { it.url == trimmed }
                list.add(profile)
                store.update {
                    it.copy(
                        subscriptions = SubscriptionCodec.encode(list),
                        activeSubId = profile.id,
                        subName = profile.name
                    )
                }
                _message.value = "已添加并切换到「${profile.name}」"
            }.onFailure { _message.value = "添加失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun activateSubscription(profile: SubscriptionProfile) {
        viewModelScope.launch {
            _busy.value = true
            profiles.import(profile.url).onSuccess {
                val current = settings.value
                val list = SubscriptionCodec.decode(current.subscriptions).map {
                    if (it.id == profile.id) {
                        it.copy(
                            lastUpdateAt = current.lastUpdateAt,
                            nodeCount = current.nodeCount
                        )
                    } else {
                        it
                    }
                }
                store.update {
                    it.copy(
                        subscriptions = SubscriptionCodec.encode(list),
                        activeSubId = profile.id,
                        subName = profile.name
                    )
                }
                _message.value = "已切换到「${profile.name}」"
            }.onFailure { _message.value = "切换失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun deleteSubscription(profile: SubscriptionProfile) {
        viewModelScope.launch {
            val current = settings.value
            val wasActive = current.activeSubId == profile.id
            val list = SubscriptionCodec.decode(current.subscriptions).filterNot { it.id == profile.id }
            store.update {
                it.copy(
                    subscriptions = SubscriptionCodec.encode(list),
                    activeSubId = if (wasActive) "" else it.activeSubId,
                    subscriptionUrl = if (wasActive) "" else it.subscriptionUrl,
                    lastUpdateAt = if (wasActive) 0L else it.lastUpdateAt,
                    nodeCount = if (wasActive) 0 else it.nodeCount
                )
            }
        }
    }
    fun buildBackup(): String {
        val current = settings.value
        val rules = org.json.JSONArray()
        ruleRepository.rules.value.forEach { rule ->
            rules.put(
                org.json.JSONObject()
                    .put("kind", rule.kind.wire)
                    .put("value", rule.value)
                    .put("target", rule.target)
                    .put("enabled", rule.enabled)
                    .put("noResolve", rule.noResolve)
            )
        }
        return org.json.JSONObject()
            .put("version", 1)
            .put(
                "settings",
                org.json.JSONObject()
                    .put("subscriptionUrl", current.subscriptionUrl)
                    .put("subName", current.subName)
                    .put("ipMode", current.ipMode.name)
                    .put("blockIpv6", current.blockIpv6)
                    .put("updateIntervalHours", current.updateIntervalHours)
            )
            .put("rules", rules)
            .toString(2)
    }

    fun restoreBackup(json: String) {
        viewModelScope.launch {
            val result = runCatching {
                val root = org.json.JSONObject(json)
                val s = root.optJSONObject("settings")
                if (s != null) {
                    store.update { current ->
                        current.copy(
                            subscriptionUrl = s.optString("subscriptionUrl", current.subscriptionUrl),
                            subName = s.optString("subName", current.subName),
                            ipMode = runCatching {
                                com.mihomo.gateway.model.IpMode.valueOf(
                                    s.optString("ipMode", current.ipMode.name)
                                )
                            }.getOrDefault(current.ipMode),
                            blockIpv6 = s.optBoolean("blockIpv6", current.blockIpv6),
                            updateIntervalHours = s.optInt("updateIntervalHours", current.updateIntervalHours)
                        )
                    }
                }
                val rules = root.optJSONArray("rules")
                if (rules != null) {
                    for (i in 0 until rules.length()) {
                        val r = rules.optJSONObject(i) ?: continue
                        val kind = com.mihomo.gateway.model.RuleKind.fromWire(r.optString("kind")) ?: continue
                        val value = r.optString("value")
                        ruleRepository.add(
                            kind,
                            value,
                            r.optString("target"),
                            r.optBoolean("noResolve", kind.needsNoResolve)
                        )
                        if (!r.optBoolean("enabled", true)) {
                            ruleRepository.rules.value
                                .firstOrNull { it.kind == kind && it.value == value.trim() }
                                ?.let { ruleRepository.update(it.copy(enabled = false)) }
                        }
                    }
                }

                // 恢复后把地址族 / IPv6 拦截真正应用到网关
                val restored = settings.value
                runCatching { gateway.setIpMode(restored.ipMode) }
                runCatching { gateway.setBlockIpv6(restored.blockIpv6) }
            }
            _message.value = result.fold(
                onSuccess = { "配置已恢复" },
                onFailure = { "恢复失败: ${it.message}" }
            )
        }
    }

    fun setSubName(name: String) {
        viewModelScope.launch {
            store.update { it.copy(subName = name) }
        }
    }

    fun setUpdateInterval(hours: Int) {
        viewModelScope.launch {
            store.update { it.copy(updateIntervalHours = hours) }
            _message.value = if (hours <= 0) "自动更新: 关闭" else "自动更新: 每 ${hours} 小时"
        }
    }

    fun updateNow() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "更新中…"
            val result = profiles.autoUpdate()
            _message.value = result.fold(
                onSuccess = { "订阅已更新" },
                onFailure = { "更新失败: ${it.message}" }
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
