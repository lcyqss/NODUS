package com.mihomo.gateway.ui.settings

import com.mihomo.gateway.model.IpMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 设置页的纯文本映射，便于复用和单独验证。 */

internal fun ipModeHint(mode: IpMode): String = when (mode) {
    IpMode.AUTO -> "探测本机 IPv6，自动二选一"
    IpMode.DUAL -> "由内核双栈择优"
    IpMode.V6 -> "优先 AAAA，回落 IPv4"
    IpMode.V4 -> "优先 A，回落 AAAA"
    IpMode.V6_ONLY -> "只解析 AAAA（严格）"
    IpMode.V4_ONLY -> "只解析 A（严格）"
}

internal fun ipModeDescription(mode: IpMode): String = when (mode) {
    IpMode.AUTO ->
        "启动或网络变化时探测本机 IPv6：可用则全节点 IPv6 优先，否则 IPv4 优先；切换时热重载配置，不重启进程"
    IpMode.DUAL ->
        "不覆写节点的 ip-version，由内核同时解析 A / AAAA，连接时择优（mihomo 默认）"
    IpMode.V6 -> "节点域名优先解析 AAAA，失败时回落到 IPv4"
    IpMode.V4 -> "节点域名优先解析 A，失败时回落到 AAAA"
    IpMode.V6_ONLY -> "只解析 AAAA，节点没有 IPv6 时无法连接（严格模式）"
    IpMode.V4_ONLY -> "只解析 A，节点没有 IPv4 时无法连接（严格模式）"
}

internal fun runtimeLabel(mode: String): String = when (mode) {
    "global" -> "全局"
    "direct" -> "直连"
    else -> "规则"
}

internal fun runtimeHint(mode: String): String = when (mode) {
    "global" -> "全部流量走代理"
    "direct" -> "全部流量不走代理"
    else -> "按分流规则决定走向"
}

internal fun geoResultLabel(result: String, version: String): String = when (result) {
    "成功" -> if (version.isBlank()) "成功" else "成功 · $version"
    "失败" -> "失败"
    else -> "尚未更新"
}

internal fun formatGeoTime(ts: Long): String {
    if (ts <= 0L) return "从未"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
}
