package com.mihomo.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.repo.GeoDataRepository
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
    private val geoData: GeoDataRepository = AppContainer.geoData,
    private val api: MihomoApi = AppContainer.api,
    private val ruleRepository: RuleRepository = AppContainer.rules,
    private val core: CoreInstaller = AppContainer.core,
    private val shell: ShellExecutor = AppContainer.shell
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _ipv6RaBlocked = MutableStateFlow(false)
    private val _coreVersion = MutableStateFlow("")
    private val _corePrevious = MutableStateFlow("")
    val coreVersion: StateFlow<String> = _coreVersion.asStateFlow()
    val corePrevious: StateFlow<String> = _corePrevious.asStateFlow()
    private val _geoVersion = MutableStateFlow("")
    private val _geoProgress = MutableStateFlow<Int?>(null)
    val geoVersion: StateFlow<String> = _geoVersion.asStateFlow()
    val geoProgress: StateFlow<Int?> = _geoProgress.asStateFlow()
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

    fun refreshComponentVersions() {
        viewModelScope.launch {
            val coreLine = shell.run("/data/adb/mihomo/mihomo -v 2>/dev/null", 10_000)
                .stdout.lineSequence().firstOrNull { it.isNotBlank() }
                .orEmpty().trim()
            val coreName = coreLine.split(' ')
                .firstOrNull { it.startsWith("v") && it.getOrNull(1)?.isDigit() == true }
                ?: coreLine
            val geo = shell.run("cat /data/adb/mihomo/.geo_version 2>/dev/null", 10_000)
                .stdout.trim()
            _corePrevious.value = runCatching { core.previousVersion() }.getOrDefault("")
            _coreVersion.value = coreName.ifBlank { "未知" }
            _geoVersion.value = geo.ifBlank { "未知" }
        }
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

    fun installCore() {
        viewModelScope.launch {
            _busy.value = true
            _message.value = "安装中…"
            val wasRunning = gateway.state.value.running
            if (wasRunning) gateway.stop()
            val result = profiles.installCore(force = true)
            if (result.isFailure) {
                if (wasRunning) gateway.start()
                _message.value = "安装失败: ${result.exceptionOrNull()?.message}"
            } else if (!wasRunning) {
                _message.value = "核心已强制重装: ${result.getOrNull()}"
            } else {
                val started = gateway.start()
                if (started.isSuccess) {
                    _message.value = "核心已强制重装: ${result.getOrNull()}"
                } else {
                    val previous = core.previousVersion()
                    if (previous.isBlank()) {
                        _message.value = "新内核启动失败，且没有可回退版本"
                    } else {
                        val rolledBack = core.rollbackToPrevious()
                        if (rolledBack.isSuccess) {
                            gateway.start()
                            _message.value = "新内核启动失败，已回退到 $previous"
                        } else {
                            _message.value = "新内核启动失败，回退也失败: ${rolledBack.exceptionOrNull()?.message}"
                        }
                    }
                }
            }
            refreshComponentVersions()
            _busy.value = false
        }
    }

    fun rollbackCore() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = "正在回退内核…"
            val previous = core.previousVersion()
            if (previous.isBlank()) {
                _message.value = "没有可回退的内核"
                _busy.value = false
                return@launch
            }
            val wasRunning = gateway.state.value.running
            if (wasRunning) gateway.stop()
            val result = core.rollbackToPrevious()
            if (wasRunning) gateway.start()
            _message.value = result.fold(
                onSuccess = { "已回退到 $it" },
                onFailure = { "回退失败: ${it.message}" }
            )
            refreshComponentVersions()
            _busy.value = false
        }
    }

    fun updateGeo() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = "正在更新 Geo 数据…"
            val wasRunning = gateway.state.value.running
            _geoProgress.value = 0
            val result = geoData.update(useProxy = wasRunning) { done, total ->
                _geoProgress.value = if (total > 0) {
                    ((done * 100) / total).toInt().coerceIn(0, 100)
                } else {
                    -1
                }
            }
            _geoProgress.value = null
            val changed = result.getOrNull()?.unchanged != true
            if (wasRunning && changed) {
                gateway.stop()
                gateway.start()
            }
            val now = System.currentTimeMillis()
            store.update {
                it.copy(
                    lastGeoUpdateAt = if (result.isSuccess) now else it.lastGeoUpdateAt,
                    geoLastCheckAt = now,
                    geoLastResult = if (result.isSuccess) "成功" else "失败",
                    geoLastError = result.exceptionOrNull()?.message?.take(200).orEmpty(),
                    geoLastVersion = result.getOrNull()?.version ?: it.geoLastVersion
                )
            }
            refreshComponentVersions()
            _message.value = result.fold(
                onSuccess = { "Geo 数据已更新: ${it.version}" },
                onFailure = { "Geo 更新失败: ${it.message}" }
            )
            _busy.value = false
        }
    }
    fun setGeoAutoUpdate(enabled: Boolean) {
        viewModelScope.launch {
            store.update { it.copy(geoAutoUpdate = enabled) }
            _message.value = if (enabled) "已开启 Geo 自动更新" else "已关闭 Geo 自动更新"
        }
    }

    fun setGeoUpdateInterval(days: Int) {
        if (days !in listOf(7, 30)) return
        viewModelScope.launch {
            store.update { it.copy(geoUpdateIntervalDays = days) }
            _message.value = "Geo 数据每 $days 天检查一次"
        }
    }

    private fun modeLabel(mode: String): String = when (mode) {
        "global" -> "全局"
        "direct" -> "直连"
        else -> "规则"
    }
}
