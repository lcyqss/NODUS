package com.mihomo.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.GatewayHealthChecker
import com.mihomo.gateway.core.HealthReport
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.ProfileRepository
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
    private val ruleRepository: RuleRepository = AppContainer.rules,
    private val healthChecker: GatewayHealthChecker = AppContainer.health
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _ipv6RaBlocked = MutableStateFlow(false)
    private val _health = MutableStateFlow<HealthReport?>(null)
    val health: StateFlow<HealthReport?> = _health.asStateFlow()
    val appUpdate: StateFlow<com.mihomo.gateway.model.AppUpdateInfo?> =
        AppContainer.appUpdates.available
    val appUpdateChecking: StateFlow<Boolean> = AppContainer.appUpdates.checking
    val appUpdateError: StateFlow<String?> = AppContainer.appUpdates.error
    val ipv6RaBlocked: StateFlow<Boolean> = _ipv6RaBlocked.asStateFlow()

    fun refreshIpv6Capability() {
        viewModelScope.launch {
            _ipv6RaBlocked.value = gateway.detectUpstreamRa()
        }
        viewModelScope.launch {
            val conf = runCatching { gateway.readConf() }.getOrNull() ?: return@launch
            store.update {
                if (it.ipv6Gateway == conf.ipv6Gateway && it.blockIpv6 == conf.blockIpv6) it
                else it.copy(ipv6Gateway = conf.ipv6Gateway, blockIpv6 = conf.blockIpv6)
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private val _runtimeMode = MutableStateFlow("rule")
    val runtimeMode: StateFlow<String> = _runtimeMode.asStateFlow()

    init {
        viewModelScope.launch {
            api.configs().onSuccess { _runtimeMode.value = it.mode }
        }
        viewModelScope.launch {
            _ipv6RaBlocked.value = gateway.detectUpstreamRa()
        }
        viewModelScope.launch {
            val conf = runCatching { gateway.readConf() }.getOrNull() ?: return@launch
            store.update {
                if (it.ipv6Gateway == conf.ipv6Gateway && it.blockIpv6 == conf.blockIpv6) it
                else it.copy(ipv6Gateway = conf.ipv6Gateway, blockIpv6 = conf.blockIpv6)
            }
        }
    }

    fun refreshHealth() {
        viewModelScope.launch {
            if (!gateway.state.value.running) {
                _health.value = HealthReport(
                    dns = false,
                    ports = false,
                    proxy = false,
                    ipv6 = false,
                    tproxy = false,
                    dnsDomains = false,
                    rules = false,
                    detail = "网关未运行"
                )
                store.update { it.copy(lastHealthAt = System.currentTimeMillis(), lastHealthOk = false) }
                return@launch
            }
            val report = healthChecker.check(settings.value.ipv6Gateway)
            _health.value = report
            store.update {
                it.copy(
                    lastHealthAt = System.currentTimeMillis(),
                    lastHealthOk = report.ok
                )
            }
        }
    }

    fun checkAppUpdate() {
        viewModelScope.launch {
            AppContainer.appUpdates.checkIfDue(force = true)
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

    fun setIpv6Gateway(enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && _ipv6RaBlocked.value) {
                _message.value = "检测到主路由 RA，旁路由下不能开启 IPv6 代理"
                return@launch
            }
            _busy.value = true
            val result = gateway.setIpv6Gateway(enabled)
            if (result.isSuccess) {
                store.update {
                    it.copy(
                        ipv6Gateway = enabled,
                        blockIpv6 = if (enabled) false else true
                    )
                }
            }
            _message.value = if (result.isSuccess) {
                if (enabled) "局域网 IPv6 代理已开启" else "局域网 IPv6 代理已关闭"
            } else {
                "设置失败: ${result.exceptionOrNull()?.message}"
            }
            _ipv6RaBlocked.value = gateway.detectUpstreamRa()
            _busy.value = false
        }
    }

    fun setBlockIpv6(enabled: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = gateway.setBlockIpv6(enabled)
            if (result.isSuccess) {
                store.update { it.copy(blockIpv6 = enabled) }
            }
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
            .put("version", 2)
            .put(
                "settings",
                org.json.JSONObject()
                    .put("subscriptionUrl", current.subscriptionUrl)
                    .put("subName", current.subName)
                    .put("subscriptions", current.subscriptions)
                    .put("activeSubId", current.activeSubId)
                    .put("ipMode", current.ipMode.name)
                    .put("blockIpv6", current.blockIpv6)
                    .put("ipv6Gateway", current.ipv6Gateway)
                    .put("updateIntervalHours", current.updateIntervalHours)
                    .put("autoStart", current.autoStart)
                    .put("geoAutoUpdate", current.geoAutoUpdate)
                    .put("geoUpdateIntervalDays", current.geoUpdateIntervalDays)
                    .put("favoriteNodes", current.favoriteNodes)
                    .put("devicePolicies", current.devicePolicies)
                    .put("nodeTags", current.nodeTags)
                    .put("conditionTemplates", current.conditionTemplates)
                    .put("policyIntervalSec", current.policyIntervalSec)
                    .put("ipFollowIntervalSec", current.ipFollowIntervalSec)
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
                    val activeId = s.optString("activeSubId", settings.value.activeSubId)
                    val restoredSubs = s.optString("subscriptions", settings.value.subscriptions)
                    val activeName = com.mihomo.gateway.model.SubscriptionCodec.decode(restoredSubs)
                        .firstOrNull { it.id == activeId }
                        ?.name
                        ?: s.optString("subName", settings.value.subName)
                    store.update { current ->
                        current.copy(
                            subscriptionUrl = s.optString("subscriptionUrl", current.subscriptionUrl),
                            subName = activeName,
                            subscriptions = restoredSubs,
                            activeSubId = activeId,
                            ipMode = runCatching {
                                com.mihomo.gateway.model.IpMode.valueOf(
                                    s.optString("ipMode", current.ipMode.name)
                                )
                            }.getOrDefault(current.ipMode),
                            blockIpv6 = s.optBoolean("blockIpv6", current.blockIpv6),
                            ipv6Gateway = s.optBoolean("ipv6Gateway", current.ipv6Gateway),
                            updateIntervalHours = s.optInt("updateIntervalHours", current.updateIntervalHours),
                            autoStart = s.optBoolean("autoStart", current.autoStart),
                            geoAutoUpdate = s.optBoolean("geoAutoUpdate", current.geoAutoUpdate),
                            geoUpdateIntervalDays = s.optInt(
                                "geoUpdateIntervalDays",
                                current.geoUpdateIntervalDays
                            ),
                            favoriteNodes = s.optString("favoriteNodes", current.favoriteNodes),
                            devicePolicies = s.optString("devicePolicies", current.devicePolicies),
                            nodeTags = s.optString("nodeTags", current.nodeTags),
                            conditionTemplates = s.optString(
                                "conditionTemplates",
                                current.conditionTemplates
                            ),
                            policyIntervalSec = s.optInt("policyIntervalSec", current.policyIntervalSec),
                            ipFollowIntervalSec = s.optInt(
                                "ipFollowIntervalSec",
                                current.ipFollowIntervalSec
                            )
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
                val activeProfile = com.mihomo.gateway.model.SubscriptionCodec
                    .decode(restored.subscriptions)
                    .firstOrNull { it.id == restored.activeSubId }
                if (activeProfile != null) {
                    profiles.switchTo(activeProfile)
                        .onFailure { throw IllegalStateException("订阅恢复失败: ${it.message}") }
                    ensureActiveNodes(activeProfile)
                }
                runCatching { gateway.setIpMode(restored.ipMode) }
                runCatching { gateway.setBlockIpv6(restored.blockIpv6) }
                runCatching { gateway.setIpv6Gateway(restored.ipv6Gateway) }
                runCatching { ruleRepository.apply() }
                    .onFailure { throw IllegalStateException("规则恢复后应用失败: ${it.message}") }
                runCatching { AppContainer.devicePolicies.apply() }
                    .onFailure { throw IllegalStateException("设备策略恢复后应用失败: ${it.message}") }
            }
            _message.value = result.fold(
                onSuccess = { "配置已恢复" },
                onFailure = { "恢复失败: ${it.message}" }
            )
        }
    }

    /**
     * 备份只保存订阅元数据，缓存文件可能在删除订阅时被清掉。
     * 恢复后如果节点目录为空，重新下载一次；仍然为空则明确报错，
     * 避免设备策略把「节点未加载」误显示成「引用的标签已被删除」。
     */
    private suspend fun ensureActiveNodes(profile: com.mihomo.gateway.model.SubscriptionProfile) {
        if (AppContainer.nodeCatalog.nodes().isNotEmpty()) return
        val fetched = profiles.fetchToCache(profile).getOrElse {
            throw IllegalStateException("订阅节点缺失且重新下载失败: ${it.message}")
        }
        if (fetched.nodeCount == 0 && fetched.providers.isEmpty()) {
            throw IllegalStateException("订阅没有返回任何节点，请检查订阅链接")
        }
        profiles.installFromCache(profile).onFailure {
            throw IllegalStateException("订阅缓存安装失败: ${it.message}")
        }
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "global" -> "全局"
        "direct" -> "直连"
        else -> "规则"
    }
}
