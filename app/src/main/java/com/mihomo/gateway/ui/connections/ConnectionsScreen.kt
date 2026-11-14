package com.mihomo.gateway.ui.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.KeyValueRow
import com.mihomo.gateway.ui.components.ScreenHeader

@Composable
fun ConnectionsScreen(vm: ConnectionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = "连接",
            subtitle = "${state.connections.size} 条活动连接 · ↑${formatBytes(state.connections.sumOf { it.upload })} ↓${formatBytes(state.connections.sumOf { it.download })}"
        ) {
            OutlinedButton(onClick = { vm.closeAll() }) { Text("全部断开") }
        }

        Spacer(Modifier.height(10.dp))

        state.error?.let {
            Text("错误: $it", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        if (state.loaded && state.connections.isEmpty()) {
            EmptyState("暂无活动连接")
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.connections, key = { it.id }) { conn ->
                ConnectionRow(
                    conn = conn,
                    expanded = expandedId == conn.id,
                    onToggle = {
                        expandedId = if (expandedId == conn.id) null else conn.id
                    },
                    onClose = { vm.close(conn.id) }
                )
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    conn: ConnectionInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    conn.host.ifBlank { "-" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "${conn.network} · ${conn.rule.ifBlank { "-" }} · ${conn.chains.joinToString(" → ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    "↑ ${formatBytes(conn.upload)}   ↓ ${formatBytes(conn.download)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "✕",
                modifier = Modifier
                    .clickable(onClick = onClose)
                    .padding(8.dp),
                color = MaterialTheme.colorScheme.error
            )
        }

        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            KeyValueRow("源地址", conn.sourceIP.ifBlank { "-" })
            KeyValueRow("目标地址", conn.destinationIP.ifBlank { "-" })
            KeyValueRow("网络类型", conn.network)
            KeyValueRow("规则", conn.rule.ifBlank { "-" })
            KeyValueRow("链路", conn.chains.joinToString(" → ").ifBlank { "-" })
            KeyValueRow("已持续", formatDuration(System.currentTimeMillis() - conn.start))
            KeyValueRow("上传", formatBytes(conn.upload))
            KeyValueRow("下载", formatBytes(conn.download))
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
    else -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        seconds < 60 -> "${seconds}s"
        minutes < 60 -> "${minutes}m ${seconds % 60}s"
        else -> "${hours}h ${minutes % 60}m"
    }
}
