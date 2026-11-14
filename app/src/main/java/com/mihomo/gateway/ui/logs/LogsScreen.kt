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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.LogEntry
import com.mihomo.gateway.model.RuleInfo
import com.mihomo.gateway.ui.rules.RulesViewModel

@Composable
fun LogsScreen(
    logsVm: LogsViewModel = viewModel(),
    rulesVm: RulesViewModel = viewModel()
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val logs by logsVm.logs.collectAsState()
    val level by logsVm.level.collectAsState()

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (tab == 0) Button(onClick = { tab = 0 }) { Text("日志") }
            else OutlinedButton(onClick = { tab = 0 }) { Text("日志") }
            if (tab == 1) Button(onClick = { tab = 1 }) { Text("规则") }
            else OutlinedButton(onClick = { tab = 1 }) { Text("规则") }
        }

        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            LogsTab(logs, level, onLevel = { logsVm.start(it) }, onClear = { logsVm.clear() })
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
    onLevel: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("info", "warning", "error").forEach { item ->
            if (item == level) Button(onClick = { onLevel(item) }) { Text(item) }
            else OutlinedButton(onClick = { onLevel(item) }) { Text(item) }
        }
        OutlinedButton(onClick = onClear) { Text("清空") }
    }

    Spacer(Modifier.height(8.dp))

    val listState = rememberLazyListState()
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(logs) { entry ->
            Text(
                text = "${entry.level.uppercase().take(4)}  ${entry.payload}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = when (entry.level) {
                    "error" -> Color(0xFFC62828)
                    "warning" -> Color(0xFFEF6C00)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun RulesTab(rules: List<RuleInfo>, error: String?, onReload: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(rules, query) {
        if (query.isBlank()) rules
        else rules.filter {
            it.payload.contains(query, true) ||
                it.type.contains(query, true) ||
                it.proxy.contains(query, true)
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${filtered.size} / ${rules.size} 条", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = onReload) { Text("刷新") }
    }

    error?.let {
        Text("错误: $it", color = MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(6.dp))

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(filtered) { rule ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(10.dp)
            ) {
                Text(rule.type, style = MaterialTheme.typography.labelMedium)
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
