package com.mihomo.gateway.data.store

import android.content.Context
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

interface SettingsStore {
    val settings: Flow<AppSettings>
    suspend fun update(block: (AppSettings) -> AppSettings)
}

class SettingsStoreImpl(context: Context) : SettingsStore {

    private val prefs = context.getSharedPreferences("gateway_settings", Context.MODE_PRIVATE)
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
                .apply()
            _settings.value = next
        }
    }

    private fun read(): AppSettings = AppSettings(
        subscriptionUrl = prefs.getString(KEY_SUB_URL, "").orEmpty(),
        ipMode = runCatching {
            IpMode.valueOf(prefs.getString(KEY_IP_MODE, IpMode.AUTO.name) ?: IpMode.AUTO.name)
        }.getOrDefault(IpMode.AUTO),
        blockIpv6 = prefs.getBoolean(KEY_BLOCK_V6, true),
        autoStart = prefs.getBoolean(KEY_AUTOSTART, true)
    )

    companion object {
        private const val KEY_SUB_URL = "subscription_url"
        private const val KEY_IP_MODE = "ip_mode"
        private const val KEY_BLOCK_V6 = "block_ipv6"
        private const val KEY_AUTOSTART = "auto_start"
    }
}
