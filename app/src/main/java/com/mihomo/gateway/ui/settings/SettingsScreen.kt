package com.mihomo.gateway.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.SettingsDivider
import com.mihomo.gateway.ui.components.SettingsGroup
import com.mihomo.gateway.ui.components.SettingsRow
import com.mihomo.gateway.ui.components.SettingsSwitchRow
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.diagnostics.DiagnosticsScreen
import com.mihomo.gateway.ui.logs.LogsScreen
import kotlinx.coroutines.delay

private enum class SettingsPage { MAIN, DIAGNOSTICS, LOGS }

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }

    BackHandler(enabled = page != SettingsPage.MAIN) { page = SettingsPage.MAIN }

    when (page) {
        SettingsPage.MAIN -> SettingsMain(
            vm = vm,
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
    onDiagnostics: () -> Unit,
    onLogs: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val runtimeMode by vm.runtimeMode.collectAsState()
    val context = LocalContext.current
    var showCleanup by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoreJson by remember { mutableStateOf("") }
    var showIpMode by remember { mutableStateOf(false) }
    var showRuntimeMode by remember { mutableStateOf(false) }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    // 操作提示几秒后自动消失
    LaunchedEffect(message) {
        if (message != null) {
            delay(4000)
            vm.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(title = "设置")

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(14.dp))

        SettingsGroup("网关") {
            SettingsRow(
                title = "节点地址族",
                subtitle = ipModeHint(settings.ipMode),
                value = settings.ipMode.label,
                enabled = !busy,
                onClick = { showIpMode = true }
            )
            SettingsDivider()
            SettingsRow(
                title = "运行模式",
                subtitle = runtimeHint(runtimeMode),
                value = runtimeLabel(runtimeMode),
                enabled = !busy,
                onClick = { showRuntimeMode = true }
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "IPv6 拦截",
                subtitle = "关闭后客户端可用 IPv6 直连",
                checked = settings.blockIpv6,
                enabled = !busy,
                onChange = vm::setBlockIpv6
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup("系统") {
            SettingsSwitchRow(
                title = "开机自启",
                subtitle = "重启后自动启动网关",
                checked = settings.autoStart,
                onChange = vm::setAutoStart
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup("诊断") {
            SettingsRow(
                title = "日志",
                subtitle = "实时输出 / 搜索 / 导出",
                onClick = onLogs
            )
            SettingsDivider()
            SettingsRow(
                title = "内核 / DNS / 规则快照",
                subtitle = "root 命令与内核状态",
                onClick = onDiagnostics
            )
        }

        Spacer(Modifier.height(24.dp))

        SettingsGroup("关于") {
            SettingsRow(
                title = "备份配置",
                subtitle = "导出规则与设置（分享文本）",
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, vm.buildBackup())
                    }
                    context.startActivity(Intent.createChooser(send, "备份配置"))
                }
            )
            SettingsDivider()
            SettingsRow(
                title = "恢复配置",
                subtitle = "粘贴备份内容并导入",
                onClick = { showRestore = true }
            )
            SettingsDivider()
            SettingsRow(
                title = "重新安装内核",
                subtitle = "从 APK 资源重新解压内核与脚本",
                enabled = !busy,
                onClick = { vm.installCore() }
            )
            SettingsDivider()
            SettingsRow(
                title = "清理运行数据（卸载前）",
                subtitle = "停止网关、清除规则、删除 /data/adb/mihomo",
                onClick = { showCleanup = true }
            )
            SettingsDivider()
            SettingsRow(
                title = "版本",
                value = "v$versionName",
                showChevron = false
            )
        }

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
        AlertDialog(
            onDismissRequest = { showRestore = false },
            title = { Text("恢复配置") },
            text = {
                OutlinedTextField(
                    value = restoreJson,
                    onValueChange = { restoreJson = it },
                    label = { Text("粘贴备份 JSON") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestore = false
                    vm.restoreBackup(restoreJson)
                }) { Text("恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestore = false }) { Text("取消") }
            }
        )
    }

    if (showCleanup) {
        AlertDialog(
            onDismissRequest = { showCleanup = false },
            title = { Text("确认清理？") },
            text = {
                Text(
                    "将停止网关、清除 iptables 规则并删除 /data/adb/mihomo。" +
                        "清理后需要点「重新安装内核」才能继续使用。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showCleanup = false
                    vm.cleanup()
                }) { Text("清理") }
            },
            dismissButton = {
                TextButton(onClick = { showCleanup = false }) { Text("取消") }
            }
        )
    }
}

/** 通用单选弹窗：iOS 设置里的「取值选择」。 */
@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    descriptions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (key, label) ->
                    val selected = key == selectedKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(key) })
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            descriptions[key]?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun ipModeHint(mode: IpMode): String = when (mode) {
    IpMode.AUTO -> "探测本机 IPv6，自动二选一"
    IpMode.DUAL -> "由内核双栈择优"
    IpMode.V6 -> "优先 AAAA，回落 IPv4"
    IpMode.V4 -> "优先 A，回落 AAAA"
    IpMode.V6_ONLY -> "只解析 AAAA（严格）"
    IpMode.V4_ONLY -> "只解析 A（严格）"
}

private fun ipModeDescription(mode: IpMode): String = when (mode) {
    IpMode.AUTO ->
        "启动或网络变化时探测本机 IPv6：可用则全节点 IPv6 优先，否则 IPv4 优先；切换时热重载配置，不重启进程"
    IpMode.DUAL ->
        "不覆写节点的 ip-version，由内核同时解析 A / AAAA，连接时择优（mihomo 默认）"
    IpMode.V6 -> "节点域名优先解析 AAAA，失败时回落到 IPv4"
    IpMode.V4 -> "节点域名优先解析 A，失败时回落到 AAAA"
    IpMode.V6_ONLY -> "只解析 AAAA，节点没有 IPv6 时无法连接（严格模式）"
    IpMode.V4_ONLY -> "只解析 A，节点没有 IPv4 时无法连接（严格模式）"
}

private fun runtimeLabel(mode: String): String = when (mode) {
    "global" -> "全局"
    "direct" -> "直连"
    else -> "规则"
}

private fun runtimeHint(mode: String): String = when (mode) {
    "global" -> "全部流量走代理"
    "direct" -> "全部流量不走代理"
    else -> "按分流规则决定走向"
}

