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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ProxyNames
import com.mihomo.gateway.model.ProxyNode
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.Tag

@Composable
fun ProxiesScreen(vm: ProxiesViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val delays by vm.delays.collectAsState()
    val testing by vm.testing.collectAsState()
    val failed by vm.failed.collectAsState()

    val currentGroup = state.groups.firstOrNull { it.name == state.selectedGroup }
    var query by remember { mutableStateOf("") }
    var sortByDelay by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = "代理",
            subtitle = currentGroup?.let { "${it.members.size} 个节点 · ${it.type}" }
        ) {
            OutlinedButton(
                onClick = { currentGroup?.let(vm::testGroup) },
                enabled = currentGroup != null
            ) { Text("测速") }
        }

        Spacer(Modifier.height(10.dp))

        when {
            state.loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            state.error != null -> {
                EmptyState("加载失败：${state.error}")
                OutlinedButton(onClick = { vm.load() }) { Text("重试") }
            }

            state.groups.isEmpty() -> {
                EmptyState("没有可用的代理组，请先在设置里导入订阅")
            }

            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.groups, key = { it.name }) { group ->
                        val selected = group.name == state.selectedGroup
                        if (selected) {
                            Button(onClick = { vm.selectGroup(group.name) }) {
                                Text(group.name, maxLines = 1)
                            }
                        } else {
                            OutlinedButton(onClick = { vm.selectGroup(group.name) }) {
                                Text(group.name, maxLines = 1)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (currentGroup != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "当前节点",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                currentGroup.now?.takeUnless { ProxyNames.isInfoNode(it) } ?: "-",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                        }
                        Tag(currentGroup.type)
                    }

                    if (failed.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${failed.size} 个节点测速失败，可能是节点不可达或网络受限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("搜索节点") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { sortByDelay = !sortByDelay }) {
                            Text(if (sortByDelay) "延迟↑" else "排序")
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    val members = remember(currentGroup, query, sortByDelay, delays) {
                        val base = if (query.isBlank()) currentGroup.members
                        else currentGroup.members.filter { it.name.contains(query, true) }
                        if (sortByDelay) {
                            base.sortedWith(
                                compareBy(
                                    { delays[it.name] ?: it.delay ?: Int.MAX_VALUE },
                                    { it.name }
                                )
                            )
                        } else {
                            base
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(members, key = { it.name }) { node ->
                            NodeRow(
                                node = node,
                                selected = node.name == currentGroup.now,
                                delay = delays[node.name],
                                testing = testing.contains(node.name),
                                failed = failed.contains(node.name),
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
    failed: Boolean,
    canSelect: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canSelect, onClick = onClick)
            .background(background, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(28.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(node.type)
                if (node.isGroup) Tag("组", MaterialTheme.colorScheme.secondary)
            }
        }
        DelayBadge(
            delay = delay ?: node.delay,
            testing = testing,
            failed = failed
        )
    }
}

@Composable
private fun DelayBadge(delay: Int?, testing: Boolean, failed: Boolean) {
    val text = when {
        testing -> "…"
        failed -> "失败"
        delay != null -> "${delay}ms"
        else -> "—"
    }
    val color = when {
        testing -> MaterialTheme.colorScheme.outline
        failed -> Color(0xFFDC2626)
        delay == null -> MaterialTheme.colorScheme.outline
        delay < 200 -> Color(0xFF16A34A)
        delay < 500 -> Color(0xFFEA580C)
        else -> Color(0xFFDC2626)
    }
    Box(
        Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}
