package com.mihomo.gateway

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startForegroundService(Intent(this, GatewayService::class.java))
        setContent {
            MaterialTheme {
                GatewayScreen()
            }
        }
    }
}

@Composable
fun GatewayScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var status by remember { mutableStateOf("加载中…") }
    var log by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var subUrl by remember { mutableStateOf("") }

    fun perform(block: () -> String) {
        if (busy) return
        busy = true
        scope.launch {
            val out = withContext(Dispatchers.IO) { block() }
            val s = withContext(Dispatchers.IO) { Root.run("sh ${Root.BASE}/status.sh") }
            status = s
            log = out + "\n\n" + withContext(Dispatchers.IO) { Root.run("tail -n 20 ${Root.BASE}/mihomo.log") }
            busy = false
        }
    }

    fun refresh() {
        scope.launch {
            status = withContext(Dispatchers.IO) { Root.run("sh ${Root.BASE}/status.sh") }
            log = withContext(Dispatchers.IO) { Root.run("tail -n 30 ${Root.BASE}/mihomo.log") }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("Mihomo Gateway", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text("root 透明代理网关 · 热点 / 旁路由", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { perform { Root.run("sh ${Root.BASE}/start.sh") } }, enabled = !busy) { Text("启动") }
            Button(onClick = { perform { Root.run("sh ${Root.BASE}/stop.sh") } }, enabled = !busy) { Text("停止") }
            OutlinedButton(onClick = { refresh() }, enabled = !busy) { Text("刷新") }
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, DashboardActivity::class.java)) },
                enabled = !busy
            ) { Text("面板") }
        }

        Spacer(Modifier.height(20.dp))
        Text("节点地址族", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { perform { Root.run("sh ${Root.BASE}/set-ipver.sh auto") } }, enabled = !busy) { Text("自动") }
            OutlinedButton(onClick = { perform { Root.run("sh ${Root.BASE}/set-ipver.sh v6") } }, enabled = !busy) { Text("IPv6") }
            OutlinedButton(onClick = { perform { Root.run("sh ${Root.BASE}/set-ipver.sh v4") } }, enabled = !busy) { Text("IPv4") }
            OutlinedButton(onClick = { perform { Root.run("sh ${Root.BASE}/set-ipver.sh dual") } }, enabled = !busy) { Text("双栈") }
        }

        Spacer(Modifier.height(20.dp))
        Text("订阅", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = subUrl,
            onValueChange = { subUrl = it },
            label = { Text("订阅链接") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { perform { Subscriptions.import(context, subUrl) } }, enabled = !busy) { Text("导入订阅") }
            OutlinedButton(
                onClick = { perform { AssetInstaller.ensure(context) } },
                enabled = !busy
            ) { Text("安装/更新核心") }
        }

        Spacer(Modifier.height(20.dp))
        Text("日志", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(log, style = MaterialTheme.typography.bodySmall)
    }
}