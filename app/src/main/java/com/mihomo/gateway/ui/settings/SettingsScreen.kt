package com.mihomo.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.IpMode
import com.mihomo.gateway.ui.diagnostics.DiagnosticsScreen

private enum class SettingsPage { MAIN, SUBSCRIPTION, DIAGNOSTICS }

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.MAIN) }
    when (page) {
        SettingsPage.MAIN -> SettingsMain(
            vm = vm,
            onSubscription = { page = SettingsPage.SUBSCRIPTION },
            onDiagnostics = { page = SettingsPage.DIAGNOSTICS }
        )

        SettingsPage.SUBSCRIPTION -> SubscriptionPage(vm, onBack = { page = SettingsPage.MAIN })
        SettingsPage.DIAGNOSTICS -> DiagnosticsScreen(onBack = { page = SettingsPage.MAIN })
    }
}

@Composable
private fun SettingsMain(
    vm: SettingsViewModel,
    onSubscription: () -> Unit,
    onDiagnostics: () -> Unit
) {
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "-"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("节点地址族", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeButton("自动", IpMode.AUTO, settings.ipMode, busy, vm::setMode)
                    ModeButton("IPv6", IpMode.V6, settings.ipMode, busy, vm::setMode)
                    ModeButton("IPv4", IpMode.V4, settings.ipMode, busy, vm::setMode)
                    ModeButton("双栈", IpMode.DUAL, settings.ipMode, busy, vm::setMode)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SwitchRow(
                    title = "IPv6 拦截",
                    subtitle = "关闭后客户端可用 IPv6 直连",
                    checked = settings.blockIpv6,
                    enabled = !busy,
                    onChange = vm::setBlockIpv6
                )
                SwitchRow(
                    title = "开机自启",
                    subtitle = "重启后自动启动网关",
                    checked = settings.autoStart,
                    enabled = !busy,
                    onChange = vm::setAutoStart
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("管理", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSubscription, enabled = !busy) { Text("订阅管理") }
                    OutlinedButton(onClick = onDiagnostics, enabled = !busy) { Text("诊断") }
                }
                OutlinedButton(
                    onClick = { vm.installCore() },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("安装/更新核心") }
            }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Spacer(Modifier.height(4.dp))
        Text(
            "Mihomo Gateway v$versionName · 内核 mihomo",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SubscriptionPage(vm: SettingsViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val busy by vm.busy.collectAsState()
    var subUrl by remember(settings.subscriptionUrl) { mutableStateOf(settings.subscriptionUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("返回") }
            Text("订阅管理", style = MaterialTheme.typography.headlineSmall)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    label = { Text("订阅链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { vm.importSubscription(subUrl) }, enabled = !busy) {
                    Text("导入订阅")
                }
                Text(
                    "当前订阅: ${settings.subscriptionUrl.ifBlank { "未设置" }}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "导入后会：校验 Clash 配置 → 写入 config.yaml → 自动补全网关字段 → 重启内核",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        message?.let { Text(it) }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ModeButton(
    label: String,
    mode: IpMode,
    current: IpMode,
    busy: Boolean,
    onClick: (IpMode) -> Unit
) {
    if (mode == current) {
        Button(onClick = { onClick(mode) }, enabled = !busy) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, enabled = !busy) { Text(label) }
    }
}
