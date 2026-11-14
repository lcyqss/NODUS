package com.mihomo.gateway.di

import android.content.Context
import com.mihomo.gateway.core.AssetInstaller
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.NeighborResolver
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.core.ShellExecutorImpl
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.api.MihomoApiImpl
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.GatewayControllerImpl
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.repo.ProfileRepositoryImpl
import com.mihomo.gateway.data.repo.RuleRepository
import com.mihomo.gateway.data.repo.RuleRepositoryImpl
import com.mihomo.gateway.data.store.RuleStore
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.data.store.SettingsStoreImpl

/**
 * 组合根：唯一允许知道所有实现的地方。
 */
object AppContainer {

    const val SECRET = "9062b7e6cee1d259"
    const val BASE = "/data/adb/mihomo"

    lateinit var shell: ShellExecutor
        private set
    lateinit var api: MihomoApi
        private set
    lateinit var gateway: GatewayController
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var core: CoreInstaller
        private set
    lateinit var neighbors: NeighborResolver
        private set
    lateinit var rules: RuleRepository
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val app = context.applicationContext
        shell = ShellExecutorImpl()
        api = MihomoApiImpl(secret = SECRET)
        gateway = GatewayControllerImpl(shell, api, base = BASE)
        settings = SettingsStoreImpl(app)
        core = AssetInstaller(app, shell, base = BASE)
        neighbors = NeighborResolver(shell)
        rules = RuleRepositoryImpl(app, shell, api, RuleStore(app), base = BASE)
        profiles = ProfileRepositoryImpl(
            context = app,
            shell = shell,
            api = api,
            settings = settings,
            coreInstaller = core,
            rules = rules,
            base = BASE
        )
    }
}
