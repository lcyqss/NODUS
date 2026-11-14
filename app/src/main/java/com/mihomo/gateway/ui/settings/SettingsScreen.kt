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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.IpMode

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
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
        Text("设置", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("订阅", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = subUrl,
                    onValueChange = { subUrl = it },
                    label = { Text("订阅链接") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.importSubscription(subUrl) }, enabled = !busy) {
                        Text("导入订阅")
                    }
                    OutlinedButton(onClick = { vm.installCore() }, enabled = !busy) {
                        Text("安装/更新核心")
                    }
                }
            }
        }

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
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("IPv6 拦截", style = MaterialTheme.typography.titleMedium)
                    Text("关闭后客户端可用 IPv6 直连", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.blockIpv6,
                    onCheckedChange = { vm.setBlockIpv6(it) },
                    enabled = !busy
                )
            }
        }

        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(4.dp))
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
    val selected = mode == current
    val modifier = Modifier
    if (selected) {
        Button(onClick = { onClick(mode) }, enabled = !busy, modifier = modifier) { Text(label) }
    } else {
        OutlinedButton(onClick = { onClick(mode) }, enabled = !busy, modifier = modifier) { Text(label) }
    }
}
