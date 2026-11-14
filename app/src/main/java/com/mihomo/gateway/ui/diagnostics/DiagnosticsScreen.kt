package com.mihomo.gateway.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mihomo.gateway.ui.components.SubPageHeader

@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit,
    vm: DiagnosticsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val records by vm.records.collectAsState()
    val actionMessage by vm.actionMessage.collectAsState()
    val dnsAnswers by vm.dnsAnswers.collectAsState()
    val dnsBusy by vm.dnsBusy.collectAsState()
    var dnsInput by remember { mutableStateOf("www.gstatic.com") }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SubPageHeader(
            title = "诊断",
            subtitle = "内核状态 / root 命令 / 规则快照",
            onBack = onBack
        ) {
            OutlinedButton(onClick = { vm.refresh() }) { Text("刷新") }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("状态", style = MaterialTheme.typography.titleMedium)
                Text("内核: ${state.version.ifBlank { "-" }}")
                Text("运行: ${if (state.running) "是" else "否"}")
                Text("API 延迟: ${if (state.latencyMs >= 0) "${state.latencyMs}ms" else "-"}")
                state.error?.let { Text("错误: $it", color = MaterialTheme.colorScheme.error) }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("最近 root 命令", style = MaterialTheme.typography.titleMedium)
                if (records.isEmpty()) {
                    Text("暂无记录", style = MaterialTheme.typography.bodySmall)
                } else {
                    records.takeLast(8).reversed().forEach {
                        Text(
                            "[${it.trace}] code=${it.code} ${it.costMs}ms\n${it.command}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("内核操作", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.restartCore() }) { Text("重启内核") }
                    OutlinedButton(onClick = { vm.flushFakeIp() }) { Text("清 fake-ip") }
                    OutlinedButton(onClick = { vm.flushDns() }) { Text("清 DNS") }
                }
                actionMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DNS 查询", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = dnsInput,
                    onValueChange = { dnsInput = it },
                    label = { Text("域名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.queryDns(dnsInput, "A") }, enabled = !dnsBusy) { Text("A 记录") }
                    OutlinedButton(onClick = { vm.queryDns(dnsInput, "AAAA") }, enabled = !dnsBusy) { Text("AAAA 记录") }
                    OutlinedButton(onClick = { vm.queryDns(dnsInput, "CNAME") }, enabled = !dnsBusy) { Text("CNAME") }
                }
                if (dnsAnswers.isEmpty()) {
                    Text("暂无结果", style = MaterialTheme.typography.bodySmall)
                } else {
                    dnsAnswers.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("规则快照", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.snapshot() }) { Text("抓取") }
                }
                if (state.rules.isBlank()) {
                    Text("尚未抓取", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(
                        state.rules,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.buildReport())
                }
                context.startActivity(Intent.createChooser(send, "分享诊断信息"))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("分享诊断信息") }
    }
}
