package com.mihomo.gateway.di

import android.content.Context
import com.mihomo.gateway.core.AssetInstaller
import com.mihomo.gateway.core.CoreInstaller
import com.mihomo.gateway.core.GatewayHealthChecker
import com.mihomo.gateway.core.NeighborResolver
import com.mihomo.gateway.core.ShellExecutor
import com.mihomo.gateway.core.ShellExecutorImpl
import com.mihomo.gateway.data.api.MihomoApi
import com.mihomo.gateway.data.api.MihomoApiImpl
import com.mihomo.gateway.data.catalog.NodeCatalogRepository
import com.mihomo.gateway.data.catalog.NodeCatalogRepositoryImpl
import com.mihomo.gateway.data.policy.DevicePolicyRepository
import com.mihomo.gateway.data.policy.DevicePolicyRepositoryImpl
import com.mihomo.gateway.data.policy.PolicyBackupService
import com.mihomo.gateway.data.tags.NodeTagRepository
import com.mihomo.gateway.data.tags.ConditionTemplateRepository
import com.mihomo.gateway.data.tags.ConditionTemplateRepositoryImpl
import com.mihomo.gateway.data.tags.NodeTagRepositoryImpl
import com.mihomo.gateway.data.repo.CoreUpdateRepository
import com.mihomo.gateway.data.repo.CoreUpdateRepositoryImpl
import com.mihomo.gateway.data.repo.GeoDataRepository
import com.mihomo.gateway.data.repo.GeoDataRepositoryImpl
import com.mihomo.gateway.data.repo.GatewayController
import com.mihomo.gateway.data.repo.GatewayControllerImpl
import com.mihomo.gateway.data.repo.ProfileRepository
import com.mihomo.gateway.data.repo.ProfileRepositoryImpl
import com.mihomo.gateway.data.repo.RuleRepository
import com.mihomo.gateway.data.repo.RuleRepositoryImpl
import com.mihomo.gateway.data.store.RuleStore
import com.mihomo.gateway.data.store.SettingsStore
import com.mihomo.gateway.data.store.SettingsStoreImpl
import com.mihomo.gateway.data.update.AppUpdateManager
import com.mihomo.gateway.data.update.AppUpdateRepositoryImpl

/**
 * 组合根：唯一允许知道所有实现的地方。
 */
object AppContainer {

    const val SECRET = "9062b7e6cee1d259"
    const val BASE = "/data/adb/mihomo"

    lateinit var shell: ShellExecutor
        private set
    lateinit var navigator: AppNavigator
        private set
    lateinit var api: MihomoApi
        private set
    lateinit var gateway: GatewayController
        private set
    lateinit var geoData: GeoDataRepository
    lateinit var coreUpdates: CoreUpdateRepository
        private set
    lateinit var profiles: ProfileRepository
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var core: CoreInstaller
        private set
    lateinit var health: GatewayHealthChecker
        private set
    lateinit var neighbors: NeighborResolver
        private set
    lateinit var rules: RuleRepository
        private set
    lateinit var devicePolicies: DevicePolicyRepository
        private set
    lateinit var nodeTags: NodeTagRepository
        private set
    lateinit var nodeCatalog: NodeCatalogRepository
        private set
    lateinit var conditionTemplates: ConditionTemplateRepository
        private set
    lateinit var policyBackup: PolicyBackupService
        private set
    lateinit var appUpdates: AppUpdateManager
        private set

    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        initialized = true

        val app = context.applicationContext
        navigator = AppNavigator()
        shell = ShellExecutorImpl()
        api = MihomoApiImpl(secret = SECRET)
        gateway = GatewayControllerImpl(shell, api, base = BASE)
        settings = SettingsStoreImpl(app)
        core = AssetInstaller(app, shell, base = BASE)
        health = GatewayHealthChecker(shell, api)
        neighbors = NeighborResolver(shell)
        rules = RuleRepositoryImpl(app, shell, api, RuleStore(app), base = BASE)
        nodeCatalog = NodeCatalogRepositoryImpl(app, settings)
        nodeTags = NodeTagRepositoryImpl(settings)
        conditionTemplates = ConditionTemplateRepositoryImpl(settings)
        policyBackup = PolicyBackupService(settings)
        appUpdates = AppUpdateManager(AppUpdateRepositoryImpl(app), settings)
        devicePolicies = DevicePolicyRepositoryImpl(
            context = app,
            settings = settings,
            catalog = nodeCatalog,
            tagRepository = nodeTags,
            neighbors = neighbors,
            shell = shell,
            api = api,
            base = BASE
        )
        geoData = GeoDataRepositoryImpl(app, shell, base = BASE)
        coreUpdates = CoreUpdateRepositoryImpl(app)
        profiles = ProfileRepositoryImpl(
            context = app,
            shell = shell,
            api = api,
            settings = settings,
            coreInstaller = core,
            rules = rules,
            devicePolicies = devicePolicies,
            base = BASE
        )
    }
}
