package com.mihomo.gateway.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.ui.components.KeyValueRow
import com.mihomo.gateway.ui.components.Metric
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.SectionCard
import com.mihomo.gateway.ui.components.StatusPill
import com.mihomo.gateway.ui.components.TrafficChart
import com.mihomo.gateway.ui.icons.GwIcon

private val UpColor = Color(0xFF0F766E)
private val DownColor = Color(0xFF4F46E5)

@Composable
fun OverviewScreen(vm: OverviewViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val traffic by vm.traffic.collectAsState()
    val upHistory by vm.upHistory.collectAsState()
    val downHistory by vm.downHistory.collectAsState()
    val memory by vm.memory.collectAsState()
    val stats by vm.stats.collectAsState()
    val currentNode by vm.currentNode.collectAsState()
    val busy by vm.busy.collectAsState()
    val runtimeMode by vm.runtimeMode.collectAsState()
    val today by vm.today.collectAsState()

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(running = state.running)
                Spacer(Modifier.size(4.dp))
                IconButton(onClick = { vm.refresh() }, enabled = !busy) {
                    GwIcon(GwIcon.Refresh, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        SectionCard {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PowerButton(running = state.running, busy = busy) { vm.toggle() }
                Text(
                    when {
                        busy -> "处理中…"
                        state.running -> "点击停止"
                        else -> "点击启动"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "内核 ${state.version.ifBlank { "-" }}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("rule" to "规则", "global" to "全局", "direct" to "直连").forEach { (mode, label) ->
                    if (runtimeMode == mode) {
                        Button(onClick = { vm.setRuntimeMode(mode) }, enabled = !busy) { Text(label) }
                    } else {
                        OutlinedButton(onClick = { vm.setRuntimeMode(mode) }, enabled = !busy) { Text(label) }
                    }
                }
            }
            KeyValueRow("当前节点", currentNode ?: "-")
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
                    "最近 ${upHistory.size}s",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Metric("上传", formatRate(traffic.up), UpColor)
                Metric("下载", formatRate(traffic.down), DownColor)
            }
            if (upHistory.any { it > 0 } || downHistory.any { it > 0 }) {
                TrafficChart(upValues = upHistory, downValues = downHistory)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(UpColor, "上传")
                    LegendDot(DownColor, "下载")
                }
            } else {
                Text(
                    "暂无流量，产生代理连接后显示曲线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = "运行统计") {
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric("连接数", "${stats.connections}", MaterialTheme.colorScheme.onSurface)
                Metric("内存", formatMemory(memory), MaterialTheme.colorScheme.onSurface)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric("累计上传", formatBytes(stats.uploadTotal), UpColor)
                Metric("累计下载", formatBytes(stats.downloadTotal), DownColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Metric("今日上传", formatBytes(today.up), UpColor)
                Metric("今日下载", formatBytes(today.down), DownColor)
            }
        }

        Text(
            "数据来自 mihomo 本地 API（/traffic、/memory、/connections）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PowerButton(running: Boolean, busy: Boolean, onClick: () -> Unit) {
    val background = when {
        busy -> MaterialTheme.colorScheme.surfaceVariant
        running -> Color(0xFF16A34A)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .size(104.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            GwIcon(GwIcon.Power, Modifier.size(44.dp), Color.White)
        }
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

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.2fGB", bytes / 1024.0 / 1024.0 / 1024.0)
}

private fun formatMemory(bytes: Long): String = when {
    bytes <= 0 -> "-"
    bytes < 1024L * 1024 * 1024 -> String.format("%.0f MB", bytes / 1024.0 / 1024.0)
    else -> String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0)
}
