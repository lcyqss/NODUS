package com.mihomo.gateway.ui.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.ConnectionInfo
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.icons.GwIcon
import com.mihomo.gateway.ui.components.SearchBar
import com.mihomo.gateway.ui.components.SegmentedControl

private data class TopoRow(
    val clientIp: String,
    val clientPort: String,
    val group: String,
    val proxy: String,
    val rule: String,
    val count: Int,
    val idleCount: Int,
    val traffic: Long
)

@Composable
fun ConnectionsScreen(vm: ConnectionsViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var expandedId by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var frozen by remember { mutableStateOf<List<ConnectionInfo>>(emptyList()) }
    var deviceFilter by remember { mutableStateOf<String?>(null) }
    var activityFilter by rememberSaveable { mutableIntStateOf(0) }
    var showCleanDialog by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var showCloseAllDialog by remember { mutableStateOf(false) }
    val autoCleanConfig by vm.autoCleanConfig.collectAsState()

    val neighborMap by vm.neighborMap.collectAsState()
    val deviceHistory by vm.deviceHistory.collectAsState()
    val macOf: (String) -> String = { ip -> neighborMap[ip]?.mac ?: "-" }
    val labelOf: (String) -> String = { ip ->
        deviceHistory[ip]?.label ?: neighborMap[ip]?.hostname?.takeIf { it.isNotBlank() } ?: ip
    }

    DisposableEffect(Unit) {
        vm.startPolling()
        onDispose { vm.stopPolling() }
    }
    LaunchedEffect(paused) {
        if (paused) {
            frozen = state.connections
            vm.stopPolling()
        } else {
            vm.startPolling()
        }
    }

    val raw = if (paused) frozen else state.connections
    val deviceFiltered = if (deviceFilter == null) raw else raw.filter { it.sourceIP == deviceFilter }
    val activeCount = deviceFiltered.count { !it.inactive }
    val idleCount = deviceFiltered.count { it.inactive }
    val data = when (activityFilter) {
        1 -> deviceFiltered.filter { !it.inactive }
        2 -> deviceFiltered.filter { it.inactive }
        else -> deviceFiltered
    }
    val filtered = remember(data, query) {
        if (query.isBlank()) data
        else data.filter {
            it.host.contains(query, true) ||
                it.rule.contains(query, true) ||
                it.sourceIP.contains(query, true) ||
                it.chains.any { c -> c.contains(query, true) }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${data.size} 条连接",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    "↑${formatBytes(data.sumOf { it.upload })}  ↓${formatBytes(data.sumOf { it.download })}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HeaderIcon(GwIcon.Logs, selected = mode == 0) { mode = 0 }
            HeaderIcon(GwIcon.Tree, selected = mode == 1) { mode = 1 }
            HeaderIcon(GwIcon.Chart, selected = mode == 2) { mode = 2 }
            Spacer(Modifier.width(6.dp))
            HeaderIcon(
                if (paused) GwIcon.Play else GwIcon.Pause,
                selected = paused
            ) {
                paused = !paused
                if (!paused) frozen = emptyList()
            }
            HeaderIcon(
                GwIcon.Close,
                tint = MaterialTheme.colorScheme.error
            ) { showCloseAllDialog = true }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActivityChip("全部 ${deviceFiltered.size}", activityFilter == 0) { activityFilter = 0 }
                ActivityChip("活跃 $activeCount", activityFilter == 1) { activityFilter = 1 }
                ActivityChip("空闲 $idleCount", activityFilter == 2) { activityFilter = 2 }
            }
            TextButton(
                onClick = { showCleanDialog = true },
                enabled = idleCount > 0
            ) { Text("清理空闲", maxLines = 1) }
            TextButton(onClick = { showAutoDialog = true }) {
                Text(if (autoCleanConfig.first) "自动·开" else "自动", maxLines = 1)
            }
        }

        if (showCleanDialog) {
            val count = vm.cleanCandidates().size
            AlertDialog(
                onDismissRequest = { showCleanDialog = false },
                title = { Text("清理空闲连接") },
                text = { Text("将关闭 $count 条空闲超过 10 分钟且没有任何流量的连接。") },
                confirmButton = {
                    TextButton(onClick = {
                        showCleanDialog = false
                        vm.cleanIdle()
                    }) { Text("清理") }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanDialog = false }) { Text("取消") }
                }
            )
        }

        if (showAutoDialog) {
            val enabled = autoCleanConfig.first
            val threshold = autoCleanConfig.second
            AlertDialog(
                onDismissRequest = { showAutoDialog = false },
                title = { Text("自动清理空闲连接") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("超过 $threshold 条时自动清理", Modifier.weight(1f))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { vm.setAutoClean(it, threshold) }
                            )
                        }
                        Text(
                            "清理条件：空闲超过 30 分钟且没有任何流量；默认关闭。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("触发阈值（连接总数）", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(500, 800, 1200, 2000).forEach { value ->
                                ActivityChip("$value", threshold == value) {
                                    vm.setAutoClean(enabled, value)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAutoDialog = false }) { Text("完成") }
                }
            )
        }

        if (showCloseAllDialog) {
            AlertDialog(
                onDismissRequest = { showCloseAllDialog = false },
                title = { Text("关闭全部连接") },
                text = {
                    Text(
                        "将关闭全部 ${raw.size} 条连接（含活跃连接），" +
                            "所有设备的当前会话都会中断，且无法恢复。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCloseAllDialog = false
                        vm.closeAll()
                    }) {
                        Text("全部关闭", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloseAllDialog = false }) { Text("取消") }
                }
            )
        }

        SearchBar(
            value = query,
            onValueChange = { query = it },
            placeholder = if (mode == 0) "搜索域名 / 客户端 IP / 规则" else "过滤客户端 / 节点 / 规则"
        )

        Spacer(Modifier.height(6.dp))

        if (deviceHistory.isNotEmpty()) {
            RecentDevices(
                devices = deviceHistory.values.sortedByDescending { it.lastSeen },
                online = data.map { it.sourceIP }.toSet(),
                selected = deviceFilter,
                onSelect = { ip -> deviceFilter = if (ip == null || deviceFilter == ip) null else ip }
            )
            Spacer(Modifier.height(6.dp))
        }

        state.error?.let {
            Text("错误: $it", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(6.dp))
        }

        if (filtered.isEmpty()) {
            EmptyState(
                when {
                    activityFilter == 1 -> "暂无活跃连接"
                    activityFilter == 2 -> "暂无空闲连接"
                    mode == 0 -> "暂无活动连接"
                    else -> "暂无活动连接，无法生成拓扑"
                }
            )
            return@Column
        }

        when (mode) {
            0 -> ConnectionList(
                connections = filtered,
                expandedId = expandedId,
                onToggle = { id -> expandedId = if (expandedId == id) null else id },
                onClose = { id -> vm.close(id) },
                macOf = macOf
            )

            1 -> {
                val rows = remember(filtered) { aggregate(filtered) }
                TopologyTable(rows, data, labelOf)
            }

            else -> TopologyGraph(filtered, labelOf)
        }
    }
}

@Composable
private fun ConnectionList(
    connections: List<ConnectionInfo>,
    expandedId: String?,
    onToggle: (String) -> Unit,
    onClose: (String) -> Unit,
    macOf: (String) -> String
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(connections, key = { it.id }) { conn ->
            ConnectionRow(
                conn = conn,
                expanded = expandedId == conn.id,
                onToggle = { onToggle(conn.id) },
                onClose = { onClose(conn.id) },
                mac = macOf(conn.sourceIP)
            )
        }
    }
}

@Composable
private fun TopologyTable(
    rows: List<TopoRow>,
    all: List<ConnectionInfo>,
    labelOf: (String) -> String
) {
    val devices = all.map { it.sourceIP }.distinct().size
    val totalTraffic = rows.sumOf { it.traffic }
    var sort by remember { mutableIntStateOf(0) }
    val sortedRows = remember(rows, sort) {
        when (sort) {
            0 -> rows.sortedByDescending { it.traffic }
            1 -> rows.sortedBy { it.clientIp }
            else -> rows.sortedBy { it.rule }
        }
    }

    var sortMenu by remember { mutableStateOf(false) }
    val sortLabels = listOf("流量 ↓", "设备", "规则")

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                Text("设备", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$devices", style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text("连接", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${all.size}", style = MaterialTheme.typography.titleMedium)
            }
            Column {
                Text("流量", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatBytes(totalTraffic), style = MaterialTheme.typography.titleMedium)
            }
        }
        Box {
            TextButton(
                onClick = { sortMenu = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
            ) {
                Text(
                    "排序 ${sortLabels[sort]}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    " ▾",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                sortLabels.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        trailingIcon = {
                            if (sort == index) {
                                Text("✓", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = {
                            sort = index
                            sortMenu = false
                        }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(sortedRows, key = { "${it.clientIp}|${it.clientPort}|${it.group}|${it.proxy}|${it.rule}" }) { row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .alpha(if (row.count > 0 && row.idleCount >= row.count) 0.42f else 1f)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        labelOf(row.clientIp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (row.idleCount > 0) "${row.count} 条 · ${row.idleCount} 空闲" else "${row.count} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${row.clientIp}:${row.clientPort} → ${row.proxy}  (${groupLabel(row.group)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        row.rule,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatBytes(row.traffic),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    conn: ConnectionInfo,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    mac: String
) {
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (conn.inactive) 0.42f else 1f)
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
                    buildString {
                        append(conn.network)
                        append(" · ").append(conn.rule.ifBlank { "-" })
                        append(" · ").append(conn.chains.joinToString(" → "))
                        if (conn.inactive) append(" · 空闲 ").append(formatIdle(conn.idleSeconds))
                    },
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
            KeyValue("源地址", "${conn.sourceIP}:${conn.sourcePort}".trimEnd(':'))
            KeyValue("MAC", mac)
            KeyValue("目标地址", conn.destinationIP.ifBlank { "-" })
            KeyValue("网络类型", conn.network)
            KeyValue("规则", buildString {
                append(conn.rule.ifBlank { "-" })
                if (conn.rulePayload.isNotBlank()) append(": ${conn.rulePayload}")
            })
            KeyValue("链路", conn.chains.joinToString(" → ").ifBlank { "-" })
            KeyValue("已持续", formatDuration(System.currentTimeMillis() - conn.start))
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RecentDevices(
    devices: List<DeviceSeen>,
    online: Set<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            val active = selected == null
            Row(
                Modifier
                    .clickable { onSelect(null) }
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { Text("全部", style = MaterialTheme.typography.labelSmall) }
        }
        items(devices, key = { it.ip }) { device ->
            val isOnline = online.contains(device.ip)
            val active = selected == device.ip
            Row(
                Modifier
                    .clickable { onSelect(device.ip) }
                    .background(
                        if (active) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .height(6.dp)
                        .width(6.dp)
                        .background(
                            if (isOnline) Color(0xFF16A34A) else MaterialTheme.colorScheme.outline,
                            androidx.compose.foundation.shape.CircleShape
                        )
                )
                Spacer(Modifier.width(6.dp))
                Text(device.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

private fun aggregate(connections: List<ConnectionInfo>): List<TopoRow> {
    data class Key(
        val ip: String,
        val port: String,
        val group: String,
        val proxy: String,
        val rule: String
    )

    val map = linkedMapOf<Key, Triple<Int, Int, Long>>()
    connections.forEach { conn ->
        val group = conn.chains.getOrNull(1) ?: "DIRECT"
        val proxy = conn.chains.getOrNull(0) ?: "DIRECT"
        val rule = buildString {
            append(conn.rule.ifBlank { "Match" })
            if (conn.rulePayload.isNotBlank()) append(": ${conn.rulePayload}")
        }
        val key = Key(conn.sourceIP, conn.sourcePort, group, proxy, rule)
        val (count, idle, traffic) = map[key] ?: Triple(0, 0, 0L)
        map[key] = Triple(
            count + 1,
            idle + if (conn.inactive) 1 else 0,
            traffic + conn.upload + conn.download
        )
    }

    return map.map { (key, value) ->
        TopoRow(
            clientIp = key.ip,
            clientPort = key.port,
            group = key.group,
            proxy = key.proxy,
            rule = key.rule,
            count = value.first,
            idleCount = value.second,
            traffic = value.third
        )
    }.sortedByDescending { it.traffic }
}

@Composable
private fun ActivityChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg
    )
}

private fun formatIdle(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m"
    else -> "${seconds / 3600}h${(seconds % 3600) / 60}m"
}

private fun groupLabel(group: String): String = when (group) {
    "DIRECT" -> "直连"
    "REJECT" -> "拦截"
    else -> group
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

@Composable
private fun HeaderIcon(
    icon: GwIcon,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GwIcon(
            icon,
            Modifier.size(20.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else tint
        )
    }
}