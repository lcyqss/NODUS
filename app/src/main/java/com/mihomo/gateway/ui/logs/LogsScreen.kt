package com.mihomo.gateway.ui.logs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.LogEntry
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.Tag
import com.mihomo.gateway.ui.rules.RulesViewModel

@Composable
fun LogsScreen(
    logsVm: LogsViewModel = viewModel(),
    rulesVm: RulesViewModel = viewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val logs by logsVm.logs.collectAsState()
    val level by logsVm.level.collectAsState()
    val paused by logsVm.paused.collectAsState()

    LaunchedEffect(tab, level) {
        if (tab == 0) logsVm.start(level)
    }
    DisposableEffect(Unit) {
        onDispose { logsVm.stop() }
    }
    LaunchedEffect(tab) {
        if (tab == 1) rulesVm.loadIfNeeded()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = if (tab == 0) "日志" else "规则",
            subtitle = if (tab == 0) "实时输出 · 最多保留 500 行" else null
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == 0) Button(onClick = { tab = 0 }) { Text("日志") }
                else OutlinedButton(onClick = { tab = 0 }) { Text("日志") }
                if (tab == 1) Button(onClick = { tab = 1 }) { Text("规则") }
                else OutlinedButton(onClick = { tab = 1 }) { Text("规则") }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (tab == 0) {
            LogsTab(
                logs = logs,
                level = level,
                paused = paused,
                onLevel = { logsVm.start(it) },
                onPause = { logsVm.pause() },
                onResume = { logsVm.resume() },
                onClear = { logsVm.clear() }
            )
        } else {
            val rules by rulesVm.rules.collectAsState()
            val error by rulesVm.error.collectAsState()
            RulesTab(rules, error, onReload = { rulesVm.load() })
        }
    }
}

@Composable
private fun LogsTab(
    logs: List<LogEntry>,
    level: String,
    paused: Boolean,
    onLevel: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onClear: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(listOf("info", "warning", "error")) { item ->
            if (item == level) Button(onClick = { onLevel(item) }) { Text(item) }
            else OutlinedButton(onClick = { onLevel(item) }) { Text(item) }
        }
        item {
            OutlinedButton(onClick = if (paused) onResume else onPause) {
                Text(if (paused) "继续" else "暂停")
            }
        }
        item {
            OutlinedButton(onClick = onClear) { Text("清空") }
        }
    }

    Spacer(Modifier.height(8.dp))

    if (paused) {
        Text(
            "已暂停，列表不再更新",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
    }

    if (logs.isEmpty()) {
        EmptyState("等待日志…（产生代理流量后会出现）")
        return
    }

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size, paused) {
        if (!paused && logs.isNotEmpty()) listState.scrollToItem(logs.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(logs) { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Tag(
                    text = entry.level.take(4),
                    color = when (entry.level) {
                        "error" -> Color(0xFFDC2626)
                        "warning" -> Color(0xFFEA580C)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = entry.payload,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RulesTab(rules: List<RuleInfo>, error: String?, onReload: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<String?>(null) }

    val types = remember(rules) { rules.map { it.type }.distinct().take(10) }

    val filtered = remember(rules, query, typeFilter) {
        rules.filter { rule ->
            (typeFilter == null || rule.type == typeFilter) &&
                (query.isBlank() ||
                    rule.payload.contains(query, true) ||
                    rule.type.contains(query, true) ||
                    rule.proxy.contains(query, true))
        }
    }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text("搜索规则 / 策略") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            if (typeFilter == null) Button(onClick = { typeFilter = null }) { Text("全部") }
            else OutlinedButton(onClick = { typeFilter = null }) { Text("全部") }
        }
        items(types) { type ->
            if (typeFilter == type) Button(onClick = { typeFilter = type }) { Text(type) }
            else OutlinedButton(onClick = { typeFilter = type }) { Text(type) }
        }
    }

    Spacer(Modifier.height(8.dp))

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${filtered.size} / ${rules.size} 条",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onReload) { Text("刷新") }
    }

    error?.let { Text("错误: $it", color = MaterialTheme.colorScheme.error) }

    Spacer(Modifier.height(6.dp))

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(filtered) { rule ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(rule.type)
                }
                Text(rule.payload, style = MaterialTheme.typography.bodySmall)
                Text(
                    "→ ${rule.proxy}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
