package com.mihomo.gateway.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.ProviderMeta
import com.mihomo.gateway.model.SubscriptionCodec
import com.mihomo.gateway.model.SubscriptionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 订阅列表与切换逻辑。节点页（切换条 / 弹层）和订阅管理页共用一个实例，
 * 避免两处各自维护 busy / message。
 */
class SubscriptionViewModel(
    private val store: SettingsStore = AppContainer.settings,
    private val profiles: ProfileRepository = AppContainer.profiles
) : ViewModel() {

    val settings: StateFlow<AppSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val subscriptions: StateFlow<List<SubscriptionProfile>> = store.settings
        .map { SubscriptionCodec.decode(it.subscriptions) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 正在切换/更新的订阅 id（用于行内进度）。 */
    private val _switchingId = MutableStateFlow<String?>(null)
    val switchingId: StateFlow<String?> = _switchingId.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 每次成功切换/更新后自增，节点页据此刷新代理列表。 */
    private val _refreshTick = MutableStateFlow(0)
    val refreshTick: StateFlow<Int> = _refreshTick.asStateFlow()

    fun clearMessage() {
        _message.value = null
    }

    fun activeProfile(): SubscriptionProfile? {
        val current = settings.value
        return subscriptions.value.firstOrNull { it.id == current.activeSubId }
    }

    fun add(name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _message.value = "订阅链接不能为空"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val draft = SubscriptionProfile(
                id = UUID.randomUUID().toString(),
                name = name.ifBlank { "订阅 ${subscriptions.value.size + 1}" },
                url = trimmed
            )
            profiles.fetchToCache(draft).onSuccess { meta ->
                val profile = draft.copy(
                    lastUpdateAt = System.currentTimeMillis(),
                    nodeCount = meta.nodeCount
                )
                val list = subscriptions.value.toMutableList().apply { add(profile) }
                store.update {
                    it.copy(
                        subscriptions = SubscriptionCodec.encode(list),
                        activeSubId = profile.id,
                        subName = profile.name
                    )
                }
                profiles.installFromCache(profile).onSuccess {
                    _message.value = "已添加并切换到「${profile.name}」"
                    _refreshTick.value += 1
                }.onFailure {
                    _message.value = "已添加，但应用失败: ${it.message}"
                }
            }.onFailure { _message.value = "添加失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun switchTo(profile: SubscriptionProfile) {
        if (profile.id == settings.value.activeSubId) return
        viewModelScope.launch {
            _busy.value = true
            _switchingId.value = profile.id
            profiles.switchTo(profile).onSuccess {
                store.update { it.copy(activeSubId = profile.id, subName = profile.name) }
                _message.value = "已切换到「${profile.name}」"
                _refreshTick.value += 1
            }.onFailure { _message.value = "切换失败: ${it.message}" }
            _switchingId.value = null
            _busy.value = false
        }
    }

    /** 更新单个订阅；当前订阅会热重载，非当前订阅只更新缓存。 */
    fun update(profile: SubscriptionProfile) {
        viewModelScope.launch {
            _busy.value = true
            _switchingId.value = profile.id
            profiles.fetchToCache(profile).onSuccess { meta ->
                val updated = profile.copy(
                    lastUpdateAt = System.currentTimeMillis(),
                    nodeCount = meta.nodeCount
                )
                if (profile.id == settings.value.activeSubId) {
                    profiles.installFromCache(updated).onSuccess {
                        _message.value = "「${profile.name}」已更新并生效"
                        _refreshTick.value += 1
                    }.onFailure { _message.value = "更新失败: ${it.message}" }
                } else {
                    _message.value = "「${profile.name}」已更新缓存"
                }
            }.onFailure { _message.value = "更新失败: ${it.message}" }
            _switchingId.value = null
            _busy.value = false
        }
    }

    fun setInterval(profile: SubscriptionProfile, hours: Int) {
        viewModelScope.launch {
            updateList { list ->
                list.map { if (it.id == profile.id) it.copy(autoUpdateHours = hours) else it }
            }
            _message.value = if (hours <= 0) {
                "已关闭「${profile.name}」的自动更新"
            } else {
                "「${profile.name}」每 $hours 小时自动更新"
            }
        }
    }

    /** 保存名称/链接；链接变化会让缓存失效并清空节点统计。 */
    fun save(profile: SubscriptionProfile, name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _message.value = "订阅链接不能为空"
            return
        }
        val urlChanged = trimmed != profile.url
        viewModelScope.launch {
            if (urlChanged) profiles.deleteCache(profile)
            updateList { list ->
                list.map {
                    if (it.id == profile.id) {
                        it.copy(
                            name = name.ifBlank { it.name },
                            url = trimmed,
                            lastUpdateAt = if (urlChanged) 0L else it.lastUpdateAt,
                            nodeCount = if (urlChanged) 0 else it.nodeCount
                        )
                    } else {
                        it
                    }
                }
            }
            _message.value = if (urlChanged) "已保存，链接已变更，请重新更新" else "已保存"
        }
    }

    fun delete(profile: SubscriptionProfile) {
        viewModelScope.launch {
            val wasActive = profile.id == settings.value.activeSubId
            val remaining = subscriptions.value.filterNot { it.id == profile.id }
            updateList { remaining }
            profiles.deleteCache(profile)
            if (!wasActive) {
                _message.value = "已删除「${profile.name}」"
                return@launch
            }
            if (remaining.isEmpty()) {
                store.update { it.copy(activeSubId = "", subName = "") }
                _message.value = "已删除当前订阅；节点仍是旧配置，导入新订阅后才会改变"
                return@launch
            }
            val fallback = remaining.first()
            profiles.switchTo(fallback).onSuccess {
                store.update { it.copy(activeSubId = fallback.id, subName = fallback.name) }
                _message.value = "已删除当前订阅，并切换到「${fallback.name}」"
                _refreshTick.value += 1
            }.onFailure {
                store.update { it.copy(activeSubId = "", subName = "") }
                _message.value = "已删除当前订阅；切换「${fallback.name}」失败: ${it.message}"
            }
        }
    }

    /** 手动全部更新：不区分是否开启自动更新，串行执行。 */
    fun updateAll() {
        val list = subscriptions.value
        if (list.isEmpty()) {
            _message.value = "没有可更新的订阅"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            var done = 0
            var failed = 0
            list.forEachIndexed { index, profile ->
                _switchingId.value = profile.id
                _message.value = "更新中… /"
                val fetched = profiles.fetchToCache(profile)
                if (fetched.isFailure) {
                    failed++
                    return@forEachIndexed
                }
                if (profile.id == settings.value.activeSubId) {
                    profiles.installFromCache(profile).onFailure { failed++ }
                }
                done++
            }
            _switchingId.value = null
            _busy.value = false
            if (done > 0) _refreshTick.value += 1
            _message.value = if (failed == 0) {
                "已更新  个订阅"
            } else {
                "更新完成：成功 ，失败 "
            }
        }
    }

    /** 保存名称/链接并立即更新（链接变化会先清空缓存）。 */
    fun saveAndUpdate(profile: SubscriptionProfile, name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _message.value = "订阅链接不能为空"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            val urlChanged = trimmed != profile.url
            if (urlChanged) profiles.deleteCache(profile)
            val draft = profile.copy(
                name = name.ifBlank { profile.name },
                url = trimmed,
                lastUpdateAt = if (urlChanged) 0L else profile.lastUpdateAt,
                nodeCount = if (urlChanged) 0 else profile.nodeCount
            )
            updateList { list -> list.map { if (it.id == draft.id) draft else it } }
            _switchingId.value = draft.id
            profiles.fetchToCache(draft).onSuccess { meta ->
                val updated = draft.copy(
                    lastUpdateAt = System.currentTimeMillis(),
                    nodeCount = meta.nodeCount
                )
                updateList { list -> list.map { if (it.id == updated.id) updated else it } }
                if (updated.id == settings.value.activeSubId) {
                    profiles.installFromCache(updated).onSuccess {
                        _message.value = "已保存并更新生效"
                        _refreshTick.value += 1
                    }.onFailure { _message.value = "已保存，但应用失败: " }
                } else {
                    _message.value = "已保存并更新缓存"
                }
            }.onFailure { _message.value = "已保存，但更新失败: " }
            _switchingId.value = null
            _busy.value = false
        }
    }

    suspend fun providers(profile: SubscriptionProfile): List<ProviderMeta> =
        profiles.readProviders(profile)

    private suspend fun updateList(block: (List<SubscriptionProfile>) -> List<SubscriptionProfile>) {
        store.update { current ->
            current.copy(
                subscriptions = SubscriptionCodec.encode(
                    block(SubscriptionCodec.decode(current.subscriptions))
                )
            )
        }
    }
}
