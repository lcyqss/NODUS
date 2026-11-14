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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.model.LogEntry
import com.mihomo.gateway.ui.components.EmptyState
import com.mihomo.gateway.ui.components.ScreenHeader
import com.mihomo.gateway.ui.components.Tag
import com.mihomo.gateway.ui.rules.RulesScreen
import android.content.Intent

@Composable
fun LogsScreen(logsVm: LogsViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val logs by logsVm.logs.collectAsState()
    val level by logsVm.level.collectAsState()
    val paused by logsVm.paused.collectAsState()
    var query by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(tab, level) {
        if (tab == 0) logsVm.start(level)
    }
    DisposableEffect(Unit) {
        onDispose { logsVm.stop() }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader(
            title = if (tab == 0) "日志" else "规则",
            subtitle = if (tab == 0) "实时输出 · 最多保留 500 行" else "自定义规则优先于订阅规则"
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
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索日志") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            val filtered = remember(logs, query) {
                if (query.isBlank()) logs else logs.filter { it.payload.contains(query, true) }
            }
            LogsTab(
                logs = filtered,
                level = level,
                paused = paused,
                onLevel = { logsVm.start(it) },
                onPause = { logsVm.pause() },
                onResume = { logsVm.resume() },
                onClear = { logsVm.clear() },
                onExport = {
                    val text = filtered.joinToString("\n") { "${it.level.uppercase()} ${it.payload}" }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text.ifBlank { "(无日志)" })
                    }
                    context.startActivity(Intent.createChooser(intent, "导出日志"))
                }
            )
        } else {
            RulesScreen()
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
    onClear: () -> Unit,
    onExport: () -> Unit
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
        item {
            OutlinedButton(onClick = onExport) { Text("导出") }
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
