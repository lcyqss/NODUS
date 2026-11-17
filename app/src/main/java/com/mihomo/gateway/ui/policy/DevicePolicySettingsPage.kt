package com.mihomo.gateway.ui.policy

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.model.NodeTag
import com.mihomo.gateway.ui.icons.GwIcon

/** 设备策略的二级设置页：条件与标签、运行调度、数据。 */
@Composable
internal fun DevicePolicySettingsScreen(
    tags: List<NodeTag>,
    templateCount: Int,
    intervalSec: Int,
    ipIntervalSec: Int,
    onBack: () -> Unit,
    onTags: () -> Unit,
    onTemplates: () -> Unit,
    onInterval: (Int) -> Unit,
    onIpInterval: (Int) -> Unit,
    onBackup: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                GwIcon(GwIcon.Back, tint = MaterialTheme.colorScheme.onSurface)
            }
            Column(Modifier.weight(1f)) {
                Text("策略设置", style = MaterialTheme.typography.titleMedium)
                Text(
                    "条件、调度与数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        SectionLabel("条件与标签")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTags)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("节点标签", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${tags.count { !it.builtin }} 个自定义 · ${tags.count { it.builtin }} 个内置",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onTemplates)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("条件模板", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "$templateCount 个模板 · 可复用条件树",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(Modifier.height(12.dp))
        SectionLabel("运行调度")
        IntervalRow(
            title = "延迟调度间隔",
            subtitle = "仅对设置了延迟阈值的策略生效",
            seconds = intervalSec,
            options = listOf(30 to "30 秒", 60 to "1 分钟", 300 to "5 分钟"),
            onChange = onInterval
        )
        Spacer(Modifier.height(8.dp))
        IntervalRow(
            title = "IP 跟随检查",
            subtitle = "设备 IP 变化后自动重建规则",
            seconds = ipIntervalSec,
            options = listOf(30 to "30 秒", 60 to "1 分钟", 120 to "2 分钟", 300 to "5 分钟"),
            onChange = onIpInterval
        )

        Spacer(Modifier.height(12.dp))
        SectionLabel("数据")
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onBackup)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("备份与恢复", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "导出/导入策略、标签、条件模板",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun IntervalRow(
    title: String,
    subtitle: String,
    seconds: Int,
    options: List<Pair<Int, String>>,
    onChange: (Int) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { menu = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                options.firstOrNull { it.first == seconds }?.second ?: "$seconds 秒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(" ▾", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        menu = false
                        onChange(value)
                    }
                )
            }
        }
    }
}
