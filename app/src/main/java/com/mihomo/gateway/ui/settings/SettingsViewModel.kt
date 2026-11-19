package com.mihomo.gateway.ui.settings

import com.mihomo.gateway.core.AppConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.GatewayHealthChecker
import com.mihomo.gateway.core.HealthReport
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.backup.BackupCodec
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

    fun setRuntimeMode(mode: String) {
        viewModelScope.launch {
            _busy.value = true
            gateway.setRuntimeMode(mode)
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

    fun setDirectPrivateLan(enabled: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = gateway.setDirectPrivateLan(enabled)
            if (result.isSuccess) {
                store.update { it.copy(directPrivateLan = enabled) }
            }
            _message.value = if (result.isSuccess) {
                if (enabled) "内网直连已开启" else "内网直连已关闭"
            } else {
                "设置失败: ${result.exceptionOrNull()?.message}"
            }
            _busy.value = false
        }
    }

    fun setUdpProxy(enabled: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = gateway.setUdpProxy(enabled)
            if (result.isSuccess) {
                store.update { it.copy(udpProxy = enabled) }
            }
            _message.value = if (result.isSuccess) {
                if (enabled) "UDP/QUIC 代理已开启" else "UDP/QUIC 代理已关闭"
            } else {
                "设置失败: ${result.exceptionOrNull()?.message}"
            }
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
                onSuccess = { "已停止网关并清理 ${AppConfig.BASE_DIR}，可以卸载 App 了" },
                onFailure = { "清理失败: ${it.message}" }
            )
            _busy.value = false
        }
    }

    fun buildBackup(): String =
        BackupCodec.encode(settings.value, ruleRepository.rules.value, _runtimeMode.value)

    fun restoreBackup(json: String) {
        viewModelScope.launch {
            val result = runCatching {
                val backup = BackupCodec.decode(json)
                    .getOrElse { throw IllegalArgumentException(it.message) }

                backup.settings?.let { s ->
                    val current = settings.value
                    val restoredSubs = s.subscriptions ?: current.subscriptions
                    val activeId = s.activeSubId ?: current.activeSubId
                    val activeName = BackupCodec.activeSubName(
                        restoredSubs,
                        activeId,
                        s.subName ?: current.subName
                    )
                    store.update { c ->
                        c.copy(
                            subscriptionUrl = s.subscriptionUrl ?: c.subscriptionUrl,
                            subName = activeName,
                            subscriptions = restoredSubs,
                            activeSubId = activeId,
                            ipMode = s.ipMode
                                ?.let { raw -> runCatching { IpMode.valueOf(raw) }.getOrNull() }
                                ?: c.ipMode,
                            blockIpv6 = s.blockIpv6 ?: c.blockIpv6,
                            ipv6Gateway = s.ipv6Gateway ?: c.ipv6Gateway,
                            updateIntervalHours = s.updateIntervalHours ?: c.updateIntervalHours,
                            autoStart = s.autoStart ?: c.autoStart,
                            geoAutoUpdate = s.geoAutoUpdate ?: c.geoAutoUpdate,
                            geoUpdateIntervalDays = s.geoUpdateIntervalDays ?: c.geoUpdateIntervalDays,
                            favoriteNodes = s.favoriteNodes ?: c.favoriteNodes,
                            devicePolicies = s.devicePolicies ?: c.devicePolicies,
                            nodeTags = s.nodeTags ?: c.nodeTags,
                            conditionTemplates = s.conditionTemplates ?: c.conditionTemplates,
                            policyIntervalSec = s.policyIntervalSec ?: c.policyIntervalSec,
                            ipFollowIntervalSec = s.ipFollowIntervalSec ?: c.ipFollowIntervalSec
                        )
                    }
                }

                backup.rules.forEach { rule ->
                    ruleRepository.add(rule.kind, rule.value, rule.target, rule.noResolve)
                    if (!rule.enabled) {
                        ruleRepository.rules.value
                            .firstOrNull { it.kind == rule.kind && it.value == rule.value }
                            ?.let { ruleRepository.update(it.copy(enabled = false)) }
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
                // 先恢复 IPv6 代理、再恢复拦截：关闭代理会强制打开拦截，
                // 顺序反了会把备份里的「拦截=关」覆盖成「开」。
                runCatching { gateway.setIpv6Gateway(restored.ipv6Gateway) }
                if (!restored.ipv6Gateway) {
                    runCatching { gateway.setBlockIpv6(restored.blockIpv6) }
                }
                backup.settings?.runtimeMode?.let { mode ->
                    gateway.setRuntimeMode(mode)
                        .onFailure { throw IllegalStateException("运行模式恢复失败: ${it.message}") }
                    _runtimeMode.value = mode
                }
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
