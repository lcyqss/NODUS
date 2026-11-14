package com.mihomo.gateway.ui.subscription

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.di.AppContainer
import com.mihomo.gateway.model.AppSettings
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
 * 订阅列表与切换逻辑。节点页（切换条 / 弹层）和订阅管理页共用同一个实例，
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
            profiles.import(trimmed).onSuccess {
                val current = settings.value
                val list = SubscriptionCodec.decode(current.subscriptions).toMutableList()
                val profile = SubscriptionProfile(
                    id = UUID.randomUUID().toString(),
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
                _refreshTick.value += 1
            }.onFailure { _message.value = "添加失败: ${it.message}" }
            _busy.value = false
        }
    }

    fun activate(profile: SubscriptionProfile) {
        if (profile.id == settings.value.activeSubId) return
        viewModelScope.launch {
            _busy.value = true
            _switchingId.value = profile.id
            profiles.import(profile.url).onSuccess {
                val current = settings.value
                val list = SubscriptionCodec.decode(current.subscriptions).map {
                    if (it.id == profile.id) {
                        it.copy(lastUpdateAt = current.lastUpdateAt, nodeCount = current.nodeCount)
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
                _refreshTick.value += 1
            }.onFailure { _message.value = "切换失败: ${it.message}" }
            _switchingId.value = null
            _busy.value = false
        }
    }

    /** 重新拉取当前订阅（多订阅下以 active profile 的 URL 为准）。 */
    fun updateActive() {
        val current = settings.value
        val active = activeProfile()
        val url = active?.url ?: current.subscriptionUrl
        if (url.isBlank()) {
            _message.value = "未设置订阅"
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _switchingId.value = active?.id
            _message.value = "更新中…"
            profiles.import(url).onSuccess {
                val now = settings.value
                val list = SubscriptionCodec.decode(now.subscriptions).map {
                    if (it.id == active?.id) {
                        it.copy(lastUpdateAt = now.lastUpdateAt, nodeCount = now.nodeCount)
                    } else {
                        it
                    }
                }
                store.update { it.copy(subscriptions = SubscriptionCodec.encode(list)) }
                _message.value = "订阅已更新"
                _refreshTick.value += 1
            }.onFailure { _message.value = "更新失败: ${it.message}" }
            _switchingId.value = null
            _busy.value = false
        }
    }

    fun delete(profile: SubscriptionProfile) {
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
            _message.value = if (wasActive) {
                "已删除当前订阅；节点仍是旧配置，切换或更新后生效"
            } else {
                "已删除「${profile.name}」"
            }
        }
    }
}
