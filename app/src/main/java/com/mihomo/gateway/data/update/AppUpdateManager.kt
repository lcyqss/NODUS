package com.mihomo.gateway.data.update

import com.mihomo.gateway.BuildConfig
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.model.AppUpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class AppUpdateManager(
    private val repository: AppUpdateRepository,
    private val settings: SettingsStore
) {
    private val _available = MutableStateFlow<AppUpdateInfo?>(null)
    val available: StateFlow<AppUpdateInfo?> = _available.asStateFlow()

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val mutex = Mutex()

    suspend fun checkIfDue(force: Boolean = false): Result<AppUpdateInfo?> = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!force) {
            val last = runCatching { settings.settings.first().appUpdateLastCheckAt }.getOrDefault(0L)
            if (now - last < CHECK_INTERVAL_MS) return Result.success(_available.value)
        }
        _checking.value = true
        _error.value = null
        runCatching {
            settings.update { it.copy(appUpdateLastCheckAt = now) }
        }
        val result = repository.latest(BuildConfig.VERSION_NAME)
        _checking.value = false
        result.onSuccess { _available.value = it }
            .onFailure { _error.value = it.message ?: "检查更新失败" }
        result
    }

    suspend fun download(info: AppUpdateInfo, onProgress: (Int) -> Unit): Result<File> =
        repository.download(info, onProgress)

    companion object {
        private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
