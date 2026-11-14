package com.mihomo.gateway.ui.overview

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun OverviewScreen(vm: OverviewViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("概览", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (state.running) "运行中" else "未启动",
                    style = MaterialTheme.typography.titleLarge
                )
                Text("内核版本: ${state.version.ifBlank { "-" }}")
                Text("当前模式: ${state.mode.name}")
                Text("IPv6 拦截: ${if (state.blockIpv6) "开" else "关"}")
                state.error?.let { Text("错误: $it", color = MaterialTheme.colorScheme.error) }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("实时流量", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("↑ ${formatRate(traffic.up)}")
                    Text("↓ ${formatRate(traffic.down)}")
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.start() }, enabled = !busy) { Text("启动") }
            Button(onClick = { vm.stop() }, enabled = !busy) { Text("停止") }
            OutlinedButton(onClick = { vm.refresh() }, enabled = !busy) { Text("刷新") }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "提示：状态来自 mihomo API，不再解析 shell 输出。",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun formatRate(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    return when {
        kb < 1024 -> String.format("%.0f KB/s", kb)
        else -> String.format("%.1f MB/s", kb / 1024.0)
    }
}
