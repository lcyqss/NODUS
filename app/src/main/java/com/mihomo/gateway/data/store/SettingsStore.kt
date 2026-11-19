package com.mihomo.gateway.data.store

import android.content.Context
import com.mihomo.gateway.data.tags.RegionPresets
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.model.NodeTagCodec
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
        migrateRegionPresets()
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
                .putBoolean(KEY_IPV6_GATEWAY, next.ipv6Gateway)
                .putBoolean(KEY_DIRECT_PRIVATE, next.directPrivateLan)
                .putBoolean(KEY_UDP_PROXY, next.udpProxy)
                .putBoolean(KEY_AUTO_CLEAN, next.autoCleanIdle)
                .putInt(KEY_AUTO_CLEAN_THRESHOLD, next.autoCleanThreshold)
                .putBoolean(KEY_GEO_AUTO, next.geoAutoUpdate)
                .putInt(KEY_GEO_INTERVAL, next.geoUpdateIntervalDays)
                .putLong(KEY_GEO_LAST, next.lastGeoUpdateAt)
                .putLong(KEY_GEO_LAST_CHECK, next.geoLastCheckAt)
                .putString(KEY_GEO_LAST_RESULT, next.geoLastResult)
                .putString(KEY_GEO_LAST_ERROR, next.geoLastError)
                .putString(KEY_GEO_LAST_VERSION, next.geoLastVersion)
                .putBoolean(KEY_AUTOSTART, next.autoStart)
                .putBoolean(KEY_DESIRED, next.desiredRunning)
                .putLong(KEY_HEALTH_AT, next.lastHealthAt)
                .putBoolean(KEY_HEALTH_OK, next.lastHealthOk)
                .putLong(KEY_APP_UPDATE_CHECK, next.appUpdateLastCheckAt)
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
                .putString(KEY_DEVICE_POLICIES, next.devicePolicies)
                .putString(KEY_NODE_TAGS, next.nodeTags)
                .putInt(KEY_POLICY_INTERVAL, next.policyIntervalSec)
                .putInt(KEY_IP_FOLLOW_INTERVAL, next.ipFollowIntervalSec)
                .putString(KEY_CONDITION_TEMPLATES, next.conditionTemplates)
                .apply()
            _settings.value = next
        }
    }

    /** 旧版本只有单个订阅：迁移成订阅对象，自动更新间隔归属该订阅。 */
    /** 把内置地区迁移成可编辑的预设标签，保留 __region_* ID。 */
    private fun migrateRegionPresets() {
        if (prefs.getBoolean(KEY_REGION_PRESETS, false)) return
        val current = NodeTagCodec.decode(prefs.getString(KEY_NODE_TAGS, "").orEmpty())
        val ids = current.map { it.id }.toSet()
        val presets = RegionPresets.all.filter { it.id !in ids }.map(RegionPresets::asTag)
        prefs.edit()
            .putString(KEY_NODE_TAGS, NodeTagCodec.encode(current + presets))
            .putBoolean(KEY_REGION_PRESETS, true)
            .apply()
    }

    private fun migrateLegacySubscription() {
        val raw = prefs.getString(KEY_SUBSCRIPTIONS, "").orEmpty()
        if (raw.isNotBlank()) {
            val list = SubscriptionCodec.decode(raw)
            if (list.isEmpty()) return
            val legacyInterval = prefs.getInt(KEY_UPDATE_INTERVAL, 0)
            val active = prefs.getString(KEY_ACTIVE_SUB, "").orEmpty()
                .ifBlank { list.first().id }
            val migrated = if (legacyInterval > 0 && list.none { it.autoUpdateMinutes > 0 }) {
                list.map { if (it.id == active) it.copy(autoUpdateMinutes = legacyInterval * 60) else it }
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
            autoUpdateMinutes = prefs.getInt(KEY_UPDATE_INTERVAL, 0) * 60
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
        ipv6Gateway = prefs.getBoolean(KEY_IPV6_GATEWAY, false),
        directPrivateLan = prefs.getBoolean(KEY_DIRECT_PRIVATE, false),
        udpProxy = prefs.getBoolean(KEY_UDP_PROXY, false),
        autoCleanIdle = prefs.getBoolean(KEY_AUTO_CLEAN, false),
        autoCleanThreshold = prefs.getInt(KEY_AUTO_CLEAN_THRESHOLD, 800),
        geoAutoUpdate = prefs.getBoolean(KEY_GEO_AUTO, false),
        geoUpdateIntervalDays = prefs.getInt(KEY_GEO_INTERVAL, 7),
        lastGeoUpdateAt = prefs.getLong(KEY_GEO_LAST, 0L),
        geoLastCheckAt = prefs.getLong(KEY_GEO_LAST_CHECK, 0L),
        geoLastResult = prefs.getString(KEY_GEO_LAST_RESULT, "").orEmpty(),
        geoLastError = prefs.getString(KEY_GEO_LAST_ERROR, "").orEmpty(),
        geoLastVersion = prefs.getString(KEY_GEO_LAST_VERSION, "").orEmpty(),
        autoStart = prefs.getBoolean(KEY_AUTOSTART, true),
        desiredRunning = prefs.getBoolean(KEY_DESIRED, true),
        lastHealthAt = prefs.getLong(KEY_HEALTH_AT, 0L),
        lastHealthOk = prefs.getBoolean(KEY_HEALTH_OK, false),
        appUpdateLastCheckAt = prefs.getLong(KEY_APP_UPDATE_CHECK, 0L),
        updateIntervalHours = prefs.getInt(KEY_UPDATE_INTERVAL, 0),
        lastUpdateAt = prefs.getLong(KEY_LAST_UPDATE, 0L),
        todayDate = prefs.getString(KEY_TODAY_DATE, "").orEmpty(),
        todayUp = prefs.getLong(KEY_TODAY_UP, 0L),
        todayDown = prefs.getLong(KEY_TODAY_DOWN, 0L),
        subName = prefs.getString(KEY_SUB_NAME, "").orEmpty(),
        nodeCount = prefs.getInt(KEY_NODE_COUNT, 0),
        favoriteNodes = prefs.getString(KEY_FAVORITES, "").orEmpty(),
        subscriptions = prefs.getString(KEY_SUBSCRIPTIONS, "").orEmpty(),
        activeSubId = prefs.getString(KEY_ACTIVE_SUB, "").orEmpty(),
        devicePolicies = prefs.getString(KEY_DEVICE_POLICIES, "").orEmpty(),
        nodeTags = prefs.getString(KEY_NODE_TAGS, "").orEmpty(),
        policyIntervalSec = prefs.getInt(KEY_POLICY_INTERVAL, 60),
        ipFollowIntervalSec = prefs.getInt(KEY_IP_FOLLOW_INTERVAL, 60),
        conditionTemplates = prefs.getString(KEY_CONDITION_TEMPLATES, "").orEmpty()
    )

    companion object {
        private const val KEY_SUB_URL = "subscription_url"
        private const val KEY_IP_MODE = "ip_mode"
        private const val KEY_BLOCK_V6 = "block_ipv6"
        private const val KEY_IPV6_GATEWAY = "ipv6_gateway"
    private const val KEY_DIRECT_PRIVATE = "direct_private_lan"
    private const val KEY_UDP_PROXY = "udp_proxy"
    private const val KEY_AUTO_CLEAN = "auto_clean_idle"
    private const val KEY_AUTO_CLEAN_THRESHOLD = "auto_clean_threshold"
        private const val KEY_GEO_AUTO = "geo_auto_update"
        private const val KEY_GEO_INTERVAL = "geo_update_interval_days"
        private const val KEY_GEO_LAST = "geo_last_update"
        private const val KEY_GEO_LAST_CHECK = "geo_last_check"
        private const val KEY_GEO_LAST_RESULT = "geo_last_result"
        private const val KEY_GEO_LAST_ERROR = "geo_last_error"
        private const val KEY_GEO_LAST_VERSION = "geo_last_version"
        private const val KEY_AUTOSTART = "auto_start"
        private const val KEY_DESIRED = "desired_running"
        private const val KEY_HEALTH_AT = "last_health_at"
        private const val KEY_HEALTH_OK = "last_health_ok"
        private const val KEY_APP_UPDATE_CHECK = "app_update_check_at"
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
        private const val KEY_DEVICE_POLICIES = "device_policies"
        private const val KEY_NODE_TAGS = "node_tags"
        private const val KEY_REGION_PRESETS = "region_presets_seeded"
        private const val KEY_POLICY_INTERVAL = "policy_interval_sec"
        private const val KEY_IP_FOLLOW_INTERVAL = "ip_follow_interval_sec"
        private const val KEY_CONDITION_TEMPLATES = "condition_templates"
    }
}
