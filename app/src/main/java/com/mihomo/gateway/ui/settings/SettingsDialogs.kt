package com.mihomo.gateway.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.core.HealthReport
import com.mihomo.gateway.data.repo.CoreUpdateInfo
import com.mihomo.gateway.model.AppSettings

/** 通用单选弹窗：iOS 设置里的「取值选择」。 */
@Composable
internal fun OptionDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String?,
    descriptions: Map<String, String> = emptyMap(),
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { (key, label) ->
                    val selected = key == selectedKey
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(key) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { onSelect(key) })
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            descriptions[key]?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun RestoreConfigDialog(
    json: String,
    onJsonChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("恢复配置") },
        text = {
            OutlinedTextField(
                value = json,
                onValueChange = onJsonChange,
                label = { Text("粘贴备份 JSON") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("恢复") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun HealthDialog(
    health: HealthReport?,
    ipv6Gateway: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("运行健康") },
        text = {
            Text(
                if (health == null) {
                    "尚未检查"
                } else {
                    buildString {
                        appendLine("DNS 1053：${if (health.dns) "正常" else "异常"}")
                        appendLine("网关端口：${if (health.ports) "正常" else "异常"}")
                        appendLine("代理 204：${if (health.proxy) "正常" else "异常"}")
                        appendLine("TPROXY：${if (health.tproxy) "正常" else "异常"}")
                        appendLine(
                            "IPv6 代理：" + when {
                                !ipv6Gateway -> "未启用"
                                health.ipv6 -> "正常"
                                else -> "异常"
                            }
                        )
                        if (health.clientIpv6.isNotBlank()) {
                            appendLine("客户端 IPv6（普通设备）：${health.clientIpv6}")
                        }
                        appendLine("DNS 域名：${if (health.dnsDomains) "正常" else "异常"}")
                        appendLine("规则加载：${if (health.rules) "正常" else "异常"}")
                        append(health.detail)
                    }
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
internal fun CoreUpdateDialog(
    info: CoreUpdateInfo,
    currentVersion: String,
    onInstall: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("内核更新") },
        text = {
            Text(
                "当前：$currentVersion\n最新：${info.latest}\n资源：${info.assetName}\n" +
                    "校验：${if (info.sha256.isNullOrBlank()) "官方未提供（不可安装）" else "SHA256 已提供"}\n\n" +
                    "更新会短暂中断网关，失败时会尝试回退上一内核。"
            )
        },
        confirmButton = {
            TextButton(onClick = onInstall) { Text("更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun CoreRollbackDialog(
    currentVersion: String,
    previousVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("回退内核") },
        text = {
            Text("将从 $currentVersion 回退到 $previousVersion，网关会短暂中断，配置和数据不会丢失。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("回退") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun GeoHistoryDialog(
    settings: AppSettings,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Geo 更新记录") },
        text = {
            Text(
                buildString {
                    appendLine("上次检查：${formatGeoTime(settings.geoLastCheckAt)}")
                    appendLine("结果：${geoResultLabel(settings.geoLastResult, settings.geoLastVersion)}")
                    if (settings.geoLastError.isNotBlank()) {
                        appendLine()
                        appendLine("错误：")
                        append(settings.geoLastError)
                    }
                }.trim()
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
internal fun GeoIntervalDialog(
    days: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    OptionDialog(
        title = "Geo 更新间隔",
        options = listOf("7" to "每周", "30" to "每月"),
        selectedKey = days.toString(),
        onSelect = { onSelect(it.toIntOrNull() ?: 7) },
        onDismiss = onDismiss
    )
}

@Composable
internal fun GeoUpdateDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新 Geo 数据") },
        text = {
            Text("将从官方源下载 GeoIP / GeoSite，更新期间会短暂重启内核。")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("开始更新") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
internal fun CleanupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认清理？") },
        text = {
            Text(
                "将停止网关、清除 iptables 规则并删除 " +
                    "${com.mihomo.gateway.core.AppConfig.BASE_DIR}。" +
                    "清理后需要点「重新安装内核」才能继续使用。"
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("清理") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
