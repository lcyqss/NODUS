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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.ScreenHeader

@Composable
fun ConnectionsScreen(vm: ConnectionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = "连接",
            subtitle = "${state.connections.size} 条活动连接"
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
                ConnectionRow(conn) { vm.close(conn.id) }
            }
        }
    }
}

@Composable
private fun ConnectionRow(conn: ConnectionInfo, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> String.format("%.1fKB", bytes / 1024.0)
    else -> String.format("%.1fMB", bytes / 1024.0 / 1024.0)
}
