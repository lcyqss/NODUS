package com.mihomo.gateway.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.ui.icons.GwIcon

/**
 * iOS 风格插入式分组：组标题（小灰字）+ 圆角卡片，卡片内用 [SettingsDivider] 分隔行。
 */
@Composable
fun SettingsGroup(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface),
            content = content
        )
    }
}

/** 分组内的次级标题，用于内核 / Geo 数据等分区。 */
@Composable
fun SettingsSubheader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp)
    )
}

/** 分组内行分隔线，左侧留出缩进。 */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
    )
}

/** 设置行：标题（可带副标题）+ 右侧值 / 自定义尾部 / 箭头。 */
@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    enabled: Boolean = true,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick)
                else Modifier
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        trailing()
        if (showChevron) {
            Text(
                "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** 带开关的设置行。 */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outline
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** 二级页标题：返回箭头 + 标题 + 右侧操作。 */
@Composable
fun SubPageHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            GwIcon(GwIcon.Back, tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}
