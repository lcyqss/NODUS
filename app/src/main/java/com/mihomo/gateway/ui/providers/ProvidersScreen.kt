package com.mihomo.gateway.ui.providers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ProviderInfo
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.KeyValueRow
import com.mihomo.gateway.ui.components.SubPageHeader
import com.mihomo.gateway.ui.components.Tag

@Composable
fun ProvidersScreen(onBack: () -> Unit, vm: ProvidersViewModel = viewModel()) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        SubPageHeader(
            title = "节点来源",
            subtitle = "Provider · 节点数 / 健康检查",
            onBack = onBack
        ) {
            OutlinedButton(onClick = { vm.load() }) { Text("刷新") }
        }

        Spacer(Modifier.height(10.dp))

        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
        }
        state.error?.let {
            Text("错误: $it", color = MaterialTheme.colorScheme.error)
        }

        if (state.loading && state.providers.isEmpty()) {
            EmptyState("加载中…")
        } else if (state.providers.isEmpty()) {
            EmptyState("没有 provider（订阅可能是内联 proxies）")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(state.providers, key = { it.name }) { provider ->
                ProviderCard(
                    provider = provider,
                    busy = state.busyProvider == provider.name,
                    onHealthCheck = { vm.healthCheck(provider.name) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(provider: ProviderInfo, busy: Boolean, onHealthCheck: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(provider.type)
                    Tag(provider.vehicleType, MaterialTheme.colorScheme.secondary)
                }
            }
            KeyValueRow("节点数", "${provider.nodeCount}")
            KeyValueRow("更新时间", formatUpdatedAt(provider.updatedAt))
            if (provider.testUrl.isNotBlank()) {
                KeyValueRow("测试地址", provider.testUrl)
            }
            OutlinedButton(onClick = onHealthCheck, enabled = !busy) {
                Text(if (busy) "检查中…" else "健康检查")
            }
        }
    }
}

private fun formatUpdatedAt(value: String): String {
    if (value.isBlank() || value.startsWith("0001-01-01")) return "—"
    return value.replace("T", " ").take(19)
}
