package com.mihomo.gateway.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.BuildConfig
import com.mihomo.gateway.core.ApkInstaller
import com.mihomo.gateway.di.AppContainer
import kotlinx.coroutines.launch

@Composable
fun AppUpdateDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val update by AppContainer.appUpdates.available.collectAsState()
    val checking by AppContainer.appUpdates.checking.collectAsState()
    val checkError by AppContainer.appUpdates.error.collectAsState()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (update == null && !checking) AppContainer.appUpdates.checkIfDue(force = true)
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("应用更新") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "当前版本 v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                when {
                    checking -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(Modifier.height(18.dp))
                        Text("正在检查 GitHub Release…", style = MaterialTheme.typography.bodySmall)
                    }

                    checkError != null && update == null -> Text(
                        "检查失败：$checkError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    update == null -> Text("当前已是最新版本", style = MaterialTheme.typography.bodyMedium)

                    else -> {
                        val info = update!!
                        Text(
                            "新版本 v${info.version}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "发布 ${info.tag} · ${formatSize(info.size)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (info.notes.isNotBlank()) {
                            Text(
                                info.notes.take(3000),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("下载中 $progress%", style = MaterialTheme.typography.bodySmall)
                        }
                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (update != null && !checking) {
                TextButton(
                    enabled = !downloading,
                    onClick = {
                        val info = update ?: return@TextButton
                        downloading = true
                        progress = 0
                        message = null
                        scope.launch {
                            val result = AppContainer.appUpdates.download(info) { progress = it }
                            downloading = false
                            result.onSuccess { file ->
                                ApkInstaller.install(context, file)
                                    .onSuccess { message = "已打开系统安装器" }
                                    .onFailure { message = "安装失败：${it.message}" }
                            }.onFailure { message = "下载失败：${it.message}" }
                        }
                    }
                ) { Text(if (downloading) "下载中…" else "下载并安装") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !downloading) { Text("关闭") }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "未知大小"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}
