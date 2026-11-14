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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.ui.components.KeyValueRow
import com.mihomo.gateway.ui.components.Metric
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.SectionCard
import com.mihomo.gateway.ui.components.StatusPill
import com.mihomo.gateway.ui.components.TrafficSparkline

@Composable
fun OverviewScreen(vm: OverviewViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val history by vm.history.collectAsState()
    val busy by vm.busy.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ScreenHeader(
            title = "Mihomo Gateway",
            subtitle = "root 透明代理网关 · 热点 / 旁路由"
        ) {
            StatusPill(running = state.running)
        }

        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        if (state.running) "网关运行中" else "网关未启动",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "内核 ${state.version.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            KeyValueRow("节点地址族", state.mode.name)
            KeyValueRow("IPv6 拦截", if (state.blockIpv6) "开" else "关")
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard(
            title = "实时流量",
            trailing = {
                Text(
                    "最近 ${history.size}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric("上传", formatRate(traffic.up), Color(0xFF0F766E))
                Metric("下载", formatRate(traffic.down), Color(0xFF4F46E5))
            }
            TrafficSparkline(history)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.start() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("启动") }
            FilledTonalButton(
                onClick = { vm.stop() },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) { Text("停止") }
            OutlinedButton(
                onClick = { vm.refresh() },
                enabled = !busy
            ) { Text("刷新") }
        }

        Text(
            "状态与流量均来自 mihomo 本地 API（127.0.0.1:9090）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatRate(bytesPerSec: Long): String {
    val kb = bytesPerSec / 1024.0
    return when {
        kb < 1 -> "0 KB/s"
        kb < 1024 -> String.format("%.0f KB/s", kb)
        else -> String.format("%.1f MB/s", kb / 1024.0)
    }
}
