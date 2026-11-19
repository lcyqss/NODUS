package com.mihomo.gateway.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.ui.diagnostics.DiagnosticsScreen
import com.mihomo.gateway.ui.logs.LogsScreen
import kotlinx.coroutines.delay

private enum class SettingsPage { MAIN, DIAGNOSTICS, LOGS }

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    components: ComponentUpdateViewModel = viewModel()
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }

    when (page) {
        SettingsPage.MAIN -> SettingsMain(
            vm = vm,
            components = components,
            onDiagnostics = { page = SettingsPage.DIAGNOSTICS },
            onLogs = { page = SettingsPage.LOGS }
        )

        SettingsPage.DIAGNOSTICS -> DiagnosticsScreen(onBack = { page = SettingsPage.MAIN })
        SettingsPage.LOGS -> LogsScreen(onBack = { page = SettingsPage.MAIN })
    }
}

@Composable
private fun SettingsMain(
    vm: SettingsViewModel,
    components: ComponentUpdateViewModel,
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val settingsMessage by vm.message.collectAsState()
    val componentMessage by components.message.collectAsState()
    val message = settingsMessage ?: componentMessage
    val busy by vm.busy.collectAsState()
    val componentsBusy by components.busy.collectAsState()
    val ipv6RaBlocked by vm.ipv6RaBlocked.collectAsState()
    val coreVersion by components.coreVersion.collectAsState()
    val corePrevious by components.corePrevious.collectAsState()
    val coreUpdate by components.coreUpdate.collectAsState()
    val coreProgress by components.coreProgress.collectAsState()
    val health by vm.health.collectAsState()
    val geoVersion by components.geoVersion.collectAsState()
    val geoProgress by components.geoProgress.collectAsState()
    val appUpdate by vm.appUpdate.collectAsState()
    val appUpdateChecking by vm.appUpdateChecking.collectAsState()
    val appUpdateError by vm.appUpdateError.collectAsState()
    val runtimeMode by vm.runtimeMode.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshIpv6Capability()
        components.refreshComponentVersions()
    }

    val context = LocalContext.current
    var showCleanup by remember { mutableStateOf(false) }
    var showGeoUpdate by remember { mutableStateOf(false) }
    var showGeoInterval by remember { mutableStateOf(false) }
    var showGeoHistory by remember { mutableStateOf(false) }
    var showCoreRollback by remember { mutableStateOf(false) }
    var showHealth by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf("") }
    var showIpMode by remember { mutableStateOf(false) }
    var showRuntimeMode by remember { mutableStateOf(false) }
    var showAppUpdate by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    // 操作提示几秒后自动消失（两个 ViewModel 的提示共用这一条）
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
            components.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(14.dp))

        GatewaySection(
            settings = settings,
            runtimeMode = runtimeMode,
            ipv6RaBlocked = ipv6RaBlocked,
            enabled = !busy && !componentsBusy,
            onIpMode = { showIpMode = true },
            onRuntimeMode = { showRuntimeMode = true },
            onBlockIpv6 = vm::setBlockIpv6,
            onIpv6Gateway = vm::setIpv6Gateway,
            onDirectPrivateLan = vm::setDirectPrivateLan,
            onUdpProxy = vm::setUdpProxy,
            onUdpProxyAll = vm::setUdpProxyAll
        )

        Spacer(Modifier.height(24.dp))

        ComponentsSection(
            coreVersion = coreVersion,
            corePrevious = corePrevious,
            coreProgress = coreProgress,
            geoVersion = geoVersion,
            geoProgress = geoProgress,
            geoAutoUpdate = settings.geoAutoUpdate,
            geoIntervalDays = settings.geoUpdateIntervalDays,
            geoLastResult = settings.geoLastResult,
            geoLastVersion = settings.geoLastVersion,
            busy = busy || componentsBusy,
            onRollback = { showCoreRollback = true },
            onCheckCore = components::checkCoreUpdate,
            onInstallCore = components::installCore,
            onGeoHistory = { showGeoHistory = true },
            onGeoAutoUpdate = components::setGeoAutoUpdate,
            onGeoInterval = { showGeoInterval = true },
            onGeoUpdate = { showGeoUpdate = true },
            onCleanup = { showCleanup = true }
        )

        Spacer(Modifier.height(24.dp))

        AppSection(
            autoStart = settings.autoStart,
            onAutoStart = vm::setAutoStart,
            onBackup = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.buildBackup())
                }
                context.startActivity(Intent.createChooser(send, "备份配置"))
            },
            onRestore = { showRestore = true }
        )

        Spacer(Modifier.height(24.dp))

        DiagnosticsSection(
            health = health,
            onLogs = onLogs,
            onDiagnostics = onDiagnostics,
            onHealth = {
                showHealth = true
                vm.refreshHealth()
            }
        )

        Spacer(Modifier.height(24.dp))

        AboutSection(
            versionName = versionName,
            appUpdate = appUpdate,
            appUpdateChecking = appUpdateChecking,
            appUpdateError = appUpdateError,
            onCheckUpdate = { showAppUpdate = true }
        )

        Spacer(Modifier.height(12.dp))
    }

    if (showIpMode) {
        OptionDialog(
            title = "节点地址族",
            options = IpMode.entries.map { it.name to it.label },
            selectedKey = settings.ipMode.name,
            descriptions = IpMode.entries.associate { it.name to ipModeDescription(it) },
            onSelect = {
                showIpMode = false
                vm.setMode(IpMode.valueOf(it))
            },
            onDismiss = { showIpMode = false }
        )
    }

    if (showRuntimeMode) {
        OptionDialog(
            title = "运行模式",
            options = listOf(
                "rule" to "规则",
                "global" to "全局",
                "direct" to "直连"
            ),
            selectedKey = runtimeMode,
            descriptions = mapOf(
                "rule" to "按分流规则决定走代理还是直连",
                "global" to "全部流量走代理",
                "direct" to "全部流量不走代理"
            ),
            onSelect = {
                showRuntimeMode = false
                vm.setRuntimeMode(it)
            },
            onDismiss = { showRuntimeMode = false }
        )
    }

    if (showRestore) {
        RestoreConfigDialog(
            json = restoreJson,
            onJsonChange = { restoreJson = it },
            onConfirm = {
                showRestore = false
                vm.restoreBackup(restoreJson)
            },
            onDismiss = { showRestore = false }
        )
    }

    if (showHealth) {
        HealthDialog(
            health = health,
            ipv6Gateway = settings.ipv6Gateway,
            onDismiss = { showHealth = false }
        )
    }

    coreUpdate?.let { info ->
        CoreUpdateDialog(
            info = info,
            currentVersion = coreVersion,
            onInstall = components::installCoreUpdate,
            onDismiss = components::dismissCoreUpdate
        )
    }

    if (showCoreRollback) {
        CoreRollbackDialog(
            currentVersion = coreVersion,
            previousVersion = corePrevious,
            onConfirm = {
                showCoreRollback = false
                components.rollbackCore()
            },
            onDismiss = { showCoreRollback = false }
        )
    }

    if (showGeoHistory) {
        GeoHistoryDialog(settings = settings, onDismiss = { showGeoHistory = false })
    }

    if (showGeoInterval) {
        GeoIntervalDialog(
            days = settings.geoUpdateIntervalDays,
            onSelect = {
                showGeoInterval = false
                components.setGeoUpdateInterval(it)
            },
            onDismiss = { showGeoInterval = false }
        )
    }

    if (showGeoUpdate) {
        GeoUpdateDialog(
            onConfirm = {
                showGeoUpdate = false
                components.updateGeo()
            },
            onDismiss = { showGeoUpdate = false }
        )
    }

    if (showAppUpdate) {
        AppUpdateDialog(onDismiss = { showAppUpdate = false })
    }

    if (showCleanup) {
        CleanupDialog(
            onConfirm = {
                showCleanup = false
                vm.cleanup()
            },
            onDismiss = { showCleanup = false }
        )
    }
}
