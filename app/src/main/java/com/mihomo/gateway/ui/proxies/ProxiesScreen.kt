package com.mihomo.gateway.ui.proxies

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ProxyNode

@Composable
fun ProxiesScreen(vm: ProxiesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val delays by vm.delays.collectAsState()
    val testing by vm.testing.collectAsState()

    val currentGroup = state.groups.firstOrNull { it.name == state.selectedGroup }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("代理", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                Text("加载失败: ${state.error}", color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vm.load() }) { Text("重试") }
            }

            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.groups, key = { it.name }) { group ->
                        val selected = group.name == state.selectedGroup
                        if (selected) {
                            Button(onClick = { vm.selectGroup(group.name) }) {
                                Text(group.name)
                            }
                        } else {
                            OutlinedButton(onClick = { vm.selectGroup(group.name) }) {
                                Text(group.name)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                if (currentGroup != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("当前: ${currentGroup.now ?: "-"}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "类型: ${currentGroup.type} · ${currentGroup.members.size} 个节点",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = { vm.testGroup(currentGroup) }) {
                            Text("测速")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(currentGroup.members, key = { it.name }) { node ->
                            NodeRow(
                                node = node,
                                selected = node.name == currentGroup.now,
                                delay = delays[node.name],
                                testing = testing.contains(node.name),
                                canSelect = currentGroup.type == "Selector" && !node.isGroup,
                                onClick = {
                                    if (currentGroup.type == "Selector" && !node.isGroup) {
                                        vm.selectNode(currentGroup.name, node.name)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeRow(
    node: ProxyNode,
    selected: Boolean,
    delay: Int?,
    testing: Boolean,
    canSelect: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canSelect, onClick = onClick)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (selected) "●" else "○",
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(node.name, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            Text(node.type, style = MaterialTheme.typography.bodySmall)
        }
        if (node.isGroup) {
            Text("组", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = when {
                testing -> "测速中…"
                delay != null -> "${delay}ms"
                node.delay != null -> "${node.delay}ms"
                else -> "-"
            },
            color = when {
                testing -> MaterialTheme.colorScheme.outline
                delay != null -> delayColor(delay)
                node.delay != null -> delayColor(node.delay)
                else -> MaterialTheme.colorScheme.outline
            },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun delayColor(delay: Int): Color = when {
    delay < 200 -> Color(0xFF2E7D32)
    delay < 500 -> Color(0xFFEF6C00)
    else -> Color(0xFFC62828)
}
