package com.mihomo.gateway.data.store

import android.content.Context
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.model.SubscriptionCodec
import com.mihomo.gateway.model.SubscriptionProfile
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(block: (AppSettings) -> AppSettings)
}

class SettingsStoreImpl(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)

    init {
        migrateLegacySubscription()
    }

    private val _settings = MutableStateFlow(read())
    override val settings: Flow<AppSettings> = _settings

    override suspend fun update(block: (AppSettings) -> AppSettings) {
        synchronized(this) {
            val next = block(_settings.value)
            prefs.edit()
                .putString(KEY_SUB_URL, next.subscriptionUrl)
                .putString(KEY_IP_MODE, next.ipMode.name)
                .putBoolean(KEY_BLOCK_V6, next.blockIpv6)
                .putBoolean(KEY_AUTOSTART, next.autoStart)
                .putBoolean(KEY_DESIRED, next.desiredRunning)
                .putInt(KEY_UPDATE_INTERVAL, next.updateIntervalHours)
                .putLong(KEY_LAST_UPDATE, next.lastUpdateAt)
                .putString(KEY_TODAY_DATE, next.todayDate)
                .putLong(KEY_TODAY_UP, next.todayUp)
                .putLong(KEY_TODAY_DOWN, next.todayDown)
                .putString(KEY_SUB_NAME, next.subName)
                .putInt(KEY_NODE_COUNT, next.nodeCount)
                .putString(KEY_FAVORITES, next.favoriteNodes)
                .putString(KEY_SUBSCRIPTIONS, next.subscriptions)
                .putString(KEY_ACTIVE_SUB, next.activeSubId)
                .apply()
            _settings.value = next
        }
    }

    /** 旧版本只有单个订阅：迁移成订阅对象，自动更新间隔归属该订阅。 */
    private fun migrateLegacySubscription() {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, "").orEmpty()
        if (raw.isNotBlank()) {
            val list = SubscriptionCodec.decode(raw)
            if (list.isEmpty()) return
            val legacyInterval = prefs.getInt(KEY_UPDATE_INTERVAL, 0)
            val active = prefs.getString(KEY_ACTIVE_SUB, "").orEmpty()
                .ifBlank { list.first().id }
            val migrated = if (legacyInterval > 0 && list.none { it.autoUpdateHours > 0 }) {
                list.map { if (it.id == active) it.copy(autoUpdateHours = legacyInterval) else it }
            } else {
                list
            }
            prefs.edit()
                .putString(KEY_SUBSCRIPTIONS, SubscriptionCodec.encode(migrated))
                .putString(KEY_ACTIVE_SUB, active)
                .putInt(KEY_UPDATE_INTERVAL, 0)
                .apply()
            return
        }
        val url = prefs.getString(KEY_SUB_URL, "").orEmpty()
        if (url.isBlank()) return
        val profile = SubscriptionProfile(
            id = UUID.randomUUID().toString(),
            name = prefs.getString(KEY_SUB_NAME, "").orEmpty().ifBlank { "订阅 1" },
            url = url,
            lastUpdateAt = prefs.getLong(KEY_LAST_UPDATE, 0L),
            nodeCount = prefs.getInt(KEY_NODE_COUNT, 0),
            autoUpdateHours = prefs.getInt(KEY_UPDATE_INTERVAL, 0)
        )
        prefs.edit()
            .putString(KEY_SUBSCRIPTIONS, SubscriptionCodec.encode(listOf(profile)))
            .putString(KEY_ACTIVE_SUB, profile.id)
            .putInt(KEY_UPDATE_INTERVAL, 0)
            .apply()
    }

    private fun read(): AppSettings = AppSettings(
        subscriptionUrl = prefs.getString(KEY_SUB_URL, "").orEmpty(),
        ipMode = runCatching {
            IpMode.valueOf(prefs.getString(KEY_IP_MODE, IpMode.AUTO.name) ?: IpMode.AUTO.name)
        }.getOrDefault(IpMode.AUTO),
        blockIpv6 = prefs.getBoolean(KEY_BLOCK_V6, true),
        autoStart = prefs.getBoolean(KEY_AUTOSTART, true),
        desiredRunning = prefs.getBoolean(KEY_DESIRED, true),
        updateIntervalHours = prefs.getInt(KEY_UPDATE_INTERVAL, 0),
        lastUpdateAt = prefs.getLong(KEY_LAST_UPDATE, 0L),
        todayDate = prefs.getString(KEY_TODAY_DATE, "").orEmpty(),
        todayUp = prefs.getLong(KEY_TODAY_UP, 0L),
        todayDown = prefs.getLong(KEY_TODAY_DOWN, 0L),
        subName = prefs.getString(KEY_SUB_NAME, "").orEmpty(),
        nodeCount = prefs.getInt(KEY_NODE_COUNT, 0),
        favoriteNodes = prefs.getString(KEY_FAVORITES, "").orEmpty(),
        subscriptions = prefs.getString(KEY_SUBSCRIPTIONS, "").orEmpty(),
        activeSubId = prefs.getString(KEY_ACTIVE_SUB, "").orEmpty()
    )

    companion object {
        private const val KEY_SUB_URL = "subscription_url"
        private const val KEY_IP_MODE = "ip_mode"
        private const val KEY_BLOCK_V6 = "block_ipv6"
        private const val KEY_AUTOSTART = "auto_start"
        private const val KEY_DESIRED = "desired_running"
        private const val KEY_UPDATE_INTERVAL = "update_interval_hours"
        private const val KEY_LAST_UPDATE = "last_update_at"
        private const val KEY_TODAY_DATE = "today_date"
        private const val KEY_TODAY_UP = "today_up"
        private const val KEY_TODAY_DOWN = "today_down"
        private const val KEY_SUB_NAME = "sub_name"
        private const val KEY_NODE_COUNT = "node_count"
        private const val KEY_FAVORITES = "favorite_nodes"
        private const val KEY_SUBSCRIPTIONS = "subscriptions"
        private const val KEY_ACTIVE_SUB = "active_sub_id"
    }
}
