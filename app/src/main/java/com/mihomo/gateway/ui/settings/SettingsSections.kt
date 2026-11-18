package com.mihomo.gateway.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mihomo.gateway.core.AppConfig
import com.mihomo.gateway.core.HealthReport
import com.mihomo.gateway.model.AppSettings
import com.mihomo.gateway.model.AppUpdateInfo
import com.mihomo.gateway.ui.components.SettingsDivider
import com.mihomo.gateway.ui.components.SettingsGroup
import com.mihomo.gateway.ui.components.SettingsRow
import com.mihomo.gateway.ui.components.SettingsSubheader
import com.mihomo.gateway.ui.components.SettingsSwitchRow

/** 设置页「网关」分区：地址族、运行模式、IPv6 开关。 */
@Composable
internal fun GatewaySection(
    settings: AppSettings,
    runtimeMode: String,
    ipv6RaBlocked: Boolean,
    enabled: Boolean,
    onIpMode: () -> Unit,
    onRuntimeMode: () -> Unit,
    onBlockIpv6: (Boolean) -> Unit,
    onIpv6Gateway: (Boolean) -> Unit,
    onDirectPrivateLan: (Boolean) -> Unit
) {
    SettingsGroup("网关") {
        SettingsRow(
            title = "节点地址族",
            subtitle = ipModeHint(settings.ipMode),
            value = settings.ipMode.label,
            enabled = enabled,
            onClick = onIpMode
        )
        SettingsDivider()
        SettingsRow(
            title = "运行模式",
            subtitle = runtimeHint(runtimeMode),
            value = runtimeLabel(runtimeMode),
            enabled = enabled,
            onClick = onRuntimeMode
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = "IPv6 拦截",
            subtitle = if (settings.ipv6Gateway) "IPv6 代理开启时不生效" else "关闭后客户端可用 IPv6 直连",
            checked = settings.blockIpv6,
            enabled = enabled && !settings.ipv6Gateway,
            onChange = onBlockIpv6
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = "局域网 IPv6 代理",
            subtitle = when {
                ipv6RaBlocked -> "检测到主路由 RA，旁路由下不可用"
                settings.ipv6Gateway -> "客户端 ULA + IPv6 TPROXY，经节点转发"
                else -> "让客户端 IPv6 字面量也走代理"
            },
            checked = settings.ipv6Gateway,
            enabled = enabled && (!ipv6RaBlocked || settings.ipv6Gateway),
            onChange = onIpv6Gateway
        )
        SettingsDivider()
        SettingsSwitchRow(
            title = "内网直连",
            subtitle = if (settings.directPrivateLan) {
                "访问 10/8、172.16/12、192.168/16 等内网地址不走代理"
            } else {
                "内网地址也会进代理，可能访问不到 NAS / 打印机"
            },
            checked = settings.directPrivateLan,
            enabled = enabled,
            onChange = onDirectPrivateLan
        )
    }
}

/** 设置页「运行组件」分区：内核、Geo 数据与维护。 */
@Composable
internal fun ComponentsSection(
    coreVersion: String,
    corePrevious: String,
    coreProgress: Int?,
    geoVersion: String,
    geoProgress: Int?,
    geoAutoUpdate: Boolean,
    geoIntervalDays: Int,
    geoLastResult: String,
    geoLastVersion: String,
    busy: Boolean,
    onRollback: () -> Unit,
    onCheckCore: () -> Unit,
    onInstallCore: () -> Unit,
    onGeoHistory: () -> Unit,
    onGeoAutoUpdate: (Boolean) -> Unit,
    onGeoInterval: () -> Unit,
    onGeoUpdate: () -> Unit,
    onCleanup: () -> Unit
) {
    SettingsGroup("运行组件") {
        SettingsSubheader("内核")
        SettingsRow(
            title = "内核版本",
            value = coreVersion.ifBlank { "未知" },
            showChevron = false
        )
        SettingsDivider()
        SettingsRow(
            title = "上一内核",
            value = corePrevious.ifBlank { "无" },
            showChevron = false
        )
        SettingsDivider()
        SettingsRow(
            title = "回退上一内核",
            subtitle = if (corePrevious.isBlank()) "暂无可回退版本" else "恢复到上一可用内核",
            enabled = corePrevious.isNotBlank() && !busy,
            onClick = onRollback
        )
        SettingsDivider()
        SettingsRow(
            title = "检查内核更新",
            subtitle = when {
                coreProgress == -1 -> "下载中…"
                coreProgress != null -> "下载中 ${coreProgress}%"
                else -> "从官方 release 检查 Android arm64 内核"
            },
            enabled = !busy,
            onClick = onCheckCore
        )
        SettingsDivider()
        SettingsRow(
            title = "重新安装内核",
            subtitle = "强制从 APK 重装内核、脚本和 Geo 数据",
            enabled = !busy,
            onClick = onInstallCore
        )

        SettingsDivider()
        SettingsSubheader("Geo 数据")
        SettingsRow(
            title = "Geo 版本",
            value = geoVersion.ifBlank { "未知" },
            showChevron = false
        )
        SettingsDivider()
        SettingsRow(
            title = "上次更新",
            value = geoResultLabel(geoLastResult, geoLastVersion),
            onClick = onGeoHistory
        )
        SettingsDivider()
        SettingsRow(
            title = "自动更新",
            value = if (geoAutoUpdate) "开启" else "关闭",
            onClick = { onGeoAutoUpdate(!geoAutoUpdate) }
        )
        SettingsDivider()
        SettingsRow(
            title = "更新间隔",
            value = "每 $geoIntervalDays 天",
            enabled = geoAutoUpdate,
            onClick = onGeoInterval
        )
        SettingsDivider()
        SettingsRow(
            title = "更新 Geo 数据",
            subtitle = when {
                geoProgress == -1 -> "下载中…"
                geoProgress != null -> "下载中 ${geoProgress}%"
                else -> "从官方源下载最新 GeoIP / GeoSite"
            },
            enabled = !busy,
            onClick = onGeoUpdate
        )

        SettingsDivider()
        SettingsSubheader("维护")
        SettingsRow(
            title = "清理运行数据（卸载前）",
            subtitle = "停止网关、清除规则、删除 ${AppConfig.BASE_DIR}",
            onClick = onCleanup
        )
    }
}

/** 设置页「应用」分区：自启、备份与恢复。 */
@Composable
internal fun AppSection(
    autoStart: Boolean,
    onAutoStart: (Boolean) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    SettingsGroup("应用") {
        SettingsSwitchRow(
            title = "开机自启",
            subtitle = "重启后自动启动网关",
            checked = autoStart,
            onChange = onAutoStart
        )
        SettingsDivider()
        SettingsRow(
            title = "备份配置",
            subtitle = "导出规则与设置（分享文本）",
            onClick = onBackup
        )
        SettingsDivider()
        SettingsRow(
            title = "恢复配置",
            subtitle = "粘贴备份内容并导入",
            onClick = onRestore
        )
    }
}

/** 设置页「诊断」分区。 */
@Composable
internal fun DiagnosticsSection(
    health: HealthReport?,
    onLogs: () -> Unit,
    onDiagnostics: () -> Unit,
    onHealth: () -> Unit
) {
    SettingsGroup("诊断") {
        SettingsRow(
            title = "日志",
            subtitle = "实时输出 / 搜索 / 导出",
            onClick = onLogs
        )
        SettingsDivider()
        SettingsRow(
            title = "内核 / DNS / 规则快照",
            subtitle = "root 命令与内核状态",
            onClick = onDiagnostics
        )
        SettingsDivider()
        SettingsRow(
            title = "运行健康",
            value = when {
                health == null -> "未检查"
                health.ok -> "正常"
                else -> "异常"
            },
            onClick = onHealth
        )
    }
}

/** 设置页「关于」分区：版本与应用更新。 */
@Composable
internal fun AboutSection(
    versionName: String,
    appUpdate: AppUpdateInfo?,
    appUpdateChecking: Boolean,
    appUpdateError: String?,
    onCheckUpdate: () -> Unit
) {
    SettingsGroup("关于") {
        SettingsRow(
            title = "版本",
            value = "v$versionName",
            showChevron = false
        )
        SettingsDivider()
        SettingsRow(
            title = "检查应用更新",
            subtitle = when {
                appUpdateChecking -> "正在检查 GitHub Release…"
                appUpdateError != null -> "检查失败，可重试"
                appUpdate != null -> "发现新版本 v${appUpdate.version}"
                else -> "从 GitHub Releases 检查新版本"
            },
            value = when {
                appUpdateChecking -> "检查中"
                appUpdate != null -> "可更新"
                appUpdateError != null -> "失败"
                else -> null
            },
            onClick = onCheckUpdate
        )
    }
}
