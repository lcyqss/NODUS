package com.mihomo.gateway.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.data.repo.CoreUpdateInfo
import com.mihomo.gateway.data.repo.CoreUpdateRepository
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.GeoDataRepository
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 运行组件（mihomo 内核 + Geo 数据）的检查、下载、安装与回退。
 * 与网关设置分离：这些操作都会中断内核，状态（进度、错误）也自成一套。
 */
class ComponentUpdateViewModel(
    private val store: SettingsStore = AppContainer.settings,
    private val gateway: GatewayController = AppContainer.gateway,
    private val profiles: ProfileRepository = AppContainer.profiles,
    private val geoData: GeoDataRepository = AppContainer.geoData,
    private val core: CoreInstaller = AppContainer.core,
    private val coreUpdates: CoreUpdateRepository = AppContainer.coreUpdates,
    private val shell: ShellExecutor = AppContainer.shell
) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _coreVersion = MutableStateFlow("")
    private val _corePrevious = MutableStateFlow("")
    private val _coreUpdate = MutableStateFlow<CoreUpdateInfo?>(null)
    private val _coreProgress = MutableStateFlow<Int?>(null)
    private val _geoVersion = MutableStateFlow("")
    private val _geoProgress = MutableStateFlow<Int?>(null)

    val coreVersion: StateFlow<String> = _coreVersion.asStateFlow()
    val corePrevious: StateFlow<String> = _corePrevious.asStateFlow()
    val coreUpdate: StateFlow<CoreUpdateInfo?> = _coreUpdate.asStateFlow()
    val coreProgress: StateFlow<Int?> = _coreProgress.asStateFlow()
    val geoVersion: StateFlow<String> = _geoVersion.asStateFlow()
    val geoProgress: StateFlow<Int?> = _geoProgress.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    /** 一次 root 调用同时读取内核版本、上一内核与 Geo 版本。 */
    fun refreshComponentVersions() {
        viewModelScope.launch {
            val probe = shell.run(
                "/data/adb/mihomo/mihomo -v 2>/dev/null; echo '@@'; " +
                    "cat /data/adb/mihomo/.geo_version 2>/dev/null",
                10_000
            ).stdout
            val parts = probe.split("@@")
            val coreLine = parts.getOrNull(0).orEmpty()
                .lineSequence().firstOrNull { it.isNotBlank() }
                .orEmpty().trim()
            val coreName = coreLine.split(' ')
                .firstOrNull { it.startsWith("v") && it.getOrNull(1)?.isDigit() == true }
                ?: coreLine
            val geo = parts.getOrNull(1).orEmpty().trim()
            _corePrevious.value = runCatching { core.previousVersion() }.getOrDefault("")
            _coreVersion.value = coreName.ifBlank { "未知" }
            _geoVersion.value = geo.ifBlank { "未知" }
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
                    _message.value = rollbackMessage("新内核启动失败")
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

    fun checkCoreUpdate() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = "正在检查内核更新…"
            val result = coreUpdates.check(useProxy = gateway.state.value.running)
            result.onSuccess { info ->
                val current = _coreVersion.value.trim()
                if (info.latest.trim() == current) {
                    _message.value = "内核已是最新：${info.latest}"
                } else {
                    _coreUpdate.value = info
                }
            }.onFailure {
                _message.value = "检查内核更新失败: ${it.message}"
            }
            _busy.value = false
        }
    }

    fun dismissCoreUpdate() {
        _coreUpdate.value = null
    }

    fun installCoreUpdate() {
        val info = _coreUpdate.value ?: return
        if (_busy.value) return
        if (info.sha256.isNullOrBlank()) {
            _message.value = "官方未提供 SHA256，已拒绝安装"
            _coreUpdate.value = null
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _coreUpdate.value = null
            _message.value = "正在下载内核 ${info.latest}…"
            val wasRunning = gateway.state.value.running
            _coreProgress.value = 0
            val download = coreUpdates.download(info, useProxy = wasRunning) { done, total ->
                _coreProgress.value = percent(done, total)
            }
            _coreProgress.value = null
            val file = download.getOrNull()
            if (file == null) {
                _message.value = "内核下载失败: ${download.exceptionOrNull()?.message}"
                _busy.value = false
                return@launch
            }
            if (wasRunning) gateway.stop()
            val installed = core.installBinary(file)
            if (installed.isFailure) {
                if (wasRunning) gateway.start()
                _message.value = "内核安装失败: ${installed.exceptionOrNull()?.message}"
            } else if (!wasRunning) {
                _message.value = "内核已安装: ${installed.getOrNull()}"
            } else {
                val started = gateway.start()
                _message.value = if (started.isSuccess) {
                    "内核已更新: ${info.latest}"
                } else {
                    rollbackMessage("新内核启动失败")
                }
            }
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
                _geoProgress.value = percent(done, total)
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

    private fun percent(done: Long, total: Long): Int =
        if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else -1

    /** 新内核启动失败时尝试回退，返回给用户看的结果文案。 */
    private suspend fun rollbackMessage(prefix: String): String {
        val previous = core.previousVersion()
        if (previous.isBlank()) return "$prefix，且没有可回退版本"
        val rolledBack = core.rollbackToPrevious()
        return if (rolledBack.isSuccess) {
            gateway.start()
            "$prefix，已回退到 $previous"
        } else {
            "$prefix，回退也失败: ${rolledBack.exceptionOrNull()?.message}"
        }
    }
}
